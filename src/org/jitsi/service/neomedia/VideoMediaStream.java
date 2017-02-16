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

import org.jitsi.service.neomedia.rtp.*;

/**
 * Extends the <tt>MediaStream</tt> interface and adds methods specific to
 * video streaming.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public interface VideoMediaStream
    extends MediaStream
{
    /**
     * The name of the property used to control whether {@link VideoMediaStream}
     * should request retransmissions for lost RTP packets using RTCP NACK.
     */
    String REQUEST_RETRANSMISSIONS_PNAME
            = VideoMediaStream.class.getName() + ".REQUEST_RETRANSMISSIONS";

    /**
     * Gets the <tt>RemoteBitrateEstimator</tt> of this
     * <tt>VideoMediaStream</tt>.
     *
     * @return the <tt>RemoteBitrateEstimator</tt> of this
     * <tt>VideoMediaStream</tt> if any; otherwise, <tt>null</tt>
     */
    RemoteBitrateEstimator getRemoteBitrateEstimator();

    /**
     * Creates an instance of {@link BandwidthEstimator} for this
     * {@link MediaStream} if one doesn't already exist. Returns the instance.
     */
    public BandwidthEstimator getOrCreateBandwidthEstimator();
}
