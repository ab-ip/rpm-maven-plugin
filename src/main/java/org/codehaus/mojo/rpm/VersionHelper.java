package org.codehaus.mojo.rpm;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;

/**
 * Assists in calculating an rpm compatible version and build number (release) from the maven version.
 * <p>
 * If not populated, the release will be generated from the modifier portion of the
 * {@link RPMVersionableMojo#getVersion() project version} using the following rules:
 * <ul>
 * <li>If no modifier exists, the release will be <code>1</code>.</li>
 * <li>If the modifier ends with <i>SNAPSHOT</i>, the timestamp (in UTC) of the build will be appended to end.</li>
 * <li>All instances of <code>'-'</code> in the modifier will be replaced with <code>'_'</code>.</li>
 * <li>If a modifier exists and does not end with <i>SNAPSHOT</i>, <code>"_1"</code> will be appended to end.</li>
 * </ul>
 * </p>
 *
 * @author Brett Okken
 * @since 2.0
 */
final class VersionHelper
{
    /**
     * Wraps the version and release components that make up the entire version.
     */
    static final class Version
    {
        String version;

        String release;

        public String toString()
        {
            return version + '-' + release;
        }
    }

    /**
     * Interface for the rpm related Mojos to implement. Provides access to the configured/default version and release
     * attributes.
     */
    interface RPMVersionableMojo
        extends Mojo
    {
        String getVersion();

        String getRelease();

        Date getBuildTimestamp();
    }

    private final RPMVersionableMojo mojo;

    /**
     * @param mojo
     */
    public VersionHelper( RPMVersionableMojo mojo )
    {
        super();
        this.mojo = mojo;
    }

    /**
     * Calculates and formats the version and release attributes based on the documented rules.
     *
     * @return The calculated/formatted version attributes.
     */
    Version calculateVersion()
    {
        final Version response = new Version();

        final String version = mojo.getVersion();
        final String release = mojo.getRelease();

        // this will get overwritten if we calculate a "release" value
        response.release = release;

        int modifierIndex = version.indexOf( '-' );
        if ( modifierIndex == -1 )
        {
            response.version = version;

            if ( release == null || release.length() == 0 )
            {
                response.release = "1";
            }
        }
        else
        {
            response.version = version.substring( 0, modifierIndex );
            mojo.getLog().warn( "rpm version string truncated to " + response.version );

            if ( release == null || release.length() == 0 )
            {
                String modifier = version.substring( modifierIndex + 1, version.length() );

                modifier = modifier.replace( '-', '_' );

                if ( modifier.endsWith( "SNAPSHOT" ) )
                {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat( "yyyyMMddHHmmss" );
                    simpleDateFormat.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
                    modifier += simpleDateFormat.format( mojo.getBuildTimestamp() );
                }
                else
                {
                    modifier += "_1";
                }

                response.release = modifier;
            }
        }

        return response;
    }
    
    /**
     * Evaluates the <i>macro</i> by executing <code>rpm --eval %<i>macro</i></code>.
     *
     * @param macro The macro to evaluate.
     * @return The result of rpm --eval.
     * @throws MojoExecutionException
     * @since 2.1-alpha-1
     */
    public String evaluateMacro( String macro )
        throws MojoExecutionException
    {
        final Commandline cl = new Commandline();
        cl.setExecutable( "rpm" );
        cl.createArg().setValue( "--eval" );
        cl.createArg().setValue( '%' + macro );

        final Log log = mojo.getLog();

        final StringStreamConsumer stdout = new StringStreamConsumer();
        final StreamConsumer stderr = new LogStreamConsumer( LogStreamConsumer.INFO, log );
        try
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "About to execute \'" + cl.toString() + "\'" );
            }

            int result = CommandLineUtils.executeCommandLine( cl, stdout, stderr );
            if ( result != 0 )
            {
                throw new MojoExecutionException( "rpm --eval returned: \'" + result + "\' executing \'"
                    + cl.toString() + "\'" );
            }
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Unable to evaluate macro: " + macro, e );
        }

        return stdout.getOutput().trim();
    }
}
