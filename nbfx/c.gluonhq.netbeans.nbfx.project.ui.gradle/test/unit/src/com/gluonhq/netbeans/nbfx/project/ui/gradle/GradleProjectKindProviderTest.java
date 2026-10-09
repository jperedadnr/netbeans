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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Verifies the {@code settings.gradle} include parsing used for subproject discovery.
 */
public class GradleProjectKindProviderTest {

    @Test
    public void parsesIncludeDeclarations() throws Exception {
        Path settings = Files.createTempFile("nbfx-settings", ".gradle");
        Files.writeString(settings, String.join("\n",
                "rootProject.name = 'demo'",
                "include ':app'",
                "include 'noColon'",
                "include ':libs:core', ':libs:util'",
                "include(\":feature\")",
                "// include ':ignored'",
                "include ':commented' // trailing"));
        assertEquals(List.of("app", "noColon", "libs/core", "libs/util", "feature", "commented"), parse(settings));
    }

    @Test
    public void ignoresNonIncludeLines() throws Exception {
        Path settings = Files.createTempFile("nbfx-settings", ".gradle.kts");
        Files.writeString(settings, String.join("\n",
                "pluginManagement { }",
                "dependencyResolutionManagement { }",
                "include(\":only\")"));
        assertEquals(List.of("only"), parse(settings));
    }

    private static List<String> parse(Path settings) throws Exception {
        try (InputStream in = Files.newInputStream(settings)) {
            return GradleProjectKindProvider.parseIncludes(in);
        }
    }
}
