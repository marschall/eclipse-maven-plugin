package org.apache.maven.plugin.ide;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;

final class GroupIdArtifactIdArtifactsFilter
    implements ArtifactsFilter
{

    private final Set<GroupIdArtifactId> excluded;

    private GroupIdArtifactIdArtifactsFilter( Set<GroupIdArtifactId> excluded )
    {
        this.excluded = excluded;
    }

    static ArtifactsFilter fromExcludes( List<String> excludes )
    {
        if ( excludes == null || excludes.isEmpty() )
        {
            return new GroupIdArtifactIdArtifactsFilter( Collections.emptySet() );
        }

        Set<GroupIdArtifactId> excluded = new HashSet<>();
        for ( String s : excludes )
        {
            excluded.add( GroupIdArtifactId.fromString( s ) );
        }
        return new GroupIdArtifactIdArtifactsFilter( excluded );
    }

    @Override
    public Set<Artifact> filter( Set<Artifact> artifacts )
        throws ArtifactFilterException
    {

        if ( this.excluded.isEmpty() )
        {
            return artifacts;
        }

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
        throws ArtifactFilterException
    {
        if ( this.excluded.isEmpty() )
        {
            return true;
        }

        GroupIdArtifactId key = new GroupIdArtifactId( artifact.getGroupId(), artifact.getArtifactId() );
        return !this.excluded.contains( key );
    }

    static final class GroupIdArtifactId
    {

        private final String groupId;

        private final String artifactId;

        private GroupIdArtifactId( String groupId, String artifactId )
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        static GroupIdArtifactId fromString( String s )
        {
            int dotIndex = s.indexOf( ':' );
            String groupId = s.substring( 0, dotIndex );
            String artifactId = s.substring( dotIndex + 1 );
            return new GroupIdArtifactId( groupId, artifactId );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( artifactId, groupId );
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( !( obj instanceof GroupIdArtifactId ) )
            {
                return false;
            }
            GroupIdArtifactId other = (GroupIdArtifactId) obj;
            return Objects.equals( artifactId, other.artifactId ) && Objects.equals( groupId, other.groupId );
        }

    }

}
