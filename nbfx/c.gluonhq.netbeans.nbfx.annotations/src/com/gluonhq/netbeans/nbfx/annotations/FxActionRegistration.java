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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a JavaFX action implemented by the annotated class.
 * <p>
 * The annotated class must be public, must have a public no-argument constructor and should be an
 * action body that the JavaFX frontend can run (for example a {@link java.lang.Runnable}). It is
 * published in the generated layer under {@code NbFx/Actions/<id>.instance} together with its
 * display metadata, and can then be referenced from menus, tool bars and context menus with
 * {@link FxActionReference}.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FxActionRegistration {

    /** The stable identifier of the action, unique across the application. */
    String id();

    /** The display name shown for the action; may be a {@code #key} bundle reference. */
    String displayName() default "";

    /** The name of the icon resource shown for the action, or an empty string for none. */
    String iconName() default "";

    /** The keyboard accelerator, in the form {@code "Shortcut+S"} or {@code "Ctrl+Shift+F"}; empty for none. */
    String accelerator() default "";

    /** The position of the action relative to other actions registered in the same folder. */
    int position() default Integer.MAX_VALUE;
}
