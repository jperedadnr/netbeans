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
package com.gluonhq.netbeans.nbfx.project.ui.gradle;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildActionProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildExecution;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.io.File;
import java.nio.file.Files;
import junit.framework.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;

/**
 * Runs a real Gradle build through {@link GradleBuildActionProvider} inside the module system, to
 * verify the Tooling API works there and streams output.
 */
public class GradleBuildTest extends NbTestCase {

    public GradleBuildTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(GradleBuildTest.class)
                .clusters(".*")
                .enableModules(".*")
                .gui(false)
                .suite();
    }

    public void testBuildStreamsOutput() throws Exception {
        File dir = Files.createTempDirectory("nbfx-gradle").toFile();
        Files.writeString(dir.toPath().resolve("build.gradle"),
                "tasks.register('build') { doLast { println 'HELLO_GRADLE' } }\n");

        StringBuilder text = new StringBuilder();
        BuildOutput output = new BuildOutput() {
            @Override
            public void clear() {
            }

            @Override
            public void show() {
            }

            @Override
            public void append(String delta) {
                text.append(delta);
            }
        };
        BuildProgress progress = new BuildProgress() {
            @Override
            public void start(String name, Runnable cancel) {
            }

            @Override
            public void finish() {
            }
        };

        BuildActionProvider provider = new GradleBuildActionProvider();
        BuildExecution execution = provider.start(dir.toPath(), "build", output, progress);
        assertNotNull(execution);

        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline
                && text.indexOf("BUILD SUCCESSFUL") < 0 && text.indexOf("BUILD FAILED") < 0) {
            Thread.sleep(200);
        }
        // The build may succeed or fail (JDK/Gradle compatibility), but the executor must stream its
        // output and a terminal marker into the console.
        assertTrue(text.toString(), text.indexOf("Connecting to Gradle") >= 0);
        assertTrue(text.toString(), text.indexOf("BUILD SUCCESSFUL") >= 0 || text.indexOf("BUILD FAILED") >= 0);
    }
}
