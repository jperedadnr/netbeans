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
package com.gluonhq.netbeans.nbfx.options;

import com.gluonhq.netbeans.nbfx.annotations.FxLayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Discovers the {@link FxOptionsPanel}s registered under {@code NbFx/Options} in the module layer.
 *
 * @since 1.0
 */
public final class OptionsRegistry {

    private OptionsRegistry() {
    }

    /**
     * Discovers and orders all registered options panels. Reads the configuration filesystem, so it
     * must be called once the platform is initialized.
     *
     * @return the panel registrations, ordered by position then id
     */
    public static List<OptionsRegistration> discover() {
        Lookup lookup = Lookups.forPath(FxLayer.OPTIONS);
        FileObject folder = FileUtil.getConfigFile(FxLayer.OPTIONS);
        return read(folder, lookup.lookupAll(FxOptionsPanel.class));
    }

    /** Reads the registrations of {@code panels}, taking metadata from {@code folder}'s attributes. */
    static List<OptionsRegistration> read(FileObject folder, Iterable<? extends FxOptionsPanel> panels) {
        List<OptionsRegistration> registrations = new ArrayList<>();
        for (FxOptionsPanel panel : panels) {
            String id = panel.getId();
            FileObject file = folder == null ? null : folder.getFileObject(id, "instance");
            String displayName = stringAttribute(file, "displayName", panel.getDisplayName());
            int position = intAttribute(file, "position", panel.getPosition());
            registrations.add(new OptionsRegistration(id, displayName, panel.getCategory(), position, panel));
        }
        return sort(registrations);
    }

    /** Orders panels by position, then id. */
    static List<OptionsRegistration> sort(List<OptionsRegistration> registrations) {
        List<OptionsRegistration> sorted = new ArrayList<>(registrations);
        sorted.sort(Comparator
                .comparingInt(OptionsRegistration::position)
                .thenComparing(OptionsRegistration::id));
        return List.copyOf(sorted);
    }

    private static String stringAttribute(FileObject file, String name, String fallback) {
        Object value = file == null ? null : file.getAttribute(name);
        return value instanceof String text ? text : fallback;
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        Object value = file == null ? null : file.getAttribute(name);
        return value instanceof Integer number ? number : fallback;
    }
}
