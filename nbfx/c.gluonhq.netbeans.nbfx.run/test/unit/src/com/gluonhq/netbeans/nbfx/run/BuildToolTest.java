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
package com.gluonhq.netbeans.nbfx.run;

import java.nio.file.Files;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildCommands;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Verifies build-tool detection and the resulting command lines.
 *
 * @since 1.0
 */
public class BuildToolTest {

    @Test
    public void detectsByMarkerFile() throws Exception {
        Path maven = Files.createTempDirectory("nbfx-maven");
        Files.writeString(maven.resolve("pom.xml"), "<project/>");
        assertEquals(BuildTool.MAVEN, BuildTool.detect(maven));

        Path gradle = Files.createTempDirectory("nbfx-gradle");
        Files.writeString(gradle.resolve("settings.gradle"), "rootProject.name='x'");
        assertEquals(BuildTool.GRADLE, BuildTool.detect(gradle));

        Path ant = Files.createTempDirectory("nbfx-ant");
        Files.writeString(ant.resolve("build.xml"), "<project/>");
        assertEquals(BuildTool.ANT, BuildTool.detect(ant));

        assertEquals(BuildTool.UNKNOWN, BuildTool.detect(Files.createTempDirectory("nbfx-none")));
        assertEquals(BuildTool.UNKNOWN, BuildTool.detect(null));
    }

    @Test
    public void mavenCommands() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-maven");
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        assertEquals(List.of("mvn", "package"), BuildTool.MAVEN.commandLine(dir, BuildCommands.BUILD));
        assertEquals(List.of("mvn", "clean"), BuildTool.MAVEN.commandLine(dir, BuildCommands.CLEAN));
        assertEquals(List.of("mvn", "test"), BuildTool.MAVEN.commandLine(dir, BuildCommands.TEST));
    }

    @Test
    public void prefersProjectWrapper() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-gradle");
        Files.writeString(dir.resolve("settings.gradle"), "rootProject.name='x'");
        Files.writeString(dir.resolve("gradlew"), "#!/bin/sh");
        assertEquals(List.of(dir.resolve("gradlew").toString(), "build"),
                BuildTool.GRADLE.commandLine(dir, BuildCommands.BUILD));
    }

    @Test
    public void unknownHasNoCommand() {
        assertEquals(null, BuildTool.UNKNOWN.commandLine(null, BuildCommands.BUILD));
    }

    @Test
    public void antCommands() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-ant");
        Files.writeString(dir.resolve("build.xml"), "<project/>");
        assertEquals(List.of("ant", "jar"), BuildTool.ANT.commandLine(dir, BuildCommands.BUILD));
        assertEquals(List.of("ant", "clean", "jar"), BuildTool.ANT.commandLine(dir, BuildCommands.REBUILD));
        assertEquals(List.of("ant", "clean"), BuildTool.ANT.commandLine(dir, BuildCommands.CLEAN));
        assertEquals(List.of("ant", "test"), BuildTool.ANT.commandLine(dir, BuildCommands.TEST));
        assertEquals(List.of("ant", "run"), BuildTool.ANT.commandLine(dir, BuildCommands.RUN));
    }

    @Test
    public void netbeansModuleAntCommands() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-module");
        Files.writeString(dir.resolve("build.xml"), "<project/>");
        Files.createDirectories(dir.resolve("nbproject"));
        Files.writeString(dir.resolve("nbproject").resolve("project.xml"), "<project/>");
        assertEquals(List.of("ant", "build"), BuildTool.ANT.commandLine(dir, BuildCommands.BUILD));
        assertEquals(List.of("ant", "clean", "build"), BuildTool.ANT.commandLine(dir, BuildCommands.REBUILD));
        assertEquals(List.of("ant", "clean"), BuildTool.ANT.commandLine(dir, BuildCommands.CLEAN));
        assertEquals(List.of("ant", "test-unit"), BuildTool.ANT.commandLine(dir, BuildCommands.TEST));
        assertEquals(List.of("ant", "run"), BuildTool.ANT.commandLine(dir, BuildCommands.RUN));
    }
}
