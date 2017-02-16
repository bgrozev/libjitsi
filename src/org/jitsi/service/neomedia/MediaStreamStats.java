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
package org.jitsi.service.neomedia;

import java.awt.*;

import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.service.neomedia.stats.*;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 */
public interface MediaStreamStats
{
    /**
     * Gets the detailed statistics about the RTCP reports sent and received by
     * the associated local peer.
     *
     * @return the detailed statistics about the RTCP reports sent and received
     * by the associated local peer
     */
    RTCPReports getRTCPReports();

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @deprecated use the appropriate method from {@link MediaStreamStats2}
     * instead.
     * @return The RTT computed with the RTCP feedback. Returns <tt>-1</tt> if
     * the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    @Deprecated
    long getRttMs();

    /**
     * Adds a listener which will be notified when RTCP packets are received.
     * @param listener the listener.
     */
    void addRTCPPacketListener(RTCPPacketListener listener);

    /**
     * Removes a listener from the list of listeners which will be notified when
     * RTCP packets are received.
     * @param listener the listener.
     */
    void removeRTCPPacketListener(RTCPPacketListener listener);
}
