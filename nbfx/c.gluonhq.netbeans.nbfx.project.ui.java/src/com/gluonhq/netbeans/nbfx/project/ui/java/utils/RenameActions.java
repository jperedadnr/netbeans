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
package com.gluonhq.netbeans.nbfx.project.ui.java.utils;

import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import java.io.IOException;
import javafx.scene.control.TextInputDialog;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * Renames a file or folder from the navigator: prompts for the new name and renames the backing
 * {@link FileObject}. The tree updates through the file-change listener.
 *
 * @since 1.0
 */
public final class RenameActions {

    private RenameActions() {
    }

    public static void rename(FileObject file) {
        if (file == null) {
            return;
        }
        String current = file.getNameExt();
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle(message("Rename.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(message("Rename.prompt"));
        dialog.showAndWait()
                .map(String::trim)
                .filter(name -> !name.isEmpty() && !name.equals(current))
                .ifPresent(name -> apply(file, name));
    }

    private static void apply(FileObject file, String newName) {
        String name = newName;
        String ext = "";
        if (!file.isFolder()) {
            int dot = newName.lastIndexOf('.');
            if (dot > 0) {
                name = newName.substring(0, dot);
                ext = newName.substring(dot + 1);
            }
        }
        FileLock lock = null;
        try {
            lock = file.lock();
            file.rename(lock, name, ext);
        } catch (IOException ex) {
            ErrorReporter.report(message("Rename.title"), message("Rename.error"), ex.getMessage(), ex);
        } finally {
            if (lock != null) {
                lock.releaseLock();
            }
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(RenameActions.class, key);
    }
}
