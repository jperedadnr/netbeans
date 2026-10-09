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
package com.gluonhq.netbeans.nbfx.annotations;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Compiles a sample source annotated with the nbfx annotations and asserts that the layer-generating
 * processors emit the expected {@code NbFx/*} fragments.
 *
 * @since 1.0
 */
public class FxAnnotationsProcessorTest {

    private static final String SOURCE =
            "package demo;\n"
            + "import com.gluonhq.netbeans.nbfx.annotations.*;\n"
            + "public class DemoActions {\n"
            + "    @FxActionRegistration(id = \"demo.hello\", displayName = \"Hello\", iconName = \"hello.png\","
            + " accelerator = \"Shortcut+H\")\n"
            + "    @FxActionReference(id = \"demo.hello\", path = \"Menus/File\", position = 100)\n"
            + "    @FxActionReference(id = \"demo.hello\", path = \"Toolbars/Main\", position = 10)\n"
            + "    public static class Hello implements Runnable {\n"
            + "        public void run() {}\n"
            + "    }\n"
            + "    @FxViewRegistration(id = \"demo.view\", displayName = \"Demo View\")\n"
            + "    public static class DemoView {}\n"
            + "    @FxStatusRegistration(id = \"demo.status\")\n"
            + "    public static class DemoStatus {}\n"
            + "    @FxPropertyEditorRegistration(valueType = String.class)\n"
            + "    public static class StringEditor {}\n"
            + "    @FxOptionsRegistration(id = \"demo.options\", categoryName = \"Demo\")\n"
            + "    public static class DemoOptions {}\n"
            + "}\n";

    private static final String PROCESSORS = String.join(",",
            "com.gluonhq.netbeans.nbfx.annotations.processors.FxActionRegistrationProcessor",
            "com.gluonhq.netbeans.nbfx.annotations.processors.FxViewRegistrationProcessor",
            "com.gluonhq.netbeans.nbfx.annotations.processors.FxStatusRegistrationProcessor",
            "com.gluonhq.netbeans.nbfx.annotations.processors.FxPropertyEditorRegistrationProcessor",
            "com.gluonhq.netbeans.nbfx.annotations.processors.FxOptionsRegistrationProcessor",
            "com.gluonhq.netbeans.nbfx.annotations.processors.FxProjectTypeRegistrationProcessor",
            "com.gluonhq.netbeans.nbfx.annotations.processors.FxWizardRegistrationProcessor");

    @Test
    public void generatesLayerFragments() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-annotations-test");
        Path source = dir.resolve("demo/DemoActions.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, SOURCE);
        Path out = dir.resolve("out");
        Files.createDirectories(out);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("no system Java compiler", compiler);
        String classpath = System.getProperty("java.class.path");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter output = new StringWriter();
        int result;
        JavaCompiler.CompilationTask task;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromStrings(List.of(source.toString()));
            task = compiler.getTask(output, fileManager, diagnostics,
                    List.of(
                            "-classpath", classpath,
                            "-processorpath", classpath,
                            "-processor", PROCESSORS,
                            "-d", out.toString()),
                    null, units);
            Boolean success = task.call();
            result = success != null && success ? 0 : 1;
        }
        assertEquals("compilation failed: " + output + diagnostics.getDiagnostics(),
                0, result);

        Path layer = out.resolve("META-INF/generated-layer.xml");
        assertTrue("generated layer missing", Files.exists(layer));
        String xml = Files.readString(layer);

        assertTrue(xml, xml.contains("<folder name=\"NbFx\">"));
        // Actions
        assertTrue(xml, xml.contains("name=\"demo.hello.instance\""));
        assertTrue(xml, xml.contains("instanceClass\" stringvalue=\"demo.DemoActions$Hello\""));
        // References into a menu and a tool bar
        assertTrue(xml, xml.contains("<folder name=\"Menus\">"));
        assertTrue(xml, xml.contains("name=\"demo.hello.ref\""));
        assertTrue(xml, xml.contains("actionId\" stringvalue=\"demo.hello\""));
        assertTrue(xml, xml.contains("<folder name=\"Toolbars\">"));
        // Views
        assertTrue(xml, xml.contains("name=\"demo.view.instance\""));
        assertTrue(xml, xml.contains("location\" stringvalue=\"LEFT\""));
        // Status
        assertTrue(xml, xml.contains("name=\"demo.status.instance\""));
        // Property editors, keyed by value type
        assertTrue(xml, xml.contains("name=\"java-lang-String.instance\""));
        assertTrue(xml, xml.contains("valueType\" stringvalue=\"java.lang.String\""));
        // Options
        assertTrue(xml, xml.contains("name=\"demo.options.instance\""));
    }
}
