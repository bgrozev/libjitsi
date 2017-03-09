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

import javax.media.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.stats.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.csrc.*;
import org.jitsi.impl.neomedia.transform.fec.*;
import org.jitsi.impl.neomedia.transform.pt.*;
import org.jitsi.impl.neomedia.transform.rtcp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

/**
 * Implements <tt>MediaStream</tt> using JMF.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Sebastien Vincent
 * @author Boris Grozev
 * @author George Politis
 */
public class MediaStreamImpl
    extends AbstractMediaStream
{
    /**
     * The <tt>Logger</tt> used by the <tt>MediaStreamImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamImpl.class);

    /**
     * The map of currently active <tt>RTPExtension</tt>s and the IDs that they
     * have been assigned for the lifetime of this <tt>MediaStream</tt>.
     */
    private final Map<Byte, RTPExtension> activeRTPExtensions
        = new Hashtable<>();

    /**
     * The <tt>MediaDirection</tt> in which this <tt>MediaStream</tt> is allowed
     * to stream media.
     */
    private MediaDirection direction;

    /**
     * The <tt>Map</tt> of associations in this <tt>MediaStream</tt> and the
     * <tt>RTPManager</tt> it utilizes of (dynamic) RTP payload types to
     * <tt>MediaFormat</tt>s.
     */
    private final Map<Byte, MediaFormat> dynamicRTPPayloadTypes
        = new HashMap<>();

    /**
     * Our own SSRC identifier.
     *
     * XXX(gp) how about taking the local source ID directly from
     * {@link this.rtpManager}, given that it offers this information with its
     * getLocalSSRC() method? TAG(cat4-local-ssrc-hurricane)
     */
    private long localSourceID = (new Random().nextInt()) & 0x00000000FFFFFFFFL;

    /**
     * The MediaStreamStatsImpl object used to compute the statistics about
     * this MediaStreamImpl.
     */
    private MediaStreamStats2Impl mediaStreamStatsImpl;

    /**
     * Engine chain overriding payload type if needed.
     */
    private PayloadTypeTransformEngine ptTransformEngine;

    /**
     * The <tt>SrtpControl</tt> which controls the SRTP functionality of this
     * <tt>MediaStream</tt>.
     */
    private final SrtpControl srtpControl;

    /**
     * The <tt>SSRCFactory</tt> to be utilized by this instance to generate new
     * synchronization source (SSRC) identifiers. If <tt>null</tt>, this
     * instance will employ internal logic to generate new synchronization
     * source (SSRC) identifiers.
     */
    private SSRCFactory ssrcFactory = new SSRCFactoryImpl(localSourceID);

    /**
     * The indicator which determines whether {@link #start()} has been called
     * on this <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}.
     */
    private boolean started = false;

    /**
     * The <tt>MediaDirection</tt> in which this instance is started. For
     * example, {@link MediaDirection#SENDRECV} if this instances is both
     * sending and receiving data (e.g. RTP and RTCP) or
     * {@link MediaDirection#SENDONLY} if this instance is only sending data.
     */
    private MediaDirection startedDirection;

    /**
     * Engine chain reading sent RTCP sender reports and stores/prints
     * statistics.
     */
    private StatisticsEngine statisticsEngine = null;

    /**
     * The <tt>TransformEngine</tt> instance that logs packets going in and out
     * of this <tt>MediaStream</tt>.
     */
    private DebugTransformEngine debugTransformEngine;

    /**
     * The <tt>TransformEngine</tt> instance registered in the
     * <tt>RTPConnector</tt>'s transformer chain, which allows the "external"
     * transformer to be swapped.
     */
    private final TransformEngineWrapper<TransformEngine>
        externalTransformerWrapper
            = new TransformEngineWrapper<>();

    /**
     * The transformer which replaces the timestamp in an abs-send-time RTP
     * header extension.
     */
    private AbsSendTimeEngine absSendTimeEngine;

    /**
     * The transformer which caches outgoing RTP packets for this
     * {@link MediaStream}.
     */
    private CachingTransformer cachingTransformer = createCachingTransformer();

    /**
     * The chain used to by the RTPConnector to transform packets.
     */
    private TransformEngineChain transformEngineChain;

    /**
     * The {@code RetransmissionRequesterImpl} instance for this
     * {@code MediaStream} which will request missing packets by sending
     * RTCP NACKs.
     */
    private final RetransmissionRequesterImpl retransmissionRequester
        = createRetransmissionRequester();

    /**
     * The engine which adds an Original Header Block header extension to
     * incoming packets.
     */
    private final OriginalHeaderBlockTransformEngine ohbEngine
        = new OriginalHeaderBlockTransformEngine();

    /**
     * The ID of the frame markings RTP header extension. We use this field as
     * a cache, in order to not access {@link #activeRTPExtensions} every time.
     */
    private byte frameMarkingsExtensionId = -1;

    private final MediaType mediaType;

    private final PacketSwitch packetSwitch;
    private final ReceiveThread receiveThread;
    private final SendThread sendThread;
    private final DatagramSocket socket;

    /**
     * Initializes a new <tt>MediaStreamImpl</tt> instance which will use the
     * specified <tt>MediaDevice</tt> for both capture and playback of media
     * exchanged via the specified <tt>StreamConnector</tt>.
     *
     * @param srtpControl an existing control instance to control the ZRTP
     * operations or <tt>null</tt> if a new control instance is to be created by
     * the new <tt>MediaStreamImpl</tt>
     */
    public MediaStreamImpl(
            SrtpControl srtpControl,
            MediaType mediaType,
            PacketSwitch packetSwitch,
            DatagramSocket socket)
    {
        this.mediaType = mediaType;
        this.packetSwitch = Objects.requireNonNull(packetSwitch, "packetSwitch");
        this.socket = socket;
        this.receiveThread = new ReceiveThread(this, socket);
        this.sendThread = new SendThread(this, socket);

        this.srtpControl = srtpControl;
        this.srtpControl.registerUser(this);

        this.mediaStreamStatsImpl = new MediaStreamStats2Impl(this);

        if (logger.isTraceEnabled())
        {
            logger.trace(
                    "Created " + getClass().getSimpleName() + " with hashCode "
                        + hashCode());
        }
    }

    /**
     * Adds a new association in this <tt>MediaStream</tt> of the specified RTP
     * payload type with the specified <tt>MediaFormat</tt> in order to allow it
     * to report <tt>rtpPayloadType</tt> in RTP flows sending and receiving
     * media in <tt>format</tt>. Usually, <tt>rtpPayloadType</tt> will be in the
     * range of dynamic RTP payload types.
     *
     * @param rtpPayloadType the RTP payload type to be associated in this
     * <tt>MediaStream</tt> with the specified <tt>MediaFormat</tt>
     * @param format the <tt>MediaFormat</tt> to be associated in this
     * <tt>MediaStream</tt> with <tt>rtpPayloadType</tt>
     * @see MediaStream#addDynamicRTPPayloadType(byte, MediaFormat)
     */
    @Override
    public void addDynamicRTPPayloadType(
            byte rtpPayloadType,
            MediaFormat format)
    {
        @SuppressWarnings("unchecked")
        MediaFormatImpl<? extends Format> mediaFormatImpl
            = (MediaFormatImpl<? extends Format>) format;

        synchronized (dynamicRTPPayloadTypes)
        {
            dynamicRTPPayloadTypes.put(Byte.valueOf(rtpPayloadType), format);

            String encoding = format.getEncoding();

            if (Constants.RED.equals(encoding))
            {
                REDTransformEngine redTransformEngine = getRedTransformEngine();
                if (redTransformEngine != null)
                {
                    redTransformEngine.setIncomingPT(rtpPayloadType);
                    // setting outgoingPT enables RED encapsulation for outgoing
                    // packets.
                    redTransformEngine.setOutgoingPT(rtpPayloadType);
                }
            }
            else if (Constants.ULPFEC.equals(encoding))
            {
                FECTransformEngine fecTransformEngine = getFecTransformEngine();
                if (fecTransformEngine != null)
                {
                    fecTransformEngine.setIncomingPT(rtpPayloadType);
                    // TODO ULPFEC without RED doesn't make sense.
                    fecTransformEngine.setOutgoingPT(rtpPayloadType);
                }
            }
        }

        this.onDynamicPayloadTypesChanged();
    }

    /**
     * Adds an additional RTP payload mapping that will overriding one that
     * we've set with {@link #addDynamicRTPPayloadType(byte, MediaFormat)}.
     * This is necessary so that we can support the RFC3264 case where the
     * answerer has the right to declare what payload type mappings it wants to
     * receive RTP packets with even if they are different from those in the
     * offer. RFC3264 claims this is for support of legacy protocols such as
     * H.323 but we've been bumping with a number of cases where multi-component
     * pure SIP systems also need to behave this way.
     * <p>
     *
     * @param originalPt the payload type that we are overriding
     * @param overloadPt the payload type that we are overriding it with
     */
    @Override
    public void addDynamicRTPPayloadTypeOverride(byte originalPt,
                                                 byte overloadPt)
    {
        if (ptTransformEngine != null)
            ptTransformEngine.addPTMappingOverride(originalPt, overloadPt);
    }

    /**
     * Maps or updates the mapping between <tt>extensionID</tt> and
     * <tt>rtpExtension</tt>. If <tt>rtpExtension</tt>'s <tt>MediaDirection</tt>
     * attribute is set to <tt>INACTIVE</tt> the mapping is removed from the
     * local extensions table and the extension would not be transmitted or
     * handled by this stream's <tt>RTPConnector</tt>.
     *
     * @param extensionID the ID that is being mapped to <tt>rtpExtension</tt>
     * @param rtpExtension the <tt>RTPExtension</tt> that we are mapping.
     */
    @Override
    public void addRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        if (rtpExtension == null)
            return;

        boolean active
                = !MediaDirection.INACTIVE.equals(rtpExtension.getDirection());
        synchronized (activeRTPExtensions)
        {
            if (active)
                activeRTPExtensions.put(extensionID, rtpExtension);
            else
                activeRTPExtensions.remove(extensionID);
        }

        enableRTPExtension(extensionID, rtpExtension);
    }

    /**
     * Enables all RTP extensions configured for this {@link MediaStream}.
     */
    private void enableRTPExtensions()
    {
        synchronized (activeRTPExtensions)
        {
            for (Map.Entry<Byte, RTPExtension> entry
                    : activeRTPExtensions.entrySet())
            {
                enableRTPExtension(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Enables the use of a specific RTP extension.
     * @param extensionID the ID.
     * @param rtpExtension the extension.
     */
    private void enableRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        boolean active
            = !MediaDirection.INACTIVE.equals(rtpExtension.getDirection());

        byte effectiveId = active ? extensionID : -1;

        String uri = rtpExtension.getURI().toString();
        if (RTPExtension.ABS_SEND_TIME_URN.equals(uri))
        {
            if (absSendTimeEngine != null)
            {
                absSendTimeEngine.setExtensionID(effectiveId);
            }
        }
        else if (RTPExtension.FRAME_MARKING_URN.equals(uri))
        {
            frameMarkingsExtensionId = effectiveId;
        }
        else if (RTPExtension.ORIGINAL_HEADER_BLOCK_URN.equals(uri))
        {
            ohbEngine.setExtensionID(effectiveId);
        }
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     *
     * @see MediaStream#close()
     */
    @Override
    public void close()
    {
        stop();

        srtpControl.cleanup(this);

        if (cachingTransformer != null)
        {
            cachingTransformer.close();
            cachingTransformer = null;
        }

        if (retransmissionRequester != null)
        {
            retransmissionRequester.close();
        }

        if (transformEngineChain != null)
        {
            PacketTransformer t = transformEngineChain.getRTPTransformer();
            if (t != null)
                t.close();
            t = transformEngineChain.getRTCPTransformer();
            if (t != null)
                t.close();
            transformEngineChain = null;
        }
    }

    protected SsrcTransformEngine createSsrcTransformEngine()
    {
        return null;
    }

    /**
     * Creates the {@link AbsSendTimeEngine} for this {@code MediaStream}.
     * @return the created {@link AbsSendTimeEngine}.
     */
    protected AbsSendTimeEngine createAbsSendTimeEngine()
    {
        return new AbsSendTimeEngine();
    }

    /**
     * Creates the {@link CachingTransformer} for this {@code MediaStream}.
     * @return the created {@link CachingTransformer}.
     */
    protected CachingTransformer createCachingTransformer()
    {
        return null;
    }

    /**
     * Creates the {@link RetransmissionRequesterImpl} for this
     * {@code MediaStream}.
     * @return the created {@link RetransmissionRequesterImpl}.
     */
    protected RetransmissionRequesterImpl createRetransmissionRequester()
    {
        return null;
    }

    /**
     * Creates a chain of transform engines for use with this stream. Note
     * that this is the only place where the <tt>TransformEngineChain</tt> is
     * and should be manipulated to avoid problems with the order of the
     * transformers.
     *
     * @return the <tt>TransformEngineChain</tt> that this stream should be
     * using.
     */
    private TransformEngineChain createTransformEngineChain()
    {
        List<TransformEngine> engineChain = new ArrayList<>(9);

        engineChain.add(externalTransformerWrapper);

        // RRs and REMBs.
        RTCPReceiverFeedbackTermination rtcpFeedbackTermination = getRTCPTermination();
        if (rtcpFeedbackTermination != null)
        {
            engineChain.add(rtcpFeedbackTermination);
        }

        // here comes the override payload type transformer
        // as it changes headers of packets, need to go before encryption
        if (ptTransformEngine == null)
            ptTransformEngine = new PayloadTypeTransformEngine();
        engineChain.add(ptTransformEngine);

        // FEC
        FECTransformEngine fecTransformEngine = getFecTransformEngine();
        if (fecTransformEngine != null)
            engineChain.add(fecTransformEngine);

        // RED
        REDTransformEngine redTransformEngine = getRedTransformEngine();
        if (redTransformEngine != null)
            engineChain.add(redTransformEngine);

        // RTCP Statistics
        if (statisticsEngine == null)
            statisticsEngine = new StatisticsEngine(this);
        engineChain.add(statisticsEngine);

        if (retransmissionRequester != null)
        {
            engineChain.add(retransmissionRequester);
        }

        if (cachingTransformer != null)
        {
            engineChain.add(cachingTransformer);
        }

        // Discard
        DiscardTransformEngine discardEngine = createDiscardEngine();
        if (discardEngine != null)
            engineChain.add(discardEngine);

        MediaStreamTrackReceiver mediaStreamTrackReceiver
            = getMediaStreamTrackReceiver();

        if (mediaStreamTrackReceiver != null)
        {
            engineChain.add(mediaStreamTrackReceiver);
        }

        // Padding termination.
        PaddingTermination paddingTermination = getPaddingTermination();
        if (paddingTermination != null)
        {
            engineChain.add(paddingTermination);
        }

        // RTX
        RtxTransformer rtxTransformer = getRtxTransformer();
        if (rtxTransformer != null)
        {
            engineChain.add(rtxTransformer);
        }

        // TODO RTCP termination should end up here.

        RemoteBitrateEstimator
            remoteBitrateEstimator = getRemoteBitrateEstimator();
        if (remoteBitrateEstimator != null)
        {
            engineChain.add(remoteBitrateEstimator);
        }

        absSendTimeEngine = createAbsSendTimeEngine();
        if (absSendTimeEngine != null)
        {
            engineChain.add(absSendTimeEngine);
        }

        // Debug
        debugTransformEngine
            = DebugTransformEngine.createDebugTransformEngine(this);
        if (debugTransformEngine != null)
            engineChain.add(debugTransformEngine);

        // OHB
        engineChain.add(new OriginalHeaderBlockTransformEngine());

        // SRTP
        engineChain.add(srtpControl.getTransformEngine());

        // SSRC audio levels
        /*
         * It needs to go first in the reverse transform in order to be able to
         * prevent RTP packets from a muted audio source from being decrypted.
         */
        SsrcTransformEngine ssrcEngine = createSsrcTransformEngine();
        if (ssrcEngine != null)
            engineChain.add(ssrcEngine);

        // RTP extensions may be implemented in some of the engines just
        // created (e.g. abs-send-time). So take into account their
        // configuration.
        enableRTPExtensions();

        return
            new TransformEngineChain(
                    engineChain.toArray(
                            new TransformEngine[engineChain.size()]));
    }

    /**
     * Returns a map containing all currently active <tt>RTPExtension</tt>s in
     * use by this stream.
     *
     * @return a map containing all currently active <tt>RTPExtension</tt>s in
     * use by this stream.
     */
    @Override
    public Map<Byte, RTPExtension> getActiveRTPExtensions()
    {
        synchronized (activeRTPExtensions)
        {
            return new HashMap<>(activeRTPExtensions);
        }
    }

    /**
     * Gets the direction in which this <tt>MediaStream</tt> is allowed to
     * stream media.
     *
     * @return the <tt>MediaDirection</tt> in which this <tt>MediaStream</tt> is
     * allowed to stream media
     */
    @Override
    public MediaDirection getDirection()
    {
        return (direction == null) ? MediaDirection.INACTIVE : direction;
    }

    /**
     * Returns the payload type number that has been negotiated for the
     * specified <tt>encoding</tt> or <tt>-1</tt> if no payload type has been
     * negotiated for it. If multiple formats match the specified
     * <tt>encoding</tt>, then this method would return the first one it
     * encounters while iterating through the map.
     *
     * @param encoding the encoding whose payload type we are trying to obtain.
     *
     * @return the payload type number that has been negotiated for the
     * specified <tt>encoding</tt> or <tt>-1</tt> if no payload type has been
     * negotiated for it.
     */
    public byte getDynamicRTPPayloadType(String encoding)
    {
        synchronized (dynamicRTPPayloadTypes)
        {
            for (Map.Entry<Byte, MediaFormat> dynamicRTPPayloadType
                    : dynamicRTPPayloadTypes.entrySet())
            {
                if (dynamicRTPPayloadType.getValue().getEncoding().equals(
                        encoding))
                {
                    return dynamicRTPPayloadType.getKey().byteValue();
                }
            }
            return -1;
        }
    }

    /**
     * Gets the existing associations in this <tt>MediaStream</tt> of RTP
     * payload types to <tt>MediaFormat</tt>s. The returned <tt>Map</tt>
     * only contains associations previously added in this instance with
     * {@link #addDynamicRTPPayloadType(byte, MediaFormat)} and not globally or
     * well-known associations reported by
     * {@link MediaFormat#getRTPPayloadType()}.
     *
     * @return a <tt>Map</tt> of RTP payload type expressed as <tt>Byte</tt> to
     * <tt>MediaFormat</tt> describing the existing (dynamic) associations in
     * this instance of RTP payload types to <tt>MediaFormat</tt>s. The
     * <tt>Map</tt> represents a snapshot of the existing associations at the
     * time of the <tt>getDynamicRTPPayloadTypes()</tt> method call and
     * modifications to it are not reflected on the internal storage
     * @see MediaStream#getDynamicRTPPayloadTypes()
     */
    @Override
    public Map<Byte, MediaFormat> getDynamicRTPPayloadTypes()
    {
        synchronized (dynamicRTPPayloadTypes)
        {
            return new HashMap<>(dynamicRTPPayloadTypes);
        }
    }

    /**
     * Creates the <tt>FECTransformEngine</tt> for this <tt>MediaStream</tt>.
     * By default none is created, allows extenders to implement it.
     * @return the <tt>FECTransformEngine</tt> created.
     */
    protected FECTransformEngine getFecTransformEngine()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaFormat getFormat(byte pt)
    {
        synchronized (dynamicRTPPayloadTypes)
        {
            return dynamicRTPPayloadTypes.get(pt);
        }
    }

    /**
     * Gets the synchronization source (SSRC) identifier of the local peer or
     * <tt>-1</tt> if it is not yet known.
     *
     * @return  the synchronization source (SSRC) identifier of the local peer
     * or <tt>-1</tt> if it is not yet known
     * @see MediaStream#getLocalSourceID()
     */
    @Override
    public long getLocalSourceID()
    {
        return localSourceID;
    }

    /**
     * Returns the statistical information gathered about this
     * <tt>MediaStream</tt>.
     *
     * @return the statistical information gathered about this
     * <tt>MediaStream</tt>
     */
    @Override
    public MediaStreamStats2Impl getMediaStreamStats()
    {
        return mediaStreamStatsImpl;
    }

    /**
     * Creates the <tt>REDTransformEngine</tt> for this <tt>MediaStream</tt>.
     * By default none is created, allows extenders to implement it.
     * @return the <tt>REDTransformEngine</tt> created.
     */
    protected REDTransformEngine getRedTransformEngine()
    {
        return null;
    }

    /**
     * Gets the <tt>SrtpControl</tt> which controls the SRTP of this stream.
     *
     * @return the <tt>SrtpControl</tt> which controls the SRTP of this stream
     */
    @Override
    public SrtpControl getSrtpControl()
    {
        return srtpControl;
    }

    /**
     * Determines whether {@link #start()} has been called on this
     * <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}
     * afterwards.
     *
     * @return <tt>true</tt> if {@link #start()} has been called on this
     * <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}
     * afterwards
     * @see MediaStream#isStarted()
     */
    @Override
    public boolean isStarted()
    {
        return started;
    }

    /**
     * Sets the direction in which media in this <tt>MediaStream</tt> is to be
     * streamed. If this <tt>MediaStream</tt> is not currently started, calls to
     * {@link #start()} later on will start it only in the specified
     * <tt>direction</tt>. If it is currently started in a direction different
     * than the specified, directions other than the specified will be stopped.
     *
     * @param direction the <tt>MediaDirection</tt> in which this
     * <tt>MediaStream</tt> is to stream media when it is started
     * @see MediaStream#setDirection(MediaDirection)
     */
    @Override
    public void setDirection(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");
        if(this.direction == direction)
            return;

        if(logger.isTraceEnabled())
        {
            logger.trace(
                    "Changing direction of stream " + hashCode()
                        + " from:" + this.direction
                        + " to:" + direction);
        }

        this.direction = direction;

        switch (this.direction)
        {
        case INACTIVE:
            stop(MediaDirection.SENDRECV);
            return;
        case RECVONLY:
            stop(MediaDirection.SENDONLY);
            break;
        case SENDONLY:
            stop(MediaDirection.RECVONLY);
            break;
        case SENDRECV:
            break;
        default:
            // Don't know what it may be (in the future) so ignore it.
            return;
        }
        if (started)
            start(this.direction);

    }

    /**
     * Sets the local SSRC identifier and fires the corresponding
     * <tt>PropertyChangeEvent</tt>.
     *
     * @param localSourceID the SSRC identifier that this stream will be using
     * in outgoing RTP packets from now on
     */
    protected void setLocalSourceID(long localSourceID)
    {
        if (this.localSourceID != localSourceID)
        {
            Long oldValue = this.localSourceID;

            this.localSourceID = localSourceID;

            firePropertyChange(PNAME_LOCAL_SSRC, oldValue, this.localSourceID);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSSRCFactory(SSRCFactory ssrcFactory)
    {
        if (this.ssrcFactory != ssrcFactory)
        {
            this.ssrcFactory = ssrcFactory;
        }
    }

    /**
     * Starts capturing media from this stream's <tt>MediaDevice</tt> and then
     * streaming it through the local <tt>StreamConnector</tt> toward the
     * stream's target address and port. Also puts the <tt>MediaStream</tt> in a
     * listening state which make it play all media received from the
     * <tt>StreamConnector</tt> on the stream's <tt>MediaDevice</tt>.
     *
     * @see MediaStream#start()
     */
    @Override
    public void start()
    {
        start(getDirection());
        started = true;
    }

    /**
     * Starts the processing of media in this instance in a specific direction.
     *
     * @param direction a <tt>MediaDirection</tt> value which represents the
     * direction of the processing of media to be started. For example,
     * {@link MediaDirection#SENDRECV} to start both capture and playback of
     * media in this instance or {@link MediaDirection#SENDONLY} to only start
     * the capture of media in this instance
     */
    private void start(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        if (direction.allowsReceiving()
                && ((startedDirection == null)
                        || !startedDirection.allowsReceiving()))
        {
            if (MediaDirection.SENDONLY.equals(startedDirection))
                startedDirection = MediaDirection.SENDRECV;
            else if (startedDirection == null)
                startedDirection = MediaDirection.RECVONLY;
        }
    }

    /**
     * Stops all streaming and capturing in this <tt>MediaStream</tt> and closes
     * and releases all open/allocated devices/resources. Has no effect if this
     * <tt>MediaStream</tt> is already closed and is simply ignored.
     *
     * @see MediaStream#stop()
     */
    @Override
    public void stop()
    {
        stop(MediaDirection.SENDRECV);
        started = false;
    }

    /**
     * Stops the processing of media in this instance in a specific direction.
     *
     * @param direction a <tt>MediaDirection</tt> value which represents the
     * direction of the processing of media to be stopped. For example,
     * {@link MediaDirection#SENDRECV} to stop both capture and playback of
     * media in this instance or {@link MediaDirection#SENDONLY} to only stop
     * the capture of media in this instance
     */
    private void stop(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        if ((MediaDirection.SENDRECV.equals(direction)
                    || MediaDirection.SENDONLY.equals(direction))
                && (MediaDirection.SENDRECV.equals(startedDirection)
                        || MediaDirection.SENDONLY.equals(startedDirection)))
        {
            if (MediaDirection.SENDRECV.equals(startedDirection))
                startedDirection = MediaDirection.RECVONLY;
            else if (MediaDirection.SENDONLY.equals(startedDirection))
                startedDirection = null;
        }

        if ((MediaDirection.SENDRECV.equals(direction)
                || MediaDirection.RECVONLY.equals(direction))
            && (MediaDirection.SENDRECV.equals(startedDirection)
                    || MediaDirection.RECVONLY.equals(startedDirection)))
        {
            if (MediaDirection.SENDRECV.equals(startedDirection))
                startedDirection = MediaDirection.SENDONLY;
            else if (MediaDirection.RECVONLY.equals(startedDirection))
                startedDirection = null;
        }
    }

    /**
     * Returns the <tt>StatisticsEngine</tt> of this instance.
     * @return  the <tt>StatisticsEngine</tt> of this instance.
     */
    StatisticsEngine getStatisticsEngine()
    {
        return statisticsEngine;
    }

    /**
     * {@inheritDoc}
     */
    public void setExternalTransformer(TransformEngine transformEngine)
    {
        externalTransformerWrapper.setWrapped(transformEngine);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void injectPacket(RawPacket pkt, boolean data, TransformEngine after)
        throws TransmissionFailedException
    {
        try
        {
            if (pkt == null)
            {
                // It's a waste of time to invoke the method with a null pkt so
                // disallow it.
                throw new NullPointerException("pkt");
            }

            // We utilize TransformEngineWrapper so it is possible to have after
            // wrapped. Unless we wrap after, pkt will go through the whole
            // TransformEngine chain (which is obviously not the idea of the
            // caller).
            if (after != null)
            {
                TransformEngineWrapper wrapper;

                // externalTransformerWrapper
                wrapper = externalTransformerWrapper;
                if (wrapper != null && wrapper.contains(after))
                {
                    after = wrapper;
                }
            }

            pkt.setContext(after);
            sendThread.writePacket(pkt);
        }
        catch (IllegalStateException | NullPointerException e)
        {
            throw new TransmissionFailedException(e);
        }
    }

    /**
     * Utility method that determines the temporal layer index (TID) of an RTP
     * packet.
     *
     * @param buf the buffer that holds the RTP payload.
     * @param off the offset in the buff where the RTP payload is found.
     * @param len then length of the RTP payload in the buffer.
     *
     * @return the TID of the packet, -1 otherwise.
     *
     * FIXME(gp) conceptually this belongs to the {@link VideoMediaStreamImpl},
     * but I don't want to be obliged to cast to use this method.
     */
    public int getTemporalID(byte[] buf, int off, int len)
    {
        REDBlock redBlock = getPayloadBlock(buf, off, len);
        if (redBlock == null || redBlock.getLength() == 0)
        {
            return -1;
        }

        final byte vp8PT = getDynamicRTPPayloadType(Constants.VP8);

        if (redBlock.getPayloadType() == vp8PT)
        {
            return org.jitsi.impl
                .neomedia.codec.video.vp8.DePacketizer.VP8PayloadDescriptor
                .getTemporalLayerIndex(
                    redBlock.getBuffer(),
                    redBlock.getOffset(),
                    redBlock.getLength());
        }
        else
        {
            return -1;
        }
    }

    /**
     * Utility method that determines whether or not a packet is a start of
     * frame.
     *
     * @param buf the buffer that holds the RTP payload.
     * @param off the offset in the buff where the RTP payload is found.
     * @param len then length of the RTP payload in the buffer.
     *
     * @return true if the packet is the start of a frame, false otherwise.
     *
     * FIXME(gp) conceptually this belongs to the {@link VideoMediaStreamImpl},
     * but I don't want to be obliged to cast to use this method.
     *
     */
    public boolean isStartOfFrame(byte[] buf, int off, int len)
    {
        REDBlock redBlock = getPayloadBlock(buf, off, len);
        if (redBlock == null || redBlock.getLength() == 0)
        {
            return false;
        }

        final byte vp8PT = getDynamicRTPPayloadType(Constants.VP8);

        if (redBlock.getPayloadType() == vp8PT)
        {
            return org.jitsi.impl
                .neomedia.codec.video.vp8.DePacketizer.VP8PayloadDescriptor
                .isStartOfFrame(redBlock.getBuffer(), redBlock.getOffset());
        }
        else
        {
            return false;
        }
    }

    /**
     * Utility method that determines whether or not a packet is an end of
     * frame.
     *
     * @param buf the buffer that holds the RTP payload.
     * @param off the offset in the buff where the RTP payload is found.
     * @param len then length of the RTP payload in the buffer.
     *
     * @return true if the packet is the end of a frame, false otherwise.
     *
     * FIXME(gp) conceptually this belongs to the {@link VideoMediaStreamImpl},
     * but I don't want to be obliged to cast to use this method.
     *
     */
    public boolean isEndOfFrame(byte[] buf, int off, int len)
    {
        // XXX(gp) this probably won't work well with spatial scalability.
        return RawPacket.isPacketMarked(buf, off, len);
    }

    /**
     * {@inheritDoc}
     * </p>
     * This is absolutely terrible, but we need a RawPacket and the method is
     * used from RTPTranslator, which doesn't work with RawPacket.
     */
    public boolean isKeyFrame(byte[] buf, int off, int len)
    {
        return isKeyFrame(new RawPacket(buf, off, len));
    }

    /**
     * {@inheritDoc}
     */
    public boolean isKeyFrame(RawPacket pkt)
    {
        if (!RTPPacketPredicate.INSTANCE.test(pkt))
        {
            return false;
        }

        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        if (frameMarkingsExtensionId != -1)
        {
            RawPacket.HeaderExtension fmhe
                = pkt.getHeaderExtension(frameMarkingsExtensionId);
            if (fmhe != null)
            {
                return FrameMarkingHeaderExtension.isKeyframe(fmhe);
            }

            if (logger.isDebugEnabled())
            {
                logger.debug("Packet with no frame marking, while frame marking"
                             + " is enabled.");
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }

        REDBlock redBlock = getPayloadBlock(buf, off, len);
        if (redBlock == null || redBlock.getLength() == 0)
        {
            return false;
        }

        final byte vp8PT = getDynamicRTPPayloadType(Constants.VP8),
            h264PT = getDynamicRTPPayloadType(Constants.H264);

        if (redBlock.getPayloadType() == vp8PT)
        {
            return org.jitsi.impl.neomedia.codec.video.vp8.DePacketizer
                .isKeyFrame(buf, redBlock.getOffset(), redBlock.getLength());
        }
        else if (redBlock.getPayloadType() == h264PT)
        {
            return org.jitsi.impl.neomedia.codec.video.h264.DePacketizer
                .isKeyFrame(
                    redBlock.getBuffer(),
                    redBlock.getOffset(),
                    redBlock.getLength());
        }
        else
        {
            return false;
        }
    }

    /**
     * Gets the {@link CachingTransformer} which (optionally) caches outgoing
     * packets for this {@link MediaStreamImpl}, if it exists.
     * @return the {@link CachingTransformer} for this {@link MediaStreamImpl}.
     */
    public CachingTransformer getCachingTransformer()
    {
        return cachingTransformer;
    }

    /**
     * {@inheritDoc}
     */
    public RetransmissionRequester getRetransmissionRequester()
    {
        return retransmissionRequester;
    }

    /**
     * {@inheritDoc}
     * <br/>
     */
    @Override
    public TransformEngineChain getTransformEngineChain()
    {
        return transformEngineChain;
    }

    /**
     * Gets the {@link REDBlock} that contains the payload of the packet passed
     * in as a parameter.
     *
     * @param buf the buffer that holds the RTP payload.
     * @param off the offset in the buff where the RTP payload is found.
     * @param len then length of the RTP payload in the buffer.
     * @return the {@link REDBlock} that contains the payload of the packet
     * passed in as a parameter, or null if the buffer is invalid.
     */
    public REDBlock getPayloadBlock(byte[] buf, int off, int len)
    {
        if (buf == null || buf.length < off + len
            || len < RawPacket.FIXED_HEADER_SIZE)
        {
            return null;
        }

        final byte redPT = getDynamicRTPPayloadType(Constants.RED),
            pktPT = (byte) RawPacket.getPayloadType(buf, off, len);

        if (redPT == pktPT)
        {
            return REDBlockIterator.getPrimaryBlock(buf, off, len);
        }
        else
        {
            final int payloadOff = RawPacket.getPayloadOffset(buf, off, len),
                payloadLen = RawPacket.getPayloadLength(buf, off, len, true);

            return new REDBlock(buf, payloadOff, payloadLen, pktPT);
        }
    }

    /**
     * Gets the {@code RtxTransformer}, if any, used by the {@code MediaStream}.
     *
     * @return the {@code RtxTransformer} used by the {@code MediaStream} or
     * {@code null}
     */
    public RtxTransformer getRtxTransformer()
    {
        return null;
    }

    /**
     * Creates the {@link DiscardTransformEngine} for this stream. Allows
     * extenders to override.
     */
    protected DiscardTransformEngine createDiscardEngine()
    {
        return null;
    }

    /**
     * Gets the RTCP termination for this {@link MediaStreamImpl}.
     */
    protected RTCPReceiverFeedbackTermination getRTCPTermination()
    {
        return null;
    }

    /**
     * Gets the {@link PaddingTermination} for this {@link MediaStreamImpl}.
     */
    protected PaddingTermination getPaddingTermination()
    {
        return null;
    }

    /**
     * Gets the <tt>RemoteBitrateEstimator</tt> of this
     * <tt>VideoMediaStream</tt>.
     *
     * @return the <tt>RemoteBitrateEstimator</tt> of this
     * <tt>VideoMediaStream</tt> if any; otherwise, <tt>null</tt>
     */
    public RemoteBitrateEstimator getRemoteBitrateEstimator()
    {
        return null;
    }
    /**
     * Code that runs when the dynamic payload types change.
     */
    private void onDynamicPayloadTypesChanged()
    {
        RtxTransformer rtxTransformer = getRtxTransformer();
        if (rtxTransformer != null)
        {
            rtxTransformer.onDynamicPayloadTypesChanged();
        }
    }

    public MediaType getMediaType()
    {
        return mediaType;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void clearDynamicRTPPayloadTypes()
    {
        synchronized (dynamicRTPPayloadTypes)
        {
            dynamicRTPPayloadTypes.clear();

            REDTransformEngine redTransformEngine = getRedTransformEngine();
            if (redTransformEngine != null)
            {
                redTransformEngine.setIncomingPT((byte) -1);
                redTransformEngine.setOutgoingPT((byte) -1);
            }

            FECTransformEngine fecTransformEngine = getFecTransformEngine();
            if (fecTransformEngine != null)
            {
                fecTransformEngine.setIncomingPT((byte) -1);
                fecTransformEngine.setOutgoingPT((byte) -1);
            }
        }

        this.onDynamicPayloadTypesChanged();
    }

    @Override
    public boolean writePacket(RawPacket pkt, MediaStream source, boolean needToCopy)
    {
        // add to the send thread
        return true;
    }

    @Override
    public PacketSwitch getPacketSwitch()
    {
        return packetSwitch;
    }

    @Override
    public DatagramSocket getSocket()
    {
        return socket;
    }
}
