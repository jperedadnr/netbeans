package com.gluonhq.netbeans.nbfx.findusages.actions;

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsagesViewProvider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;

/** Window ▸ Usages (Shortcut+3): opens or fronts the Usages view. Always enabled. */
final class SelectUsagesCommand extends AbstractCommand {

    SelectUsagesCommand() {
        super(ActionIds.SELECT_USAGES, NbBundle.getMessage(SelectUsagesCommand.class, "CTL_SelectUsagesCommand"),
                new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.SHORTCUT_DOWN), false);
    }

    @Override
    public void run() {
        UsagesViewProvider view = UsagesViewProvider.instance();
        if (view != null) {
            view.show(null);
        }
    }
}
