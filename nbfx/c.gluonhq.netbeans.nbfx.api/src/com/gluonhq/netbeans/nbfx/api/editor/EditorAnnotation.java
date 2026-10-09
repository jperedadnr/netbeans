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

import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;

/**
 * A glyph in the editor's gutter at a line - NetBeans' editor annotations, such as the badges of
 * a method that overrides or is overridden - with a tooltip and an action for a click on it.
 *
 * @param line    the paragraph index the glyph marks
 * @param icon    the glyph, shown at 16 pixels
 * @param tooltip what the glyph says when hovered, or {@code null}
 * @param action  what a click on the glyph does, given where the glyph is in screen coordinates
 *                (for a popup); {@code null} for a glyph that is only shown
 */
public record EditorAnnotation(int line, Image icon, String tooltip, Consumer<Point2D> action) {

    public EditorAnnotation {
        Objects.requireNonNull(icon, "icon");
        if (line < 0) {
            throw new IllegalArgumentException("line out of bounds: " + line);
        }
    }
}
