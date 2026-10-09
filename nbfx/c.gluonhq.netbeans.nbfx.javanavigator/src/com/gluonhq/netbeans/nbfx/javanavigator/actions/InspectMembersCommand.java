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

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.javanavigator.ui.NavigatorViewProvider;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.util.Subscription;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * Navigate &#9656; Inspect &#9656; Members (Shift+Shortcut+F12) and Navigate &#9656; Inspect &#9656;
 * File Members (Shortcut+F12): shows in the Navigator the members of the type at the caret of the
 * active Java document, or of the document's main type, and records it in the Inspect Members
 * history. Enabled while the active document is a Java file.
 */
final class InspectMembersCommand extends AbstractCommand {

    static final KeyCombination MEMBERS_SHORTCUT =
            new KeyCodeCombination(KeyCode.F12, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    static final KeyCombination FILE_MEMBERS_SHORTCUT =
            new KeyCodeCombination(KeyCode.F12, KeyCombination.SHORTCUT_DOWN);

    private final ObservableValue<EditorDocument> activeDocument;
    private final boolean atCaret;
    private final Subscription subscription;

    /** @param atCaret the type at the caret ({@code true}) or the file's main type */
    InspectMembersCommand(ObservableValue<EditorDocument> activeDocument, boolean atCaret) {
        super(atCaret ? ActionIds.INSPECT_MEMBERS : ActionIds.INSPECT_FILE_MEMBERS,
                NbBundle.getMessage(InspectMembersCommand.class,
                        atCaret ? "CTL_InspectMembersCommand" : "CTL_InspectFileMembersCommand"),
                atCaret ? MEMBERS_SHORTCUT : FILE_MEMBERS_SHORTCUT);
        this.activeDocument = activeDocument;
        this.atCaret = atCaret;
        this.subscription = activeDocument.subscribe(document -> setDisabled(!isJava(document)));
    }

    static boolean isJava(EditorDocument document) {
        FileObject file = document == null ? null : document.getFileObject();
        return file != null && "java".equalsIgnoreCase(file.getExt());
    }

    @Override
    public void run() {
        EditorDocument document = activeDocument.getValue();
        NavigatorViewProvider navigator = NavigatorViewProvider.instance();
        if (!isJava(document) || navigator == null) {
            return;
        }
        if (atCaret) {
            navigator.inspectAt(document.getFileObject(), document.getCaretOffset());
        } else {
            navigator.inspectFile(document.getFileObject());
        }
    }

    @Override
    public void dispose() {
        subscription.unsubscribe();
    }
}
