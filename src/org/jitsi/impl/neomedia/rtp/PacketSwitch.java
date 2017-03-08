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
package org.jitsi.impl.neomedia.rtp;

import org.ice4j.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;


/**
 * @author Boris Grozev
 */
public class PacketSwitch
{
    private MediaStream[] mediaStreams = new MediaStream[0];
    private final Object mediaStreamsSyncRoot = new Object();
    private final PacketPool packetPool = new PacketPool(1000, null);
    private final RawPacketQueue queue
        = new RawPacketQueue(1000 /* capacity */,
                             false /* copy */,
                             false /* stats */,
                             "PacketSwitch queue",
                             new PacketQueue.PacketHandler<RawPacket>()
                             {
                                 @Override
                                 public boolean handlePacket(RawPacket pkt)
                                 {
                                     return PacketSwitch.this.writePacket(pkt);
                                 }
                             });

    public PacketSwitch()
    {
    }

    public void addMediaStream(MediaStream mediaStream)
    {
        if (mediaStream == null)
            return;
        synchronized (mediaStreamsSyncRoot)
        {
            mediaStreams
                = ArrayUtils.add(mediaStreams, MediaStream.class, mediaStream);
        }
    }

    public void removeMediaStream(MediaStream mediaStream)
    {
        if (mediaStream == null)
            return;

        synchronized (mediaStreamsSyncRoot)
        {
            mediaStreams
                = ArrayUtils.remove(mediaStreams, MediaStream.class, mediaStream);
        }
    }

    private boolean writePacket(RawPacket pkt)
    {
        MediaStream[] mediaStreams = this.mediaStreams;

        MediaStream source = pkt.getMediaStream();
        for (MediaStream ms : mediaStreams)
        {
            if (!ms.equals(source))
            {
                ms.writePacket(pkt, source);
            }
        }
        return true;
    }

    public void close()
    {
        packetPool.close();
        queue.close();
    }
}