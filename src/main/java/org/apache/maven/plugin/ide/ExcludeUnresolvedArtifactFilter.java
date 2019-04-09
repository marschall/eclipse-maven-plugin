package org.apache.maven.plugin.ide;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;

public class ExcludeUnresolvedArtifactFilter
    implements ArtifactsFilter
{
    public ExcludeUnresolvedArtifactFilter()
    {
        super();
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
        return artifact.isResolved();
    }

}
