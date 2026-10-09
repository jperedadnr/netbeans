package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.actions.ActionLayerReader;
import com.gluonhq.netbeans.nbfx.api.actions.ActionRegistry;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.EditorContextMenuIds;
import com.gluonhq.netbeans.nbfx.api.actions.FxMenuEntry;
import com.gluonhq.netbeans.nbfx.api.actions.LayerMenuBuilder;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.openide.util.Lookup;

/**
 * The context menu of a code editor, built each time it opens from the {@code NbFx/ContextMenus/Editor}
 * layer folder - the references modules declare with {@code @FxActionReference}, and the submenus
 * they declare with {@code @FxMenuRegistration(path = "ContextMenus/Editor")}, such as Navigate -
 * resolved against the {@link ActionRegistry}, so the items reflect the current enablement; the ids
 * of {@link EditorContextMenuIds} are the fallback of a layer without entries. Opening the menu
 * focuses the editor first, making it the active document the commands act on.
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
        List<FxMenuEntry> entries = ActionLayerReader.readEntries("NbFx/ContextMenus/Editor");
        if (!entries.isEmpty()) {
            if (registry != null) {
                items = new LayerMenuBuilder(registry).build(entries);
            }
        } else {
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
        }
        menu.getItems().setAll(items);
    }

    /**
     * An item for {@code command}, showing its shortcut as the menu bar does. The accelerator is
     * display-only here, as in the items {@link LayerMenuBuilder} builds: only a control's
     * {@code contextMenu} property registers accelerators with the scene (where they would fight the
     * menu bar's for the same keys), and this menu is shown directly instead.
     */
    private static MenuItem itemFor(Command command) {
        MenuItem item = new MenuItem(command.getText());
        item.disableProperty().bind(command.disabledProperty());
        item.setOnAction(e -> command.run());
        KeyCombination accelerator = command.getAccelerator();
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        return item;
    }
}
