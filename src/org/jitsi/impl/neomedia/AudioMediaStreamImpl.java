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

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.csrc.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.util.*;

/**
 * Extends <tt>MediaStreamImpl</tt> in order to provide an implementation of
 * <tt>AudioMediaStream</tt>.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Boris Grozev
 */
public class AudioMediaStreamImpl
    extends MediaStreamImpl
{
    /**
     * List of RTP format strings which are supported by SIP Communicator in
     * addition to the JMF standard formats.
     *
     * @see #registerCustomCodecFormats(StreamRTPManager)
     */
    private static final AudioFormat[] CUSTOM_CODEC_FORMATS
        = new AudioFormat[]
                {
                    /*
                     * these formats are specific, since RTP uses format numbers
                     * with no parameters.
                     */
                    new AudioFormat(
                            Constants.ALAW_RTP,
                            8000,
                            8,
                            1,
                            Format.NOT_SPECIFIED,
                            AudioFormat.SIGNED),
                    new AudioFormat(
                            Constants.G722_RTP,
                            8000,
                            Format.NOT_SPECIFIED /* sampleSizeInBits */,
                            1)
                };

    /**
     * The <tt>Logger</tt> used by the <tt>AudioMediaStreamImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioMediaStreamImpl.class);

    /**
     * The listener that gets notified of changes in the audio level of
     * remote conference participants.
     */
    private CsrcAudioLevelListener csrcAudioLevelListener;

    private SsrcTransformEngine ssrcTransformEngine;

    /**
     * Initializes a new <tt>AudioMediaStreamImpl</tt> instance which will use
     * the specified <tt>MediaDevice</tt> for both capture and playback of audio
     * exchanged via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the new instance is to use
     * for sending and receiving audio
     * @param srtpControl a control which is already created, used to control
     * the srtp operations.
     */
    public AudioMediaStreamImpl(
            StreamConnector connector,
            SrtpControl srtpControl)
    {
        super(connector, srtpControl, MediaType.AUDIO);
    }

    /**
     * In addition to calling
     * {@link MediaStreamImpl#addRTPExtension(byte, RTPExtension)}
     * this method enables sending of CSRC audio levels. The reason we are
     * doing this here rather than in the super class is that CSRC levels only
     * make sense for audio streams so we don't want them enabled in any other
     * type.
     *
     * @param extensionID the ID assigned to <tt>rtpExtension</tt> for the
     * lifetime of this stream.
     * @param rtpExtension the RTPExtension that is being added to this stream.
     */
    @Override
    public void addRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        if (rtpExtension != null)
            super.addRTPExtension(extensionID, rtpExtension);

        // Do go on even if the extension is null, to make sure that the
        // currently active extensions are configured.

         // The method invocation may add, remove, or replace the value
         // associated with extensionID. Consequently, we have to update
         // csrcEngine with whatever is in activeRTPExtensions eventually.
        SsrcTransformEngine ssrcEngine = this.ssrcTransformEngine;

        if (ssrcEngine != null)
        {
            Map<Byte,RTPExtension> activeRTPExtensions
                = getActiveRTPExtensions();
            Byte ssrcExtID = null;
            MediaDirection ssrcDir = MediaDirection.INACTIVE;

            if ((activeRTPExtensions != null)
                    && !activeRTPExtensions.isEmpty())
            {
                for (Map.Entry<Byte,RTPExtension> e
                        : activeRTPExtensions.entrySet())
                {
                    RTPExtension ext = e.getValue();
                    String uri = ext.getURI().toString();

                    if (RTPExtension.SSRC_AUDIO_LEVEL_URN.equals(uri))
                    {
                        ssrcExtID = e.getKey();
                        ssrcDir = ext.getDirection();
                    }
                }
            }

            ssrcEngine.setSsrcAudioLevelExtensionID(
                    (ssrcExtID == null) ? -1 : ssrcExtID.byteValue(),
                    ssrcDir);
        }
    }

    /**
     * Delivers the <tt>audioLevels</tt> map to whoever is interested. This
     * method is meant for use primarily by the transform engine handling
     * incoming RTP packets (currently <tt>CsrcTransformEngine</tt>).
     *
     * @param audioLevels an array mapping CSRC IDs to audio levels in
     * consecutive elements.
     */
    public void audioLevelsReceived(long[] audioLevels)
    {
        CsrcAudioLevelListener csrcAudioLevelListener
            = this.csrcAudioLevelListener;

        if (csrcAudioLevelListener != null)
            csrcAudioLevelListener.audioLevelsReceived(audioLevels);
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
        super.close();

        if (ssrcTransformEngine != null)
        {
            ssrcTransformEngine.close();
            ssrcTransformEngine = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SsrcTransformEngine createSsrcTransformEngine()
    {
        if (ssrcTransformEngine == null)
            ssrcTransformEngine = new SsrcTransformEngine(this);
        return ssrcTransformEngine;
    }

    /**
     * Registers {@link #CUSTOM_CODEC_FORMATS} with a specific
     * <tt>RTPManager</tt>.
     *
     * @param rtpManager the <tt>RTPManager</tt> to register
     * {@link #CUSTOM_CODEC_FORMATS} with
     * @see MediaStreamImpl#registerCustomCodecFormats(StreamRTPManager)
     */
    @Override
    protected void registerCustomCodecFormats(StreamRTPManager rtpManager)
    {
        super.registerCustomCodecFormats(rtpManager);

        for (AudioFormat format : CUSTOM_CODEC_FORMATS)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "registering format " + format + " with RTPManager");
            }
            /*
             * NOTE (mkoch@rowa.de): com.sun.media.rtp.RtpSessionMgr.addFormat
             * leaks memory, since it stores the Format in a static Vector.
             * AFAIK there is no easy way around it, but the memory impact
             * should not be too bad.
             */
            rtpManager.addFormat(
                    format,
                    MediaUtils.getRTPPayloadType(
                            format.getEncoding(),
                            format.getSampleRate()));
        }
    }

    /**
     * Registers <tt>listener</tt> as the <tt>CsrcAudioLevelListener</tt> that
     * will receive notifications for changes in the levels of conference
     * participants that the remote party could be mixing.
     *
     * @param listener the <tt>CsrcAudioLevelListener</tt> that we'd like to
     * register or <tt>null</tt> if we'd like to stop receiving notifications.
     */
    public void setCsrcAudioLevelListener(CsrcAudioLevelListener listener)
    {
        csrcAudioLevelListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DiscardTransformEngine createDiscardEngine()
    {
        return new DiscardTransformEngine(this);
    }
}
