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
package com.gluonhq.netbeans.nbfx.findusages.actions;

import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.FIND_USAGES;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;

/**
 * Declares Find Usages' entry in the code editor's context menu in the layer: a section of its own,
 * after the clipboard and edit sections. The processor writes
 * {@code NbFx/ContextMenus/Editor/findUsages.ref}.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxActionReference(id = FIND_USAGES, path = "ContextMenus/Editor", position = 140, separatorBefore = true)
final class FindUsagesContextMenuRegistrations {

    private FindUsagesContextMenuRegistrations() {
    }
}
