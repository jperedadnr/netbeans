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
 * Registers a sub-panel inside a top-level JavaFX options category (a tab in the category).
 * <p>
 * The annotated class must be public, must have a public no-argument constructor and must implement
 * the nbfx options panel SPI. It is published in the generated layer under
 * {@code NbFx/Options/<location>/<id>.instance}.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface FxOptionsSubRegistration {

    /** The stable identifier of the sub-panel. */
    String id();

    /** The id of the top-level category this sub-panel belongs to (see {@link FxOptionsRegistration#id()}). */
    String location();

    /** The display name of the sub-panel; may be a {@code #key} bundle reference. */
    String displayName();

    /** Comma-separated keywords for the options Quick Search. */
    String keywords() default "";

    /** The position of the sub-panel relative to its siblings. */
    int position() default Integer.MAX_VALUE;
}
