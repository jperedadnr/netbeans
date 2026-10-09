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
package com.gluonhq.netbeans.nbfx.javanavigator.actions;

import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.GO_TO_DECLARATION;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.GO_TO_IMPLEMENTATION;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.GO_TO_SOURCE;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.GO_TO_SUPER_IMPLEMENTATION;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.INSPECT_MEMBERS;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;
import com.gluonhq.netbeans.nbfx.annotations.FxMenuRegistration;

/**
 * Declares the Navigate submenu of the code editor's context menu in the layer, as NetBeans' editor
 * popup has it: the Go to commands, then Inspect Members. The processor writes the
 * {@code NbFx/ContextMenus/Editor/Navigate} folder and its {@code *.ref} entries, which the editor
 * reads each time the menu opens.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxMenuRegistration(id = "Navigate", path = "ContextMenus/Editor", displayName = "#MENU_Navigate",
        position = 100, separatorBefore = true)
@FxActionReference(id = GO_TO_SOURCE, path = "ContextMenus/Editor/Navigate", position = 10)
@FxActionReference(id = GO_TO_DECLARATION, path = "ContextMenus/Editor/Navigate", position = 20)
@FxActionReference(id = GO_TO_SUPER_IMPLEMENTATION, path = "ContextMenus/Editor/Navigate", position = 30)
@FxActionReference(id = GO_TO_IMPLEMENTATION, path = "ContextMenus/Editor/Navigate", position = 40)
@FxActionReference(id = INSPECT_MEMBERS, path = "ContextMenus/Editor/Navigate", position = 50, separatorBefore = true)
final class NavigatorContextMenuRegistrations {

    private NavigatorContextMenuRegistrations() {
    }
}
