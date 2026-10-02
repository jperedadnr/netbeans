package com.gluonhq.netbeans.nbfx.findinprojects.actions;

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.SearchViewProvider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;

/** Window ▸ Search Results (Shortcut+4, next to Usages' Shortcut+3): opens or fronts the Search view. Always enabled. */
final class SelectSearchResultsCommand extends AbstractCommand {

    SelectSearchResultsCommand() {
        super(ActionIds.SELECT_SEARCH_RESULTS,
                NbBundle.getMessage(SelectSearchResultsCommand.class, "CTL_SelectSearchResultsCommand"),
                new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.SHORTCUT_DOWN), false);
    }

    @Override
    public void run() {
        SearchViewProvider view = SearchViewProvider.instance();
        if (view != null) {
            view.show(null);
        }
    }
}
