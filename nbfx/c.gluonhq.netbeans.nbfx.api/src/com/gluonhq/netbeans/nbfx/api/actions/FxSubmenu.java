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
package com.gluonhq.netbeans.nbfx.api.actions;

import java.util.List;
import java.util.Objects;

/**
 * A submenu of a menu surface, as read from a nested folder of the layer (declared with
 * {@code @FxMenuRegistration(path = ...)}): its own entries are the references and submenus the
 * folder holds.
 *
 * @param id              the submenu id (the folder name)
 * @param displayName     the submenu's title
 * @param position        the position among the siblings of the same surface
 * @param separatorBefore whether a separator is drawn before this submenu
 * @param entries         the submenu's entries, in order
 * @since 1.0
 */
public record FxSubmenu(String id, String displayName, int position, boolean separatorBefore,
        List<FxMenuEntry> entries) implements FxMenuEntry {

    public FxSubmenu {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        entries = List.copyOf(entries);
    }
}
