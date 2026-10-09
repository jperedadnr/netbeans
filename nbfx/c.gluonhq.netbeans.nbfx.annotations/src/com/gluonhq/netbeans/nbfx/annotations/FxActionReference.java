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
 * References an already registered action from a UI surface: a menu, a tool bar or a context menu.
 * <p>
 * The reference is emitted into the generated layer under {@code NbFx/<path>/<id>.ref}, where
 * {@code path} is one of {@code Menus/<menu>}, {@code Toolbars/<bar>} or
 * {@code ContextMenus/<context>}. The action itself is supplied either by an
 * {@link FxActionRegistration} on the same element or by an action id published by an action
 * provider.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(FxActionReferences.class)
public @interface FxActionReference {

    /** The id of the action to reference (see {@link FxActionRegistration#id()}). */
    String id();

    /** The UI surface path, for example {@code "Menus/File"} or {@code "Toolbars/Editor"}. */
    String path();

    /** The position of the reference relative to its siblings in the same surface. */
    int position() default Integer.MAX_VALUE;

    /** Whether a separator is drawn before this reference. */
    boolean separatorBefore() default false;
}
