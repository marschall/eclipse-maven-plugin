package org.apache.maven.plugin.eclipse.reader;

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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.maven.plugin.eclipse.Messages;
import org.apache.maven.plugin.eclipse.WorkspaceConfiguration;
import org.apache.maven.plugin.ide.IdeDependency;
import org.apache.maven.plugin.ide.IdeUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.core.internal.localstore.SafeChunkyInputStream;

/**
 * Scan the eclipse workspace and create a array with {@link IdeDependency} for all found artefacts.
 * 
 * @author Richard van Nieuwenhoven
 * @version $Id$
 */
public class ReadWorkspaceLocations
{

    public static final String BINARY_LOCATION_FILE = ".location";

    public static final String METADATA_PLUGINS_ORG_ECLIPSE_CORE_RESOURCES_PROJECTS =
        ".metadata/.plugins/org.eclipse.core.resources/.projects";

    private static final String[] PARENT_VERSION = new String[] { "parent", "version" };

    private static final String[] PARENT_GROUP_ID = new String[] { "parent", "groupId" };

    private static final String[] PACKAGING = new String[] { "packaging" };

    private static final String[] VERSION = new String[] { "version" };

    private static final String[] GROUP_ID = new String[] { "groupId" };

    private static final String[] ARTEFACT_ID = new String[] { "artifactId" };

    private static final String METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_LAUNCHING_PREFS =
        ".metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.jdt.launching.prefs";

    private static final String METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_PREFS_VM_KEY =
        "org.eclipse.jdt.launching.PREF_VM_XML";

    private static final String METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_SERVER_PREFS =
        ".metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.wst.server.core.prefs";

    private static final String METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_PREFS_RUNTIMES_KEY = "runtimes";

    private static final String CLASSPATHENTRY_DEFAULT = "org.eclipse.jdt.launching.JRE_CONTAINER";

    private static final String CLASSPATHENTRY_STANDARD = CLASSPATHENTRY_DEFAULT
        + "/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/";

    private static final String CLASSPATHENTRY_FORMAT = ReadWorkspaceLocations.CLASSPATHENTRY_DEFAULT + "/{0}/{1}";

    public void init( Log log, WorkspaceConfiguration workspaceConfiguration, MavenProject project,
                      String wtpDefaultServer, boolean preferStandardClasspathContainer )
    {
        workspaceConfiguration.setDefaultClasspathContainer( 
                                                 detectDefaultJREContainer( workspaceConfiguration,
                                                                            project,
                                                                            preferStandardClasspathContainer,
                                                                            log ) );
        readWorkspace( workspaceConfiguration, log );
        detectWTPDefaultServer( workspaceConfiguration, wtpDefaultServer, log );
    }

    /**
     * Detect WTP Default Server. Do nothing if tehre are no defined servers in the settings.
     * 
     * @param workspaceConfiguration the workspace configuration
     * @param wtpDefaultServer Default server
     * @param log the log
     */
    private void detectWTPDefaultServer( WorkspaceConfiguration workspaceConfiguration, 
                                         String wtpDefaultServer, 
                                         Log log )
    {
        Map<String, String> servers = readDefinedServers( workspaceConfiguration, log );
        if ( servers == null || servers.isEmpty() )
        {
            return;
        }
        if ( wtpDefaultServer != null )
        {
            Set<String> ids = servers.keySet();
            // first we try the exact match
            Iterator<String> idIterator = ids.iterator();
            while ( workspaceConfiguration.getDefaultDeployServerId() == null && idIterator.hasNext() )
            {
                String id = idIterator.next();
                String name = servers.get( id );
                if ( wtpDefaultServer.equals( id ) || wtpDefaultServer.equals( name ) )
                {
                    workspaceConfiguration.setDefaultDeployServerId( id );
                    workspaceConfiguration.setDefaultDeployServerName( name );
                }
            }
            if ( workspaceConfiguration.getDefaultDeployServerId() == null )
            {
                log.info( "no exact wtp server match." );
                // now we will try the substring match
                idIterator = ids.iterator();
                while ( workspaceConfiguration.getDefaultDeployServerId() == null && idIterator.hasNext() )
                {
                    String id = idIterator.next();
                    String name = servers.get( id );
                    if ( id.contains( wtpDefaultServer ) || name.contains( wtpDefaultServer ) )
                    {
                        workspaceConfiguration.setDefaultDeployServerId( id );
                        workspaceConfiguration.setDefaultDeployServerName( name );
                    }
                }
            }
        }
        if ( workspaceConfiguration.getDefaultDeployServerId() == null && servers.size() > 0 )
        {
            // now take the default server
            log.info( "no substring wtp server match." );
            workspaceConfiguration.setDefaultDeployServerId( servers.get( "" ) );
            workspaceConfiguration.setDefaultDeployServerName( 
                                               servers.get( workspaceConfiguration.getDefaultDeployServerId() ) );
        }
        log.info( "Using as WTP server : " + workspaceConfiguration.getDefaultDeployServerName() );
    }

    /**
     * Take the compiler executable and try to find a JRE that contains that compiler.
     * 
     * @param rawExecutable the executable with the complete path.
     * @param jreMap the map with defined JRE's.
     * @param logger the logger to log the error's
     * @return the found container or null if non found.
     */
    private String getContainerFromExecutable( String rawExecutable, Map<String, String> jreMap, Log logger )
    {
        String foundContainer;
        if ( rawExecutable != null )
        {
            String executable;
            try
            {
                executable = new File( rawExecutable ).getCanonicalPath();
                logger.debug( "detected executable: " + executable );
            }
            catch ( Exception e )
            {
                return null;
            }
            File executableFile = new File( executable );
            while ( executableFile != null )
            {
                foundContainer = jreMap.get( executableFile.getPath() );
                if ( foundContainer != null )
                {
                    logger.debug( "detected classpathContainer from executable: " + foundContainer );
                    return foundContainer;

                }
                executableFile = executableFile.getParentFile();
            }
        }
        return null;
    }

    /**
     * Search the default JREContainer from eclipse for the current MavenProject
     * 
     * @param workspaceConfiguration the location of the workspace.
     * @param project the maven project the get the configuration
     * @param preferStandardClasspathContainer prefer using the standard classpath container name
     * @param logger the logger for errors
     */
    private String detectDefaultJREContainer( WorkspaceConfiguration workspaceConfiguration, MavenProject project,
                                              boolean preferStandardClasspathContainer, Log logger )
    {
        File workspaceDirectory = workspaceConfiguration.getWorkspaceDirectory();

        Map<String, String> jreMap =
            readAvailableJREs( preferStandardClasspathContainer ? null : workspaceDirectory, logger );
        if ( jreMap != null )
        {
            String foundContainer = null;
            if ( preferStandardClasspathContainer )
            {
                foundContainer = getContainerFromSourceVersion( project, jreMap, logger );
                if ( foundContainer != null )
                {
                    return foundContainer;
                }
            }

            foundContainer =
                getContainerFromExecutable( System.getProperty( "maven.compiler.executable" ), jreMap, logger );
            if ( foundContainer != null )
            {
                return foundContainer;
            }

            foundContainer =
                getContainerFromExecutable( IdeUtils.getCompilerPluginSetting( project, "executable" ), jreMap, 
                                            logger );
            if ( foundContainer != null )
            {
                return foundContainer;
            }

            if ( !preferStandardClasspathContainer )
            {
                foundContainer = getContainerFromSourceVersion( project, jreMap, logger );
                if ( foundContainer != null )
                {
                    return foundContainer;
                }
            }

            foundContainer = getContainerFromExecutable( System.getProperty( "java.home" ), jreMap, logger );
            if ( foundContainer != null )
            {
                return foundContainer;
            }
        }
        return ReadWorkspaceLocations.CLASSPATHENTRY_DEFAULT;
    }

    private String getContainerFromSourceVersion( MavenProject project, Map<String, String> jreMap, Log logger )
    {
        String foundContainer;
        String sourceVersion = IdeUtils.getCompilerSourceVersion( project );
        if ( sourceVersion == null )
        {
            sourceVersion = IdeUtils.getCompilerReleaseVersion( project );
        }
        foundContainer = jreMap.get( sourceVersion );
        if ( foundContainer != null )
        {
            if ( logger.isDebugEnabled() )
            {
                logger.debug( "detected classpathContainer from sourceVersion(" + sourceVersion + "): "
                    + foundContainer );
            }
        }
        return foundContainer;
    }

    /**
     * Get the project location for a project in the eclipse metadata.
     * 
     * @param workspaceLocation the location of the workspace
     * @param project the project subdirectory in the metadata
     * @return the full path to the project.
     * @throws IOException failures to read location file
     * @throws URISyntaxException failures to read location file
     */
    /* package */File getProjectLocation( File workspaceLocation, File project )
        throws IOException, URISyntaxException
    {
        File location = new File( project, ReadWorkspaceLocations.BINARY_LOCATION_FILE );
        if ( location.exists() )
        {
            SafeChunkyInputStream fileInputStream = null;
            DataInputStream dataInputStream = null;
            try
            {
                fileInputStream = new SafeChunkyInputStream( location );
                dataInputStream = new DataInputStream( fileInputStream );
                String file = dataInputStream.readUTF().trim();

                if ( file.length() > 0 )
                {
                    if ( !file.startsWith( "URI//" ) )
                    {
                        throw new IOException( location.getAbsolutePath() + " contains unexpected data: " + file );
                    }
                    file = file.substring( "URI//".length() );
                    return new File( new URI( file ) );
                }
            }
            finally
            {
                IOUtil.close( fileInputStream );
                IOUtil.close( dataInputStream );
            }
        }
        File projectBase = new File( workspaceLocation, project.getName() );
        if ( projectBase.isDirectory() )
        {
            return projectBase;
        }

        return null;
    }

    /**
     * get a value from a dom element.
     * 
     * @param element the element to get a value from
     * @param elementNames the sub elements to get
     * @param defaultValue teh default value if the value was null or empty
     * @return the value of the dome element.
     */
    private String getValue( Xpp3Dom element, String[] elementNames, String defaultValue )
    {
        String value = null;
        Xpp3Dom dom = element;
        for ( int index = 0; dom != null && index < elementNames.length; index++ )
        {
            dom = dom.getChild( elementNames[index] );
        }
        if ( dom != null )
        {
            value = dom.getValue();
        }
        if ( value == null || value.trim().length() == 0 )
        {
            return defaultValue;
        }
        else
        {
            return value;
        }
    }

    /**
     * Read the artefact information from the pom in the project location and the eclipse project name from the .project
     * file.
     * 
     * @param projectLocation the location of the project
     * @param logger the logger to report errors and debug info.
     * @return an {@link IdeDependency} or null.
     * @throws XmlPullParserException
     * @throws IOException
     */
    private IdeDependency readArtefact( File projectLocation, Log logger )
        throws XmlPullParserException, IOException
    {
        File projectFile = new File( projectLocation, ".project" );
        String eclipseProjectName = projectLocation.getName();
        if ( projectFile.exists() )
        {
            Xpp3Dom project = Xpp3DomBuilder.build( new FileReader( projectFile ) );
            eclipseProjectName = getValue( project, new String[] { "name" }, eclipseProjectName );
        }
        File pomFile = new File( projectLocation, "pom.xml" );
        if ( pomFile.exists() )
        {
            Xpp3Dom pom = Xpp3DomBuilder.build( new FileReader( pomFile ) );

            String artifact = getValue( pom, ReadWorkspaceLocations.ARTEFACT_ID, null );
            String group =
                getValue( pom, ReadWorkspaceLocations.GROUP_ID,
                          getValue( pom, ReadWorkspaceLocations.PARENT_GROUP_ID, null ) );
            String version =
                getValue( pom, ReadWorkspaceLocations.VERSION,
                          getValue( pom, ReadWorkspaceLocations.PARENT_VERSION, null ) );
            String packaging = getValue( pom, ReadWorkspaceLocations.PACKAGING, "jar" );

            logger.debug( "found workspace artefact " + group + ":" + artifact + ":" + version + " " + packaging + " ("
                + eclipseProjectName + ")" + " -> " + projectLocation.getAbsolutePath() );
            return new IdeDependency( group, artifact, version, packaging, true, false, false, false, false, null,
                                      packaging, eclipseProjectName );
        }
        else
        {
            logger.debug( "ignored workspace project NO pom available " + projectLocation.getAbsolutePath() );
            return null;
        }
    }

    /* package */Map<String, String> readDefinedServers( WorkspaceConfiguration workspaceConfiguration, Log logger )
    {
        Map<String, String> detectedRuntimes = new HashMap<>();
        if ( workspaceConfiguration.getWorkspaceDirectory() != null )
        {
            Xpp3Dom runtimesElement = null;
            try
            {
                File prefs =
                    new File( workspaceConfiguration.getWorkspaceDirectory(),
                              ReadWorkspaceLocations.METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_SERVER_PREFS );
                if ( prefs.exists() )
                {
                    Properties properties = new Properties();
                    properties.load( new FileInputStream( prefs ) );
                    String runtimes = properties.getProperty( 
                              ReadWorkspaceLocations.METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_PREFS_RUNTIMES_KEY );
                    if ( runtimes != null )
                    {
                        runtimesElement = Xpp3DomBuilder.build( new StringReader( runtimes ) );
                    }
                }
            }
            catch ( Exception e )
            {
                logger.error( "Could not read workspace wtp server runtimes preferences : " + e.getMessage() );
            }

            if ( runtimesElement != null )
            {
                Xpp3Dom[] runtimeArray = runtimesElement.getChildren( "runtime" );
                for ( int index = 0; runtimeArray != null && index < runtimeArray.length; index++ )
                {
                    String id = runtimeArray[index].getAttribute( "id" );
                    String name = runtimeArray[index].getAttribute( "name" );
                    if ( detectedRuntimes.isEmpty() )
                    {
                        logger.debug( "Using WTP runtime with id: \"" + id + "\" as default runtime" );
                        detectedRuntimes.put( "", id );
                    }
                    detectedRuntimes.put( id, name );
                    logger.debug( "Detected WTP runtime with id: \"" + id + "\" and name: \"" + name + "\"" );
                }
            }
        }
        return detectedRuntimes;
    }

    /**
     * Read the JRE definition configured in the workspace. They will be put in a HashMap with as key there path and as
     * value the JRE constant. a second key is included with the JRE version as a key.
     * 
     * @param workspaceLocation the workspace location
     * @param logger the logger to error messages
     * @return the map with found jre's
     */
    private Map<String, String> readAvailableJREs( File workspaceLocation, Log logger )
    {
        Map<String, String> jreMap = new HashMap<>();
        jreMap.put( "1.2", CLASSPATHENTRY_STANDARD + "J2SE-1.2" );
        jreMap.put( "1.3", CLASSPATHENTRY_STANDARD + "J2SE-1.3" );
        jreMap.put( "1.4", CLASSPATHENTRY_STANDARD + "J2SE-1.4" );
        jreMap.put( "1.5", CLASSPATHENTRY_STANDARD + "J2SE-1.5" );
        jreMap.put( "5", jreMap.get( "1.5" ) );
        jreMap.put( "1.6", CLASSPATHENTRY_STANDARD + "JavaSE-1.6" );
        jreMap.put( "6", jreMap.get( "1.6" ) );
        jreMap.put( "1.7", CLASSPATHENTRY_STANDARD + "JavaSE-1.7" );
        jreMap.put( "7", jreMap.get( "1.7" ) );
        jreMap.put( "1.8", CLASSPATHENTRY_STANDARD + "JavaSE-1.8" );
        jreMap.put( "8", jreMap.get( "1.8" ) );
        for ( int i = 9; i < 18; i++ )
        {
            jreMap.put( Integer.toString( i ), CLASSPATHENTRY_STANDARD + "JavaSE-" + i );
        }

        if ( workspaceLocation == null )
        {
            return jreMap;
        }

        Xpp3Dom vms;
        try
        {
            File prefs =
                new File( workspaceLocation,
                          ReadWorkspaceLocations.METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_LAUNCHING_PREFS );
            if ( !prefs.exists() )
            {
                return null;
            }
            Properties properties = new Properties();
            properties.load( new FileInputStream( prefs ) );
            vms =
                Xpp3DomBuilder.build( new StringReader( properties.getProperty( 
                                ReadWorkspaceLocations.METADATA_PLUGINS_ORG_ECLIPSE_CORE_RUNTIME_PREFS_VM_KEY ) ) );
        }
        catch ( Exception e )
        {
            logger.error( "Could not read workspace JRE preferences", e );
            return null;
        }
        String defaultJRE = vms.getAttribute( "defaultVM" ).trim();
        Xpp3Dom[] vmTypes = vms.getChildren( "vmType" );
        for ( Xpp3Dom vmType : vmTypes )
        {
            String typeId = vmType.getAttribute( "id" );
            Xpp3Dom[] vm = vmType.getChildren( "vm" );
            for ( Xpp3Dom aVm : vm )
            {
                try
                {
                    String path = aVm.getAttribute( "path" );
                    String name = aVm.getAttribute( "name" );
                    String vmId = aVm.getAttribute( "id" ).trim();
                    String classpathEntry =
                        MessageFormat.format( ReadWorkspaceLocations.CLASSPATHENTRY_FORMAT, typeId, name );
                    String jrePath = new File( path ).getCanonicalPath();
                    File rtJarFile = new File( new File( jrePath ), "jre/lib/rt.jar" );
                    if ( !rtJarFile.exists() )
                    {
                        logger.warn( Messages.getString( "EclipsePlugin.invalidvminworkspace", jrePath ) );
                        continue;
                    }
                    String version;
                    try (JarFile rtJar = new JarFile( rtJarFile )) {
                        version = rtJar.getManifest().getMainAttributes().getValue( "Specification-Version" );
                    }
                    if ( defaultJRE.endsWith( "," + vmId ) )
                    {
                        jreMap.put( jrePath, ReadWorkspaceLocations.CLASSPATHENTRY_DEFAULT );
                        jreMap.put( version, ReadWorkspaceLocations.CLASSPATHENTRY_DEFAULT );
                        logger.debug( "Default Classpath Container version: " + version + "  location: " + jrePath );
                    }
                    else if ( !jreMap.containsKey( jrePath ) )
                    {
                        if ( !jreMap.containsKey( version ) )
                        {
                            jreMap.put( version, classpathEntry );
                        }
                        jreMap.put( jrePath, classpathEntry );
                        logger.debug( "Additional Classpath Container version: " + version + " " + classpathEntry
                            + " location: " + jrePath );
                    }
                    else
                    {
                        logger.debug( "Ignored (duplicated) additional Classpath Container version: " + version + " "
                            + classpathEntry + " location: " + jrePath );
                    }
                }
                catch ( IOException e )
                {
                    logger.warn( "Could not interpret entry: " + aVm.toString() );
                }
            }
        }
        return jreMap;
    }

    /**
     * @param workspaceDirectory the directory of the workspace
     * @param logger logger
     * @return the physical locations of all workspace projects
     */
    public List<File> readProjectLocations( File workspaceDirectory, Log logger )
    {
        List<File> projectLocations = new ArrayList<>();
        File projectsDirectory =
            new File( workspaceDirectory, ReadWorkspaceLocations.METADATA_PLUGINS_ORG_ECLIPSE_CORE_RESOURCES_PROJECTS );

        if ( projectsDirectory.exists() )
        {
            for ( File project : projectsDirectory.listFiles() )
            {
                if ( project.isDirectory() )
                {
                    try
                    {
                        File projectLocation = getProjectLocation( workspaceDirectory, project );
                        if ( projectLocation != null )
                        {
                            projectLocations.add( projectLocation );
                        }
                    }
                    catch ( Exception e )
                    {
                        logger.warn( "could not read workspace project:" + project, e );
                    }
                }
            }
        }

        return projectLocations;
    }

    /**
     * Scan the eclipse workspace and create a array with {@link IdeDependency} for all found artifacts.
     * 
     * @param workspaceConfiguration the location of the eclipse workspace.
     * @param logger the logger to report errors and debug info.
     */
    private void readWorkspace( WorkspaceConfiguration workspaceConfiguration, Log logger )
    {
        List<IdeDependency> dependencies = new ArrayList<>();
        File workspaceDirectory = workspaceConfiguration.getWorkspaceDirectory();
        if ( workspaceDirectory != null )
        {
            for ( File projectLocation : readProjectLocations( workspaceDirectory, logger ) )
            {
                try
                {
                    logger.debug( "read workpsace project " + projectLocation );
                    IdeDependency ideDependency = readArtefact( projectLocation, logger );
                    if ( ideDependency != null )
                    {
                        dependencies.add( ideDependency );
                    }
                }
                catch ( Exception e )
                {
                    logger.warn( "could not read workspace project from:" + projectLocation, e );
                }
            }
        }
        logger.debug( dependencies.size() + " from workspace " + workspaceDirectory );
        workspaceConfiguration.setWorkspaceArtefacts( dependencies.toArray( new IdeDependency[dependencies.size()] ) );
    }
}
