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

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildCommands;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A build tool, detected from a project's directory, and the command line to run a build command
 * with it. Used as a fallback when no project-type {@link
 * com.gluonhq.netbeans.nbfx.project.ui.api.BuildActionProvider} matches the project.
 *
 * @since 1.0
 */
public enum BuildTool {

    /** Apache Maven, detected by a {@code pom.xml}. */
    MAVEN,

    /** Gradle, detected by a {@code build.gradle[.kts]} or {@code settings.gradle[.kts]}. */
    GRADLE,

    /** Apache Ant, detected by a {@code build.xml}. */
    ANT,

    /** No supported build tool. */
    UNKNOWN;

    /**
     * Detects the build tool of {@code dir} from its marker files, preferring Maven, then Gradle,
     * then Ant.
     *
     * @param dir the project directory; may be {@code null}
     * @return the detected tool, or {@link #UNKNOWN}
     */
    public static BuildTool detect(Path dir) {
        if (dir == null) {
            return UNKNOWN;
        }
        if (Files.isRegularFile(dir.resolve("pom.xml"))) {
            return MAVEN;
        }
        if (anyFile(dir, "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")) {
            return GRADLE;
        }
        if (Files.isRegularFile(dir.resolve("build.xml"))) {
            return ANT;
        }
        return UNKNOWN;
    }

    /**
     * The command line to run {@code command} (a {@link BuildCommands} id) in {@code dir}, or
     * {@code null} when this tool is {@link #UNKNOWN} or does not support the command. A
     * project-local wrapper ({@code mvnw}/{@code gradlew}) is preferred over a tool on the
     * {@code PATH}.
     *
     * @param dir     the project directory
     * @param command the build command id
     * @return the command line, or {@code null}
     */
    public List<String> commandLine(Path dir, String command) {
        return switch (this) {
            case MAVEN -> maven(dir, command);
            case GRADLE -> gradle(dir, command);
            case ANT -> ant(dir, command);
            case UNKNOWN -> null;
        };
    }

    private static List<String> maven(Path dir, String command) {
        List<String> goals = switch (command) {
            case BuildCommands.BUILD -> List.of("package");
            case BuildCommands.CLEAN -> List.of("clean");
            case BuildCommands.REBUILD -> List.of("clean", "package");
            case BuildCommands.TEST -> List.of("test");
            case BuildCommands.RUN -> List.of("compile", "exec:java");
            case BuildCommands.JAVADOC -> List.of("javadoc:javadoc");
            default -> null;
        };
        return goals == null ? null : prepend(executable(dir, "mvnw", "mvn"), goals);
    }

    private static List<String> gradle(Path dir, String command) {
        String executable = executable(dir, "gradlew", "gradle");
        return switch (command) {
            case BuildCommands.BUILD -> List.of(executable, "build");
            case BuildCommands.CLEAN -> List.of(executable, "clean");
            case BuildCommands.REBUILD -> List.of(executable, "clean", "build");
            case BuildCommands.TEST -> List.of(executable, "test");
            case BuildCommands.RUN -> List.of(executable, "run");
            case BuildCommands.JAVADOC -> List.of(executable, "javadoc");
            default -> null;
        };
    }

    private static List<String> ant(Path dir, String command) {
        boolean netbeansModule = Files.isRegularFile(dir.resolve("nbproject").resolve("project.xml"));
        if (netbeansModule) {
            return switch (command) {
                case BuildCommands.BUILD -> List.of("ant", "build");
                case BuildCommands.CLEAN -> List.of("ant", "clean");
                case BuildCommands.REBUILD -> List.of("ant", "clean", "build");
                case BuildCommands.TEST -> List.of("ant", "test-unit");
                case BuildCommands.RUN -> List.of("ant", "run");
                case BuildCommands.JAVADOC -> List.of("ant", "javadoc-nb");
                default -> null;
            };
        }
        return switch (command) {
            case BuildCommands.BUILD -> List.of("ant", "jar");
            case BuildCommands.CLEAN -> List.of("ant", "clean");
            case BuildCommands.REBUILD -> List.of("ant", "clean", "jar");
            case BuildCommands.TEST -> List.of("ant", "test");
            case BuildCommands.RUN -> List.of("ant", "run");
            case BuildCommands.JAVADOC -> List.of("ant", "javadoc");
            default -> null;
        };
    }

    private static List<String> prepend(String head, List<String> tail) {
        List<String> command = new ArrayList<>(tail.size() + 1);
        command.add(head);
        command.addAll(tail);
        return List.copyOf(command);
    }

    private static String executable(Path dir, String wrapper, String fallback) {
        Path unix = dir.resolve(wrapper);
        if (Files.isRegularFile(unix)) {
            return unix.toString();
        }
        Path windows = dir.resolve(wrapper + ".cmd");
        if (Files.isRegularFile(windows)) {
            return windows.toString();
        }
        return fallback;
    }

    private static boolean anyFile(Path dir, String... names) {
        for (String name : names) {
            if (Files.isRegularFile(dir.resolve(name))) {
                return true;
            }
        }
        return false;
    }
}
