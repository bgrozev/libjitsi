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
package org.jitsi.impl.neomedia.codec;

import java.io.*;
import java.util.*;

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Utility class that handles registration of FMJ packages and plugins.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public class FMJPlugInConfiguration
{
    /**
     * The package prefixes of the additional JMF <tt>DataSource</tt>s (e.g. low
     * latency PortAudio and ALSA <tt>CaptureDevice</tt>s).
     */
    private static final String[] CUSTOM_PACKAGES
        = {
            "org.jitsi.impl.neomedia.jmfext",
            "net.java.sip.communicator.impl.neomedia.jmfext",
            "net.sf.fmj"
        };

    /**
     * The <tt>Logger</tt> used by the <tt>FMJPlugInConfiguration</tt> class
     * for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(FMJPlugInConfiguration.class);

    /**
     * Whether custom packages have been registered with JFM
     */
    private static boolean packagesRegistered = false;

    /**
     * Register in JMF the custom packages we provide
     */
    public static void registerCustomPackages()
    {
        if(packagesRegistered)
            return;

        @SuppressWarnings("unchecked")
        Vector<String> packages = PackageManager.getProtocolPrefixList();
        boolean loggerIsDebugEnabled = logger.isDebugEnabled();

        // We prefer our custom packages/protocol prefixes over FMJ's.
        for (int i = CUSTOM_PACKAGES.length - 1; i >= 0; i--)
        {
            String customPackage = CUSTOM_PACKAGES[i];

            /*
             * Linear search in a loop but it doesn't have to scale since the
             * list is always short.
             */
            if (!packages.contains(customPackage))
            {
                packages.add(0, customPackage);
                if (loggerIsDebugEnabled)
                    logger.debug("Adding package  : " + customPackage);
            }
        }

        PackageManager.setProtocolPrefixList(packages);
        PackageManager.commitProtocolPrefixList();
        if (loggerIsDebugEnabled)
            logger.debug("Registering new protocol prefix list: " + packages);

        packagesRegistered = true;
    }
}
