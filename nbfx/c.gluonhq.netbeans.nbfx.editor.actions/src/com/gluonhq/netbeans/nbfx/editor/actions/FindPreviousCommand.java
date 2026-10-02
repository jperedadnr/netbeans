package com.gluonhq.netbeans.nbfx.editor.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;

/**
 * Edit ▸ Find Previous (Shift+F3): moves the active editor document to the previous match of its
 * search. Enabled whenever there is an active document.
 */
class FindPreviousCommand extends ActiveDocumentCommand {

    FindPreviousCommand(ObservableValue<EditorDocument> activeDocument) {
        super(ActionIds.FIND_PREVIOUS, NbBundle.getMessage(FindPreviousCommand.class, "CTL_FindPreviousCommand"),
                new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN),
                activeDocument, doc -> ActiveDocumentCommand.ALWAYS, EditorDocument::findPrevious);
    }
}
