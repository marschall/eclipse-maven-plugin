package org.apache.maven.plugin.ide;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;

public class ExcludeReactorProjectsArifactFilter implements ArtifactsFilter
{
    private final Set<ArtifactKey> reactorArtifactKeys;

    public ExcludeReactorProjectsArifactFilter( List<MavenProject> reactorProjects )
    {
        this.reactorArtifactKeys = new HashSet<>( reactorProjects.size() );
        for ( MavenProject project : reactorProjects )
        {
            this.reactorArtifactKeys.add( ArtifactKey.key( project.getArtifact() ) );
        }
    }

    @Override
    public Set<Artifact> filter( Set<Artifact> artifacts )
    {
        Set<Artifact> filtered = new LinkedHashSet<>();
        for ( Artifact artifact : artifacts )
        {
            if ( this.isArtifactIncluded( artifact ) )
            {
                filtered.add( artifact );
            }
        }

        return filtered;
    }


    @Override
    public boolean isArtifactIncluded( Artifact artifact )
    {
        return !isDependencyArtifactInReactor( ArtifactKey.key( artifact ) );
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
