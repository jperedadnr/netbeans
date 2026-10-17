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
package com.gluonhq.netbeans.nbfx.structure.java.ui;

import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.TrackedStructurePanel;
import java.util.Objects;
import javafx.scene.Parent;
import org.openide.filesystems.FileObject;

/**
 * A Navigator panel for Java files ({@code text/x-java}): a {@link TrackedStructurePanel} whose
 * node carries the Java navigator's stylesheet.
 *
 * @param <R> the result of the panel's scan
 */
abstract class JavaStructurePanel<R> extends TrackedStructurePanel<R> {

    @Override
    protected void uiCreated(ScanningPanel<R> ui) {
        if (ui.getNode() instanceof Parent parent) {
            parent.getStylesheets().add(Objects.requireNonNull(
                    JavaStructurePanel.class.getResource("navigator.css")).toExternalForm());
        }
    }

    @Override
    public String[] contentTypes() {
        return new String[] {"text/x-java"};
    }

    @Override
    protected boolean accepts(FileObject file) {
        return file != null && "java".equalsIgnoreCase(file.getExt());
    }
}
