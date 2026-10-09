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
package com.gluonhq.netbeans.nbfx.run;

import com.gluonhq.netbeans.nbfx.api.actions.FileContextMenuContributor;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildCommands;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.MenuItem;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Adds the Build / Clean / Test / Run items to the context menu of a project root (and any folder
 * that looks like a build project), acting on that folder's build tool.
 *
 * @since 1.0
 */
@ServiceProvider(service = FileContextMenuContributor.class)
public final class BuildContextMenuContributor implements FileContextMenuContributor {

    @Override
    public List<MenuItem> itemsFor(FileObject file) {
        return List.of();
    }

    @Override
    public List<MenuItem> itemsForFolders(List<FileObject> folders) {
        for (FileObject folder : folders) {
            File directory = folder == null ? null : FileUtil.toFile(folder);
            if (directory == null) {
                continue;
            }
            Path dir = directory.toPath();
            if (BuildTool.detect(dir) == BuildTool.UNKNOWN) {
                continue;
            }
            String base = folder.getName();
            List<MenuItem> items = new ArrayList<>(5);
            items.add(item("BuildCommand.build", dir, BuildCommands.BUILD, base));
            items.add(item("BuildCommand.cleanBuild", dir, BuildCommands.REBUILD, base));
            items.add(item("BuildCommand.clean", dir, BuildCommands.CLEAN, base));
            items.add(item("BuildCommand.test", dir, BuildCommands.TEST, base));
            items.add(item("BuildCommand.run", dir, BuildCommands.RUN, base));
            return items;
        }
        return List.of();
    }

    private MenuItem item(String key, Path dir, String command, String base) {
        String text = NbBundle.getMessage(BuildContextMenuContributor.class, key);
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> BuildRunner.run(dir, command, text + " " + base));
        return item;
    }
}
