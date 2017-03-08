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

import org.jitsi.service.neomedia.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * TODO: implement and experiment with different memory management strategies:
 * 1. just use 'new byte[]' every time
 * 2. use cached buffers of fixed size (e.g. 1600 bytes)
 * 3. use cached buffers with the exact size requested
 * 4. add artificial padding (e.g. 20 in front, 20 bytes in the end) to avoid
 *    reallocation for e.g. SRTP and RTP header extensions
 *
 * @author Boris Grozev
 */
public class PacketPool
{
    private final static PacketPool globalPool = new PacketPool(-1, null);

    /**
     * The pool of <tt>RawPacket</tt> instances to reduce their allocations and
     * garbage collection.
     */
    private final Queue<RawPacket> queue;
    private final int maxSize;
    private final PacketPool parent;

    public PacketPool(int maxSize, PacketPool parent)
    {
        this.maxSize = maxSize;
        this.parent = parent;
        queue = new LinkedBlockingQueue<>(maxSize);
    }

    public RawPacket getRawPacket(int size)
    {
        RawPacket pkt = queue.poll();
        if (pkt == null && parent != null)
        {
            return parent.getRawPacket(size);
        }

        if (pkt == null)
        {
            pkt = new RawPacket();
        }

        byte[] buf = pkt.getBuffer();
        if (buf == null || buf.length < size + 40)
        {
            buf = new byte[Math.min(size + 40, 1600)];
            pkt.setBuffer(buf);
        }

        pkt.setOffset(20);
        pkt.setLength(size);
        return pkt;
    }

    /**
     * Return true if the packet was accepted.
     * @param pkt
     * @return
     */
    public boolean returnPacket(RawPacket pkt)
    {
        if (queue.offer(pkt))
        {
            return true;
        }
        else if (parent.returnPacket(pkt))
        {
            return true;
        }
        return false;
    }

    public void returnAllPackets(Collection<RawPacket> packets)
    {
        for (RawPacket pkt : packets)
        {
            if (!returnPacket(pkt))
            {
                // short-circuit
                return;
            }
        }
    }

    public void close()
    {
        if (parent != null)
        {
            returnAllPackets(queue);
        }
        else
        {
            globalPool.returnAllPackets(queue);
        }
    }
}
