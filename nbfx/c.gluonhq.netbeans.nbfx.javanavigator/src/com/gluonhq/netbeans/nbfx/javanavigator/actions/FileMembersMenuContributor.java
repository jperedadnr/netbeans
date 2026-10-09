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
package com.gluonhq.netbeans.nbfx.javanavigator.actions;

import com.gluonhq.netbeans.nbfx.api.actions.FileContextMenuContributor;
import com.gluonhq.netbeans.nbfx.javanavigator.ui.NavigatorViewProvider;
import java.util.List;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * "File Members" on a Java file node of the Projects / Files views: shows the members of the file's
 * main type in the Navigator, as Navigate &#9656; Inspect &#9656; File Members does for the editor.
 * Placed after the Find Usages item, in its own section.
 */
@ServiceProvider(service = FileContextMenuContributor.class, position = 200)
public final class FileMembersMenuContributor implements FileContextMenuContributor {

    @Override
    public List<MenuItem> itemsFor(FileObject file) {
        if (file == null || !"java".equalsIgnoreCase(file.getExt())) {
            return List.of();
        }
        MenuItem item = new MenuItem(NbBundle.getMessage(FileMembersMenuContributor.class, "CTL_FileMembers"));
        // Shown for consistency with the menu's item; the key itself is bound to the editor command.
        item.setAccelerator(InspectMembersCommand.FILE_MEMBERS_SHORTCUT);
        item.setOnAction(e -> {
            NavigatorViewProvider navigator = NavigatorViewProvider.instance();
            if (navigator != null) {
                navigator.inspectFile(file);
            }
        });
        return List.of(new SeparatorMenuItem(), item);
    }
}
