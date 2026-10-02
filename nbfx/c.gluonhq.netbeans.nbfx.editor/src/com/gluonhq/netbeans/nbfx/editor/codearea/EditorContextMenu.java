package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.actions.ActionRegistry;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.EditorContextMenuIds;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.openide.util.Lookup;

/**
 * The context menu of a code editor, built from the {@link ActionRegistry} commands named by
 * {@link EditorContextMenuIds} each time it opens, so modules can extend it and the items reflect
 * the current enablement. Opening the menu focuses the editor first, making it the active document
 * the commands act on.
 */
final class EditorContextMenu {

    private EditorContextMenu() {
    }

    /**
     * Installs the menu. It is shown from an event filter, as the rich text area's behaviour
     * consumes the context-menu request itself (and would not show the control's
     * {@code contextMenu} either). A popup anchored on a node does not auto-hide on clicks inside
     * that node, so a press in the editor hides it explicitly, as the platform's text controls do.
     */
    static void install(CodeArea codeArea) {
        ContextMenu menu = new ContextMenu();
        codeArea.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            if (!codeArea.isFocused()) {
                codeArea.requestFocus();
            }
            if (menu.isShowing()) {
                menu.hide();
            }
            rebuild(menu);
            if (!menu.getItems().isEmpty()) {
                menu.show(codeArea, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });
        codeArea.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (menu.isShowing()) {
                menu.hide();
            }
        });
    }

    private static void rebuild(ContextMenu menu) {
        ActionRegistry registry = Lookup.getDefault().lookup(ActionRegistry.class);
        List<MenuItem> items = new ArrayList<>();
        boolean pendingSeparator = false;
        for (String id : EditorContextMenuIds.ids()) {
            if (EditorContextMenuIds.SEPARATOR.equals(id)) {
                pendingSeparator = !items.isEmpty();
                continue;
            }
            Command command = registry == null ? null : registry.find(id).orElse(null);
            if (command == null) {
                continue;
            }
            if (pendingSeparator) {
                items.add(new SeparatorMenuItem());
                pendingSeparator = false;
            }
            items.add(itemFor(command));
        }
        menu.getItems().setAll(items);
    }

    /**
     * An item for {@code command}. Its accelerator is deliberately not set: a control's context menu
     * registers its accelerators with the scene, which would fight the menu bar's for the same keys.
     */
    private static MenuItem itemFor(Command command) {
        MenuItem item = new MenuItem(command.getText());
        item.disableProperty().bind(command.disabledProperty());
        item.setOnAction(e -> command.run());
        return item;
    }
}
