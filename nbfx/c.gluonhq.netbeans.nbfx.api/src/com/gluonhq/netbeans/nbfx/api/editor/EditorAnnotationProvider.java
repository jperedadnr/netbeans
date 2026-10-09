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
package com.gluonhq.netbeans.nbfx.api.editor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.openide.filesystems.FileObject;

/**
 * Computes the {@link EditorAnnotation}s of a file for the editor's gutter. Implementations are
 * registered in the global {@link org.openide.util.Lookup} by the modules that can analyse a kind
 * of file (the Java navigator for {@code .java} files); the editor asks the first one that
 * {@link #handles} the file when the file opens and after its edits settle, and shows what it
 * answers at the lines it names, moving them along with later edits until the next answer.
 */
public interface EditorAnnotationProvider {

    /** Whether this provider annotates {@code file}. */
    boolean handles(FileObject file);

    /**
     * The annotations of {@code file} with {@code text} as its content. Called on the FX thread;
     * the work runs off it and the future completes on any thread, with an empty list when
     * there is nothing to show. {@code cancelled} says when the editor no longer wants the answer
     * (the text changed again), for the work to stop early.
     *
     * @param file      the file open in the editor
     * @param text      its editor content, with one {@code '\n'} per line break
     * @param cancelled whether the answer is no longer wanted
     * @return the annotations, by line
     */
    CompletableFuture<List<EditorAnnotation>> annotate(FileObject file, String text, BooleanSupplier cancelled);
}
