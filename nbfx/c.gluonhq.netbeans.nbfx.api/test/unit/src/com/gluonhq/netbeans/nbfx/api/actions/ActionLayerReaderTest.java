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

import java.io.IOException;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

/**
 * Verifies that action references are read from a layer folder and ordered by position then id, and
 * that malformed or unrelated entries are ignored.
 *
 * @since 1.0
 */
public class ActionLayerReaderTest {

    @Test
    public void readsAndOrdersReferences() throws IOException {
        FileObject menu = menuFolder();

        addRef(menu, "run", "run", 30, false);
        addRef(menu, "save", "save", 10, false);
        addRef(menu, "saveAs", "save-as", 20, true);
        // Unrelated data file without a .ref extension.
        menu.createData("notes", "txt");
        // A .ref file without an actionId is ignored.
        menu.createData("broken", "ref");

        List<FxActionRef> refs = ActionLayerReader.read(menu);

        assertEquals(List.of("save", "save-as", "run"),
                refs.stream().map(FxActionRef::actionId).toList());
        assertFalse(refs.get(0).separatorBefore());
        assertTrue(refs.get(1).separatorBefore());
    }

    @Test
    public void breaksPositionTiesById() throws IOException {
        FileObject menu = menuFolder();
        addRef(menu, "z", "z", 1, false);
        addRef(menu, "a", "a", 1, false);

        List<FxActionRef> refs = ActionLayerReader.read(menu);

        assertEquals(List.of("a", "z"), refs.stream().map(FxActionRef::actionId).toList());
    }

    @Test
    public void nullFolderYieldsEmptyList() {
        assertEquals(List.of(), ActionLayerReader.read((FileObject) null));
    }

    private static FileObject menuFolder() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return FileUtil.createFolder(fs.getRoot(), "NbFx/Menus/File");
    }

    private static void addRef(FileObject folder, String name, String actionId, int position,
            boolean separatorBefore) throws IOException {
        FileObject file = folder.createData(name, "ref");
        file.setAttribute("actionId", actionId);
        file.setAttribute("position", position);
        file.setAttribute("separatorBefore", separatorBefore);
    }
}
