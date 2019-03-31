package org.apache.maven.plugin.eclipse.writers;

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

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.eclipse.BuildCommand;
import org.apache.maven.plugin.eclipse.EclipseSourceDir;
import org.apache.maven.plugin.eclipse.LinkedResource;
import org.apache.maven.plugin.eclipse.WorkspaceConfiguration;
import org.apache.maven.plugin.ide.IdeDependency;
import org.apache.maven.project.MavenProject;

/**
 * @author Fabrizio Giustina
 * @version $Id$
 */
public class EclipseWriterConfig
{
    /**
     * The maven project.
     */
    private MavenProject project;

    /**
     * The maven project packaging.
     */
    private String packaging;

    /**
     * Eclipse project dir.
     */
    private File eclipseProjectDirectory;

    /**
     * The name of the project in eclipse.
     */
    private String eclipseProjectName;

    /**
     * Base project dir.
     */
    private File projectBaseDir;

    /**
     * List of IDE dependencies.
     */
    private IdeDependency[] deps = new IdeDependency[0];

    /**
     * Source directories.
     */
    private EclipseSourceDir[] sourceDirs;

    /**
     * Local maven repo.
     */
    private ArtifactRepository localRepository;

    /**
     * Build output directory for eclipse.
     */
    private File buildOutputDirectory;

    /**
     * Manifest file.
     */
    private File osgiManifestFile;

    /**
     * Project natures.
     */
    private List<String> projectnatures;

    /**
     * Project facets.
     */
    private Map<String, String> projectFacets;

    /**
     * Build commands.
     */
    private List<BuildCommand> buildCommands;

    /**
     * Classpath containers.
     */
    private List<String> classpathContainers;

    /**
     * @see org.apache.maven.plugin.eclipse.EclipsePlugin#getProjectNameTemplate()
     */
    private String projectNameTemplate;

    /**
     * @see org.apache.maven.plugin.eclipse.EclipsePlugin#deployName()
     */

    private String contextName;

    /**
     * @see org.apache.maven.plugin.eclipse.EclipsePlugin#wtpapplicationxml()
     */
    private boolean wtpapplicationxml;

    /**
     * @see org.apache.maven.plugin.eclipse.EclipsePlugin#getWtpversion()
     */
    private float wtpVersion;

    private WorkspaceConfiguration workspaceConfiguration;

    private List<LinkedResource> linkedResources;

    /**
     * @See {@link org.apache.maven.plugin.eclipse.EclipsePlugin#classpathContainersLast}
     */
    private boolean classpathContainersLast;

    /**
     * @see {@link org.apache.maven.plugin.eclipse.EclipsePlugin#jeeversion}
     */
    private String jeeVersion;

    public WorkspaceConfiguration getWorkspaceConfiguration()
    {
        return workspaceConfiguration;
    }

    public void setWorkspaceConfiguration( WorkspaceConfiguration workspaceConfiguration )
    {
        this.workspaceConfiguration = workspaceConfiguration;
    }

    /**
     * Getter for <code>deps</code>.
     * 
     * @return Returns the deps.
     */
    public IdeDependency[] getDeps()
    {
        return deps;
    }

    /**
     * Setter for <code>deps</code>.
     * 
     * @param deps The deps to set.
     */
    public void setDeps( IdeDependency[] deps )
    {
        this.deps = deps;
    }

    /**
     * Getter for <code>eclipseProjectDir</code>.
     * 
     * @return Returns the eclipseProjectDir.
     */
    public File getEclipseProjectDirectory()
    {
        return eclipseProjectDirectory;
    }

    /**
     * Setter for <code>eclipseProjectDir</code>.
     * 
     * @param eclipseProjectDir The eclipseProjectDir to set.
     */
    public void setEclipseProjectDirectory( File eclipseProjectDir )
    {
        eclipseProjectDirectory = eclipseProjectDir;
    }

    /**
     * Getter for <code>eclipseProjectName</code>.
     * 
     * @return Returns the project name used in eclipse.
     */
    public String getEclipseProjectName()
    {
        return eclipseProjectName;
    }

    /**
     * Setter for <code>eclipseProjectName</code>.
     * 
     * @param eclipseProjectName the project name used in eclipse.
     */
    public void setEclipseProjectName( String eclipseProjectName )
    {
        this.eclipseProjectName = eclipseProjectName;
    }

    /**
     * Getter for <code>project</code>.
     * 
     * @return Returns the project.
     */
    public MavenProject getProject()
    {
        return project;
    }

    /**
     * Setter for <code>project</code>.
     * 
     * @param project The project to set.
     */
    public void setProject( MavenProject project )
    {
        this.project = project;
    }

    /**
     * Getter for <code>sourceDirs</code>.
     * 
     * @return Returns the sourceDirs.
     */
    public EclipseSourceDir[] getSourceDirs()
    {
        return sourceDirs;
    }

    /**
     * Setter for <code>sourceDirs</code>.
     * 
     * @param sourceDirs The sourceDirs to set.
     */
    public void setSourceDirs( EclipseSourceDir[] sourceDirs )
    {
        this.sourceDirs = sourceDirs;
    }

    /**
     * Getter for <code>buildOutputDirectory</code>.
     * 
     * @return Returns the buildOutputDirectory.
     */
    public File getBuildOutputDirectory()
    {
        return buildOutputDirectory;
    }

    /**
     * Setter for <code>buildOutputDirectory</code>.
     * 
     * @param buildOutputDirectory The buildOutputDirectory to set.
     */
    public void setBuildOutputDirectory( File buildOutputDirectory )
    {
        this.buildOutputDirectory = buildOutputDirectory;
    }

    /**
     * Getter for <code>localRepository</code>.
     * 
     * @return Returns the localRepository.
     */
    public ArtifactRepository getLocalRepository()
    {
        return localRepository;
    }

    /**
     * Setter for <code>localRepository</code>.
     * 
     * @param localRepository The localRepository to set.
     */
    public void setLocalRepository( ArtifactRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    /**
     * Getter for <code>manifestFile</code>.
     * 
     * @return Returns the manifestFile.
     */
    public File getOSGIManifestFile()
    {
        return osgiManifestFile;
    }

    /**
     * Setter for <code>manifestFile</code>.
     * 
     * @param manifestFile The manifestFile to set.
     */
    public void setOSGIManifestFile( File manifestFile )
    {
        this.osgiManifestFile = manifestFile;
    }

    /**
     * Getter for <code>classpathContainers</code>.
     * 
     * @return Returns the classpathContainers.
     */
    public List<String> getClasspathContainers()
    {
        return classpathContainers;
    }

    /**
     * Setter for <code>classpathContainers</code>.
     * 
     * @param classpathContainers The classpathContainers to set.
     */
    public void setClasspathContainers( List<String> classpathContainers )
    {
        this.classpathContainers = classpathContainers;
    }

    /**
     * Getter for <code>buildCommands</code>.
     *
     * @return Returns the buildCommands.
     */
    public List<BuildCommand> getBuildCommands()
    {
        return buildCommands;
    }

    /**
     * Setter for <code>buildCommands</code>.
     * 
     * @param buildCommands The buildCommands to set.
     */
    public void setBuildCommands( List<BuildCommand> buildCommands )
    {
        this.buildCommands = buildCommands;
    }

    /**
     * Getter for <code>projectnatures</code>.
     * 
     * @return Returns the projectnatures.
     */
    public List<String> getProjectnatures()
    {
        return projectnatures;
    }

    /**
     * Setter for <code>projectnatures</code>.
     * 
     * @param projectnatures The projectnatures to set.
     */
    public void setProjectnatures( List<String> projectnatures )
    {
        this.projectnatures = projectnatures;
    }

    /**
     * Getter for <code>projectFacets</code>.
     * 
     * @return Returns the projectFacets
     */
    public Map<String, String> getProjectFacets()
    {
        return projectFacets;
    }

    /**
     * Setter for <code>projectFacets</code>
     * 
     * @param projectFacets The projectFacets to set.
     */
    public void setProjectFacets( Map<String, String> projectFacets )
    {
        this.projectFacets = projectFacets;
    }

    /**
     * Getter for <code>projectBaseDir</code>.
     * 
     * @return Returns the projectBaseDir.
     */
    public File getProjectBaseDir()
    {
        return projectBaseDir;
    }

    /**
     * Setter for <code>projectBaseDir</code>.
     * 
     * @param projectBaseDir The projectBaseDir to set.
     */
    public void setProjectBaseDir( File projectBaseDir )
    {
        this.projectBaseDir = projectBaseDir;
    }

    public void setProjectNameTemplate( String projectNameTemplate )
    {
        this.projectNameTemplate = projectNameTemplate;
    }

    public String getProjectNameTemplate()
    {
        return projectNameTemplate;
    }

    public String getContextName()
    {
        return contextName;
    }

    public void setContextName( String deployName )
    {
        contextName = deployName;
    }

    /**
     * @return the packaging
     */
    public String getPackaging()
    {
        return packaging;
    }

    /**
     * @param packaging the packaging to set
     */
    public void setPackaging( String packaging )
    {
        this.packaging = packaging;
    }

    /**
     * Getter for <code>wtpapplicationxml</code>.
     * 
     * @return Returns the wtpapplicationxml.
     */
    public boolean getWtpapplicationxml()
    {
        return wtpapplicationxml;
    }

    /**
     * Setter for <code>wtpapplicationxml</code>.
     * 
     * @param wtpapplicationxml The wtpapplicationxml to set.
     */
    public void setWtpapplicationxml( boolean wtpapplicationxml )
    {
        this.wtpapplicationxml = wtpapplicationxml;
    }

    /**
     * Getter for <code>wtpVersion</code>.
     * 
     * @return Returns the wtpVersion.
     */
    public float getWtpVersion()
    {
        return wtpVersion;
    }

    /**
     * Setter for <code>wtpVersion</code>.
     * 
     * @param wtpVersion The wtpVersion to set.
     */
    public void setWtpVersion( float wtpVersion )
    {
        this.wtpVersion = wtpVersion;
    }

    /**
     * @return the linkedResources
     */
    public List<LinkedResource> getLinkedResources()
    {
        return linkedResources;
    }

    /**
     * @param linkedResources the linkedResources to set
     */
    public void setLinkedResources( List<LinkedResource> linkedResources )
    {
        this.linkedResources = linkedResources;
    }

    /**
     * Returns the classpathContainersLast.
     * 
     * @return the classpathContainersLast
     */
    public boolean isClasspathContainersLast()
    {
        return classpathContainersLast;
    }

    /**
     * Sets the classpathContainersLast.
     * 
     * @param classpathContainersLast the classpathContainersLast to set
     */
    public void setClasspathContainersLast( boolean classpathContainersLast )
    {
        this.classpathContainersLast = classpathContainersLast;
    }

    /**
     * Returns the jeeversion.
     * 
     * @return the jeeversion
     */
    public String getJeeVersion()
    {
        return jeeVersion;
    }

    /**
     * Sets the jeeversion.
     * 
     * @param jeeVersion the jeeVersion
     */
    public void setJeeVersion( String jeeVersion )
    {
        this.jeeVersion = jeeVersion;
    }

}
