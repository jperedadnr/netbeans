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
package com.gluonhq.netbeans.nbfx.project.ui.maven.api;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildExecution;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs Maven goals in-process, through the NetBeans Maven embedder, for the build commands of a
 * Maven project and for a goal picked elsewhere (the Navigator's <em>Related goals</em>).
 *
 * @since 1.0
 */
public final class MavenGoals {

    private MavenGoals() {
    }

    /**
     * Starts {@code goals} in the project at {@code dir}, with {@code properties} as the user
     * properties ({@code -Dname=value}), streaming the build's log to {@code output} and reporting
     * to {@code progress}.
     *
     * @return the running build, to cancel it
     */
    public static BuildExecution start(Path dir, List<String> goals, Map<String, String> properties,
            BuildOutput output, BuildProgress progress) {
        MavenBuild build = new MavenBuild(dir, goals.toArray(String[]::new), Map.copyOf(properties), output, progress);
        build.start();
        return build;
    }
}
