package org.apache.maven.plugin.ide;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.eclipse.aether.artifact.ArtifactProperties;

/**
 * Utility classes for dealing with Aether aritfacts and dependencies.
 */
final class AetherToMaven
{

    private AetherToMaven()
    {
        throw new AssertionError( "not instantiable" );
    }

    static String getType( org.eclipse.aether.artifact.Artifact artifact )
    {
        return artifact.getProperty( ArtifactProperties.TYPE, artifact.getExtension() );
    }
    
    static boolean isAddToClasspath( org.eclipse.aether.artifact.Artifact artifact )
    {
        String constitutesBuildPath = artifact.getProperty( ArtifactProperties.CONSTITUTES_BUILD_PATH, "" );
        return Boolean.parseBoolean( constitutesBuildPath );
    }

    static Artifact aetherToMavenArtifact( org.eclipse.aether.artifact.Artifact eatherArtifact,
                                           ArtifactHandlerManager artifactHandlerManager)
    {
        String groupId = eatherArtifact.getGroupId();
        String artifactId = eatherArtifact.getArtifactId();
        String version = eatherArtifact.getVersion();
        String scope = null;
        String type = getType( eatherArtifact );
        String classifier = null;
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( type );
        DefaultArtifact mavenArtifact =
            new DefaultArtifact( groupId, artifactId, version, scope, type, classifier, artifactHandler );

        mavenArtifact.setFile( eatherArtifact.getFile() );
        mavenArtifact.setResolved( eatherArtifact.getFile() != null );

        return mavenArtifact;
    }

}
