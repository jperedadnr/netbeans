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
package com.gluonhq.netbeans.nbfx.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a menu, so the window can build the set and order of its menus from the layer: a
 * top-level menu of the menu bar, or a submenu of any menu surface.
 * <p>
 * The folder {@code NbFx/<path>/<id>} is created with the declared {@code position} (and optional
 * display name); the menu's items are the {@link FxActionReference}s whose {@code path} is
 * {@code <path>/<id>}. With the default {@code path}, {@code Menus}, this is a top-level menu
 * ({@code NbFx/Menus/<id>}); with {@code path = "Menus/Navigate"} it is a submenu of the Navigate
 * menu, with {@code path = "ContextMenus/Editor"} a submenu of the editor's context menu, the way
 * NetBeans nests {@code Menu/} folders.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(FxMenuRegistrations.class)
public @interface FxMenuRegistration {

    /** The menu id, matching the {@code <path>/<id>} folder of its references. */
    String id();

    /**
     * The surface the menu belongs to: {@code Menus} (the default) for a top-level menu,
     * {@code Menus/<menu>} for a submenu of a menu, {@code ContextMenus/<context>} for a submenu
     * of a context menu.
     */
    String path() default "Menus";

    /** The menu's display text, or a {@code #key} bundle reference; empty uses the menu id. */
    String displayName() default "";

    /** The position of the menu among the others, from left to right or top to bottom. */
    int position() default Integer.MAX_VALUE;

    /** Whether a separator is drawn before this menu; only meaningful for a submenu. */
    boolean separatorBefore() default false;
}
