package org.apache.maven.plugin.ide;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.eclipse.Messages;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.resolve.OrFilter;
import org.apache.maven.shared.artifact.filter.resolve.ScopeFilter;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.filter.PatternExclusionsDependencyFilter;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

/**
 * Abstract base plugin which takes care of the common stuff usually needed by maven IDE plugins. A plugin extending
 * AbstractIdeSupportMojo should implement the <code>setup()</code> and <code>writeConfiguration()</code> methods, plus
 * the getters needed to get the various configuration flags and required components. The lifecycle:
 * 
 * <pre>
 *      *** calls setup() where you can configure your specific stuff and stop the mojo from execute if appropriate ***
 *      - manually resolve project dependencies, NOT failing if a dependency is missing
 *      - compute project references (reactor projects) if the getUseProjectReferences() flag is set
 *      - download sources/javadocs if the getDownloadSources() flag is set
 *      *** calls writeConfiguration(), passing the list of resolved referenced dependencies ***
 *      - report the list of missing sources or just tell how to turn this feature on if the flag was disabled
 * </pre>
 * 
 * @author Fabrizio Giustina
 * @version $Id$
 */
public abstract class AbstractIdeSupportMojo
    extends AbstractMojo
    implements LogEnabled
{

    /**
     * The project whose project files to create.
     */
    @Parameter( property = "project", required = true, readonly = true )
    protected MavenProject project;

    /**
     * The currently executed project (can be a reactor project).
     */
    @Parameter( property = "executedProject", readonly = true )
    protected MavenProject executedProject;

    /**
     * The project packaging.
     */
    @Parameter( property = "project.packaging" )
    protected String packaging;

//    /**
//     * Artifact factory, needed to download source jars for inclusion in classpath.
//     */
//    @Component( role = ArtifactFactory.class )
//    protected ArtifactFactory artifactFactory;

    /**
     * Artifact resolver, needed to download source jars for inclusion in classpath.
     */
    @Component( role = ArtifactResolver.class )
    protected ArtifactResolver artifactResolver;

    @Component
    private DependencyResolver dependencyResolver;

    @Component
    private ProjectDependenciesResolver resolver;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Remote repositories which will be searched for artifacts.
     */
    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
    private List<ArtifactRepository> remoteRepositories;
    
//    @Component( role = ProjectDependenciesResolver.class )
//    protected ProjectDependenciesResolver projectDependenciesResolver;
//    
//    @Component( role = RepositorySystemSession.class, hint = "maven" )
//    protected RepositorySystemSession repositorySystemSession;

    /**
     * The Maven session
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    @Component
    private ProjectBuilder projectBuilder;

    /**
     * The runtime information for Maven, used to retrieve Maven's version number.
     */
    @Component
    private RuntimeInformation runtimeInformation;

//    /**
//     * Remote repositories which will be searched for source attachments.
//     */
//    @Parameter( property = "project.remoteArtifactRepositories", required = true, readonly = true )
//    protected List remoteArtifactRepositories;

    /**
     * Local maven repository.
     */
    @Parameter( property = "localRepository", required = true, readonly = true )
    protected ArtifactRepository localRepository;

    /**
     * If the executed project is a reactor project, this will contains the full list of projects in the reactor.
     */
    @Parameter( property = "reactorProjects", required = true, readonly = true )
    protected List<MavenProject> reactorProjects;

    /**
     * Skip the operation when true.
     */
    @Parameter( property = "eclipse.skip", defaultValue = "false" )
    private boolean skip;

    /**
     * Enables/disables the downloading of source attachments. Defaults to false. When this flag is <code>true</code>
     * remote repositories are checked for sources: in order to avoid repeated check for unavailable source archives, a
     * status cache is mantained. With versions 2.6+ of the plugin to reset this cache run
     * <code>mvn eclipse:remove-cache</code>, or use the <code>forceRecheck</code> option with versions. With older
     * versions delete the file <code>mvn-eclipse-cache.properties</code> in the target directory.
     */
    @Parameter( property = "downloadSources" )
    protected boolean downloadSources;

    /**
     * Enables/disables the downloading of javadoc attachments. Defaults to false. When this flag is <code>true</code>
     * remote repositories are checked for javadocs: in order to avoid repeated check for unavailable javadoc archives,
     * a status cache is mantained. With versions 2.6+ of the plugin to reset this cache run
     * <code>mvn eclipse:remove-cache</code>, or use the <code>forceRecheck</code> option with versions. With older
     * versions delete the file <code>mvn-eclipse-cache.properties</code> in the target directory.
     */
    @Parameter( property = "downloadJavadocs" )
    protected boolean downloadJavadocs;

    /**
     * Enables/disables the rechecking of the remote repository for downloading source/javadoc attachments. Defaults to
     * false. When this flag is <code>true</code> and the source or javadoc attachment has a status cache to indicate
     * that it is not available, then the remote repository will be rechecked for a source or javadoc attachment and 
     * the status cache updated to reflect the new state.
     */
    @Parameter( property = "forceRecheck" )
    protected boolean forceRecheck;

    /**
     * Plexus logger needed for debugging manual artifact resolution.
     */
    protected Logger logger;

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
     * Getter for <code>reactorProjects</code>.
     * 
     * @return Returns the reactorProjects.
     */
    public List<MavenProject> getReactorProjects()
    {
        return reactorProjects;
    }

    /**
     * Setter for <code>reactorProjects</code>.
     * 
     * @param reactorProjects The reactorProjects to set.
     */
    public void setReactorProjects( List<MavenProject> reactorProjects )
    {
        this.reactorProjects = reactorProjects;
    }

//    /**
//     * Getter for <code>remoteArtifactRepositories</code>.
//     * 
//     * @return Returns the remoteArtifactRepositories.
//     */
//    public List getRemoteArtifactRepositories()
//    {
//        return remoteArtifactRepositories;
//    }
//
//    /**
//     * Setter for <code>remoteArtifactRepositories</code>.
//     * 
//     * @param remoteArtifactRepositories The remoteArtifactRepositories to set.
//     */
//    public void setRemoteArtifactRepositories( List remoteArtifactRepositories )
//    {
//        this.remoteArtifactRepositories = remoteArtifactRepositories;
//    }

//    /**
//     * Getter for <code>artifactFactory</code>.
//     * 
//     * @return Returns the artifactFactory.
//     */
//    public ArtifactFactory getArtifactFactory()
//    {
//        return artifactFactory;
//    }
//
//    /**
//     * Setter for <code>artifactFactory</code>.
//     * 
//     * @param artifactFactory The artifactFactory to set.
//     */
//    public void setArtifactFactory( ArtifactFactory artifactFactory )
//    {
//        this.artifactFactory = artifactFactory;
//    }

    /**
     * Getter for <code>artifactResolver</code>.
     * 
     * @return Returns the artifactResolver.
     */
    public ArtifactResolver getArtifactResolver()
    {
        return artifactResolver;
    }

    /**
     * Setter for <code>artifactResolver</code>.
     * 
     * @param artifactResolver The artifactResolver to set.
     */
    public void setArtifactResolver( ArtifactResolver artifactResolver )
    {
        this.artifactResolver = artifactResolver;
    }

    /**
     * Getter for <code>executedProject</code>.
     * 
     * @return Returns the executedProject.
     */
    public MavenProject getExecutedProject()
    {
        return executedProject;
    }

    /**
     * Setter for <code>executedProject</code>.
     * 
     * @param executedProject The executedProject to set.
     */
    public void setExecutedProject( MavenProject executedProject )
    {
        this.executedProject = executedProject;
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
     * Getter for <code>downloadJavadocs</code>.
     * 
     * @return Returns the downloadJavadocs.
     */
    public boolean getDownloadJavadocs()
    {
        return downloadJavadocs;
    }

    /**
     * Setter for <code>downloadJavadocs</code>.
     * 
     * @param downloadJavadoc The downloadJavadocs to set.
     */
    public void setDownloadJavadocs( boolean downloadJavadoc )
    {
        downloadJavadocs = downloadJavadoc;
    }

    /**
     * Getter for <code>downloadSources</code>.
     * 
     * @return Returns the downloadSources.
     */
    public boolean getDownloadSources()
    {
        return downloadSources;
    }

    /**
     * Setter for <code>downloadSources</code>.
     * 
     * @param downloadSources The downloadSources to set.
     */
    public void setDownloadSources( boolean downloadSources )
    {
        this.downloadSources = downloadSources;
    }

//    protected void setResolveDependencies( boolean resolveDependencies )
//    {
//        this.resolveDependencies = resolveDependencies;
//    }
//
//    protected boolean isResolveDependencies()
//    {
//        return resolveDependencies;
//    }

    /**
     * return <code>false</code> if projects available in a reactor build should be considered normal dependencies,
     * <code>true</code> if referenced project will be linked and not need artifact resolution.
     * 
     * @return <code>true</code> if referenced project will be linked and not need artifact resolution
     */
    protected abstract boolean getUseProjectReferences();

    /**
     * Hook for preparation steps before the actual plugin execution.
     * 
     * @return <code>true</code> if execution should continue or <code>false</code> if not.
     * @throws MojoExecutionException generic mojo exception
     */
    protected abstract boolean setup()
        throws MojoExecutionException;

    /**
     * Main plugin method where dependencies should be processed in order to generate IDE configuration files.
     * 
     * @param deps list of <code>IdeDependency</code> objects, with artifacts, sources and javadocs already resolved
     * @throws MojoExecutionException generic mojo exception
     */
    protected abstract void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException;

//    /**
//     * Cached array of resolved dependencies.
//     */
//    private IdeDependency[] ideDeps;

//    /**
//     * Flag for mojo implementations to control whether normal maven dependencies should be resolved. Default value is
//     * true.
//     */
//    private boolean resolveDependencies = true;

    /**
     * @see org.codehaus.plexus.logging.LogEnabled#enableLogging(org.codehaus.plexus.logging.Logger)
     */
    @Override
    public void enableLogging( Logger logger )
    {
        this.logger = logger;
    }

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            return;
        }

        boolean processProject = setup();
        if ( !processProject )
        {
            return;
        }

        // resolve artifacts
        IdeDependency[] deps = doDependencyResolution();

        writeConfiguration( deps );

        reportMissingArtifacts( getMissingSourceDependencies( deps ), getJavadocDependencies( deps ) );

    }

    private IdeDependency[] resolveProjectDependencies() throws MojoExecutionException {
        DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject( project );
        request.setRepositorySession( session.getRepositorySession() );
        request.setResolutionFilter( new PatternExclusionsDependencyFilter( getExcludes() ) );
        DependencyResolutionResult result;
        try
        {
            result = this.resolver.resolve( request );
        }
        catch ( DependencyResolutionException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
//        List<org.eclipse.aether.graph.Dependency> dependencies = result.getDependencies();
        
        DependencyNode dependencyGraph = result.getDependencyGraph();
        PreorderNodeListGenerator nodeListGenerator = new PreorderNodeListGenerator();
        dependencyGraph.accept( nodeListGenerator );
        
        List<org.eclipse.aether.graph.Dependency> dependencies = nodeListGenerator.getDependencies( false );
        
        return etherToIdeDependencies( dependencies );
    }

    /**
     * This method resolves the dependency artifacts from the project.
     *
     * @return set of resolved dependency artifacts.
     * @throws DependencyResolverException in case of an error while resolving the artifacts.
     */
    protected Set<Artifact> resolveDependencyArtifacts()
            throws DependencyResolverException
    {
        Collection<Dependency> dependencies = getProject().getDependencies();
        Set<DependableCoordinate> dependableCoordinates = new LinkedHashSet<>();
        Map<DependableCoordinate, String> scopes = new HashMap<>();
        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

//        Set<Artifact> resolved = new LinkedHashSet<>();
        for ( Dependency dependency : dependencies )
        {
            DependableCoordinate coordinate = createDependendableCoordinateFromDependency( dependency );
            scopes.put( coordinate, dependency.getScope() );
//            resolved.addAll( resolveDependableCoordinate( buildingRequest, coordinate, dependency.getScope() ) );
            dependableCoordinates.add( createDependendableCoordinateFromDependency( dependency ) );
        }

//        return resolved;
        return resolveDependableCoordinates( buildingRequest, dependableCoordinates, scopes );
    }

    private Set<Artifact> resolveDependableCoordinates( ProjectBuildingRequest buildingRequest,
                                                        Collection<DependableCoordinate> coordinates,
                                                        Map<DependableCoordinate, String> scopes )
            throws DependencyResolverException
    {
        TransformableFilter filter = getTransformableFilter();

        Set<Artifact> results = new LinkedHashSet<>();

        for ( DependableCoordinate coordinate : coordinates )
        {
            Iterable<ArtifactResult> artifactResults;
            try
            {
                artifactResults = this.dependencyResolver.resolveDependencies(
                        buildingRequest, coordinate, filter );
            }
            catch ( DependencyResolverException e )
            {
                // TODO add to unresolved
                // an error occurred during resolution, log it an continue
                getLog().debug( "error resolving: " + coordinate );
                getLog().debug( e );
                continue;
            }

            String scope = scopes.get( coordinate );
            for ( ArtifactResult artifactResult : artifactResults )
            {
                Artifact artifact = artifactResult.getArtifact();
                artifact.setScope( scope );
                results.add( artifact );
            }
        }

        return results;
    }

    private Set<Artifact> resolveDependableCoordinate( ProjectBuildingRequest buildingRequest,
                                                       DependableCoordinate coordinate, String scope )
        throws DependencyResolverException
    {
        TransformableFilter filter = getTransformableFilter();

        Set<Artifact> results = new HashSet<>();

        try
        {
            Iterable<ArtifactResult> artifactResults =
                this.dependencyResolver.resolveDependencies( buildingRequest, coordinate, filter );
            for ( ArtifactResult artifactResult : artifactResults )
            {
                Artifact artifact = artifactResult.getArtifact();
                artifact.setScope( scope );
                results.add( artifact );
            }
        }
        catch ( DependencyResolverException e )
        {
            // TODO add to unresolved
            // an error occurred during resolution, log it an continue
            getLog().debug( "error resolving: " + coordinate );
            getLog().debug( e );
        }

        return results;
    }

    private TransformableFilter getTransformableFilter()
    {
        ScopeFilter excludeSystem = ScopeFilter.excluding( Artifact.SCOPE_SYSTEM );
        if ( this.getUseProjectReferences() )
        {
            ExcludeReactorProjectsDependencyFilter excludeReactorProjects =
                new ExcludeReactorProjectsDependencyFilter( this.reactorProjects, getLog() );
            return new OrFilter( Arrays.asList( excludeReactorProjects, excludeSystem ) );
        }
        else
        {
            return excludeSystem;
        }
    }

    private DependableCoordinate createDependendableCoordinateFromDependency( final Dependency dependency )
    {
        DefaultDependableCoordinate result = new DefaultDependableCoordinate();
        result.setGroupId( dependency.getGroupId() );
        result.setArtifactId( dependency.getArtifactId() );
        result.setVersion( dependency.getVersion() );
        result.setType( dependency.getType() );

        return result;
    }
    
    /**
     * Resolve project dependencies. Manual resolution is needed in order to avoid resolution of multiproject artifacts
     * (if projects will be linked each other an installed jar is not needed) and to avoid a failure when a jar is
     * missing.
     * 
     * @throws MojoExecutionException if dependencies can't be resolved
     * @return resolved IDE dependencies, with attached jars for non-reactor dependencies
     */
    protected IdeDependency[] doDependencyResolution()
                    throws MojoExecutionException
    {
        // we can't rely of project dependency resolution as test classifiers can't be resolved
//        Set<Artifact> artifacts;
//        try
//        {
//            artifacts = resolveDependencyArtifacts();
//        }
//        catch ( DependencyResolverException e )
//        {
//            throw new MojoExecutionException( e.getMessage(), e );
//        }
//        return artifactsToIdeDependencies( artifacts );
        return resolveProjectDependencies();
    }

    /**
     * Resolve project dependencies. Manual resolution is needed in order to avoid resolution of multiproject artifacts
     * (if projects will be linked each other an installed jar is not needed) and to avoid a failure when a jar is
     * missing.
     * 
     * @throws MojoExecutionException if dependencies can't be resolved
     * @return resolved IDE dependencies, with attached jars for non-reactor dependencies
     */
    protected IdeDependency[] doDependencyResolution2()
                    throws MojoExecutionException
    {
        DependencyStatusSets dependencySets = getDependencySets();

        Set<Artifact> artifacts = dependencySets.getResolvedDependencies();
        return artifactsToIdeDependencies( artifacts );
    }

    private IdeDependency[] artifactsToIdeDependencies( Set<Artifact> artifacts ) throws MojoExecutionException
    {
        List<IdeDependency> dependencies = new ArrayList<>();
        for ( Artifact artifact : artifacts )
        {

            String scope = artifact.getScope();
            IdeDependency dependency =
                new IdeDependency( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                                   artifact.getClassifier(), useProjectReference( artifact ),
                                   Artifact.SCOPE_TEST.equals( scope ), Artifact.SCOPE_SYSTEM.equals( scope ),
                                   Artifact.SCOPE_PROVIDED.equals( scope ),
                                   artifact.getArtifactHandler().isAddedToClasspath(), artifact.getFile(),
                                   artifact.getType(), getProjectNameForArifact( artifact ) );
            dependencies.add( dependency );

        }
        DependencyStatusSets resolvedSourceAndJavadocArtifacts = resolveSourceAndJavadocArtifacts( artifacts );
        updateSourceAndJavadocAttachements( dependencies, resolvedSourceAndJavadocArtifacts.getResolvedDependencies() );
        return dependencies.toArray( new IdeDependency[0] );
    }
    
    private IdeDependency[] etherToIdeDependencies( List<org.eclipse.aether.graph.Dependency> dependencies )
                    throws MojoExecutionException
    {
        List<IdeDependency> ideDependencies = new ArrayList<>();
        for ( org.eclipse.aether.graph.Dependency dependency : dependencies )
        {

            String scope = dependency.getScope();
            org.eclipse.aether.artifact.Artifact artifact = dependency.getArtifact();
            String type = AetherToMaven.getType( artifact );
            boolean addedToClasspath = AetherToMaven.isAddToClasspath( artifact );

            IdeDependency ideDependency =
                            new IdeDependency( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                                               artifact.getClassifier(), useProjectReference( artifact ),
                                               Artifact.SCOPE_TEST.equals( scope ), Artifact.SCOPE_SYSTEM.equals( scope ),
                                               Artifact.SCOPE_PROVIDED.equals( scope ),
                                               addedToClasspath, artifact.getFile(),
                                               type, getProjectNameForArifact( artifact ) );
            ideDependencies.add( ideDependency );
        }
        DependencyStatusSets resolvedSourceAndJavadocArtifacts = resolveSourceAndJavadocArtifacts( dependencies );
        updateSourceAndJavadocAttachements( ideDependencies, resolvedSourceAndJavadocArtifacts.getResolvedDependencies() );
        return ideDependencies.toArray( new IdeDependency[0] );
    }

    private void updateSourceAndJavadocAttachements( List<IdeDependency> dependencies,
                                                     Set<Artifact> resolvedDependencies )
    {

        Map<IdeDependencyCoordinate, IdeDependency> dependencyMap = new HashMap<>();
        for ( IdeDependency dependency : dependencies )
        {
            IdeDependencyCoordinate coordinate = new IdeDependencyCoordinate( dependency.getGroupId(),
                                                                              dependency.getArtifactId(),
                                                                              dependency.getVersion(),
                                                                              dependency.getClassifier(),
                                                                              dependency.getType() );
            dependencyMap.put( coordinate, dependency );
        }
        for ( Artifact artifact : resolvedDependencies )
        {
            String originalClassifier = artifact.getClassifier();
            boolean isSources = false;
            boolean isJavadoc = false;
            String classifier;
            if ( "sources".equals( originalClassifier ) )
            {
                isSources = true;
                classifier = "";
            }
            else if ( "javadoc".equals( originalClassifier ) )
            {
                isJavadoc = true;
                classifier = "";

            }
            else if ( "test-sources".equals( originalClassifier ) )
            {
                isSources = true;
                classifier = "tests";

            }
            else if ( originalClassifier.endsWith( "-sources" ) )
            {
                // jdk15-sources -> jdk15
                isSources = true;
                classifier = originalClassifier.substring( 0, originalClassifier.length() - 8 );
                
            }
            else
            {
                classifier = "";
                getLog().debug( "unknwon classifier: " + originalClassifier );
            }
            
            
            IdeDependencyCoordinate coordinate = new IdeDependencyCoordinate( artifact.getGroupId(),
                                         artifact.getArtifactId(),
                                         artifact.getVersion(),
                                         classifier,
                                         artifact.getType() );
            
            IdeDependency dependency = dependencyMap.get( coordinate );
            if ( dependency != null )
            {
                if ( isJavadoc )
                {
                    dependency.setJavadocAttachment( artifact.getFile() );
                }
                if ( isSources )
                {
                    dependency.setSourceAttachment( artifact.getFile() );
                }
            }
            else
            {
                getLog().debug( "could not get ide dependency for "
                                + "groupId: " + artifact.getGroupId()
                                + "artifactId: " + artifact.getArtifactId()
                                + "version: " + artifact.getVersion()
                                + "classifier: " + originalClassifier
                                + "type: " + artifact.getType());
            }
        }

    }
    
    private void updateSourceAndJavadocAttachements( List<IdeDependency> ideDependencies,
                                                     List<org.eclipse.aether.graph.Dependency> dependencies )
    {
        
        Map<IdeDependencyCoordinate, IdeDependency> dependencyMap = new HashMap<>();
        for ( IdeDependency ideDependency : ideDependencies )
        {
            IdeDependencyCoordinate coordinate = new IdeDependencyCoordinate( ideDependency.getGroupId(),
                                                                              ideDependency.getArtifactId(),
                                                                              ideDependency.getVersion(),
                                                                              ideDependency.getClassifier(),
                                                                              ideDependency.getType() );
            dependencyMap.put( coordinate, ideDependency );
        }
        for ( org.eclipse.aether.graph.Dependency dependency : dependencies )
        {

            org.eclipse.aether.artifact.Artifact artifact = dependency.getArtifact();
            String type = AetherToMaven.getType( artifact );
            String originalClassifier = artifact.getClassifier();
            boolean isSources = false;
            boolean isJavadoc = false;
            String classifier;
            if ( "sources".equals( originalClassifier ) )
            {
                isSources = true;
                classifier = null;
            }
            else if ( "javadoc".equals( originalClassifier ) )
            {
                isJavadoc = true;
                classifier = null;
                
            }
            else if ( "test-sources".equals( originalClassifier ) )
            {
                isSources = true;
                classifier = null;
                
            }
            else
            {
                classifier = null;
                getLog().debug( "unknwon classifier: " + originalClassifier );
            }
            
            
            IdeDependencyCoordinate coordinate = new IdeDependencyCoordinate( artifact.getGroupId(),
                                                                              artifact.getArtifactId(),
                                                                              artifact.getVersion(),
                                                                              classifier,
                                                                              type );
            
            IdeDependency ideDependency = dependencyMap.get( coordinate );
            if ( ideDependency != null )
            {
                if ( isJavadoc )
                {
                    ideDependency.setJavadocAttachment( artifact.getFile() );
                }
                if ( isSources )
                {
                    ideDependency.setSourceAttachment( artifact.getFile() );
                }
            }
            else
            {
                getLog().debug( "could not get ide dependency for "
                                + "groupId: " + artifact.getGroupId()
                                + "artifactId: " + artifact.getArtifactId()
                                + "version: " + artifact.getVersion()
                                + "classifier: " + originalClassifier
                                + "type: " + type);
            }
        }
        
    }

    /**
     * Method creates filters and filters the projects dependencies. This method also transforms the dependencies if
     * classifier is set. The dependencies are filtered in least specific to most specific order
     *
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
     * @throws MojoExecutionException in case of errors.
     */
    protected DependencyStatusSets getDependencySets()
        throws MojoExecutionException
    {
        // add filters in well known order, least specific to most specific
        FilterArtifacts filter = new FilterArtifacts();

        filter.addFilter( GroupIdArtifactIdArtifactsFilter.fromExcludes( getExcludes() ) );

        // start with all artifacts.
        Set<Artifact> artifacts = getProject().getArtifacts();

        boolean includeParents = false;
        if ( includeParents )
        {
            // add dependencies parents
            for ( Artifact dep : new ArrayList<>( artifacts ) )
            {
                addParentArtifacts( buildProjectFromArtifact( dep ), artifacts );
            }

            // add current project parent
            addParentArtifacts( getProject(), artifacts );
        }

        // perform filtering
        try
        {
            artifacts = filter.filter( artifacts );
        }
        catch ( ArtifactFilterException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        // transform artifacts if classifier is set
        DependencyStatusSets status;
        status = filterMarkedDependencies( artifacts );

        return status;
    }

    private MavenProject buildProjectFromArtifact( Artifact artifact )
        throws MojoExecutionException
    {
        try
        {
            return projectBuilder.build( artifact, session.getProjectBuildingRequest() ).getProject();
        }
        catch ( ProjectBuildingException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    private void addParentArtifacts( MavenProject project, Set<Artifact> artifacts )
        throws MojoExecutionException
    {
//        while ( project.hasParent() )
//        {
//            project = project.getParent();
//
//            if ( artifacts.contains( project.getArtifact() ) )
//            {
//                // artifact already in the set
//                break;
//            }
//            try
//            {
//                ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();
//
//                Artifact resolvedArtifact =
//                    artifactResolver.resolveArtifact( buildingRequest, project.getArtifact() ).getArtifact();
//
//                artifacts.add( resolvedArtifact );
//            }
//            catch ( ArtifactResolverException e )
//            {
//                throw new MojoExecutionException( e.getMessage(), e );
//            }
//        }
    }

//    /**
//     * Resolve project dependencies. Manual resolution is needed in order to avoid resolution of multiproject artifacts
//     * (if projects will be linked each other an installed jar is not needed) and to avoid a failure when a jar is
//     * missing.
//     * 
//     * @throws MojoExecutionException if dependencies can't be resolved
//     * @return resolved IDE dependencies, with attached jars for non-reactor dependencies
//     */
//    protected IdeDependency[] doDependencyResolution2()
//        throws MojoExecutionException
//    {
//        if ( ideDeps == null )
//        {
//            if ( resolveDependencies )
//            {
//                MavenProject project = getProject();
//                ArtifactRepository localRepo = getLocalRepository();
//
//                List<Dependency> deps = getProject().getDependencies();
//
//                // Collect the list of resolved IdeDependencies.
//                List<IdeDependency> dependencies = new ArrayList<>();
//
//                if ( deps != null )
//                {
//                    Map<String, Artifact> managedVersions =
//                        createManagedVersionMap( getArtifactFactory(), project.getId(),
//                                                 project.getDependencyManagement() );
//
//                    DependencyResolutionResult dependencyResolutionResult;
//
//                    try
//                    {
//
//                        List listeners = new ArrayList();
//
//                        if ( logger.isDebugEnabled() )
//                        {
//                            listeners.add( new DebugResolutionListener( logger ) );
//                        }
//
//                        listeners.add( new WarningResolutionListener( logger ) );
//
//                        artifactResolutionResult =
//                            artifactCollector.collect( getProjectArtifacts(), project.getArtifact(), managedVersions,
//                                                       localRepo, project.getRemoteArtifactRepositories(),
//                                                       getArtifactMetadataSource(), null, listeners );
//                        
//                        DependencyResolutionRequest resolutionRequest = new DefaultDependencyResolutionRequest( project, session );
//                        dependencyResolutionResult = projectDependenciesResolver.resolve( resolutionRequest );
//                    }
//                    catch ( DependencyResolutionException e )
//                    {
//                        getLog().debug( e.getMessage(), e );
//                        getLog().error( Messages.getString( "AbstractIdeSupportMojo.artifactresolution", new Object[] {
//                                                            e.getGroupId(), e.getArtifactId(), e.getVersion(),
//                                                                e.getMessage() } ) );
//
//                        // if we are here artifactResolutionResult is null, create a project without dependencies but
//                        // don't fail
//                        // (this could be a reactor projects, we don't want to fail everything)
//                        // Causes MECLIPSE-185. Not sure if it should be handled this way??
//                        return new IdeDependency[0];
//                    }
//
//                    // keep track of added reactor projects in order to avoid duplicates
//                    Set<String> emittedReactorProjectId = new HashSet<>();
//                    
//                    dependencyResolutionResult.getDependencyGraph().accept( new DependencyVisitor()
//                    {
//
//                        @Override
//                        public boolean visitLeave( DependencyNode node )
//                        {
//                            return true;
//                        }
//
//                        @Override
//                        public boolean visitEnter( DependencyNode node )
//                        {
//
//
//                            int dependencyDepth = -1;
//                            org.eclipse.aether.artifact.Artifact art = node.getArtifact();
//                            // don't resolve jars for reactor projects
//                            if ( hasToResolveJar( art ) )
//                            {
//                                try
//                                {
//                                    artifactResolver.resolve( art, node.getRemoteRepositories(), localRepository );
//                                }
//                                catch ( ArtifactNotFoundException e )
//                                {
//                                    getLog().debug( e.getMessage(), e );
//                                    getLog().warn( Messages.getString( "AbstractIdeSupportMojo.artifactdownload",
//                                                                       new Object[] { e.getGroupId(), e.getArtifactId(),
//                                                                           e.getVersion(), e.getMessage() } ) );
//                                }
//                                catch ( ArtifactResolutionException e )
//                                {
//                                    getLog().debug( e.getMessage(), e );
//                                    getLog().warn( Messages.getString( "AbstractIdeSupportMojo.artifactresolution",
//                                                                       new Object[] { e.getGroupId(), e.getArtifactId(),
//                                                                           e.getVersion(), e.getMessage() } ) );
//                                }
//                            }
//
//                            boolean includeArtifact = true;
//                            if ( getExcludes() != null )
//                            {
//                                String artifactFullId = art.getGroupId() + ":" + art.getArtifactId();
//                                if ( getExcludes().contains( artifactFullId ) )
//                                {
//                                    getLog().info( "excluded: " + artifactFullId );
//                                    includeArtifact = false;
//                                }
//                            }
//
//                            if ( includeArtifact
//                                            && ( !( getUseProjectReferences() && isAvailableAsAReactorProject( art ) ) 
//                                                            || emittedReactorProjectId.add( art.getGroupId() + '-' + art.getArtifactId() ) ) )
//                            {
//
//
//                                org.eclipse.aether.graph.Dependency dependency = node.getDependency();
//                                String scope = dependency.getScope();
//                                IdeDependency dep =
//                                                new IdeDependency( art.getGroupId(), art.getArtifactId(), art.getVersion(),
//                                                                   art.getClassifier(), useProjectReference( art ),
//                                                                   Artifact.SCOPE_TEST.equals( scope ),
//                                                                   Artifact.SCOPE_SYSTEM.equals( scope ),
//                                                                   Artifact.SCOPE_PROVIDED.equals( scope ),
//                                                                   art.getArtifactHandler().isAddedToClasspath(), art.getFile(),
//                                                                   art.getType(), getProjectNameForArifact( art ) );
//                                // no duplicate entries allowed. System paths can cause this problem.
//                                if ( !dependencies.contains( dep ) )
//                                {
//                                    dependencies.add( dep );
//                                }
//                            }
//
//                            return true;
//                        }
//                    } );
//
//                    // TODO: a final report with the list of missingArtifacts?
//                    dependencyResolutionResult.getUnresolvedDependencies();
//                    
//
//                }
//
//                ideDeps = dependencies.toArray( new IdeDependency[dependencies.size()] );
//            }
//            else
//            {
//                ideDeps = new IdeDependency[0];
//            }
//        }
//
//        return ideDeps;
//    }

    /**
     * Find the name of the project as used in eclipse.
     * 
     * @param artifact The artifact to find the eclipse name for.
     * @return The name os the eclipse project.
     */
    public abstract String getProjectNameForArifact( Artifact artifact );
    
    /**
     * Find the name of the project as used in eclipse.
     * 
     * @param artifact The artifact to find the eclipse name for.
     * @return The name os the eclipse project.
     */
    public abstract String getProjectNameForArifact( org.eclipse.aether.artifact.Artifact artifact );

//    /**
//     * Returns the list of project artifacts. Also artifacts generated from referenced projects will be added, 
//     * but with the <code>resolved</code> property set to true.
//     * 
//     * @return list of projects artifacts
//     * @throws MojoExecutionException if unable to parse dependency versions
//     */
//    private Set<Artifact> getProjectArtifacts()
//        throws MojoExecutionException
//    {
//        // [MECLIPSE-388] Don't sort this, the order should be identical to getProject.getDependencies()
//        Set<Artifact> artifacts = new LinkedHashSet<>();
//
//        for ( Dependency dependency : getProject().getDependencies() )
//        {
//            String groupId = dependency.getGroupId();
//            String artifactId = dependency.getArtifactId();
//            VersionRange versionRange;
//            try
//            {
//                versionRange = VersionRange.createFromVersionSpec( dependency.getVersion() );
//            }
//            catch ( InvalidVersionSpecificationException e )
//            {
//                throw new MojoExecutionException(
//                                          Messages.getString( "AbstractIdeSupportMojo.unabletoparseversion",
//                                                              new Object[] { dependency.getArtifactId(),
//                                                                  dependency.getVersion(),
//                                                                  dependency.getManagementKey(), e.getMessage() } ),
//                                          e );
//            }
//
//            String type = dependency.getType();
//            if ( type == null )
//            {
//                type = Constants.PROJECT_PACKAGING_JAR;
//            }
//            String classifier = dependency.getClassifier();
//            boolean optional = dependency.isOptional();
//            String scope = dependency.getScope();
//            if ( scope == null )
//            {
//                scope = Artifact.SCOPE_COMPILE;
//            }
//
//            Artifact art =
//                getArtifactFactory().createDependencyArtifact( groupId, artifactId, versionRange, type, classifier,
//                                                               scope, optional );
//
//            if ( scope.equalsIgnoreCase( Artifact.SCOPE_SYSTEM ) )
//            {
//                art.setFile( new File( dependency.getSystemPath() ) );
//            }
//
//            handleExclusions( art, dependency );
//
//            artifacts.add( art );
//        }
//
//        return artifacts;
//    }

//    /**
//     * Apply exclusion filters to direct AND transitive dependencies.
//     * 
//     * @param artifact
//     * @param dependency
//     */
//    private void handleExclusions( Artifact artifact, Dependency dependency )
//    {
//
//        List<String> exclusions = new ArrayList<>();
//        for ( Exclusion e : dependency.getExclusions() )
//        {
//            exclusions.add( e.getGroupId() + ":" + e.getArtifactId() );
//        }
//
//        ArtifactFilter newFilter = new ExcludesArtifactFilter( exclusions );
//
//        artifact.setDependencyFilter( newFilter );
//    }

    /**
     * Utility method that locates a project producing the given artifact.
     * 
     * @param artifact the artifact a project should produce.
     * @return <code>true</code> if the artifact is produced by a reactor projectart.
     */
    protected boolean isAvailableAsAReactorProject( Artifact artifact )
    {
        return getReactorProject( artifact ) != null;
    }
    
    /**
     * Utility method that locates a project producing the given artifact.
     * 
     * @param artifact the artifact a project should produce.
     * @return <code>true</code> if the artifact is produced by a reactor projectart.
     */
    protected boolean isAvailableAsAReactorProject( org.eclipse.aether.artifact.Artifact artifact )
    {
        return getReactorProject( artifact ) != null;
    }

    /**
     * Checks the list of reactor projects to see if the artifact is included.
     * 
     * @param artifact the artifact to check if it is in the reactor
     * @return the reactor project or null if it is not in the reactor
     */
    protected MavenProject getReactorProject( Artifact artifact )
    {
        if ( reactorProjects != null )
        {
            for ( MavenProject reactorProject : reactorProjects )
            {
                if ( reactorProject.getGroupId().equals( artifact.getGroupId() )
                    && reactorProject.getArtifactId().equals( artifact.getArtifactId() ) )
                {
                    if ( reactorProject.getVersion().equals( artifact.getVersion() ) )
                    {
                        return reactorProject;
                    }
                    else
                    {
                        getLog().info( "Artifact "
                                           + artifact
                                           + " already available as a reactor project, but with different version. "
                                           + "Expected: " + artifact.getVersion() + ", found: " 
                                           + reactorProject.getVersion() );
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Checks the list of reactor projects to see if the artifact is included.
     * 
     * @param artifact the artifact to check if it is in the reactor
     * @return the reactor project or null if it is not in the reactor
     */
    protected MavenProject getReactorProject( org.eclipse.aether.artifact.Artifact artifact )
    {
        if ( reactorProjects != null )
        {
            for ( MavenProject reactorProject : reactorProjects )
            {
                if ( reactorProject.getGroupId().equals( artifact.getGroupId() )
                                && reactorProject.getArtifactId().equals( artifact.getArtifactId() ) )
                {
                    if ( reactorProject.getVersion().equals( artifact.getVersion() ) )
                    {
                        return reactorProject;
                    }
                    else
                    {
                        getLog().info( "Artifact "
                                        + artifact
                                        + " already available as a reactor project, but with different version. "
                                        + "Expected: " + artifact.getVersion() + ", found: " 
                                        + reactorProject.getVersion() );
                    }
                }
            }
        }
        return null;
    }

//    /**
//     * @return an array with all dependencies available in the workspace, to be implemented by the subclasses.
//     */
//    protected IdeDependency[] getWorkspaceArtefacts()
//    {
//        return new IdeDependency[0];
//    }

//    private Map<String, Artifact> createManagedVersionMap( ArtifactFactory artifactFactory, String projectId,
//                                         DependencyManagement dependencyManagement )
//        throws MojoExecutionException
//    {
//        Map<String, Artifact> map;
//        if ( dependencyManagement != null && dependencyManagement.getDependencies() != null )
//        {
//            map = new HashMap<>();
//            for ( Dependency d : dependencyManagement.getDependencies() )
//            {
//                try
//                {
//                    VersionRange versionRange = VersionRange.createFromVersionSpec( d.getVersion() );
//                    Artifact artifact =
//                        artifactFactory.createDependencyArtifact( d.getGroupId(), d.getArtifactId(), versionRange,
//                                                                  d.getType(), d.getClassifier(), d.getScope(),
//                                                                  d.isOptional() );
//
//                    handleExclusions( artifact, d );
//                    map.put( d.getManagementKey(), artifact );
//                }
//                catch ( InvalidVersionSpecificationException e )
//                {
//                    throw new MojoExecutionException(
//                                                      Messages.getString( "AbstractIdeSupportMojo.unabletoparseversion",
//                                                                          new Object[] { projectId, d.getVersion(),
//                                                                              d.getManagementKey(), e.getMessage() } ),
//                                                      e );
//                }
//            }
//        }
//        else
//        {
//            map = Collections.emptyMap();
//        }
//        return map;
//    }

    /**
     * Resolve source artifacts and download them if <code>downloadSources</code> is <code>true</code>. Source and
     * javadocs artifacts will be attached to the <code>IdeDependency</code> Resolve source and javadoc artifacts. The
     * resolved artifacts will be downloaded based on the <code>downloadSources</code> and <code>downloadJavadocs</code>
     * attributes. Source and
     * 
     * @param deps resolved dependencies
     * @return 
     * @throws MojoExecutionException in case of an error.
     */
    private DependencyStatusSets resolveSourceAndJavadocArtifacts( Set<Artifact> artifacts )
        throws MojoExecutionException
    {
        Set<Artifact> unResolvedArtifacts = new LinkedHashSet<>();
        Set<Artifact> resolvedArtifacts = new HashSet<>();
        DependencyStatusSets status = new DependencyStatusSets();

        // sources
        ArtifactTranslator sourcesTranslator = new SourcesTranslator( artifactHandlerManager );
        Collection<ArtifactCoordinate> sourceCoordinates = sourcesTranslator.translate( artifacts, getLog() );

        DependencyStatusSets sourcesToResolveStatus = filterMarkedDependencies( artifacts );

        // the unskipped artifacts are in the resolved set.
        Set<Artifact> sourcesToResolve = sourcesToResolveStatus.getResolvedDependencies();

        // resolve the rest of the artifacts
        Set<Artifact> resolvedSources = resolve( new LinkedHashSet<>( sourceCoordinates ), getDownloadSources() );

        // calculate the artifacts not resolved.
        unResolvedArtifacts.addAll( sourcesToResolve );
        unResolvedArtifacts.removeAll( resolvedSources );

        resolvedArtifacts.addAll( resolvedSources );

        if ( getDownloadJavadocs() )
        {
            // javadocs
            ArtifactTranslator javadocTranslator = new JavadocTranslator( artifactHandlerManager );
            Collection<ArtifactCoordinate> javadocCoordinates = javadocTranslator.translate( artifacts, getLog() );
            DependencyStatusSets javadocsToResolveStatus = filterMarkedDependencies( artifacts );
            // the unskipped artifacts are in the resolved set.
            Set<Artifact> javadocsToResolve = javadocsToResolveStatus.getResolvedDependencies();
            // resolve the rest of the artifacts
            Set<Artifact> resolvedJavadocs = resolve( new LinkedHashSet<>( javadocCoordinates ), getDownloadJavadocs() );
            // calculate the artifacts not resolved.
            unResolvedArtifacts.addAll( javadocsToResolve );
            unResolvedArtifacts.removeAll( resolvedJavadocs );
            resolvedArtifacts.addAll( resolvedJavadocs );
        }
        // return a bean of all 3 sets.
        status.setResolvedDependencies( resolvedArtifacts );
        status.setUnResolvedDependencies( unResolvedArtifacts );

        return status;
    }
    
    /**
     * Resolve source artifacts and download them if <code>downloadSources</code> is <code>true</code>. Source and
     * javadocs artifacts will be attached to the <code>IdeDependency</code> Resolve source and javadoc artifacts. The
     * resolved artifacts will be downloaded based on the <code>downloadSources</code> and <code>downloadJavadocs</code>
     * attributes. Source and
     * 
     * @param deps resolved dependencies
     * @return 
     * @throws MojoExecutionException in case of an error.
     */
    private DependencyStatusSets resolveSourceAndJavadocArtifacts( List<org.eclipse.aether.graph.Dependency> dependencies )
        throws MojoExecutionException
    {
        Set<Artifact> unResolvedArtifacts = new LinkedHashSet<>();
        Set<Artifact> resolvedArtifacts = new HashSet<>();
        DependencyStatusSets status = new DependencyStatusSets();

        // sources
        AetherArtifactTranslator sourcesTranslator = new AetherSourcesTranslator( artifactHandlerManager );
        Collection<ArtifactCoordinate> sourceCoordinates = sourcesTranslator.translate( dependencies, getLog() );

        DependencyStatusSets toResolveStatus = filterMarkedDependencies( dependencies );

        // the unskipped artifacts are in the resolved set.
        Set<Artifact> sourcesToResolve = toResolveStatus.getResolvedDependencies();

        // resolve the rest of the artifacts
        Set<Artifact> resolvedSources = resolve( new LinkedHashSet<>( sourceCoordinates ), getDownloadSources() );

        // calculate the artifacts not resolved.
        unResolvedArtifacts.addAll( sourcesToResolve );
        unResolvedArtifacts.removeAll( resolvedSources );

        resolvedArtifacts.addAll( resolvedSources );

        if ( getDownloadJavadocs() )
        {
            // javadocs
            AetherArtifactTranslator javadocTranslator = new AetherJavadocTranslator( artifactHandlerManager );
            Collection<ArtifactCoordinate> javadocCoordinates = javadocTranslator.translate( dependencies, getLog() );
            // the unskipped artifacts are in the resolved set.
            Set<Artifact> javadocsToResolve = toResolveStatus.getResolvedDependencies();
            // resolve the rest of the artifacts
            Set<Artifact> resolvedJavadocs = resolve( new LinkedHashSet<>( javadocCoordinates ), getDownloadJavadocs() );
            // calculate the artifacts not resolved.
            unResolvedArtifacts.addAll( javadocsToResolve );
            unResolvedArtifacts.removeAll( resolvedJavadocs );
            resolvedArtifacts.addAll( resolvedJavadocs );
        }
        // return a bean of all 3 sets.
        status.setResolvedDependencies( resolvedArtifacts );
        status.setUnResolvedDependencies( unResolvedArtifacts );

        return status;
    }

    /**
     * Filter the marked dependencies
     *
     * @param artifacts The artifacts set {@link Artifact}.
     * @return status set {@link DependencyStatusSets}.
     * @throws MojoExecutionException in case of an error.
     */
    protected DependencyStatusSets filterMarkedDependencies( Set<Artifact> artifacts )
        throws MojoExecutionException
    {
        // remove files that have markers already
        FilterArtifacts filter = new FilterArtifacts();
        filter.clearFilters();
        // TODO
//        filter.addFilter( getMarkedArtifactFilter() );
        filter.addFilter( new org.apache.maven.shared.artifact.filter.collection.ScopeFilter ( null, Artifact.SCOPE_SYSTEM ) );
        if ( getUseProjectReferences() )
        {
            filter.addFilter( new ExcludeReactorProjectsArifactFilter( this.reactorProjects ) );
        }
        filter.addFilter( new ExcludeUnresolvedArtifactFilter() );

        Set<Artifact> unMarkedArtifacts;
        try
        {
            unMarkedArtifacts = filter.filter( artifacts );
        }
        catch ( ArtifactFilterException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        // calculate the skipped artifacts
        Set<Artifact> skippedArtifacts = new LinkedHashSet<>();
        skippedArtifacts.addAll( artifacts );
        skippedArtifacts.removeAll( unMarkedArtifacts );

        return new DependencyStatusSets( unMarkedArtifacts, null, skippedArtifacts );
    }
    
    /**
     * Filter the marked dependencies
     *
     * @param artifacts The artifacts set {@link Artifact}.
     * @return status set {@link DependencyStatusSets}.
     * @throws MojoExecutionException in case of an error.
     */
    protected DependencyStatusSets filterMarkedDependencies( List<org.eclipse.aether.graph.Dependency> dependencies )
                    throws MojoExecutionException
    {
        Set<Artifact> artifacts = dependencies.stream()
                        .map( org.eclipse.aether.graph.Dependency::getArtifact )
                        .map( aetherArtifact -> AetherToMaven.aetherToMavenArtifact(aetherArtifact, artifactHandlerManager) )
                        .collect( toSet() );
        //        return new AetherDependencyStatusSets(new LinkedHashSet<>(Arrays.asList( artifacts )),
        //                                              Collections.emptySet(), Collections.emptySet());
        //        // remove files that have markers already
        FilterArtifacts filter = new FilterArtifacts();
        filter.clearFilters();
        // TODO
        //        filter.addFilter( getMarkedArtifactFilter() );
        filter.addFilter( new org.apache.maven.shared.artifact.filter.collection.ScopeFilter ( null, Artifact.SCOPE_SYSTEM ) );
        if ( getUseProjectReferences() )
        {
            filter.addFilter( new ExcludeReactorProjectsArifactFilter( this.reactorProjects ) );
        }
        filter.addFilter( new ExcludeUnresolvedArtifactFilter() );

        Set<Artifact> unMarkedArtifacts;
        try
        {
            unMarkedArtifacts = filter.filter( artifacts );
        }
        catch ( ArtifactFilterException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        // calculate the skipped artifacts
        Set<Artifact> skippedArtifacts = new LinkedHashSet<>();
        skippedArtifacts.addAll( artifacts );
        skippedArtifacts.removeAll( unMarkedArtifacts );

        return new DependencyStatusSets( unMarkedArtifacts, null, skippedArtifacts );
    }
    
    /**
     * @param coordinates The set of artifact coordinates{@link ArtifactCoordinate}.
     * @param stopOnFailure <code>true</code> if we should fail with exception if an artifact couldn't be resolved
     *            <code>false</code> otherwise.
     * @return the resolved artifacts. {@link Artifact}.
     * @throws MojoExecutionException in case of error.
     */
    protected Set<Artifact> resolve( Set<ArtifactCoordinate> coordinates, boolean includeRemoteRepositories )
        throws MojoExecutionException
    {
        ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest( includeRemoteRepositories );

        Set<Artifact> resolvedArtifacts = new LinkedHashSet<>();
        for ( ArtifactCoordinate coordinate : coordinates )
        {
            try
            {
                Artifact artifact = artifactResolver.resolveArtifact( buildingRequest, coordinate ).getArtifact();
                resolvedArtifacts.add( artifact );
            }
            catch ( ArtifactResolverException ex )
            {
                // an error occurred during resolution, log it an continue
                getLog().debug( "error resolving: " + coordinate );
                getLog().debug( ex );
            }
        }
        return resolvedArtifacts;
    }

    /**
     * @return Returns a new ProjectBuildingRequest populated from the current session and the current project remote
     *         repositories, used to resolve artifacts.
     */
    public ProjectBuildingRequest newResolveArtifactProjectBuildingRequest( boolean includeRemoteRepositories )
    {
        ProjectBuildingRequest buildingRequest =
            new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

        if ( includeRemoteRepositories )
        {
            buildingRequest.setRemoteRepositories( remoteRepositories );
        } else {
            buildingRequest.setRemoteRepositories( Collections.emptyList() );
        }

        return buildingRequest;
    }

//    /**
//     * Resolve source artifacts and download them if <code>downloadSources</code> is <code>true</code>. Source and
//     * javadocs artifacts will be attached to the <code>IdeDependency</code> Resolve source and javadoc artifacts. The
//     * resolved artifacts will be downloaded based on the <code>downloadSources</code> and <code>downloadJavadocs</code>
//     * attributes. Source and
//     * 
//     * @param deps resolved dependencies
//     */
//    private void resolveSourceAndJavadocArtifacts( IdeDependency[] deps )
//    {
//        List<IdeDependency> missingSources = resolveDependenciesWithClassifier( deps, "sources", getDownloadSources() );
//        missingSourceDependencies.addAll( missingSources );
//
//        List<IdeDependency> missingJavadocs = resolveDependenciesWithClassifier( deps, "javadoc", getDownloadJavadocs() );
//        missingJavadocDependencies.addAll( missingJavadocs );
//    }

//    /**
//     * Resolve the required artifacts for each of the dependency. <code>sources</code> or <code>javadoc</code> artifacts
//     * (depending on the <code>classifier</code>) are attached to the dependency.
//     * 
//     * @param deps resolved dependencies
//     * @param inClassifier the classifier we are looking for (either <code>sources</code> or <code>javadoc</code>)
//     * @param includeRemoteRepositories flag whether we should search remote repositories for the artifacts or not
//     * @return the list of dependencies for which the required artifact was not found
//     */
//    private List<IdeDependency> resolveDependenciesWithClassifier( IdeDependency[] deps, String inClassifier,
//                                                    boolean includeRemoteRepositories )
//    {
//        List<IdeDependency> missingClassifierDependencies = new ArrayList<>();
//
//        // if downloadSources is off, just check
//        // local repository for reporting missing source jars
//        List remoteRepos = includeRemoteRepositories ? getRemoteArtifactRepositories() : Collections.emptyList();
//
//        for ( IdeDependency dependency : deps )
//        {
//            if ( dependency.isReferencedProject() || dependency.isSystemScoped() )
//            {
//                // artifact not needed
//                continue;
//            }
//
//            if ( getLog().isDebugEnabled() )
//            {
//                getLog().debug( "Searching for sources for " + dependency.getId() + ":" + dependency.getClassifier()
//                                    + " at " + dependency.getId() + ":" + inClassifier );
//            }
//
//            Artifact baseArtifact =
//                artifactFactory.createArtifactWithClassifier( dependency.getGroupId(), dependency.getArtifactId(),
//                                                              dependency.getVersion(), dependency.getType(),
//                                                              dependency.getClassifier() );
//            baseArtifact =
//                IdeUtils.resolveArtifact( artifactResolver, baseArtifact, remoteRepos, localRepository, getLog() );
//            if ( !baseArtifact.isResolved() )
//            {
//                // base artifact does not exist - no point checking for javadoc/sources
//                continue;
//            }
//
//            Artifact artifact =
//                IdeUtils.createArtifactWithClassifier( dependency.getGroupId(), dependency.getArtifactId(),
//                                                       dependency.getVersion(), dependency.getClassifier(),
//                                                       inClassifier, artifactFactory );
//            File notAvailableMarkerFile = IdeUtils.getNotAvailableMarkerFile( localRepository, artifact );
//
//            if ( forceRecheck && notAvailableMarkerFile.exists() )
//            {
//                if ( !notAvailableMarkerFile.delete() )
//                {
//                    getLog().warn( Messages.getString( "AbstractIdeSupportMojo.unabletodeletenotavailablemarkerfile",
//                                                       notAvailableMarkerFile ) );
//                }
//            }
//
//            if ( !notAvailableMarkerFile.exists() )
//            {
//                artifact =
//                    IdeUtils.resolveArtifact( artifactResolver, artifact, remoteRepos, localRepository, getLog() );
//                if ( artifact.isResolved() )
//                {
//                    if ( "sources".equals( inClassifier ) )
//                    {
//                        dependency.setSourceAttachment( artifact.getFile() );
//                    }
//                    else if ( "javadoc".equals( inClassifier ) && includeRemoteRepositories )
//                    {
//                        dependency.setJavadocAttachment( artifact.getFile() );
//                    }
//                }
//                else
//                {
//                    if ( includeRemoteRepositories )
//                    {
//                        try
//                        {
//                            notAvailableMarkerFile.createNewFile();
//                            getLog().debug( 
//                                     Messages.getString( "AbstractIdeSupportMojo.creatednotavailablemarkerfile", 
//                                                         notAvailableMarkerFile ) );
//                        }
//                        catch ( IOException e )
//                        {
//                            getLog().warn( 
//                                     Messages.getString( "AbstractIdeSupportMojo.failedtocreatenotavailablemarkerfile",
//                                                         notAvailableMarkerFile ) );
//                        }
//                    }
//                    // add the dependencies to the list
//                    // of those lacking the required
//                    // artifact
//                    missingClassifierDependencies.add( dependency );
//                }
//            }
//        }
//
//        // return the list of dependencies missing the
//        // required artifact
//        return missingClassifierDependencies;
//
//    }

    private List<IdeDependency> getMissingSourceDependencies( IdeDependency[] deps )
    {
        return Arrays.stream( deps )
                     .filter( dep -> dep.getSourceAttachment() == null )
                     .collect( toList() );
    }
    
    private List<IdeDependency> getJavadocDependencies( IdeDependency[] deps )
    {
        return Arrays.stream( deps )
                        .filter( dep -> dep.getJavadocAttachment() == null )
                        .collect( toList() );
    }
    
    /**
     * Output a message with the list of missing dependencies and info on how turn download on if it was disabled.
     */
    private void reportMissingArtifacts( List<IdeDependency> missingSourceDependencies,
                                         List<IdeDependency> missingJavadocDependencies )
    {
        StringBuilder msg = new StringBuilder();

        if ( getDownloadSources() && !missingSourceDependencies.isEmpty() )
        {
            msg.append( Messages.getString( "AbstractIdeSupportMojo.sourcesnotavailable" ) );

            for ( IdeDependency art : missingSourceDependencies )
            {
                msg.append( Messages.getString( "AbstractIdeSupportMojo.sourcesmissingitem", art.getId() ) );
            }
            msg.append( "\n" ); //$NON-NLS-1$
        }

        if ( getDownloadJavadocs() && !missingJavadocDependencies.isEmpty() )
        {
            msg.append( Messages.getString( "AbstractIdeSupportMojo.javadocnotavailable" ) );

            for ( IdeDependency art : missingJavadocDependencies )
            {
                msg.append( Messages.getString( "AbstractIdeSupportMojo.javadocmissingitem", art.getId() ) );
            }
            msg.append( "\n" ); //$NON-NLS-1$
        }
        getLog().info( msg );
    }

    /**
     * @return List of dependencies to exclude from eclipse classpath.
     * @since 2.5
     */
    public abstract List<String> getExcludes();

    /**
     * Checks if jar has to be resolved for the given artifact
     * 
     * @param art the artifact to check
     * @return true if resolution should happen
     */
    protected boolean hasToResolveJar( Artifact art )
    {
        return !( getUseProjectReferences() && isAvailableAsAReactorProject( art ) );
    }

    /**
     * Checks if a projects reference has to be used for the given artifact
     * 
     * @param art the artifact to check
     * @return true if a project reference has to be used.
     */
    protected boolean useProjectReference( Artifact art )
    {
        return getUseProjectReferences() && isAvailableAsAReactorProject( art );
    }
    
    /**
     * Checks if a projects reference has to be used for the given artifact
     * 
     * @param art the artifact to check
     * @return true if a project reference has to be used.
     */
    protected boolean useProjectReference( org.eclipse.aether.artifact.Artifact art )
    {
        return getUseProjectReferences() && isAvailableAsAReactorProject( art );
    }

//    /**
//     * Checks whether the currently running Maven satisfies the specified version (range).
//     * 
//     * @param version The version range to test for, must not be <code>null</code>.
//     * @return <code>true</code> if the current Maven version matches the specified version range, <code>false</code>
//     *         otherwise.
//     */
//    protected boolean isMavenVersion( String version )
//    {
//        return runtimeInformation.isMavenVersion( version );
//    }

}
