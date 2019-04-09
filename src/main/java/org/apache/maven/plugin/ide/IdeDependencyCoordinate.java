package org.apache.maven.plugin.ide;

import java.util.Objects;

public final class IdeDependencyCoordinate
{

    private String groupId;

    private String artifactId;

    private String version;

    private String classifier;

    private String type;

    public IdeDependencyCoordinate( String groupId, String artifactId, String version, String classifier, String type )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.type = type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( artifactId, classifier, groupId, type, version );
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( !( obj instanceof IdeDependencyCoordinate ) )
        {
            return false;
        }
        IdeDependencyCoordinate other = (IdeDependencyCoordinate) obj;
        return Objects.equals( artifactId, other.artifactId )
            && Objects.equals( classifier, other.classifier )
            && Objects.equals( groupId, other.groupId )
            && Objects.equals( type, other.type )
            && Objects.equals( version, other.version );
    }

    @Override
    public String toString()
    {
        return "IdeDependencyCoordinate [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version
            + ", classifier=" + classifier + ", type=" + type + "]";
    }

    

}
