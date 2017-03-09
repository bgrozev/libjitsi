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
package org.jitsi.impl.neomedia;

import java.net.*;
import java.util.*;

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.rtp.remotebitrateestimator.*;
import org.jitsi.impl.neomedia.rtp.sendsidebandwidthestimation.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;

/**
 * Extends <tt>MediaStreamImpl</tt> in order to provide an implementation of
 * <tt>VideoMediaStream</tt>.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author George Politis
 * @author Boris Grozev
 */
public class VideoMediaStreamImpl
    extends MediaStreamImpl
    implements VideoMediaStream
{
    /**
     * The <tt>Logger</tt> used by the <tt>VideoMediaStreamImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(VideoMediaStreamImpl.class);

    /**
     * The <tt>RecurringRunnableExecutor</tt> to be utilized by the
     * <tt>MediaStreamImpl</tt> class and its instances.
     */
    private static final RecurringRunnableExecutor
        recurringRunnableExecutor = new RecurringRunnableExecutor(
        VideoMediaStreamImpl.class.getSimpleName());

    /**
     * The instance that is aware of all of the {@link RTPEncodingDesc} of the
     * remote endpoint.
     */
    private final MediaStreamTrackReceiver mediaStreamTrackReceiver
        = new MediaStreamTrackReceiver(this);

    /**
     * The transformer which handles outgoing rtx (RFC-4588) packets for this
     * {@link VideoMediaStreamImpl}.
     */
    private final RtxTransformer rtxTransformer = new RtxTransformer(this);

    /**
     * The instance that terminates RRs and REMBs.
     */
    private final RTCPReceiverFeedbackTermination rtcpFeedbackTermination
        = new RTCPReceiverFeedbackTermination(this);

    /**
     *
     */
    private final PaddingTermination paddingTermination = new PaddingTermination();

    /**
     * The <tt>RemoteBitrateEstimator</tt> which computes bitrate estimates for
     * the incoming RTP streams.
     */
    private final RemoteBitrateEstimator remoteBitrateEstimator
        = new RemoteBitrateEstimatorSingleStream(
                new RemoteBitrateObserver()
                {
                    @Override
                    public void onReceiveBitrateChanged(
                            Collection<Integer> ssrcs,
                            long bitrate)
                    {
                        VideoMediaStreamImpl.this
                            .remoteBitrateEstimatorOnReceiveBitrateChanged(
                                    ssrcs,
                                    bitrate);
                    }
                });

    /**
     * The {@link BandwidthEstimator} which estimates the available bandwidth
     * from this endpoint to the remote peer.
     */
    private BandwidthEstimatorImpl bandwidthEstimator;

    /**
     * The {@link CachingTransformer} which caches outgoing/incoming packets
     * from/to this {@link VideoMediaStreamImpl}.
     */
    private CachingTransformer cachingTransformer;

    /**
     * Initializes a new <tt>VideoMediaStreamImpl</tt> instance which will use
     * the specified <tt>MediaDevice</tt> for both capture and playback of video
     * exchanged via the specified <tt>StreamConnector</tt>.
     *
     * @param srtpControl a control which is already created, used to control
     * the srtp operations.
     */
    public VideoMediaStreamImpl(SrtpControl srtpControl, DatagramSocket socket)
    {
        super(srtpControl, MediaType.VIDEO, null, socket);

        // Register the RemoteBitrateEstimator with the
        // RecurringRunnableExecutor.
        RemoteBitrateEstimator remoteBitrateEstimator
            = getRemoteBitrateEstimator();

        if (remoteBitrateEstimator instanceof RecurringRunnable)
        {
            recurringRunnableExecutor.registerRecurringRunnable(
                    (RecurringRunnable) remoteBitrateEstimator);
        }

        recurringRunnableExecutor.registerRecurringRunnable(rtcpFeedbackTermination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RtxTransformer getRtxTransformer()
    {
        return rtxTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        try
        {
            super.close();
        }
        finally
        {
            // Deregister the RemoteBitrateEstimator with the
            // RecurringRunnableExecutor.
            RemoteBitrateEstimator remoteBitrateEstimator
                = getRemoteBitrateEstimator();

            if (remoteBitrateEstimator instanceof RecurringRunnable)
            {
                recurringRunnableExecutor.deRegisterRecurringRunnable(
                        (RecurringRunnable) remoteBitrateEstimator);
            }

            if (cachingTransformer != null)
            {
                recurringRunnableExecutor.deRegisterRecurringRunnable(
                    cachingTransformer);
            }

            if (rtcpFeedbackTermination != null)
            {
                recurringRunnableExecutor
                    .deRegisterRecurringRunnable(rtcpFeedbackTermination);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaStreamTrackReceiver getMediaStreamTrackReceiver()
    {
        return mediaStreamTrackReceiver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RemoteBitrateEstimator getRemoteBitrateEstimator()
    {
        return remoteBitrateEstimator;
    }

    /**
     * Notifies this <tt>VideoMediaStreamImpl</tt> that
     * {@link #remoteBitrateEstimator} has computed a new bitrate estimate for
     * the incoming streams.
     *
     * @param ssrcs
     * @param bitrate
     */
    private void remoteBitrateEstimatorOnReceiveBitrateChanged(
            Collection<Integer> ssrcs,
            long bitrate)
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CachingTransformer createCachingTransformer()
    {
        if (cachingTransformer == null)
        {
            cachingTransformer = new CachingTransformer(this);
            recurringRunnableExecutor.registerRecurringRunnable(
                cachingTransformer);
        }

        return cachingTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected RetransmissionRequesterImpl createRetransmissionRequester()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null && cfg.getBoolean(REQUEST_RETRANSMISSIONS_PNAME, false))
        {
            return new RetransmissionRequesterImpl(this);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected RTCPReceiverFeedbackTermination getRTCPTermination()
    {
        return rtcpFeedbackTermination;
    }

    /**
     * {@inheritDoc}
     */
    protected PaddingTermination getPaddingTermination()
    {
        return paddingTermination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BandwidthEstimator getOrCreateBandwidthEstimator()
    {
        if (bandwidthEstimator == null)
        {
            bandwidthEstimator = new BandwidthEstimatorImpl(this);
            logger.info("Creating a BandwidthEstimator for stream " + this);
        }
        return bandwidthEstimator;
    }
}
