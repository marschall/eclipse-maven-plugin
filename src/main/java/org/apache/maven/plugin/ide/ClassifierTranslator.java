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
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class ClassifierTranslator
    implements ArtifactTranslator
{
    private ArtifactHandlerManager artifactHandlerManager;

    private Set<String> classifiers;

    /**
     * @param artifactHandlerManager {@link ArtifactHandlerManager}.
     * @param theClassifiers The classifiers to use.
     */
    public ClassifierTranslator( ArtifactHandlerManager artifactHandlerManager, Set<String> theClassifiers )
    {
        this.artifactHandlerManager = artifactHandlerManager;
        this.classifiers = theClassifiers;
    }

    @Override
    public Set<ArtifactCoordinate> translate( Set<Artifact> artifacts, Log log )
    {
        Set<ArtifactCoordinate> results = new LinkedHashSet<>();

        for ( Artifact artifact : artifacts )
        {
            // this translator must pass both type and classifier here so we
            // will use the
            // base artifact value if null comes in
            String type = artifact.getType();
            
            // TODO type = "java-source";

            ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( type );

            String extension = artifactHandler.getExtension();
            
            for ( String classifier : this.classifiers )
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug( "Translating Artifacts using Classifier: " + classifier );
                }

                if ( "sources".equals( classifier ) && "tests".equals( artifact.getClassifier() ) )
                {
                    // MECLIPSE-151 - if the dependency is a test, get the correct classifier for it. (ignore forjavadocs)
                    classifier = "test-sources";
                }

                DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
                coordinate.setGroupId( artifact.getGroupId() );
                coordinate.setArtifactId( artifact.getArtifactId() );
                coordinate.setVersion( artifact.getVersion() );
                coordinate.setClassifier( classifier );
                coordinate.setExtension( extension );

                results.add( coordinate );
            }

        }

        return results;
    }

}
