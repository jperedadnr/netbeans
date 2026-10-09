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
package com.gluonhq.netbeans.nbfx.debug;

import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.CaretInfo;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/** Debug ▸ Debug File: starts a debug session for the active editor's main class. */
final class DebugFileCommand extends AbstractCommand {

    DebugFileCommand() {
        super(ActionIds.DEBUG_FILE,
                NbBundle.getMessage(DebugFileCommand.class, "CTL_DebugFileCommand"), null, false);
    }

    @Override
    public void run() {
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        EditorDocument document = context == null ? null : context.getActiveDocument();
        FileObject file = document == null ? null : document.getFileObject();
        if (file == null) {
            ErrorReporter.report(message("Debug.title"), null, message("Debug.noJavaFile"));
            return;
        }
        if (!DebugLauncher.isRunnableJavaFile(file)) {
            ErrorReporter.report(message("Debug.title"), null, message("Debug.noMain"));
            return;
        }
        CaretInfo caret = document.caretInfoProperty().getValue();
        LaunchRequest request = DebugLauncher.resolve(file, caret == null ? 0 : caret.row());
        if (request == null) {
            ErrorReporter.report(message("Debug.title"), null, message("Debug.notJavaProject"));
            return;
        }
        try {
            DebugSession.launch(request);
            DebugViewProvider view = DebugViewProvider.instance();
            if (view != null) {
                view.show();
            }
        } catch (Exception ex) {
            ErrorReporter.report(message("Debug.title"), message("Debug.launchFailed"), ex.getMessage(), ex);
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(DebugFileCommand.class, key);
    }
}
