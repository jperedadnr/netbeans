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
package com.gluonhq.netbeans.nbfx.statusbar;

import com.gluonhq.netbeans.nbfx.annotations.FxStatusAlignment;
import java.util.Objects;

/**
 * A discovered status bar element: its registration metadata plus the element itself.
 *
 * @param id        the element id (the layer file's base name)
 * @param alignment the slot the element is placed in
 * @param position  the position within the slot
 * @param element   the element
 * @since 1.0
 */
public record StatusElement(String id, FxStatusAlignment alignment, int position, FxStatusElement element) {

    public StatusElement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(element, "element");
    }
}
