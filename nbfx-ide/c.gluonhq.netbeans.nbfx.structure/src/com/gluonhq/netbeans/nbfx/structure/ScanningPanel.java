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
package com.gluonhq.netbeans.nbfx.structure;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import java.io.IOException;
import javafx.scene.Node;
import org.openide.filesystems.FileObject;

/**
 * The node of a Navigator panel that shows the result of a scan of the file in the global action
 * context, fed by a {@link DocumentTracker}: the tracker reads the file's current text, has the
 * panel {@linkplain #scan scan} it off the FX thread and {@linkplain #show shows} the result, then
 * keeps the row at the editor's caret selected.
 *
 * @param <R> the result of a scan
 * @since 1.0
 */
public interface ScanningPanel<R> {

    /** The panel's node, the content of the Navigator window while the panel is selected. */
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
     * Selects the row standing for {@code key} - what the tracker was {@linkplain DocumentTracker#pin
     * pinned} on - expanding to it, without taking the focus; clears the selection when the panel has
     * no such row. Nothing by default. FX thread.
     */
    default void select(Object key) {
    }
}
