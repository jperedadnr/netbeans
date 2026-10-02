package com.gluonhq.netbeans.nbfx.findinprojects.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.RunnableCommand;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * {@code file.find} - Find... on the selection of the Projects / Files views: the file
 * counterpart of the editor's Find bar under the same {@code Shortcut+F}, which the launcher
 * dispatches to it while a navigator view has the focus. Opens the Find in Projects dialog with
 * the selected folders - a selected file stands for its folder, as NetBeans' node
 * {@code SearchInfo} does - as the "Selected Packages and Folders" scope.
 */
final class FindInSelectionCommand {

    static final KeyCombination SHORTCUT = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);

    private FindInSelectionCommand() {
    }

    /**
     * The command, enabled while the navigator has the focus and something is selected;
     * {@code open} receives the folders to search.
     */
    static Command create(FileSelectionContext context, Consumer<List<FileObject>> open) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(open);
        ObservableValue<List<FileObject>> selection = context.selectedFiles();
        ObservableValue<Boolean> focused = context.navigatorFocused();
        BooleanBinding enabled = Bindings.createBooleanBinding(
                () -> Boolean.TRUE.equals(focused.getValue()) && !foldersOf(selection.getValue()).isEmpty(),
                focused, selection);
        return RunnableCommand.enabledWhen(ActionIds.FILE_FIND,
                NbBundle.getMessage(FindInSelectionCommand.class, "CTL_FindInSelectionCommand"), SHORTCUT, enabled,
                () -> {
                    List<FileObject> folders = foldersOf(selection.getValue());
                    if (!folders.isEmpty()) {
                        open.accept(folders);
                    }
                });
    }

    /** The folders {@code files} stand for: a folder itself, a file its parent; each once, in selection order. */
    static List<FileObject> foldersOf(List<FileObject> files) {
        if (files == null) {
            return List.of();
        }
        Set<FileObject> folders = new LinkedHashSet<>();
        for (FileObject file : files) {
            if (file == null) {
                continue;
            }
            FileObject folder = file.isFolder() ? file : file.getParent();
            if (folder != null) {
                folders.add(folder);
            }
        }
        return new ArrayList<>(folders);
    }
}
