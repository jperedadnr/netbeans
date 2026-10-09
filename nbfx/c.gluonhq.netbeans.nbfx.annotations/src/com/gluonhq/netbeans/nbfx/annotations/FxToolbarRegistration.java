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
 * Declares a tool bar, so the window can build the set and order of its tool bars from the layer.
 * <p>
 * The folder {@code NbFx/Toolbars/<id>} is created with the declared {@code position} (and optional
 * display name); the tool bar's buttons are the {@link FxActionReference}s whose {@code path} is
 * {@code Toolbars/<id>}.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(FxToolbarRegistrations.class)
public @interface FxToolbarRegistration {

    /** The tool bar id, matching the {@code Toolbars/<id>} folder of its references. */
    String id();

    /** The tool bar's display name, shown in the View ▸ Toolbars submenu; may be a bundle key. */
    String displayName() default "";

    /** The position of the tool bar among the others, from left to right. */
    int position() default Integer.MAX_VALUE;
}
