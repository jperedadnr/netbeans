package com.gluonhq.netbeans.nbfx.findinprojects.actions;

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.ActionRegistry;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FindModel;
import com.gluonhq.netbeans.nbfx.findinprojects.query.FindQuery;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.FindInProjectsDialog;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.FindSettings;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.SearchViewProvider;
import java.util.List;
import java.util.Objects;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Edit ▸ Find in Projects... (Shift+Shortcut+F) and Edit ▸ Replace in Projects...
 * (Shift+Shortcut+H): opens the search dialog - in replace mode for the latter - seeded with the
 * active editor's selection when it has one, and starts the search it describes in the Search
 * view. Always enabled: the scope combo copes with no open project. A global command, so detached
 * windows share it.
 */
final class FindInProjectsCommand extends AbstractCommand {

    static final KeyCombination SHORTCUT = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN,
            KeyCombination.SHIFT_DOWN);
    static final KeyCombination REPLACE_SHORTCUT = new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN,
            KeyCombination.SHIFT_DOWN);

    private final FindModel model;
    private final EditorContext context;
    private final boolean replace;

    /** The Find in Projects command. */
    FindInProjectsCommand(FindModel model, EditorContext context) {
        this(model, context, false);
    }

    /** @param replace {@code true} for Replace in Projects */
    FindInProjectsCommand(FindModel model, EditorContext context, boolean replace) {
        super(replace ? ActionIds.REPLACE_IN_PROJECTS : ActionIds.FIND_IN_PROJECTS,
                NbBundle.getMessage(FindInProjectsCommand.class,
                        replace ? "CTL_ReplaceInProjectsCommand" : "CTL_FindInProjectsCommand"),
                replace ? REPLACE_SHORTCUT : SHORTCUT, false);
        this.model = Objects.requireNonNull(model);
        this.context = context;
        this.replace = replace;
    }

    @Override
    public void run() {
        open(null);
    }

    /**
     * Opens the dialog for the {@code folders} selected in a tree through the registered command
     * (the menu contributor's and the {@code file.find} command's route); nothing without one.
     */
    static void openFor(List<FileObject> folders) {
        ActionRegistry registry = Lookup.getDefault().lookup(ActionRegistry.class);
        if (registry != null) {
            registry.find(ActionIds.FIND_IN_PROJECTS)
                    .filter(FindInProjectsCommand.class::isInstance)
                    .map(FindInProjectsCommand.class::cast)
                    .ifPresent(command -> command.open(folders));
        }
    }

    /**
     * Opens the dialog with {@code folders} offered as the "Selected Packages and Folders" scope
     * ({@code null} when opened from the menu) and starts the search chosen in it.
     */
    void open(List<FileObject> folders) {
        FindInProjectsDialog dialog = new FindInProjectsDialog(focusedWindow(), FindSettings.getDefault(), folders, replace);
        String selection = selectedText();
        if (!selection.isEmpty()) {
            dialog.setText(selection);
        } else if (model.getLastCriteria() != null) {
            dialog.setText(model.getLastCriteria().text().query());
        }
        dialog.showAndWait().ifPresent(criteria -> start(criteria, dialog.isOpenInNewTab()));
    }

    /** Starts the search and shows it in the Search view, selecting its tab. */
    private void start(SearchCriteria criteria, boolean newTab) {
        FindQuery query = model.find(criteria, newTab);
        SearchViewProvider view = SearchViewProvider.instance();
        if (view != null) {
            view.show(query);
        }
    }

    private String selectedText() {
        EditorDocument document = context == null ? null : context.getActiveDocument();
        String text = document == null ? null : document.getSelectedText();
        return text == null ? "" : text;
    }

    /**
     * The focused window, or the window owning it when the focus is in a popup such as a context
     * menu: a dialog takes its stylesheets from an owning stage, not from a popup.
     */
    private static Window focusedWindow() {
        Window focused = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        while (focused instanceof PopupWindow popup) {
            focused = popup.getOwnerWindow();
        }
        return focused;
    }

}
