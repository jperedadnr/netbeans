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
package com.gluonhq.netbeans.nbfx.javanavigator.ui;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import java.io.IOException;
import javafx.scene.Node;
import org.openide.filesystems.FileObject;

/**
 * One of the Navigator's views of a Java file - Members, Bean Patterns -: a node showing the result
 * of a scan of the file, fed by the {@link DocumentTracker} that follows the active editor.
 *
 * @param <R> the result of the panel's scan
 */
interface NavigatorPanel<R> {

    /** The panel's node, shown under the Navigator's header. */
    Node getNode();

    /**
     * Scans {@code text}, the current content of {@code file}; runs off the FX thread.
     *
     * @return the result, or {@code null} when the scan was cancelled or the file could not be read
     */
    R scan(FileObject file, String text, Cancellation cancellation) throws IOException;

    /** Shows {@code result}, {@code null} for a file that could not be read; FX thread. */
    void show(R result);

    /** Shows nothing; FX thread. */
    void clear();

    /**
     * Selects the row enclosing the editor's caret at {@code offset}, without taking the focus, and
     * clears the selection when none does - always for a negative offset, the file being shown
     * without an editor; FX thread.
     */
    void selectAt(int offset);

    /**
     * Selects the row of the type named {@code qualifiedName} - an inspected type - expanding to it,
     * without taking the focus; clears the selection when the panel has no such row. Nothing by
     * default. FX thread.
     */
    default void selectType(String qualifiedName) {
    }
}
