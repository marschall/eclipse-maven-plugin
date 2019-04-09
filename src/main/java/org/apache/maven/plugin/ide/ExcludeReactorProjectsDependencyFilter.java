package org.apache.maven.plugin.ide;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.resolve.AbstractFilter;
import org.apache.maven.shared.artifact.filter.resolve.Node;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;

/**
 * {@link TransformableFilter} implementation that excludes artifacts found in the Reactor.
 *
 * @author Maarten Mulders
 */
public class ExcludeReactorProjectsDependencyFilter extends AbstractFilter
{
    private final Log log;
    private final Set<ArtifactKey> reactorArtifactKeys;

    public ExcludeReactorProjectsDependencyFilter( List<MavenProject> reactorProjects, Log log )
    {
        this.log = log;
        this.reactorArtifactKeys = new HashSet<>( reactorProjects.size() );
        for ( MavenProject project : reactorProjects )
        {
            this.reactorArtifactKeys.add( ArtifactKey.key( project.getArtifact() ) );
        }
    }

    @Override
    public boolean accept( Node node, List<Node> parents )
    {
        Dependency dependency = node.getDependency();
        if ( dependency != null )
        {
            ArtifactKey dependencyArtifactKey = new ArtifactKey(
                    dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion() );

            boolean result = isDependencyArtifactInReactor( dependencyArtifactKey );

            if ( log.isDebugEnabled() && result )
            {
                log.debug( "Skipped dependency "
                        + dependencyArtifactKey
                        + " because it is present in the reactor" );
            }

            return !result;
        }
        return true;
    }

    private boolean isDependencyArtifactInReactor( ArtifactKey dependencyArtifactKey )
    {
        return this.reactorArtifactKeys.contains( dependencyArtifactKey );
    }
    
    static final class ArtifactKey
    {

        private String groupId;

        private String artifactId;

        private String version;

        public ArtifactKey( String groupId, String artifactId, String version )
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
        
        static ArtifactKey key( Artifact artifact )
        {
            return new ArtifactKey( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion() );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( artifactId, groupId, version );
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( !( obj instanceof ArtifactKey ) )
            {
                return false;
            }
            ArtifactKey other = (ArtifactKey) obj;
            return Objects.equals( artifactId, other.artifactId )
                && Objects.equals( groupId, other.groupId )
                && Objects.equals( version, other.version );
        }

        

    }
    
}
