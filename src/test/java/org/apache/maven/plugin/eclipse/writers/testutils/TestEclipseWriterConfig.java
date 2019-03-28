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
package org.apache.maven.plugin.eclipse.writers.testutils;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.eclipse.BuildCommand;
import org.apache.maven.plugin.eclipse.EclipseSourceDir;
import org.apache.maven.plugin.eclipse.LinkedResource;
import org.apache.maven.plugin.eclipse.writers.EclipseWriterConfig;
import org.apache.maven.plugin.ide.IdeDependency;

public class TestEclipseWriterConfig
    extends EclipseWriterConfig
{

    @Override
    public List<BuildCommand> getBuildCommands()
    {
        List<BuildCommand> result = super.getBuildCommands();

        if ( result == null )
        {
            result = new ArrayList<>();
        }

        return result;
    }

    @Override
    public List<String> getClasspathContainers()
    {
        List<String> result = super.getClasspathContainers();

        if ( result == null )
        {
            result = new ArrayList<>();
        }

        return result;
    }

    @Override
    public IdeDependency[] getDeps()
    {
        IdeDependency[] deps = super.getDeps();

        if ( deps == null )
        {
            deps = new IdeDependency[0];
        }

        return deps;
    }

    @Override
    public List<String> getProjectnatures()
    {
        List<String> result = super.getProjectnatures();

        if ( result == null )
        {
            result = new ArrayList<>();
        }

        return result;
    }

    @Override
    public EclipseSourceDir[] getSourceDirs()
    {
        EclipseSourceDir[] dirs = super.getSourceDirs();

        if ( dirs == null )
        {
            dirs = new EclipseSourceDir[0];
        }

        return dirs;
    }

    @Override
    public List<LinkedResource> getLinkedResources()
    {
        List<LinkedResource> result = super.getLinkedResources();

        if ( result == null )
        {
            result = new ArrayList<>();
        }

        return result;
    }

}
