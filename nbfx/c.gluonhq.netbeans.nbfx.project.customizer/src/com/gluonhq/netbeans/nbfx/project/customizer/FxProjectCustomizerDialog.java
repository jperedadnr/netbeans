package com.gluonhq.netbeans.nbfx.project.customizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;

/**
 * The JavaFX project-properties dialog: a category tree on the left and the selected panel on the
 * right, with OK / Apply / Cancel. Apply applies without closing; OK applies and closes; Cancel
 * discards. Apply and OK are gated on the panels' {@code isValid}/{@code isChanged}. Categories nest
 * through {@link FxProjectCustomizerPanel#parentId()}.
 */
public final class FxProjectCustomizerDialog {

    private FxProjectCustomizerDialog() {
    }

    public static void show(Project project) {
        List<FxProjectCustomizerPanel> panels = FxProjectCustomizers.panelsFor(project);
        if (panels.isEmpty()) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(NbBundle.getMessage(FxProjectCustomizerDialog.class, "FxProjectCustomizerDialog.Title"));
        dialog.setResizable(true);

        TreeView<FxProjectCustomizerPanel> categories = new TreeView<>(buildTree(panels));
        categories.setShowRoot(false);
        categories.setPrefWidth(200);
        categories.setCellFactory(tree -> new TreeCell<>() {
            @Override
            protected void updateItem(FxProjectCustomizerPanel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });

        // Create every panel up front, so each has initialised its fields before the button state is
        // computed from isChanged()/isValid() over all panels.
        Map<FxProjectCustomizerPanel, Node> contents = new HashMap<>();
        for (FxProjectCustomizerPanel panel : panels) {
            contents.put(panel, panel.createPanel(project));
        }

        StackPane content = new StackPane();
        content.setPadding(new Insets(12));
        content.setPrefSize(520, 360);
        categories.getSelectionModel().selectedItemProperty().addListener((observable, old, item) -> {
            Node panel = item == null ? null : contents.get(item.getValue());
            content.getChildren().setAll(panel == null ? List.of() : List.of(panel));
        });
        selectFirstLeaf(categories);

        SplitPane split = new SplitPane(categories, content);
        split.setDividerPositions(0.3);
        dialog.getDialogPane().setContent(split);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.APPLY, ButtonType.CANCEL);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        Node applyButton = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
        Runnable refreshButtons = () -> {
            boolean valid = panels.stream().allMatch(FxProjectCustomizerPanel::isValid);
            boolean changed = panels.stream().anyMatch(FxProjectCustomizerPanel::isChanged);
            okButton.setDisable(!valid);
            applyButton.setDisable(!valid || !changed);
        };
        refreshButtons.run();

        ((Button) applyButton).addEventFilter(ActionEvent.ACTION, event -> {
            applyAll(panels, project);
            refreshButtons.run();
            event.consume();
        });

        ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (result == ButtonType.OK) {
            applyAll(panels, project);
        } else if (result == ButtonType.CANCEL) {
            for (FxProjectCustomizerPanel panel : panels) {
                panel.cancel();
            }
        }
    }

    /** Builds the category tree from the flat panel list, nesting by {@code parentId()}. */
    private static TreeItem<FxProjectCustomizerPanel> buildTree(List<FxProjectCustomizerPanel> panels) {
        TreeItem<FxProjectCustomizerPanel> root = new TreeItem<>();
        Map<String, TreeItem<FxProjectCustomizerPanel>> byId = new HashMap<>();
        for (FxProjectCustomizerPanel panel : panels) {
            byId.put(panel.id(), new TreeItem<>(panel));
        }
        for (FxProjectCustomizerPanel panel : panels) {
            TreeItem<FxProjectCustomizerPanel> item = byId.get(panel.id());
            TreeItem<FxProjectCustomizerPanel> parent = panel.parentId() == null ? null : byId.get(panel.parentId());
            if (parent != null) {
                parent.getChildren().add(item);
            } else {
                root.getChildren().add(item);
            }
        }
        return root;
    }

    private static void selectFirstLeaf(TreeView<FxProjectCustomizerPanel> tree) {
        TreeItem<FxProjectCustomizerPanel> item = tree.getRoot();
        while (item != null && !item.getChildren().isEmpty()) {
            item = item.getChildren().get(0);
        }
        if (item != null) {
            tree.getSelectionModel().select(item);
        }
    }

    private static void applyAll(List<FxProjectCustomizerPanel> panels, Project project) {
        for (FxProjectCustomizerPanel panel : panels) {
            if (panel.isChanged()) {
                panel.apply(project);
            }
        }
    }
}
