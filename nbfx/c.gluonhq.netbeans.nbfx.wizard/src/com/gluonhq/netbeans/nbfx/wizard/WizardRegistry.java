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
package com.gluonhq.netbeans.nbfx.wizard;

import com.gluonhq.netbeans.nbfx.annotations.FxLayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Discovers the {@link FxWizard}s registered under {@code NbFx/Wizards} in the module layer.
 *
 * @since 1.0
 */
public final class WizardRegistry {

    private static final Logger LOG = Logger.getLogger(WizardRegistry.class.getName());

    private WizardRegistry() {
    }

    /**
     * Discovers and orders all registered wizards. Reads the configuration filesystem, so it must be
     * called once the platform is initialized.
     *
     * @return the wizard registrations, ordered by category, position and id
     */
    public static List<WizardRegistration> discover() {
        Lookup lookup = Lookups.forPath(FxLayer.WIZARDS);
        FileObject folder = FileUtil.getConfigFile(FxLayer.WIZARDS);
        return read(folder, lookup.lookupAll(FxWizard.class));
    }

    /** The registered wizard with {@code id}, or {@code null} if none. */
    public static FxWizard find(String id) {
        return discover().stream()
                .filter(registration -> registration.id().equals(id))
                .map(WizardRegistration::wizard)
                .findFirst()
                .orElse(null);
    }

    /** Reads the registrations of {@code wizards}, taking metadata from {@code folder}'s attributes. */
    static List<WizardRegistration> read(FileObject folder, Iterable<? extends FxWizard> wizards) {
        List<WizardRegistration> registrations = new ArrayList<>();
        for (FxWizard wizard : wizards) {
            String id = wizard.getId();
            FileObject file = folder == null ? null : folder.getFileObject(id, "instance");
            String displayName = stringAttribute(file, "displayName", wizard.getDisplayName());
            String iconName = stringAttribute(file, "iconName", "");
            String category = stringAttribute(file, "category", "");
            int position = intAttribute(file, "position", Integer.MAX_VALUE);
            registrations.add(new WizardRegistration(id, displayName, iconName, category, position, wizard));
        }
        return sort(registrations);
    }

    /** Orders wizards by category, then position, then id. */
    static List<WizardRegistration> sort(List<WizardRegistration> registrations) {
        List<WizardRegistration> sorted = new ArrayList<>(registrations);
        sorted.sort(Comparator
                .comparing(WizardRegistration::category)
                .thenComparingInt(WizardRegistration::position)
                .thenComparing(WizardRegistration::id));
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
