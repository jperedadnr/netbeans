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
package com.gluonhq.netbeans.nbfx.javanavigator.model;

import java.io.IOException;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.CompilationController;
import org.openide.filesystems.FileObject;

/**
 * Compiles a file from the editor's current text - an in-memory copy placed like the original
 * under its source root ({@link MemorySources}), with the class path of the original - so the
 * Navigator's scans follow unsaved edits and report the editor's offsets.
 *
 * @param classpath the class path the file is compiled with, to resolve its elements later
 * @param source    the compilation
 */
record JavaSources(ClasspathInfo classpath, JavaSource source) {

    /** The compilation of {@code text} as the content of {@code file}, or {@code null} when none can be made. */
    static JavaSources of(FileObject file, String text) throws IOException {
        ClasspathInfo classpath = ClasspathInfo.create(file);
        JavaSource source = JavaSource.create(classpath, MemorySources.copyOf(file, text));
        return source == null ? null : new JavaSources(classpath, source);
    }

    /** Runs {@code task} at {@link JavaSource.Phase#ELEMENTS_RESOLVED} on the calling thread. */
    void run(Task<CompilationController> task) throws IOException {
        run(JavaSource.Phase.ELEMENTS_RESOLVED, task);
    }

    /** Runs {@code task} at {@code phase} on the calling thread. */
    void run(JavaSource.Phase phase, Task<CompilationController> task) throws IOException {
        source.runUserActionTask(controller -> {
            controller.toPhase(phase);
            task.run(controller);
        }, true);
    }
}
