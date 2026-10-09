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
package com.gluonhq.netbeans.nbfx.output;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default {@link FxOutput}: a concurrent map of consoles and an observable, ordered view of them
 * for the output view.
 *
 * @since 1.0
 */
@ServiceProvider(service = FxOutput.class)
public final class FxOutputImpl implements FxOutput {

    private final Map<String, ConsoleModel> consoles = new ConcurrentHashMap<>();
    private final ObservableList<ConsoleModel> ordered = FXCollections.observableArrayList();

    @Override
    public FxConsole console(String name) {
        ConsoleModel model = consoles.computeIfAbsent(name, consoleName -> {
            ConsoleModel created = new ConsoleModel(consoleName);
            FxThread.run(() -> ordered.add(created));
            return created;
        });
        showOutputView();
        return model;
    }

    /**
     * Brings the Output view on screen. Called whenever a console is created, so writing to the
     * output always makes it visible, no matter which activity is writing.
     */
    private void showOutputView() {
        FxThread.run(() -> {
            ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
            OutputViewProvider view = OutputViewProvider.instance();
            if (manager != null && view != null) {
                manager.show(view);
            }
        });
    }

    /** The consoles in the order they were created, for the output view. */
    ObservableList<ConsoleModel> consoles() {
        return ordered;
    }
}
