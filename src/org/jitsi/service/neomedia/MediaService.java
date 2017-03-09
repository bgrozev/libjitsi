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
