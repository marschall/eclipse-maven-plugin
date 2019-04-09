package org.apache.maven.plugin.ide;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.graph.Dependency;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class AetherJavadocTranslator
    implements AetherArtifactTranslator
{
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * @param artifactHandlerManager {@link ArtifactHandlerManager}.
     * @param theClassifiers The classifiers to use.
     */
    public AetherJavadocTranslator( ArtifactHandlerManager artifactHandlerManager )
    {
        this.artifactHandlerManager = artifactHandlerManager;
    }

    @Override
    public Set<ArtifactCoordinate> translate( List<Dependency> dependencies, Log log )
    {
        Set<ArtifactCoordinate> results = new LinkedHashSet<>();

        for ( Dependency dependency : dependencies )
        {
            // this translator must pass both type and classifier here so we
            // will use the
            // base artifact value if null comes in
            Artifact artifact = dependency.getArtifact();
            String type = AetherToMaven.getType( artifact );

            ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( type );

            String extension = artifactHandler.getExtension();

            String classifier = "javadoc";

            DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
            coordinate.setGroupId( artifact.getGroupId() );
            coordinate.setArtifactId( artifact.getArtifactId() );
            coordinate.setVersion( artifact.getVersion() );
            coordinate.setClassifier( classifier );
            coordinate.setExtension( extension );

            results.add( coordinate );

        }

        return results;
    }

}
