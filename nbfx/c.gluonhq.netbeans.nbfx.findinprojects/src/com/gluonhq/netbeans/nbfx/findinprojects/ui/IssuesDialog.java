package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.findinprojects.model.Issue;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.openide.util.NbBundle;

/**
 * NetBeans' {@code IssuesPanel}: the files a search could not read or decode, with what went wrong,
 * behind the "Warnings and Errors" link of the summary row.
 */
final class IssuesDialog extends Dialog<Void> {

    IssuesDialog(Window owner, List<Issue> issues) {
        setTitle(message("TITLE_Issues"));
        setResizable(true);
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.NONE);

        TableView<Issue> table = new TableView<>(FXCollections.observableArrayList(issues));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefSize(640, 240);
        TableColumn<Issue, String> file = new TableColumn<>(message("COL_IssueFile"));
        file.setCellValueFactory(f -> new ReadOnlyStringWrapper(f.getValue().path()).getReadOnlyProperty());
        file.setPrefWidth(280);
        TableColumn<Issue, String> text = new TableColumn<>(message("COL_IssueMessage"));
        text.setCellValueFactory(f -> new ReadOnlyStringWrapper(f.getValue().message()).getReadOnlyProperty());
        table.getColumns().setAll(List.of(file, text));

        DialogPane pane = getDialogPane();
        // a dialog has its own scene: take the application's stylesheets from the window it belongs to
        if (owner != null && owner.getScene() != null) {
            pane.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        pane.getStylesheets().add(Objects.requireNonNull(IssuesDialog.class.getResource("search.css")).toExternalForm());
        pane.getStyleClass().add("search-issues");
        pane.setContent(table);
        pane.getButtonTypes().add(new ButtonType(message("BTN_Close"), ButtonType.CLOSE.getButtonData()));
    }

    private static String message(String key) {
        return NbBundle.getMessage(IssuesDialog.class, key);
    }
}
