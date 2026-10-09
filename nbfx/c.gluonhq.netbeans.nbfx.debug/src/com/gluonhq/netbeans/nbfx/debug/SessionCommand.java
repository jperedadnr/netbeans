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
package com.gluonhq.netbeans.nbfx.debug;

import com.gluonhq.netbeans.nbfx.api.actions.Command;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * A debug session control command (Continue / Step / Stop). It is disabled while no debug session is
 * active and acts on the current session when run.
 *
 * @since 1.0
 */
final class SessionCommand implements Command {

    private final String id;
    private final String text;
    private final Consumer<DebugSession> action;
    private final ReadOnlyBooleanWrapper disabled = new ReadOnlyBooleanWrapper(true);

    SessionCommand(String id, String text, Consumer<DebugSession> action) {
        this.id = id;
        this.text = text;
        this.action = action;
        disabled.bind(DebugStatus.ACTIVE.not());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public boolean isDisabled() {
        return disabled.get();
    }

    @Override
    public ReadOnlyBooleanProperty disabledProperty() {
        return disabled.getReadOnlyProperty();
    }

    @Override
    public void run() {
        DebugSession session = DebugSession.current();
        if (session != null) {
            action.accept(session);
        }
    }
}
