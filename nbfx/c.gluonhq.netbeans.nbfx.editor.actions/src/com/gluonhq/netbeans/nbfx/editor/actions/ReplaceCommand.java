package com.gluonhq.netbeans.nbfx.editor.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;

/**
 * Edit ▸ Replace... (Shortcut+R): shows the Find and Replace bars of the active editor document,
 * seeded with its selection. Enabled while the active document is editable.
 */
class ReplaceCommand extends ActiveDocumentCommand {

    ReplaceCommand(ObservableValue<EditorDocument> activeDocument) {
        super(ActionIds.REPLACE, NbBundle.getMessage(ReplaceCommand.class, "CTL_ReplaceCommand"),
                new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN),
                activeDocument, EditorDocument::editableProperty, EditorDocument::showReplace);
    }
}
