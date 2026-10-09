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
package com.gluonhq.netbeans.nbfx.actions;

import com.gluonhq.netbeans.nbfx.api.actions.KeyMap;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

/**
 * The {@link KeyMap} implementation, persisting the user's overrides in {@link NbPreferences} under
 * {@code keymap}. An empty stored value records that the user removed the shortcut; a missing key
 * means "no override, use the command's default".
 *
 * @since 1.0
 */
@ServiceProvider(service = KeyMap.class)
public final class KeyMapImpl implements KeyMap {

    private final Preferences prefs = NbPreferences.forModule(KeyMapImpl.class).node("keymap");

    @Override
    public KeyCombination accelerator(String actionId, KeyCombination defaultAccelerator) {
        if (actionId == null) {
            return defaultAccelerator;
        }
        String stored = prefs.get(actionId, null);
        if (stored == null) {
            return defaultAccelerator;
        }
        if (stored.isEmpty()) {
            return null;
        }
        try {
            return KeyCombination.keyCombination(stored);
        } catch (RuntimeException ex) {
            return defaultAccelerator;
        }
    }

    @Override
    public void setAccelerator(String actionId, KeyCombination accelerator) {
        if (actionId != null) {
            prefs.put(actionId, accelerator == null ? "" : accelerator.getName());
        }
    }

    @Override
    public void reset(String actionId) {
        if (actionId != null) {
            prefs.remove(actionId);
        }
    }

    @Override
    public Map<String, KeyCombination> overrides() {
        Map<String, KeyCombination> map = new HashMap<>();
        try {
            for (String key : prefs.keys()) {
                String value = prefs.get(key, "");
                map.put(key, value.isEmpty() ? null : KeyCombination.keyCombination(value));
            }
        } catch (BackingStoreException ex) {
            // ignore; treat as no overrides
        }
        return map;
    }
}
