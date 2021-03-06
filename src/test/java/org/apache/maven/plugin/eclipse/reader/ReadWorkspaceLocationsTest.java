/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.maven.plugin.eclipse.reader;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.maven.plugin.eclipse.TempEclipseWorkspace;
import org.apache.maven.plugin.eclipse.WorkspaceConfiguration;
import org.apache.maven.plugin.logging.Log;
import org.easymock.EasyMockSupport;

import junit.framework.TestCase;

/**
 * @author <a href="mailto:baerrach@apache.org">Barrie Treloar</a>
 * @version $Id$
 */
public class ReadWorkspaceLocationsTest
    extends TestCase
{

    private EasyMockSupport mm = new EasyMockSupport();
    private File workspaceLocation;
    private File metaDataDirectory;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();
        
        workspaceLocation = TempEclipseWorkspace.getFixtureEclipseDynamicWorkspace().workspaceLocation;
        metaDataDirectory = new File( workspaceLocation, ReadWorkspaceLocations.METADATA_PLUGINS_ORG_ECLIPSE_CORE_RESOURCES_PROJECTS );

    }

    /**
     * Project's at the workspace level do not have a .location file.
     * <p>
     * Therefore their project location is directly under the workspace.
     * 
     * @throws Exception
     */
    public void testGetProjectLocation_ForProjectAtWorkspaceLevel()
        throws Exception
    {
        ReadWorkspaceLocations objectUnderTest = new ReadWorkspaceLocations();

        File metadataProjectDirectory = new File( metaDataDirectory , "project-A" );

        File projectLocation = objectUnderTest.getProjectLocation( workspaceLocation, metadataProjectDirectory );

        File expectedProjectDirectory = new File( workspaceLocation, "project-A" );
        assertFileEquals( expectedProjectDirectory, projectLocation );
    }

    /**
     * Project's located other than at the workspace level have a .location file.
     * <p>
     * This URI specifies the fully qualified path to the project. Which may be located outside of the workspace as
     * well.
     * 
     * @throws Exception
     */
    public void testGetProjectLocation_ForProjectsWithinProjects()
        throws Exception
    {
        ReadWorkspaceLocations objectUnderTest = new ReadWorkspaceLocations();

        File metadataProjectDirectory = new File( metaDataDirectory, "module-A1" );
        File expectedProjectDirectory =
            new File( workspaceLocation, "project-A/module-A1" );

        File projectLocation = objectUnderTest.getProjectLocation( workspaceLocation, metadataProjectDirectory );

        assertFileEquals( expectedProjectDirectory, projectLocation );
    }

    /**
     * Project's located other than at the workspace level have a .location file.
     * <p>
     * This URI specifies the fully qualified path to the project. Which may be located outside of the workspace as
     * well.
     * 
     * @throws Exception
     */
    public void testGetProjectLocation_ForProjectsOutsideWorkspace()
        throws Exception
    {
        ReadWorkspaceLocations objectUnderTest = new ReadWorkspaceLocations();

        File metadataProjectDirectory = new File( metaDataDirectory, "project-O" );
        File expectedProjectDirectory = new File( workspaceLocation, "../project-O" );

        File projectLocation = objectUnderTest.getProjectLocation( workspaceLocation, metadataProjectDirectory );

        assertFileEquals( expectedProjectDirectory, projectLocation );
    }

    public void testReadDefinedServers_PrefsFileDoesNotExist()
        throws Exception
    {
        Log logger = mm.mock( Log.class );

        WorkspaceConfiguration workspaceConfiguration = new WorkspaceConfiguration();
        workspaceConfiguration.setWorkspaceDirectory( new File( "/does/not/exist" ) );

        mm.replayAll();

        ReadWorkspaceLocations objectUnderTest = new ReadWorkspaceLocations();
        Map<String, String> servers = objectUnderTest.readDefinedServers( workspaceConfiguration, logger );

        mm.verifyAll();
        assertTrue( servers.isEmpty() );
    }

    public void testReadDefinedServers_PrefsFileExistsWithMissingRuntimes()
        throws Exception
    {
        Log logger = mm.mock( Log.class );

        WorkspaceConfiguration workspaceConfiguration = new WorkspaceConfiguration();
        File prefsFile =
            new File(
                      "target/test-classes/eclipse/dynamicWorkspace/workspace/.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.wst.server.core.prefs" );
        workspaceConfiguration.setWorkspaceDirectory( prefsFile );
        mm.replayAll();

        ReadWorkspaceLocations objectUnderTest = new ReadWorkspaceLocations();
        Map<String, String> servers = objectUnderTest.readDefinedServers( workspaceConfiguration, logger );

        mm.verifyAll();
        assertTrue( servers.isEmpty() );
    }

    /**
     * Assert that two files represent the same absolute file.
     * 
     * @param expectedFile
     * @param actualFile
     * @throws IOException
     */
    private void assertFileEquals( File expectedFile, File actualFile )
        throws IOException
    {
        assertEquals( expectedFile.getCanonicalFile(), actualFile.getCanonicalFile() );

    }

}
