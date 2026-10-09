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

import java.util.Objects;

/**
 * A reference to an action in a UI surface (a menu, a tool bar or a context menu), as read from the
 * layer. The referenced action is supplied by the {@link ActionRegistry} under
 * {@link #actionId()}.
 *
 * @param actionId        the id of the referenced action
 * @param position        the position among the siblings of the same surface
 * @param separatorBefore whether a separator is drawn before this entry
 * @since 1.0
 */
public record FxActionRef(String actionId, int position, boolean separatorBefore) {

    public FxActionRef {
        Objects.requireNonNull(actionId, "actionId");
    }
}
