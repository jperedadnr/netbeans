package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.api.icons.ToolIcons;
import com.gluonhq.netbeans.nbfx.findinprojects.query.IgnoreList;
import com.gluonhq.netbeans.nbfx.findinprojects.query.IgnoreList.Entry;
import com.gluonhq.netbeans.nbfx.findinprojects.query.IgnoreList.Kind;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * NetBeans' {@code IgnoreListPanel}: the folders and path patterns "Use Ignore List" skips, with
 * Add Folder..., Add Path Pattern..., Edit..., Delete and Close. Every change is written to the
 * {@link IgnoreList} at once, so there is nothing to confirm.
 */
final class IgnoreListDialog extends Dialog<Void> {

    private final IgnoreList list;
    private final ListView<Entry> entries;

    IgnoreListDialog(Window owner, IgnoreList list) {
        this.list = Objects.requireNonNull(list);
        setTitle(message("TITLE_IgnoreList"));
        setResizable(true);
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.WINDOW_MODAL);

        entries = new ListView<>(list.getEntries());
        entries.setCellFactory(view -> new EntryCell(this::editSelected));
        entries.setPrefSize(480, 240);
        HBox.setHgrow(entries, Priority.ALWAYS);

        Button addFolder = new Button(message("BTN_AddFolder"));
        addFolder.setOnAction(e -> addFolder());
        Button addPattern = new Button(message("BTN_AddPattern"));
        addPattern.setOnAction(e -> addPattern());
        Button edit = new Button(message("BTN_Edit"));
        edit.setOnAction(e -> editSelected());
        Button delete = new Button(message("BTN_Delete"));
        delete.setOnAction(e -> list.getEntries().remove(entries.getSelectionModel().getSelectedItem()));
        edit.disableProperty().bind(Bindings.isNull(entries.getSelectionModel().selectedItemProperty()));
        delete.disableProperty().bind(edit.disableProperty());
        for (Button button : List.of(addFolder, addPattern, edit, delete)) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        VBox buttons = new VBox(6, addFolder, addPattern, edit, delete);

        HBox content = new HBox(8, entries, buttons); // padded by search.css: the pane's "content" rule wins over code

        DialogPane pane = getDialogPane();
        // a dialog has its own scene: take the application's stylesheets from the window it belongs to
        if (owner != null && owner.getScene() != null) {
            pane.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        pane.getStylesheets().add(Objects.requireNonNull(IgnoreListDialog.class.getResource("search.css")).toExternalForm());
        pane.getStyleClass().add("search-ignore-list");
        pane.setContent(content);
        pane.getButtonTypes().add(new ButtonType(message("BTN_Close"), ButtonData.CANCEL_CLOSE));
    }

    /** The list control, for tests. */
    ListView<Entry> entries() {
        return entries;
    }

    private void addFolder() {
        chooseFolder(null).ifPresent(folder -> add(Entry.folder(folder)));
    }

    private void addPattern() {
        askPattern("").ifPresent(regex -> add(Entry.pattern(regex)));
    }

    private void add(Entry entry) {
        if (!list.getEntries().contains(entry)) {
            list.getEntries().add(entry);
        }
        entries.getSelectionModel().select(entry);
    }

    private void editSelected() {
        Entry selected = entries.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Optional<Entry> edited = selected.kind() == Kind.FOLDER
                ? chooseFolder(new File(selected.value())).map(Entry::folder)
                : askPattern(selected.value()).map(Entry::pattern);
        edited.ifPresent(entry -> {
            int index = list.getEntries().indexOf(selected);
            if (index >= 0 && !entry.equals(selected)) {
                list.getEntries().set(index, entry);
            }
            entries.getSelectionModel().select(entry);
        });
    }

    private Optional<FileObject> chooseFolder(File initial) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(message("TITLE_IgnoredFolder"));
        if (initial != null && initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        File chosen = chooser.showDialog(getDialogPane().getScene().getWindow());
        return Optional.ofNullable(chosen == null ? null : FileUtil.toFileObject(FileUtil.normalizeFile(chosen)));
    }

    /** Asks for a path pattern starting from {@code initial}; OK stays disabled while the expression does not compile. */
    private Optional<String> askPattern(String initial) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(message("TITLE_PathPattern"));
        dialog.initOwner(getDialogPane().getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        TextField field = new TextField(initial);
        field.setPrefColumnCount(40);
        Label hint = new Label(message("HINT_PathPattern"));
        hint.getStyleClass().add("search-hint");
        Label error = new Label();
        error.getStyleClass().add("search-status");
        error.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("error"), true);
        VBox box = new VBox(6, new Label(message("LBL_PathPattern")), field, hint, error);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("search-pane");
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().addAll(getDialogPane().getStylesheets());
        pane.setContent(box);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Node ok = pane.lookupButton(ButtonType.OK);
        field.textProperty().subscribe(text -> {
            String problem = problemOf(text);
            error.setText(problem == null ? "" : problem);
            ok.setDisable(problem != null);
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? field.getText() : null);
        // the dialog focuses its default button on show: take the focus back once that is done
        dialog.setOnShown(e -> Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        }));
        return dialog.showAndWait();
    }

    /** Why {@code regex} is not a usable pattern, or {@code null}. */
    static String problemOf(String regex) {
        if (regex == null || regex.isBlank()) {
            return message("ERR_PathPatternEmpty");
        }
        try {
            Pattern.compile(regex);
            return null;
        } catch (PatternSyntaxException ex) {
            return message("ERR_PathPattern", ex.getDescription());
        }
    }

    /**
     * A folder with the folder icon and its path; a pattern with the find icon and its expression.
     * Double-clicking an entry edits it.
     */
    private static final class EntryCell extends ListCell<Entry> {

        EntryCell(Runnable onOpen) {
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !isEmpty()) {
                    getListView().getSelectionModel().select(getIndex());
                    onOpen.run();
                }
            });
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(entry.kind() == Kind.FOLDER ? entry.value() : message("LBL_PatternEntry", entry.value()));
            setGraphic(entry.kind() == Kind.FOLDER ? ToolIcons.view(ToolIcons.FOLDER) : SearchIcons.view("find"));
        }
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(IgnoreListDialog.class, key, args);
    }
}
