/*
 * Copyright @ 2015-2017 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.neomedia;

import org.ice4j.util.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.Logger;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * TODO: return packets to the pool
 * @author Boris Grozev
 */
public class SendThread
{
    private static final Logger logger
        = Logger.getLogger(SendThread.class);
    public static final int PACKET_QUEUE_CAPACITY = 1000;
    private final DatagramSocket socket;
    private final PacketPool packetPool;
    private final MediaStream mediaStream;
    private final DatagramPacket datagramPacket = new DatagramPacket(new byte[0], 0);

    private boolean closed = false;
    private boolean ioError = false;

    private final RawPacketQueue queue;

    SendThread(MediaStream mediaStream, DatagramSocket socket)
    {
        this.mediaStream = Objects.requireNonNull(mediaStream, "mediaStream");
        this.socket = Objects.requireNonNull(socket, "socket");
        PacketSwitch packetSwitch = mediaStream.getPacketSwitch();
        this.packetPool = new PacketPool(300, packetSwitch.getPacketPool());

        queue
            = new RawPacketQueue(1000 /* capacity */,
                                 false /* copy */,
                                 false /* stats */,
                                 "PacketSwitch queue",
                                 new PacketQueue.PacketHandler<RawPacket>()
                                 {
                                     @Override
                                     public boolean handlePacket(RawPacket pkt)
                                     {
                                         return SendThread.this.writePacket(pkt);
                                     }
                                 });
    }

    boolean writePacket(RawPacket pkt)
    {
        TransformEngine transformEngine = mediaStream.getTransformEngineChain();
        boolean rtcp = RTCPPacketPredicate.INSTANCE.test(pkt);
        PacketTransformer transformer
            = rtcp ? transformEngine.getRTCPTransformer() : transformEngine.getRTPTransformer();
        TransformEngineChain.PacketTransformerChain transformerAsChain
            = (TransformEngineChain.PacketTransformerChain) transformer;

        // TODO reuse ?
        RawPacket[] array = new RawPacket[1];
        array[0] = pkt;
        RawPacket [] outPkts = transformerAsChain.transform(array, (TransformEngine) pkt.getContext());

        for (RawPacket outPkt : outPkts)
        {
            send(outPkt);
        }

        return true;
    }

    private void send(RawPacket pkt)
    {
        // TODO
        try
        {
            socket.send(new DatagramPacket(pkt.getBuffer(), pkt.getOffset(),
                                           pkt.getLength()));
        }
        catch (IOException ioe)
        {
            logger.warn("failed to send: "+ioe);
        }
    }

    void close()
    {
        queue.close();
    }
}
