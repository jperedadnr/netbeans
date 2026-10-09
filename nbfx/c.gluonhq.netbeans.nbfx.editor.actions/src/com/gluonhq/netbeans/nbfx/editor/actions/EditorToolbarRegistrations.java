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
package com.gluonhq.netbeans.nbfx.editor.actions;

import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.COPY;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.CUT;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.FIND;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.PASTE;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.REDO;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.SAVE;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.SAVE_ALL;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.UNDO;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;
import com.gluonhq.netbeans.nbfx.annotations.FxToolbarRegistration;

/**
 * Declares the Clipboard and Edit tool bars and their editor entries in the layer, and the save
 * entries of the File tool bar (whose bar itself is declared by the project module).
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxToolbarRegistration(id = "clipboard", position = 20)
@FxToolbarRegistration(id = "edit", position = 30)
@FxActionReference(id = SAVE, path = "Toolbars/file", position = 30)
@FxActionReference(id = SAVE_ALL, path = "Toolbars/file", position = 40)
@FxActionReference(id = CUT, path = "Toolbars/clipboard", position = 10)
@FxActionReference(id = COPY, path = "Toolbars/clipboard", position = 20)
@FxActionReference(id = PASTE, path = "Toolbars/clipboard", position = 30)
@FxActionReference(id = FIND, path = "Toolbars/clipboard", position = 40)
@FxActionReference(id = UNDO, path = "Toolbars/edit", position = 10)
@FxActionReference(id = REDO, path = "Toolbars/edit", position = 20)
final class EditorToolbarRegistrations {

    private EditorToolbarRegistrations() {
    }
}
