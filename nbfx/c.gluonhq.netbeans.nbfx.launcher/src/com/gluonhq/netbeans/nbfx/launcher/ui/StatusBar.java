package com.gluonhq.netbeans.nbfx.launcher.ui;

import com.gluonhq.netbeans.nbfx.api.editor.CaretInfo;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.openide.util.NbBundle;

import java.util.function.Consumer;

/**
 * A persistent status bar shown at the bottom of the main window: it names the selected project -
 * the one the project-scoped actions apply to - on the left, shows the progress of the project
 * being opened in the centre, and the caret position of the editor being edited on the right..
 */
public class StatusBar extends StackPane {

    private final Label projectName = new Label();
    private final Label legend = new Label();
    private final ProgressBar progressBar = new ProgressBar();
    private final Button cancelButton = new Button();
    private final HBox progressGroup;
    private final Label caretPosition = new Label();
    private final Label lineSeparator = new Label();
    private final HBox lineSeparatorGroup;
    private final ContextMenu lineSeparatorMenu = new ContextMenu();
    private Consumer<String> onLineSeparatorChange;
    private final HBox caretGroup;

    private Runnable onCancel;

    public StatusBar() {
        getStyleClass().add("status-bar");

        projectName.getStyleClass().add("status-bar-project");
        setProject(null);

        legend.getStyleClass().add("status-bar-legend");

        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.getStyleClass().add("status-bar-progress");

        cancelButton.getStyleClass().add("status-bar-cancel");
        cancelButton.setText("\u2715"); // ✕
        cancelButton.setFocusTraversable(false);
        cancelButton.setTooltip(new Tooltip(NbBundle.getMessage(StatusBar.class, "StatusBar.cancel.tooltip")));
        cancelButton.setOnAction(_ -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });

        progressGroup = new HBox(legend, progressBar, cancelButton);
        progressGroup.getStyleClass().add("status-bar-progress-group");
        progressGroup.setAlignment(Pos.CENTER);
        setProgressVisible(false);

        caretPosition.getStyleClass().add("status-bar-caret");
        caretPosition.setTooltip(new Tooltip(NbBundle.getMessage(StatusBar.class, "StatusBar.caret.tooltip")));
        Separator caretSeparator = new Separator(Orientation.VERTICAL);
        caretSeparator.getStyleClass().add("status-bar-separator");
        caretGroup = new HBox(caretSeparator, caretPosition);
        caretGroup.getStyleClass().add("status-bar-caret-group");
        caretGroup.setAlignment(Pos.CENTER_RIGHT);
        caretGroup.setMaxWidth(Region.USE_PREF_SIZE);
        setCaretInfo(null);

        lineSeparator.getStyleClass().add("status-bar-line-separator");
        lineSeparator.setTooltip(new Tooltip(NbBundle.getMessage(StatusBar.class, "StatusBar.lineSeparator.tooltip")));
        Separator lineSeparatorSeparator = new Separator(Orientation.VERTICAL);
        lineSeparatorSeparator.getStyleClass().add("status-bar-separator");
        lineSeparatorGroup = new HBox(lineSeparatorSeparator, lineSeparator);
        lineSeparatorGroup.getStyleClass().addAll("status-bar-caret-group", "status-bar-line-separator-group");
        lineSeparatorGroup.setAlignment(Pos.CENTER_RIGHT);
        lineSeparatorGroup.setMaxWidth(Region.USE_PREF_SIZE);
        setLineSeparator(null);

        for (String separator : new String[] { "\n", "\r", "\r\n" }) {
            MenuItem item = new MenuItem(lineSeparatorName(separator));
            item.setOnAction(_ -> {
                if (onLineSeparatorChange != null) {
                    onLineSeparatorChange.accept(separator);
                }
            });
            lineSeparatorMenu.getItems().add(item);
        }
        lineSeparatorGroup.setOnMouseClicked(_ ->
                lineSeparatorMenu.show(lineSeparatorGroup, Side.TOP, 0, 0));

        HBox rightGroup = new HBox(caretGroup, lineSeparatorGroup);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);
        rightGroup.setMaxWidth(Region.USE_PREF_SIZE);

        getChildren().addAll(projectName, progressGroup, rightGroup);
        StackPane.setAlignment(projectName, Pos.CENTER_LEFT);
        StackPane.setAlignment(rightGroup, Pos.CENTER_RIGHT);
    }

    /**
     * Shows the progress area with the given legend, running {@code onCancel} when the user presses
     * the cancel button; when {@code onCancel} is {@code null} the cancel button is hidden (the
     * activity cannot be cancelled). Safe to call from any thread.
     *
     * @param legendText the text shown to the left of the progress bar
     * @param onCancel   the action to run when cancel is pressed (may be {@code null})
     */
    public void showProgress(String legendText, Runnable onCancel) {
        runOnFxThread(() -> {
            this.onCancel = onCancel;
            cancelButton.setVisible(onCancel != null);
            cancelButton.setManaged(onCancel != null);
            legend.setText(legendText);
            setProgressVisible(true);
        });
    }

    /**
     * Names the selected project, or clears the area when {@code name} is {@code null} (no project
     * is open). Safe to call from any thread.
     */
    public void setProject(String name) {
        runOnFxThread(() -> {
            projectName.setText(name == null ? "" : name);
            projectName.setVisible(name != null);
            projectName.setManaged(name != null);
        });
    }

    /**
     * Hides the progress area. Safe to call from any thread.
     */
    public void hideProgress() {
        runOnFxThread(() -> {
            this.onCancel = null;
            setProgressVisible(false);
        });
    }

    /**
     * Shows the caret position of the document being edited, or clears the area when {@code info}
     * is {@code null} (no document is being edited). Safe to call from any thread.
     *
     * @param info the caret position and selection size, or {@code null}
     */
    public void setCaretInfo(CaretInfo info) {
        runOnFxThread(() -> {
            caretPosition.setText(format(info));
            caretGroup.setVisible(info != null);
            caretGroup.setManaged(info != null);
        });
    }

    /**
     * The caret position as shown in the status bar: {@code row:column}, and
     * {@code row:column/rows:columns} when there is a selection, where {@code rows} and
     * {@code columns} are the number of selected lines and characters.
     */
    static String format(CaretInfo info) {
        if (info == null) {
            return "";
        }
        String caret = info.row() + ":" + info.column();
        return info.hasSelection()
                ? caret + "/" + info.selectedRows() + ":" + info.selectedColumns()
                : caret;
    }

    /**
     * Names the line separator of the document being edited, or clears the area when
     * {@code separator} is {@code null} (no document is being edited, or its separator is
     * unknown). Safe to call from any thread.
     *
     * @param separator the file's line separator ({@code "\n"}, {@code "\r"} or {@code "\r\n"}),
     *                  or {@code null}
     */
    public void setLineSeparator(String separator) {
        runOnFxThread(() -> {
            String name = lineSeparatorName(separator);
            lineSeparator.setText(name == null ? "" : name);
            lineSeparatorGroup.setVisible(name != null);
            lineSeparatorGroup.setManaged(name != null);
        });
    }

    /**
     * Registers the action invoked when the user picks a line separator from the status-bar
     * popup, receiving the chosen separator string ({@code "\n"}, {@code "\r"} or {@code "\r\n"}).
     *
     * @param onLineSeparatorChange the action to run on selection (may be {@code null})
     */
    public void setOnLineSeparatorChange(Consumer<String> onLineSeparatorChange) {
        this.onLineSeparatorChange = onLineSeparatorChange;
    }

    /**
     * The display name of a line separator as shown in the status bar, or {@code null} when the
     * separator is {@code null} or not one of {@code "\n"}, {@code "\r"}, {@code "\r\n"}.
     */
    static String lineSeparatorName(String separator) {
        return switch (separator) {
            case "\n" -> NbBundle.getMessage(StatusBar.class, "StatusBar.lineSeparator.lf");
            case "\r" -> NbBundle.getMessage(StatusBar.class, "StatusBar.lineSeparator.cr");
            case "\r\n" -> NbBundle.getMessage(StatusBar.class, "StatusBar.lineSeparator.crlf");
            case null, default -> null;
        };
    }

    private void setProgressVisible(boolean visible) {
        progressGroup.setVisible(visible);
        progressGroup.setManaged(visible);
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
