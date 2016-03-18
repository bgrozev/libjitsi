/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * @author Boris Grozev
 */
public class ChecksumEngine
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * Initializes a new {@link ChecksumEngine} instance.
     */
    public ChecksumEngine()
    {
        super(RTPPacketPredicate.INSTANCE);
    }

    /**
     * Implements {@link SinglePacketTransformer#reverseTransform(RawPacket)}.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        int hl = pkt.getHeaderLength();
        int pl = pkt.getPayloadLength();
        if (hl < 0 || pl <0 || (hl+pl != pkt.getLength()))
        {
            System.err.println("Ops, nevermind2 checksum");
            return pkt;
        }

        byte checksum = pkt.getBuffer()[pkt.getOffset()+pkt.getLength()-1];
        pkt.setLength(pkt.getLength()-1);
        if (checksum != getChecksum(pkt))
        {
            System.err.println("Woo-hoo, we fucked up a packet. checksum pt="+pkt.getPayloadType()+" "+pkt.getBuffer()[pkt.getOffset()+pkt.getHeaderLength()]);
        }
        else
        {
            System.err.println("Another clean packet. checksum");
        }

        return pkt;
    }
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        int hl = pkt.getHeaderLength();
        int pl = pkt.getPayloadLength();
        if (hl < 0 || pl <0 || (hl+pl != pkt.getLength()))
        {
            System.err.println("Ops, nevermind checksum");
            return pkt;
        }

        byte checksum = getChecksum(pkt);
        byte[] newBuf = pkt.getBuffer();
        int newOff = pkt.getOffset();
        //if (newBuf.length < pkt.getOffset()+pkt.getLength()+1)
        {
            newBuf = new byte[pkt.getLength()+1];
            newOff = 0;
            System.arraycopy(pkt.getBuffer(), pkt.getOffset(), newBuf, 0, pkt.getLength());
        }
        newBuf[newOff+pkt.getLength()] = checksum;

        pkt.setBuffer(newBuf);
        pkt.setOffset(newOff);
        pkt.setLength(pkt.getLength() + 1);
        System.err.println("Added a checksum.");

        return pkt;
    }

    private byte getChecksum(RawPacket pkt)
    {
        byte res = 0;
        byte[] buf = pkt.getBuffer();
        for (int i = pkt.getPayloadOffset(); i < pkt.getPayloadOffset()+pkt.getPayloadLength(); i++)
        {
            res ^= buf[i];
        }

        return res;
    }

    /**
     * Implements {@link TransformEngine#getRTPTransformer()}.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Implements {@link TransformEngine#getRTCPTransformer()}.
     *
     * This <tt>TransformEngine</tt> does not transform RTCP packets.
     *
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

}
