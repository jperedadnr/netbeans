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
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.PASTE;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;

/**
 * Declares the base entries of the code editor's context menu in the layer, so the menu is
 * assembled from registrations. The processor turns each reference into an
 * {@code NbFx/ContextMenus/Editor/*.ref} entry that the editor reads.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxActionReference(id = CUT, path = "ContextMenus/Editor", position = 10)
@FxActionReference(id = COPY, path = "ContextMenus/Editor", position = 20)
@FxActionReference(id = PASTE, path = "ContextMenus/Editor", position = 30)
final class EditorContextMenuRegistrations {

    private EditorContextMenuRegistrations() {
    }
}
