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
import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver;
import com.gluonhq.netbeans.nbfx.javanavigator.ui.GoToOpener;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.util.Subscription;
import org.openide.util.NbBundle;

/**
 * Navigate &#9656; Go to Source (Shift+Shortcut+B), Go to Declaration (Ctrl+Shift+G), Go to Super
 * Implementation (Shift+Shortcut+P) and Go to Implementation (Alt+Shortcut+B), also in the editor's
 * Navigate context submenu: opens the file declaring what the caret of the active Java document
 * stands for, at its declaration (see {@link GoToResolver.Kind}); Go to Implementation lists the
 * implementations in a popup at the caret first. Enabled while the active document is a Java file.
 */
final class GoToCommand extends AbstractCommand {

    static final KeyCombination SOURCE_SHORTCUT =
            new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    static final KeyCombination DECLARATION_SHORTCUT =
            new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    static final KeyCombination SUPER_IMPLEMENTATION_SHORTCUT =
            new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    static final KeyCombination IMPLEMENTATION_SHORTCUT =
            new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);

    private final GoToResolver.Kind kind;
    private final ObservableValue<EditorDocument> activeDocument;
    private final Subscription subscription;

    GoToCommand(GoToResolver.Kind kind, ObservableValue<EditorDocument> activeDocument) {
        super(idOf(kind), NbBundle.getMessage(GoToCommand.class, textKeyOf(kind)), shortcutOf(kind));
        this.kind = kind;
        this.activeDocument = activeDocument;
        this.subscription = activeDocument.subscribe(document -> setDisabled(!InspectMembersCommand.isJava(document)));
    }

    /** The command id of {@code kind}. */
    static String idOf(GoToResolver.Kind kind) {
        return switch (kind) {
            case SOURCE -> ActionIds.GO_TO_SOURCE;
            case DECLARATION -> ActionIds.GO_TO_DECLARATION;
            case SUPER_IMPLEMENTATION -> ActionIds.GO_TO_SUPER_IMPLEMENTATION;
            case IMPLEMENTATION -> ActionIds.GO_TO_IMPLEMENTATION;
        };
    }

    private static String textKeyOf(GoToResolver.Kind kind) {
        return switch (kind) {
            case SOURCE -> "CTL_GoToSourceCommand";
            case DECLARATION -> "CTL_GoToDeclarationCommand";
            case SUPER_IMPLEMENTATION -> "CTL_GoToSuperImplementationCommand";
            case IMPLEMENTATION -> "CTL_GoToImplementationCommand";
        };
    }

    private static KeyCombination shortcutOf(GoToResolver.Kind kind) {
        return switch (kind) {
            case SOURCE -> SOURCE_SHORTCUT;
            case DECLARATION -> DECLARATION_SHORTCUT;
            case SUPER_IMPLEMENTATION -> SUPER_IMPLEMENTATION_SHORTCUT;
            case IMPLEMENTATION -> IMPLEMENTATION_SHORTCUT;
        };
    }

    @Override
    public void run() {
        EditorDocument document = activeDocument.getValue();
        if (!InspectMembersCommand.isJava(document)) {
            return;
        }
        if (kind == GoToResolver.Kind.IMPLEMENTATION) {
            GoToOpener.goToImplementation(document.getFileObject(), document.getCaretOffset(),
                    document.getCaretScreenPosition(), getText());
        } else {
            GoToOpener.goTo(kind, document.getFileObject(), document.getCaretOffset(), getText());
        }
    }

    @Override
    public void dispose() {
        subscription.unsubscribe();
    }
}
