package com.gluonhq.netbeans.nbfx.editor.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;

/**
 * Edit ▸ Find Selection (Shortcut+F3): searches the active editor document for its selection, or
 * the identifier at its caret, and moves to the next occurrence. Enabled whenever there is an
 * active document.
 */
class FindSelectionCommand extends ActiveDocumentCommand {

    FindSelectionCommand(ObservableValue<EditorDocument> activeDocument) {
        super(ActionIds.FIND_SELECTION, NbBundle.getMessage(FindSelectionCommand.class, "CTL_FindSelectionCommand"),
                new KeyCodeCombination(KeyCode.F3, KeyCombination.SHORTCUT_DOWN),
                activeDocument, doc -> ActiveDocumentCommand.ALWAYS, EditorDocument::findSelection);
    }
}
