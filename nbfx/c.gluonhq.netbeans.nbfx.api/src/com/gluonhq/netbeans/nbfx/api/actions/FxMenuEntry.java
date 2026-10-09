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

/**
 * An entry of a menu surface as read from the layer: an {@link FxActionRef action reference} or an
 * {@link FxSubmenu submenu}, the nested folder NetBeans menus are built from. Entries are ordered by
 * {@link #position()} within their folder.
 *
 * @since 1.0
 */
public sealed interface FxMenuEntry permits FxActionRef, FxSubmenu {

    /** The position among the siblings of the same surface. */
    int position();

    /** Whether a separator is drawn before this entry. */
    boolean separatorBefore();
}
