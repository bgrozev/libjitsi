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

import java.io.*;
import java.security.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.impl.neomedia.transform.dtls.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;

import com.sun.media.util.*;

/**
 * Implements <tt>MediaService</tt> for JMF.
 *
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 * @author Boris Grozev
 */
public class MediaServiceImpl
    extends PropertyChangeNotifier
    implements MediaService
{
    /**
     * The <tt>Logger</tt> used by the <tt>MediaServiceImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaServiceImpl.class);

    /**
     * The name of the <tt>System</tt> boolean property which specifies whether
     * the committing of the JMF/FMJ <tt>Registry</tt> is to be disabled.
     */
    private static final String JMF_REGISTRY_DISABLE_COMMIT
        = "net.sf.fmj.utility.JmfRegistry.disableCommit";

    /**
     * The name of the <tt>System</tt> boolean property which specifies whether
     * the loading of the JMF/FMJ <tt>Registry</tt> is to be disabled.
     */
    private static final String JMF_REGISTRY_DISABLE_LOAD
        = "net.sf.fmj.utility.JmfRegistry.disableLoad";

    /**
     * The indicator which determined whether
     * {@link #postInitializeOnce()} has been executed in order
     * to perform one-time initialization after initializing the first instance
     * of <tt>MediaServiceImpl</tt>.
     */
    private static boolean postInitializeOnce;

    /**
     * The prefix that is used to store configuration for encodings preference.
     */
    private static final String ENCODING_CONFIG_PROP_PREFIX
        = "net.java.sip.communicator.impl.neomedia.codec.EncodingConfiguration";

    /**
     * The {@link EncodingConfiguration} instance that holds the current (global)
     * list of formats and their preference.
     */
    private final EncodingConfiguration currentEncodingConfiguration;

    /**
     * The <tt>MediaFormatFactory</tt> through which <tt>MediaFormat</tt>
     * instances may be created for the purposes of working with the
     * <tt>MediaStream</tt>s created by this <tt>MediaService</tt>.
     */
    private MediaFormatFactory formatFactory;

    static
    {
        setupFMJ();
    }

    /**
     * Initializes a new <tt>MediaServiceImpl</tt> instance.
     */
    public MediaServiceImpl()
    {
        currentEncodingConfiguration
             = new EncodingConfigurationConfigImpl(ENCODING_CONFIG_PROP_PREFIX);

        /*
         * Perform one-time initialization after initializing the first instance
         * of MediaServiceImpl.
         */
        synchronized (MediaServiceImpl.class)
        {
            if (!postInitializeOnce)
            {
                postInitializeOnce = true;
                postInitializeOnce();
            }
        }
    }

    /**
     * Create a <tt>MediaStream</tt> instance.
     *
     * @return a newly-created <tt>MediaStream</tt> which will use the specified
     * <tt>device</tt> for capture and playback of media
     */
    public MediaStream createMediaStream()
    {
        return createMediaStream(null, null, null);
    }

    /**
     * {@inheritDoc}
     *
     * Implements {@link MediaService#createMediaStream(MediaType)}. Initializes
     * a new <tt>AudioMediaStreamImpl</tt> or <tt>VideoMediaStreamImpl</tt> in
     * accord with <tt>mediaType</tt>
     */
    public MediaStream createMediaStream(MediaType mediaType)
    {
        return createMediaStream(null, mediaType, null);
    }

    /**
     * Creates a new <tt>MediaStream</tt> instance.
     *
     * @param connector the <tt>StreamConnector</tt> that the new
     * <tt>MediaStream</tt> instance is to use for sending and receiving media
     * @return a new <tt>MediaStream</tt> instance
     */
    public MediaStream createMediaStream(StreamConnector connector)
    {
        return createMediaStream(connector, null, null);
    }

    /**
     * {@inheritDoc}
     */
    public MediaStream createMediaStream(
            StreamConnector connector,
            MediaType mediaType)
    {
        return createMediaStream(connector, mediaType, null);
    }

    /**
     * Creates a new <tt>MediaStream</tt> instance which will use the specified
     * <tt>MediaDevice</tt> for both capture and playback of media exchanged
     * via the specified <tt>StreamConnector</tt>.
     *
     * @param connector the <tt>StreamConnector</tt> that the new
     * <tt>MediaStream</tt> instance is to use for sending and receiving media
     * @param srtpControl a control which is already created, used to control
     * the SRTP operations.
     *
     * @return a new <tt>MediaStream</tt> instance
     */
    public MediaStream createMediaStream(
            StreamConnector connector,
            SrtpControl srtpControl)
    {
        return createMediaStream(connector, null, srtpControl);
    }

    /**
     * {@inheritDoc}
     */
    public MediaStream createMediaStream(
            StreamConnector connector,
            MediaType mediaType,
            SrtpControl srtpControl)
    {
        // Make sure that mediaType and device are in accord.
        if (mediaType == null)
        {
            // TODO FIXME
            mediaType = MediaType.AUDIO;
            //throw new NullPointerException("mediaType");
        }

        switch (mediaType)
        {
        case AUDIO:
            return new AudioMediaStreamImpl(connector, srtpControl);
        case VIDEO:
            return new VideoMediaStreamImpl(connector, srtpControl);
        default:
            return null;
        }
    }

    /**
     * Returns the current encoding configuration -- the instance that contains
     * the global settings. Note that any changes made to this instance will
     * have immediate effect on the configuration.
     *
     * @return the current encoding configuration -- the instance that contains
     * the global settings.
     */
    public EncodingConfiguration getCurrentEncodingConfiguration()
    {
        return currentEncodingConfiguration;
    }

    /**
     * Gets the <tt>MediaFormatFactory</tt> through which <tt>MediaFormat</tt>
     * instances may be created for the purposes of working with the
     * <tt>MediaStream</tt>s created by this <tt>MediaService</tt>.
     *
     * @return the <tt>MediaFormatFactory</tt> through which
     * <tt>MediaFormat</tt> instances may be created for the purposes of working
     * with the <tt>MediaStream</tt>s created by this <tt>MediaService</tt>
     * @see MediaService#getFormatFactory()
     */
    public MediaFormatFactory getFormatFactory()
    {
        if (formatFactory == null)
            formatFactory = new MediaFormatFactoryImpl();
        return formatFactory;
    }

    /**
     * {@inheritDoc}
     */
    public SrtpControl createSrtpControl(SrtpControlType srtpControlType)
    {
        switch (srtpControlType)
        {
        case DTLS_SRTP:
            return new DtlsControlImpl();
        default:
            return null;
        }
    }

    /**
     * Initializes a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s.
     *
     * @return a new <tt>RTPTranslator</tt> which is to forward RTP and RTCP
     * traffic between multiple <tt>MediaStream</tt>s
     * @see MediaService#createRTPTranslator()
     */
    public RTPTranslator createRTPTranslator()
    {
        return new RTPTranslatorImpl();
    }

    /**
     * Performs one-time initialization after initializing the first instance of
     * <tt>MediaServiceImpl</tt>.
     */
    private static void postInitializeOnce()
    {
        /*
         * Some SecureRandom() implementations like SHA1PRNG call
         * /dev/random to seed themselves on first use.
         * Call SecureRandom early to avoid blocking when establishing
         * a connection for example.
         */
        logger.info("Warming up SecureRandom...");
        SecureRandom rnd = new SecureRandom();
        byte[] b = new byte[20];
        rnd.nextBytes(b);
        logger.info("Warming up SecureRandom finished.");
    }

    /**
     * Sets up FMJ for execution. For example, sets properties which instruct
     * FMJ whether it is to create a log, where the log is to be created.
     */
    private static void setupFMJ()
    {
        /*
         * FMJ now uses java.util.logging.Logger, but only logs if allowLogging
         * is set in its registry. Since the levels can be configured through
         * properties for the net.sf.fmj.media.Log class, we always enable this
         * (as opposed to only enabling it when this.logger has debug enabled).
         */
        Registry.set("allowLogging", true);

        /*
         * Disable the loading of .fmj.registry because Kertesz Laszlo has
         * reported that audio input devices duplicate after restarting Jitsi.
         * Besides, Jitsi does not really need .fmj.registry on startup.
         */
        if (System.getProperty(JMF_REGISTRY_DISABLE_LOAD) == null)
            System.setProperty(JMF_REGISTRY_DISABLE_LOAD, "true");
        if (System.getProperty(JMF_REGISTRY_DISABLE_COMMIT) == null)
            System.setProperty(JMF_REGISTRY_DISABLE_COMMIT, "true");

        String scHomeDirLocation
            = System.getProperty(
                ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION);

        if (scHomeDirLocation != null)
        {
            String scHomeDirName
                = System.getProperty(
                        ConfigurationService.PNAME_SC_HOME_DIR_NAME);

            if (scHomeDirName != null)
            {
                File scHomeDir = new File(scHomeDirLocation, scHomeDirName);

                /* Write FMJ's log in Jitsi's log directory. */
                Registry.set(
                        "secure.logDir",
                        new File(scHomeDir, "log").getPath());

                /* Write FMJ's registry in Jitsi's user data directory. */
                String jmfRegistryFilename
                    = "net.sf.fmj.utility.JmfRegistry.filename";

                if (System.getProperty(jmfRegistryFilename) == null)
                {
                    System.setProperty(
                            jmfRegistryFilename,
                            new File(scHomeDir, ".fmj.registry")
                                .getAbsolutePath());
                }
            }
        }

        FMJPlugInConfiguration.registerCustomPackages();
    }
}
