package com.gluonhq.netbeans.nbfx.editor.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;

/**
 * Edit ▸ Find... (Shortcut+F): shows the Find bar of the active editor document, seeded with its
 * selection. Enabled whenever there is an active document.
 */
class FindCommand extends ActiveDocumentCommand {

    FindCommand(ObservableValue<EditorDocument> activeDocument) {
        super(ActionIds.FIND, NbBundle.getMessage(FindCommand.class, "CTL_FindCommand"),
                new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                activeDocument, doc -> ActiveDocumentCommand.ALWAYS, EditorDocument::showFind);
    }
}
