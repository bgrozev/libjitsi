package org.jitsi.service.neomedia;

/**
 * Created by boris on 09/03/2017.
 */
public interface RawPacketFilter
{
    boolean accept(RawPacket pkt);
}
