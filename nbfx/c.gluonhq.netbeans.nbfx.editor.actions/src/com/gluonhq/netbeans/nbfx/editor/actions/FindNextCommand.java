package com.gluonhq.netbeans.nbfx.editor.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;

/**
 * Edit ▸ Find Next (F3): moves the active editor document to the next match of its search.
 * Enabled whenever there is an active document.
 */
class FindNextCommand extends ActiveDocumentCommand {

    FindNextCommand(ObservableValue<EditorDocument> activeDocument) {
        super(ActionIds.FIND_NEXT, NbBundle.getMessage(FindNextCommand.class, "CTL_FindNextCommand"),
                new KeyCodeCombination(KeyCode.F3),
                activeDocument, doc -> ActiveDocumentCommand.ALWAYS, EditorDocument::findNext);
    }
}
