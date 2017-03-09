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

import java.util.*;
import java.util.List;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.remotebitrateestimator.*;
import org.jitsi.impl.neomedia.stats.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * Note: please do not add more code here. New code should be added to
 * {@link MediaStreamStats2Impl} instead, where we can manage the complexity
 * and consistency better.
 *
 * @author Vincent Lucas
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 */
public class MediaStreamStatsImpl
    implements MediaStreamStats
{
    /**
     * Enumeration of the direction (DOWNLOAD or UPLOAD) used for the stats.
     */
    public enum StreamDirection
    {
        DOWNLOAD,
        UPLOAD
    }

    /**
     * The <tt>Logger</tt> used by the <tt>MediaStreamImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamStatsImpl.class);

    /**
     * Keeps track of when a given NTP time (found in an SR) has been received.
     * This is used to compute the correct RTT in the translator case.
     */
    private final Map<Long, Long> emission2reception
        = Collections.synchronizedMap(new LRUCache<Long, Long>(100));

    /**
     * The last jitter received/sent in a RTCP feedback (in RTP timestamp
     * units).
     */
    private double[] jitterRTPTimestampUnits = {0, 0};

    /**
     * The source data stream to analyze in order to compute the stats.
     */
    private final MediaStreamImpl mediaStreamImpl;

    /**
     * The <tt>RTCPReportListener</tt> which listens to {@link #rtcpReports}
     * about the sending and the receiving of RTCP sender/receiver reports and
     * updates this <tt>MediaStreamStats</tt> with their feedback reports.
     */
    private final RTCPReportListener rtcpReportListener
        = new RTCPReportAdapter()
        {
            /**
             * {@inheritDoc}
             *
             * Updates this <tt>MediaStreamStats</tt> with the received feedback
             * (report).
             */
            @Override
            public void rtcpReportReceived(RTCPReport report)
            {
                MediaStreamStatsImpl.this.rtcpReportReceived(report);
            }

            /**
             * {@inheritDoc}
             *
             * Updates this <tt>MediaStreamStats</tt> with the sent feedback
             * (report).
             */
            @Override
            public void rtcpReportSent(RTCPReport report)
            {
                List<?> feedbackReports = report.getFeedbackReports();

                if (!feedbackReports.isEmpty())
                {
                    updateNewSentFeedback(
                            (RTCPFeedback) feedbackReports.get(0));
                }
            }
        };

    /**
     * The detailed statistics about the RTCP reports sent and received by the
     * associated local peer.
     */
    private final RTCPReports rtcpReports = new RTCPReports();

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1,
     * subsection "delay since last SR (DLSR): 32 bits").
     * -1 if the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    private long rttMs = -1;

    /**
     * The list of listeners to be notified when RTCP packets are received.
     */
    private final List<RTCPPacketListener> rtcpPacketListeners
        = Collections.synchronizedList(new LinkedList<RTCPPacketListener>());

    /**
     * Creates a new instance of stats concerning a MediaStream.
     *
     * @param mediaStreamImpl The MediaStreamImpl used to compute the stats.
     */
    public MediaStreamStatsImpl(MediaStreamImpl mediaStreamImpl)
    {
        this.mediaStreamImpl = mediaStreamImpl;

        getRTCPReports().addRTCPReportListener(rtcpReportListener);
    }

    /**
     * Computes the RTT with the data (LSR and DLSR) contained in the last
     * RTCP Sender Report (RTCP feedback). This RTT computation is based on
     * RFC3550, section 6.4.1, subsection "delay since last SR (DLSR): 32
     * bits".
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     *
     * @return The RTT in milliseconds, or -1 if the RTT is not computable.
     */
    private int computeRTTInMs(RTCPFeedback feedback)
    {
        long lsr = feedback.getLSR();
        long dlsr = feedback.getDLSR();
        int rtt = -1;

        // The RTCPFeedback may represents a Sender Report without any report
        // blocks (and so without LSR and DLSR)
        if (lsr > 0 && dlsr > 0)
        {
            long arrivalMs = System.currentTimeMillis();

            // If we are translating, the NTP timestamps we include in outgoing
            // SRs are based on the actual sender's clock.
            /* FFFF
            RTPTranslator translator = mediaStreamImpl.getRTPTranslator();
            if (translator != null)
            {
                StreamRTPManager receiveRTPManager = translator
                    .findStreamRTPManagerByReceiveSSRC((int) feedback.getSSRC());

                if (receiveRTPManager != null)
                {
                    MediaStream receiveStream
                        = receiveRTPManager.getMediaStream();

                    MediaStreamStatsImpl stats
                        = (MediaStreamStatsImpl) receiveStream.getMediaStreamStats();

                    lsr = stats.emission2reception.get(lsr);
                }
                else
                {
                    // feedback.getSSRC() might refer to the RTX SSRC but the
                    // translator doesn't know about the RTX SSRC because of the
                    // de-RTXification step. In the translator case if we can't
                    // map an emission time to a receipt time, we're bound to
                    // compute the wrong RTT, so here we return -1.
                    if (logger.isDebugEnabled())
                    {
                        logger.debug(
                            "invalid_rtt,stream=" + mediaStreamImpl.hashCode()
                                + " ssrc=" + feedback.getSSRC()
                                + ",now=" + arrivalMs
                                + ",lsr=" + lsr
                                + ",dlsr=" + dlsr);
                    }

                    return -1;
                }
            }
            */

            long arrivalNtp = TimeUtils.toNtpTime(arrivalMs);
            long arrival = TimeUtils.toNtpShortFormat(arrivalNtp);

            long ntprtd = arrival - lsr - dlsr;
            long rttLong;
            if (ntprtd >= 0)
            {
                rttLong = TimeUtils.ntpShortToMs(ntprtd);
            }
            else
            {
            /*
             * Even if ntprtd is negative we compute delayLong
             * as it might round to zero.
             * ntpShortToMs expect positive numbers.
             */
                rttLong = -TimeUtils.ntpShortToMs(-ntprtd);
            }

            // Values over 3s are suspicious and likely indicate a bug.
            if (rttLong < 0 || rttLong  >= 3000)
            {
                logger.warn(
                    "invalid_rtt,stream=" + mediaStreamImpl.hashCode()
                        + " ssrc=" + feedback.getSSRC()
                        + ",rtt=" + rttLong
                        + ",now=" + arrivalMs
                        + ",lsr=" + lsr
                        + ",dlsr=" + dlsr);

                rtt = -1;
            }
            else
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug(
                        "rtt,stream= " + mediaStreamImpl.hashCode()
                            + " ssrc=" + feedback.getSSRC()
                            + ",rtt=" + rttLong
                            + ",now=" + arrivalMs
                            + ",lsr=" + lsr
                            + ",dlsr=" + dlsr);
                }
                rtt = (int) rttLong;
            }
        }

        return rtt;
    }

    /**
     * Gets the RTP clock rate associated with the <tt>MediaStream</tt>.
     * @return the RTP clock rate associated with the <tt>MediaStream</tt>.
     */
    private double getRtpClockRate()
    {
        MediaType mediaType = mediaStreamImpl.getMediaType();

        return MediaType.VIDEO.equals(mediaType) ? 90000 : 48000;
    }

    /**
     * Converts from RTP time units (using the assumed RTP clock rate of the
     * media stream) to milliseconds. Returns -1D if an appropriate RTP clock
     * rate cannot be found.
     * @param rtpTime the RTP time units to convert.
     * @return the milliseconds corresponding to <tt>rtpTime</tt> RTP units.
     */
    private double rtpTimeToMs(double rtpTime)
    {
        double rtpClockRate = getRtpClockRate();
        if (rtpClockRate <= 0)
            return -1D;
        return (rtpTime / rtpClockRate) * 1000;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPReports getRTCPReports()
    {
        return rtcpReports;
    }

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns -1 if the RTT
     * has not been computed yet. Otherwise the RTT in ms.
     */
    public long getRttMs()
    {
        return rttMs;
    }

    /**
     * Sets a specific value on {@link #rttMs}. If there is an actual difference
     * between the old and the new values, notifies the (known)
     * <tt>CallStatsObserver</tt>s.
     *
     * @param rttMs the value to set on <tt>MediaStreamStatsImpl.rttMs</tt>
     */
    private void setRttMs(long rttMs)
    {
        if (this.rttMs != rttMs)
        {
            this.rttMs = rttMs;

            // Notify the CallStatsObservers.
            rttMs = getRttMs();
            if (rttMs >= 0)
            {
                // RemoteBitrateEstimator is a CallStatsObserver and
                // VideoMediaStream has a RemoteBitrateEstimator.
                MediaStreamImpl mediaStream = this.mediaStreamImpl;

                if (mediaStream instanceof VideoMediaStream)
                {
                    RemoteBitrateEstimator remoteBitrateEstimator
                        = mediaStream.getRemoteBitrateEstimator();

                    if (remoteBitrateEstimator instanceof CallStatsObserver)
                    {
                        ((CallStatsObserver) remoteBitrateEstimator)
                            .onRttUpdate(
                                    /* avgRttMs */ rttMs,
                                    /* maxRttMs*/ rttMs);
                    }
                }
            }
        }
    }

    /**
     * Updates the jitter stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the jitter.
     */
    private void updateJitterRTPTimestampUnits(
            RTCPFeedback feedback,
            StreamDirection streamDirection)
    {
        // Updates the download jitter in RTP timestamp units. There is no need
        // to compute a jitter average, since (cf. RFC3550, section 6.4.1 SR:
        // Sender Report RTCP Packet, subsection interarrival jitter: 32 bits)
        // the value contained in the RTCP sender report packet contains a mean
        // deviation of the jitter.
        jitterRTPTimestampUnits[streamDirection.ordinal()]
            = feedback.getJitter();

        MediaStreamStats2Impl extended = getExtended();
        extended.updateJitter(
            feedback.getSSRC(),
            streamDirection,
            rtpTimeToMs(feedback.getJitter()));
    }

    /**
     * Updates this stream stats with the new feedback received.
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     */
    private void updateNewReceivedFeedback(RTCPFeedback feedback)
    {
        StreamDirection streamDirection = StreamDirection.UPLOAD;

        updateJitterRTPTimestampUnits(feedback, streamDirection);

        // Updates the loss rate with the RTCP sender report feedback, since
        // this is the only information source available for the upload stream.
        long uploadNewNbRecv = feedback.getXtndSeqNum();

        // Computes RTT.
        int rtt = computeRTTInMs(feedback);
        // If a new RTT could not be computed based on this feedback, keep the
        // old one.
        if (rtt >= 0)
        {
            setRttMs(rtt);

            MediaStreamStats2Impl extended = getExtended();
            extended.updateRtt(feedback.getSSRC(), rtt);
        }
    }

    /**
     * Updates this stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     */
    private void updateNewSentFeedback(RTCPFeedback feedback)
    {
        updateJitterRTPTimestampUnits(feedback, StreamDirection.DOWNLOAD);

        // No need to update the download loss as we have a more accurate value
        // in the global reception stats, which are updated for each new packet
        // received.
    }

    /**
     * Notifies this instance that an RTCP REMB packet was received.
     * @param remb the packet.
     */
    public void rembReceived(RTCPREMBPacket remb)
    {
        if (remb != null)
        {
            synchronized (rtcpPacketListeners)
            {
                for (RTCPPacketListener listener : rtcpPacketListeners)
                {
                    listener.rembReceived(remb);
                }
            }
        }
    }

    /**
     * Notifies this instance that an RTCP NACK packet was received.
     * @param nack the packet.
     */
    public void nackReceived(NACKPacket nack)
    {
        if (nack != null)
        {
            synchronized (rtcpPacketListeners)
            {
                for (RTCPPacketListener listener : rtcpPacketListeners)
                {
                    listener.nackReceived(nack);
                }
            }
        }
    }

    /**
     * Notifies this instance that an RTCP SR packet was received.
     * @param sr the packet.
     */
    public void srReceived(RTCPSRPacket sr)
    {
        if (sr != null)
        {
            long emisionTime = TimeUtils.toNtpShortFormat(
                TimeUtils.constuctNtp(sr.ntptimestampmsw, sr.ntptimestamplsw));

            long arrivalTime = TimeUtils.toNtpShortFormat(
                TimeUtils.toNtpTime(System.currentTimeMillis()));

            emission2reception.put(emisionTime, arrivalTime);

            synchronized (rtcpPacketListeners)
            {
                for (RTCPPacketListener listener : rtcpPacketListeners)
                {
                    listener.srReceived(sr);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addRTCPPacketListener(RTCPPacketListener listener)
    {
        if (listener != null)
        {
            rtcpPacketListeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeRTCPPacketListener(RTCPPacketListener listener)
    {
        if (listener != null)
        {
            rtcpPacketListeners.remove(listener);
        }
    }

    /**
     * Notifies this instance that a specific RTCP RR or SR report was received
     * by {@link #rtcpReports}.
     *
     * @param report the received RTCP RR or SR report
     */
    private void rtcpReportReceived(RTCPReport report)
    {
        // reception report blocks
        List<RTCPFeedback> feedbackReports = report.getFeedbackReports();

        if (!feedbackReports.isEmpty())
        {
            MediaStreamStats2Impl extended = getExtended();
            for (RTCPFeedback rtcpFeedback : feedbackReports)
            {
                updateNewReceivedFeedback(rtcpFeedback);
                extended.rtcpReceiverReportReceived(
                    rtcpFeedback.getSSRC(),
                    rtcpFeedback.getFractionLost());
            }
        }
    }

    /**
     * @return this instance as a {@link MediaStreamStats2Impl}.
     */
    private MediaStreamStats2Impl getExtended()
    {
        return mediaStreamImpl.getMediaStreamStats();
    }
}
