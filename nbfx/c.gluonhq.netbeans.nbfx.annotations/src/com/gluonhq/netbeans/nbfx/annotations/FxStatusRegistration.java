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
 * Registers a JavaFX status bar element.
 * <p>
 * The annotated class must be public, must have a public no-argument constructor and must implement
 * the nbfx status element SPI. It is published in the generated layer under
 * {@code NbFx/Status/<id>.instance} together with its alignment and position.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface FxStatusRegistration {

    /** The stable identifier of the status element, unique across the application. */
    String id();

    /** The horizontal slot the element occupies. */
    FxStatusAlignment alignment() default FxStatusAlignment.LEFT;

    /** The position of the element relative to the other elements in the same slot. */
    int position() default 0;
}
