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

import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;

/**
 * The <tt>MediaService</tt> service is meant to be a wrapper of media libraries
 * such as JMF, FMJ, FFMPEG, and/or others. It takes care of all media play and
 * capture as well as media transport (e.g. over RTP).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public interface MediaService
{
    /**
     * Adds a <tt>PropertyChangeListener</tt> to be notified about changes in
     * the values of the properties of this instance.
     *
     * @param listener the <tt>PropertyChangeListener</tt> to be notified about
     * changes in the values of the properties of this instance
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Create a <tt>MediaStream</tt> which will use a specific
     * <tt>MediaDevice</tt> for capture and playback of media. The new instance
     * will not have a <tt>StreamConnector</tt> at the time of its construction
     * and a <tt>StreamConnector</tt> will be specified later on in order to
     * enable the new instance to send and receive media.
     *
     * @return a newly-created <tt>MediaStream</tt> which will use the specified
     * <tt>device</tt> for capture and playback of media
     */
    MediaStream createMediaStream();

    /**
     * Initializes a new <tt>MediaStream</tt> of a specific <tt>MediaType</tt>.
     *
     * @param mediaType the <tt>MediaType</tt> of the new instance to be
     * initialized
     * @return a new <tt>MediaStream</tt> instance of the specified
     * <tt>mediaType</tt>
     */
    MediaStream createMediaStream(MediaType mediaType);

    /**
     * Creates a <tt>MediaStream</tt> that will be using the specified
     * <tt>MediaDevice</tt> for both capture and playback of media exchanged
     * via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the stream should use for
     * sending and receiving media or <tt>null</tt> if the stream is to not have
     * a <tt>StreamConnector</tt> configured at initialization time and a
     * <tt>StreamConnector</tt> is to be specified later on
     *
     * @return the newly created <tt>MediaStream</tt>.
     */
    MediaStream createMediaStream(StreamConnector connector);

    /**
     * Initializes a new <tt>MediaStream</tt> instance which is to exchange
     * media of a specific <tt>MediaType</tt> via a specific
     * <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the stream should use for
     * sending and receiving media or <tt>null</tt> if the stream is to not have
     * a <tt>StreamConnector</tt> configured at initialization time and a
     * <tt>StreamConnector</tt> is to be specified later on
     * @param mediaType the <tt>MediaType</tt> of the media to be exchanged by
     * the new instance via the specified <tt>connector</tt>
     * @return a new <tt>MediaStream</tt> instance which is to exchange media of
     * the specified <tt>mediaType</tt> via the specified <tt>connector</tt>
     */
    MediaStream createMediaStream(
            StreamConnector connector, MediaType mediaType);

    /**
     * Creates a <tt>MediaStream</tt> that will be using the specified
     * <tt>MediaDevice</tt> for both capture and playback of media exchanged
     * via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the stream should use for
     * sending and receiving media or <tt>null</tt> if the stream is to not have
     * a <tt>StreamConnector</tt> configured at initialization time and a
     * <tt>StreamConnector</tt> is to be specified later on
     * @param srtpControl a control which is already created, used to control
     * the ZRTP operations.
     *
     * @return the newly created <tt>MediaStream</tt>.
     */
    MediaStream createMediaStream(
            StreamConnector connector, SrtpControl srtpControl);

    /**
     * Initializes a new <tt>MediaStream</tt> instance which is to exchange
     * media of a specific <tt>MediaType</tt> via a specific
     * <tt>StreamConnector</tt>. The security of the media exchange is to be
     * controlled by a specific <tt>SrtpControl</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> the stream should use for
     * sending and receiving media or <tt>null</tt> if the stream is to not have
     * a <tt>StreamConnector</tt> configured at initialization time and a
     * <tt>StreamConnector</tt> is to be specified later on
     * @param mediaType the <tt>MediaType</tt> of the media to be exchanged by
     * the new instance via the specified <tt>connector</tt>
     * @param srtpControl the <tt>SrtpControl</tt> to control the security of
     * the media exchange
     * @return a new <tt>MediaStream</tt> instance which is to exchange media of
     * the specified <tt>mediaType</tt> via the specified <tt>connector</tt>
     */
    MediaStream createMediaStream(
            StreamConnector connector,
            MediaType mediaType,
            SrtpControl srtpControl);

    /**
     * Initializes a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s.
     *
     * @return a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s
     */
    RTPTranslator createRTPTranslator();

    /**
     * Initializes a new <tt>SrtpControl</tt> instance with a specific
     * <tt>SrtpControlType</tt>.
     *
     * @param srtpControlType the <tt>SrtpControlType</tt> of the new instance
     * @return a new <tt>SrtpControl</tt> instance with the specified
     * <tt>srtpControlType</tt>
     */
    SrtpControl createSrtpControl(SrtpControlType srtpControlType);

    /**
     * Returns the current <tt>EncodingConfiguration</tt> instance.
     *
     * @return the current <tt>EncodingConfiguration</tt> instance.
     */
    public EncodingConfiguration getCurrentEncodingConfiguration();

    /**
     * Gets the <tt>MediaFormatFactory</tt> through which <tt>MediaFormat</tt>
     * instances may be created for the purposes of working with the
     * <tt>MediaStream</tt>s created by this <tt>MediaService</tt>.
     *
     * @return the <tt>MediaFormatFactory</tt> through which
     * <tt>MediaFormat</tt> instances may be created for the purposes of working
     * with the <tt>MediaStream</tt>s created by this <tt>MediaService</tt>
     */
    MediaFormatFactory getFormatFactory();

    /**
     * Removes a <tt>PropertyChangeListener</tt> to no longer be notified about
     * changes in the values of the properties of this instance.
     *
     * @param listener the <tt>PropertyChangeListener</tt> to no longer be
     * notified about changes in the values of the properties of this instance
     */
    void removePropertyChangeListener(PropertyChangeListener listener);
}
