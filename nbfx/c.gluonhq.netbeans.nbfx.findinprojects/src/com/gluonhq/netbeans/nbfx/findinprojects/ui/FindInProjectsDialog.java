package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * The Find in Projects dialog: a {@link BasicSearchPane} with <b>Find</b> (the default button, so
 * {@code Enter} searches) and <b>Close</b>, and an <b>Open in New Tab</b> check box at the left of
 * the button bar. The Replace in Projects dialog is the same with the form in replace mode. Resolves to the criteria to search when closed with Find, else to {@code null};
 * the form's options and histories are committed to the settings at that moment.
 */
public final class FindInProjectsDialog extends Dialog<SearchCriteria> {

    private final BasicSearchPane form;
    private final CheckBox openInNewTab = new CheckBox(message("LBL_OpenInNewTab"));
    private final FindSettings settings;

    /**
     * @param owner    the window the dialog belongs to, or {@code null}
     * @param settings the remembered options the form starts from
     * @param folders  the folders selected in a tree, else {@code null} or empty
     */
    public FindInProjectsDialog(Window owner, FindSettings settings, List<FileObject> folders) {
        this(owner, settings, folders, false);
    }

    /**
     * @param replace {@code true} for the Replace in Projects flavour: the form asks for the
     *                replacement and the main button reads <b>Continue</b> - the matches are chosen
     *                in the results before they are replaced, as in NetBeans
     */
    public FindInProjectsDialog(Window owner, FindSettings settings, List<FileObject> folders, boolean replace) {
        this.settings = Objects.requireNonNull(settings);
        form = new BasicSearchPane(settings, folders, replace);
        setTitle(message(replace ? "TITLE_ReplaceInProjects" : "TITLE_FindInProjects"));
        setResizable(true);
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.WINDOW_MODAL);

        ButtonType find = new ButtonType(message(replace ? "BTN_Continue" : "BTN_Find"), ButtonData.OK_DONE);
        ButtonType close = new ButtonType(message("BTN_Close"), ButtonData.CANCEL_CLOSE);
        DialogPane pane = getDialogPane();
        pane.getStylesheets().add(Objects.requireNonNull(FindInProjectsDialog.class.getResource("search.css")).toExternalForm());
        pane.getStyleClass().add("find-in-projects");
        pane.getButtonTypes().addAll(find, close);
        pane.setContent(new VBox(form));

        Node findButton = pane.lookupButton(find);
        findButton.disableProperty().bind(form.validProperty().not());
        form.setOnSubmit(() -> {
            if (form.validProperty().get()) {
                setResult(form.criteria());
                close();
            }
        });

        openInNewTab.setSelected(settings.isOpenInNewTab());
        ButtonBar.setButtonData(openInNewTab, ButtonData.LEFT);
        pane.getChildren().stream()
                .filter(ButtonBar.class::isInstance)
                .map(ButtonBar.class::cast)
                .findFirst()
                .ifPresent(bar -> bar.getButtons().add(openInNewTab));

        setResultConverter(button -> button == find && form.validProperty().get() ? form.criteria() : null);
        resultProperty().subscribe(result -> {
            if (result != null) {
                form.commit();
                settings.setOpenInNewTab(openInNewTab.isSelected());
            }
        });
        // the text field grows line by line with a multi-line query: follow it rather than clip it
        form.textRowsProperty().subscribe(rows -> Platform.runLater(this::fitHeightToForm));
        setOnShown(e -> {
            form.setOwner(pane.getScene().getWindow());
            Platform.runLater(form::focusText);
        });
    }

    /** Resizes the dialog to the height the form asks for now, keeping the width the user gave it. */
    private void fitHeightToForm() {
        DialogPane pane = getDialogPane();
        Window window = pane.getScene() == null ? null : pane.getScene().getWindow();
        if (window == null || !window.isShowing()) {
            return;
        }
        double width = window.getWidth();
        window.sizeToScene();
        window.setWidth(width);
    }

    /** Fills the form from an earlier search (Modify Criteria). */
    public void seed(SearchCriteria criteria) {
        form.seed(criteria);
    }

    /** Replaces the text to search for, e.g. with the editor's selection. */
    public void setText(String text) {
        form.setText(text);
    }

    /** Whether this is the Replace in Projects flavour. */
    public boolean isReplace() {
        return form.isReplace();
    }

    /** Whether the search should open a new results tab rather than reuse the last one. */
    public boolean isOpenInNewTab() {
        return openInNewTab.isSelected();
    }

    BasicSearchPane form() {
        return form;
    }

    private static String message(String key) {
        return NbBundle.getMessage(FindInProjectsDialog.class, key);
    }
}
