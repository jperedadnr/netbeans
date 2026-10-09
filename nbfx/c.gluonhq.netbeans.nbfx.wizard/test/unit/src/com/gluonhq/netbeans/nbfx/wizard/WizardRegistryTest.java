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

import java.io.IOException;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

/**
 * Verifies that wizards are read from a layer folder with their metadata and ordered by category,
 * position and id, falling back to the wizard's own defaults.
 *
 * @since 1.0
 */
public class WizardRegistryTest {

    @Test
    public void readsAttributesAndOrders() throws IOException {
        FileObject folder = wizardFolder();
        FileObject bravo = folder.createData("bravo", "instance");
        bravo.setAttribute("displayName", "Bravo");
        bravo.setAttribute("category", "Project");
        bravo.setAttribute("position", 20);
        FileObject alpha = folder.createData("alpha", "instance");
        alpha.setAttribute("displayName", "Alpha");
        alpha.setAttribute("category", "Project");
        alpha.setAttribute("position", 10);

        List<WizardRegistration> registrations =
                WizardRegistry.read(folder, List.of(wizard("bravo"), wizard("alpha")));

        assertEquals(List.of("alpha", "bravo"),
                registrations.stream().map(WizardRegistration::id).toList());
        assertEquals("Alpha", registrations.get(0).displayName());
    }

    @Test
    public void fallsBackToWizardDefaults() throws IOException {
        FileObject folder = wizardFolder();

        List<WizardRegistration> registrations = WizardRegistry.read(folder, List.of(wizard("x")));

        assertEquals("x", registrations.get(0).id());
        assertEquals("x", registrations.get(0).displayName());
        assertEquals("", registrations.get(0).category());
    }

    private static FileObject wizardFolder() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return FileUtil.createFolder(fs.getRoot(), "NbFx/Wizards");
    }

    private static FxWizard wizard(String id) {
        return new FxWizard() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return id;
            }

            @Override
            public List<FxWizardPanel> getPages() {
                return List.of();
            }

            @Override
            public void finish() {
            }
        };
    }
}
