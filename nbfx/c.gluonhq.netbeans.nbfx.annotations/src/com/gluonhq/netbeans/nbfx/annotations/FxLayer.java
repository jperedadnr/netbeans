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

/**
 * The layer folders the nbfx annotation processors write to and the JavaFX frontend reads from.
 * <p>
 * These paths are the nbfx equivalent of NetBeans' {@code Actions/}, {@code Menu/} and
 * {@code Projects/} folders: behaviour is declared once, with annotations, and every UI surface
 * builds itself from the generated layer.
 *
 * @since 1.0
 */
public final class FxLayer {

    /** Registered action instances, by id. */
    public static final String ACTIONS = "NbFx/Actions";

    /** Menu references, under {@code NbFx/Menus/<menu>}. */
    public static final String MENUS = "NbFx/Menus";

    /** Tool bar references, under {@code NbFx/Toolbars/<bar>}. */
    public static final String TOOLBARS = "NbFx/Toolbars";

    /** Context menu references, under {@code NbFx/ContextMenus/<context>}. */
    public static final String CONTEXT_MENUS = "NbFx/ContextMenus";

    /** Registered tool views, by id. */
    public static final String VIEWS = "NbFx/Views";

    /** Registered status bar elements, by id. */
    public static final String STATUS = "NbFx/Status";

    /** Registered property editors, by value type. */
    public static final String PROPERTY_EDITORS = "NbFx/PropertyEditors";

    /** Registered options panels, under {@code NbFx/Options/<category>/<sub>}. */
    public static final String OPTIONS = "NbFx/Options";

    /** Registered project type providers, under {@code NbFx/Projects/<type>}. */
    public static final String PROJECTS = "NbFx/Projects";

    /** Registered wizards, by id. */
    public static final String WIZARDS = "NbFx/Wizards";

    /** The file-name suffix used for action references in the menu/tool bar folders. */
    public static final String REF_SUFFIX = ".ref";

    private FxLayer() {
    }
}
