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

import java.beans.*;
import java.net.*;
import java.util.*;

import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.stats.*;

/**
 * The <tt>MediaStream</tt> class represents a (generally) bidirectional RTP
 * stream between exactly two parties. The class reflects one media stream, in
 * the SDP sense of the word. <tt>MediaStream</tt> instances are created through
 * the <tt>openMediaStream()</tt> method of the <tt>MediaService</tt>.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author George Politis
 * @author Boris Grozev
 */
public interface MediaStream
{
    /**
     * The name of the property which indicates whether the local SSRC is
     * currently available.
     */
    String PNAME_LOCAL_SSRC = "localSSRCAvailable";

    /**
     * The name of the property which indicates whether the remote SSRC is
     * currently available.
     */
    String PNAME_REMOTE_SSRC = "remoteSSRCAvailable";

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
     */
    void addDynamicRTPPayloadType(
            byte rtpPayloadType,
            MediaFormat format);

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
     * @param overloadPt the payload type that we are overriging it with
     */
    void addDynamicRTPPayloadTypeOverride(byte originalPt,
                                                 byte overloadPt);

    /**
     * Adds a property change listener to this stream so that it would be
     * notified upon property change events like for example an SSRC ID which
     * becomes known.
     *
     * @param listener the listener that we'd like to register for
     * <tt>PropertyChangeEvent</tt>s
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Adds or updates an association in this <tt>MediaStream</tt> mapping the
     * specified <tt>extensionID</tt> to <tt>rtpExtension</tt> and enabling or
     * disabling its use according to the direction attribute of
     * <tt>rtpExtension</tt>.
     *
     * @param extensionID the ID that is mapped to <tt>rtpExtension</tt> for
     * the lifetime of this <tt>MediaStream</tt>.
     * @param rtpExtension the <tt>RTPExtension</tt> that we are mapping to
     * <tt>extensionID</tt>.
     */
    void addRTPExtension(byte extensionID, RTPExtension rtpExtension);

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    void close();

    /**
     * Returns a map containing all currently active <tt>RTPExtension</tt>s in
     * use by this stream.
     *
     * @return a map containing all currently active <tt>RTPExtension</tt>s in
     * use by this stream.
     */
    Map<Byte, RTPExtension> getActiveRTPExtensions();

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
     */
    Map<Byte, MediaFormat> getDynamicRTPPayloadTypes();

    /**
     * Returns the payload type number that has been negotiated for the
     * specified <tt>encoding</tt> or <tt>-1</tt> if no payload type has been
     * negotiated for it. If multiple formats match the specified
     * <tt>encoding</tt>, then this method would return the first one it
     * encounters while iterating through the map.
     *
     * @param codec the encoding whose payload type we are trying to obtain.
     *
     * @return the payload type number that has been negotiated for the
     * specified <tt>codec</tt> or <tt>-1</tt> if no payload type has been
     * negotiated for it.
     */
    byte getDynamicRTPPayloadType(String codec);

    /**
     * Returns the <tt>MediaFormat</tt> that is associated to the payload type
     * passed in as a parameter.
     *
     * @param payloadType the payload type of the <tt>MediaFormat</tt> to get.
     *
     * @return the <tt>MediaFormat</tt> that is associated to the payload type
     * passed in as a parameter.
     */
    MediaFormat getFormat(byte payloadType);

    /**
     * Returns the synchronization source (SSRC) identifier of the local
     * participant or <tt>-1</tt> if that identifier is not yet known at this
     * point.
     *
     * @return the synchronization source (SSRC) identifier of the local
     * participant or <tt>-1</tt> if that identifier is not yet known at this
     * point.
     */
    long getLocalSSRC();

    /**
     * Returns a <tt>MediaStreamStats</tt> object used to get statistics about
     * this <tt>MediaStream</tt>.
     *
     * @return the <tt>MediaStreamStats</tt> object used to get statistics about
     * this <tt>MediaStream</tt>.
     */
    MediaStreamStats2 getMediaStreamStats();

    /**
     * Returns the name of this stream or <tt>null</tt> if no name has been
     * set. A stream name is used by some protocols, for diagnostic purposes
     * mostly. In XMPP for example this is the name of the content element that
     * describes a stream.
     *
     * @return the name of this stream or <tt>null</tt> if no name has been
     * set.
     */
    String getName();

    /**
     * Gets the value of a specific opaque property of this
     * <tt>MediaStream</tt>.
     *
     * @param propertyName the name of the opaque property of this
     * <tt>MediaStream</tt> the value of which is to be returned
     * @return the value of the opaque property of this <tt>MediaStream</tt>
     * specified by <tt>propertyName</tt>
     */
    Object getProperty(String propertyName);

    /**
     * The <tt>ZrtpControl</tt> which controls the ZRTP for this stream.
     *
     * @return the <tt>ZrtpControl</tt> which controls the ZRTP for this stream
     */
    SrtpControl getSrtpControl();

    /**
     * Determines whether {@link #start()} has been called on this
     * <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}
     * afterwards.
     *
     * @return <tt>true</tt> if {@link #start()} has been called on this
     * <tt>MediaStream</tt> without {@link #stop()} or {@link #close()}
     * afterwards
     */
    boolean isStarted();

    /**
     * Removes the specified property change <tt>listener</tt> from this stream
     * so that it won't receive further property change events.
     *
     * @param listener the listener that we'd like to remove.
     */
    void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Sets the name of this stream. Stream names are used by some protocols,
     * for diagnostic purposes mostly. In XMPP for example this is the name of
     * the content element that describes a stream.
     *
     * @param name the name of this stream or <tt>null</tt> if no name has been
     * set.
     */
    void setName(String name);

    /**
     * Sets the value of a specific opaque property of this
     * <tt>MediaStream</tt>.
     *
     * @param propertyName the name of the opaque property of this
     * <tt>MediaStream</tt> the value of which is to be set to the specified
     * <tt>value</tt>
     * @param value the value of the opaque property of this
     * <tt>MediaStream</tt> specified by <tt>propertyName</tt> to be set
     */
    void setProperty(String propertyName, Object value);

    /**
     * Starts capturing media from this stream's <tt>MediaDevice</tt> and then
     * streaming it through the local <tt>StreamConnector</tt> toward the
     * stream's target address and port. The method also puts the
     * <tt>MediaStream</tt> in a listening state that would make it play all
     * media received from the <tt>StreamConnector</tt> on the stream's
     * <tt>MediaDevice</tt>.
     */
    void start();

    /**
     * Stops all streaming and capturing in this <tt>MediaStream</tt> and closes
     * and releases all open/allocated devices/resources. This method has no
     * effect on an already closed stream and is simply ignored.
     */
    void stop();

    /**
     * Sets the external (application-provided) <tt>TransformEngine</tt> of
     * this <tt>MediaStream</tt>.
     * @param transformEngine the <tt>TransformerEngine</tt> to use.
     */
    void setExternalTransformer(TransformEngine transformEngine);

    /**
     * Sends a given RTP or RTCP packet to the remote peer/side.
     *
     * @param pkt the packet to send.
     * @param data {@code true} to send an RTP packet or {@code false} to send
     * an RTCP packet.
     * @param after the {@code TransformEngine} in the {@code TransformEngine}
     * chain of this {@code MediaStream} after which the injection is to begin.
     * If the specified {@code after} is not in the {@code TransformEngine}
     * chain of this {@code MediaStream}, {@code pkt} will be injected at the
     * beginning of the {@code TransformEngine} chain of this
     * {@code MediaStream}. Generally, the value of {@code after} should be
     * {@code null} unless the injection is being performed by a
     * {@code TransformEngine} itself (while executing {@code transform} or
     * {@code reverseTransform} of a {@code PacketTransformer} of its own even).
     * @throws TransmissionFailedException if the transmission failed.
     */
    void injectPacket(RawPacket pkt, boolean data, TransformEngine after)
        throws TransmissionFailedException;

    /**
     * Utility method that determines whether or not a packet is a key frame.
     *
     * @param buf the buffer that holds the RTP packet.
     * @param off the offset in the buff where the RTP packet is found.
     * @param len then length of the RTP packet in the buffer.
     * @return true if the packet is a key frame, false otherwise.
     */
    boolean isKeyFrame(byte[] buf, int off, int len);

    /**
     * Utility method that determines whether or not a packet is a key frame.
     *
     * @param pkt the packet.
     */
    boolean isKeyFrame(RawPacket pkt);

    /**
     * @return the {@link RetransmissionRequester} for this media stream.
     */
    RetransmissionRequester getRetransmissionRequester();

    /**
     * Gets the {@link TransformEngineChain} of this {@link MediaStream}.
     */
    TransformEngineChain getTransformEngineChain();

    /**
     * Gets the {@link MediaStreamTrackReceiver} of this {@link MediaStream}.
     *
     * @return the {@link MediaStreamTrackReceiver} of this {@link MediaStream},
     * or null.
     */
    MediaStreamTrackReceiver getMediaStreamTrackReceiver();

    /**
     * Clears the dynamic RTP payload type associations in this
     * <tt>MediaStream</tt>.
     */
    void clearDynamicRTPPayloadTypes();

    boolean writePacket(RawPacket pkt, boolean needToCopy);
    PacketSwitch getPacketSwitch();
    DatagramSocket getSocket();
    void setSocket(DatagramSocket socket);
    void setLocalSSRC(long ssrc);
    void setRawPacketFilter(RawPacketFilter filter);
}
