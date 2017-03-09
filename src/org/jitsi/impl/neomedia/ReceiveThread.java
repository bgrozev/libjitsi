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

import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * TODO: return packets to the pool
 * @author Boris Grozev
 */
public class ReceiveThread
{
    private final DatagramSocket socket;
    private final PacketPool packetPool;
    private final PacketSwitch packetSwitch;
    private final MediaStream mediaStream;
    private final DatagramPacket datagramPacket = new DatagramPacket(new byte[0], 0);

    private boolean closed = false;
    private boolean ioError = false;

    private Thread receiveThread;

    ReceiveThread(MediaStream mediaStream, DatagramSocket socket)
    {
        this.mediaStream = Objects.requireNonNull(mediaStream, "mediaStream");
        this.socket = Objects.requireNonNull(socket, "socket");
        this.packetSwitch = mediaStream.getPacketSwitch();
        this.packetPool = new PacketPool(300, packetSwitch.getPacketPool());

        receiveThread = new Thread()
        {
            @Override
            public void run()
            {
                runInReceiveThread();
            }
        };
        receiveThread.setName("receive thread");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /**
     * Listens for incoming datagram packets, stores them for reading by the
     * <tt>read</tt> method and notifies the local <tt>transferHandler</tt>
     * that there's data to be read.
     */
    private void runInReceiveThread()
    {
        RawPacket[] array = new RawPacket[1];
        while (!closed)
        {
            RawPacket rawPacket = packetPool.getRawPacket(1600);
            datagramPacket.setData(
                rawPacket.getBuffer(),
                rawPacket.getOffset(),
                rawPacket.getLength());
            try
            {
                socket.receive(datagramPacket);
            }
            catch (IOException e)
            {
                ioError = true;
                break;
            }

            rawPacket.setBuffer(datagramPacket.getData());
            rawPacket.setOffset(datagramPacket.getOffset());
            rawPacket.setLength(datagramPacket.getLength());

            boolean rtcp = RTCPPacketPredicate.INSTANCE.test(rawPacket);
            TransformEngine transformEngine = mediaStream.getTransformEngineChain();
            PacketTransformer packetTransformer
                = rtcp ? transformEngine.getRTCPTransformer() : transformEngine.getRTPTransformer();

            array[0] = rawPacket;
            for (RawPacket pkt : packetTransformer.reverseTransform(array))
            {
                pkt.setMediaStream(mediaStream);
                packetSwitch.addPacket(pkt);
            }

            // write to PacketSwitch
        }
    }

    void close()
    {
        closed = true;
    }
}
