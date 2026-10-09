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
package com.gluonhq.netbeans.nbfx.windows;

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import java.io.IOException;
import java.util.List;
import javafx.scene.Node;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

/**
 * Verifies that views are read from a layer folder with their placement attributes and ordered by
 * location, position and id, falling back to the view's own defaults.
 *
 * @since 1.0
 */
public class ViewRegistryTest {

    @Test
    public void readsAttributesAndOrders() throws IOException {
        FileObject folder = viewFolder();
        FileObject zeta = folder.createData("zeta", "instance");
        zeta.setAttribute("displayName", "Zeta");
        zeta.setAttribute("location", "RIGHT");
        zeta.setAttribute("position", 20);
        FileObject alpha = folder.createData("alpha", "instance");
        alpha.setAttribute("displayName", "Alpha");
        alpha.setAttribute("location", "LEFT");
        alpha.setAttribute("position", 10);
        alpha.setAttribute("navigator", true);

        List<ViewRegistration> registrations =
                ViewRegistry.read(folder, List.of(view("zeta"), view("alpha")));

        assertEquals(List.of("alpha", "zeta"),
                registrations.stream().map(ViewRegistration::id).toList());
        assertEquals(FxViewLocation.LEFT, registrations.get(0).location());
        assertTrue(registrations.get(0).navigator());
        assertEquals("Zeta", registrations.get(1).displayName());
        assertEquals(FxViewLocation.RIGHT, registrations.get(1).location());
        assertFalse(registrations.get(1).navigator());
    }

    @Test
    public void fallsBackToViewDefaults() throws IOException {
        FileObject folder = viewFolder();

        List<ViewRegistration> registrations = ViewRegistry.read(folder, List.of(view("v")));

        assertEquals("v", registrations.get(0).id());
        assertEquals("v", registrations.get(0).displayName());
        assertEquals(FxViewLocation.LEFT, registrations.get(0).location());
    }

    private static FileObject viewFolder() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return FileUtil.createFolder(fs.getRoot(), "NbFx/Views");
    }

    private static ViewProvider view(String id) {
        return new ViewProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getTitle() {
                return id;
            }

            @Override
            public Node getView() {
                return null;
            }
        };
    }
}
