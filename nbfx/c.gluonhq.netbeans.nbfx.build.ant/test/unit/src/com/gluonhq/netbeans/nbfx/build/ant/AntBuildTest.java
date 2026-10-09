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
package com.gluonhq.netbeans.nbfx.build.ant;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Verifies that an Ant build runs in-process and streams its output. */
public class AntBuildTest {

    @Test
    public void runsAntInProcess() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-ant");
        Files.writeString(dir.resolve("build.xml"),
                "<project default='hello'>"
                + "<target name='hello'><echo message='HELLO_ANT'/></target>"
                + "</project>");

        StringBuilder out = new StringBuilder();
        BuildOutput output = new BuildOutput() {
            @Override
            public void clear() {
            }

            @Override
            public void show() {
            }

            @Override
            public void append(String text) {
                out.append(text);
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

        new AntBuild(dir, new String[] {"hello"}, output, progress).start();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && out.indexOf("BUILD SUCCESSFUL") < 0) {
            Thread.sleep(50);
        }
        assertTrue(out.toString(), out.indexOf("HELLO_ANT") >= 0);
        assertTrue(out.toString(), out.indexOf("BUILD SUCCESSFUL") >= 0);
    }
}
