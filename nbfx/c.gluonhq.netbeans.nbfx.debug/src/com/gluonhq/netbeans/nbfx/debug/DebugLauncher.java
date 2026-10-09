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
package com.gluonhq.netbeans.nbfx.debug;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.netbeans.api.java.classpath.ClassPath;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Resolves a Java source file to a {@link LaunchRequest}: the main class, its runtime classpath, the
 * working directory, the sources classpath and an optional breakpoint on the current line.
 *
 * @since 1.0
 */
final class DebugLauncher {

    private static final Pattern MAIN = Pattern.compile("static\\s+void\\s+main\\s*\\(");

    private DebugLauncher() {
    }

    static boolean isRunnableJavaFile(FileObject file) {
        if (file == null || !"java".equals(file.getExt())) {
            return false;
        }
        try {
            return MAIN.matcher(file.asText()).find();
        } catch (java.io.IOException ex) {
            return false;
        }
    }

    /**
     * Resolves {@code file} into a {@link LaunchRequest}, or {@code null} when it is not a runnable
     * Java file of a project with an execution classpath.
     */
    static LaunchRequest resolve(FileObject file, int caretLine) {
        if (!isRunnableJavaFile(file)) {
            return null;
        }
        ClassPath source = ClassPath.getClassPath(file, ClassPath.SOURCE);
        ClassPath execute = ClassPath.getClassPath(file, ClassPath.EXECUTE);
        if (source == null || execute == null) {
            return null;
        }
        String resource = source.getResourceName(file);
        if (resource == null || !resource.endsWith(".java")) {
            return null;
        }
        String mainClass = resource.substring(0, resource.length() - ".java".length()).replace('/', '.');
        List<String> entries = new ArrayList<>();
        for (FileObject root : execute.getRoots()) {
            File file2 = FileUtil.toFile(root);
            if (file2 != null) {
                entries.add(file2.getAbsolutePath());
            }
        }
        String classpath = String.join(File.pathSeparator, entries);
        return new LaunchRequest(mainClass, classpath, workDir(file), source, file, caretLine);
    }

    private static Path workDir(FileObject file) {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.ownerOf(file);
        if (project != null) {
            return Path.of(project.getPath());
        }
        File parent = FileUtil.toFile(file.getParent());
        return parent == null ? Path.of(".") : parent.toPath();
    }
}
