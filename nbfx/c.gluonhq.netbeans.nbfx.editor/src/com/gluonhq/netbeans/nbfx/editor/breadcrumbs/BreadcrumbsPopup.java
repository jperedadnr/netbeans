package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.elements.ElementIcons;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.PopupWindow.AnchorLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Popup listing the child elements of a breadcrumb, opened by right-clicking a crumb
 * (or clicking its trailing separator) in the {@link BreadcrumbsBar}.
 *
 * <p>The popup opens bottom-aligned with the bar's top edge and left-aligned with the clicked crumb.
 * Shows at most {@value #MAX_VISIBLE_ROWS} rows. When the current breadcrumb path already descends
 * through one of the listed children, that child is preselected; otherwise the first row is. {@code Enter} or a
 * primary click navigates to the selected element's start offset and closes the popup;
 * {@code Esc}, focus loss or clicking elsewhere closes it without navigating.</p>
 *
 * <p>All methods must be called on the JavaFX Application Thread.</p>
 */
final class BreadcrumbsPopup extends PopupControl {

    /** Maximum rows shown without scrolling, matching the NetBeans IDE popup cap. */
    static final int MAX_VISIBLE_ROWS = 20;
    private static final double ROW_HEIGHT = 22;

    private final ListView<BreadcrumbElement> listView = new ListView<>();
    private Consumer<BreadcrumbElement> onNavigate = _ -> { };

    BreadcrumbsPopup() {
        setAutoFix(true);
        setAutoHide(true);
        setHideOnEscape(true);
        // The popup's bottom-left corner sits at the show(x, y) point, so it grows upwards
        setAnchorLocation(AnchorLocation.CONTENT_BOTTOM_LEFT);

        listView.getStyleClass().add("breadcrumbs-popup-list");
        listView.setFixedCellSize(ROW_HEIGHT);
        listView.setCellFactory(_ -> createCell());
        listView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                commitSelection();
            }
        });
        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                commitSelection();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hide();
                e.consume();
            }
        });

        StackPane root = new StackPane(listView);
        root.getStyleClass().add("breadcrumbs-popup-root");
        root.getStylesheets().add(Objects.requireNonNull(
                BreadcrumbsPopup.class.getResource("breadcrumbs-popup.css")).toExternalForm());
        getStyleClass().add("breadcrumbs-popup");
        getScene().setRoot(root);
    }

    /**
     * Shows the popup above the breadcrumbs bar, listing {@code children}.
     *
     * @param anchor the crumb (or separator) node the popup aligns to; must be in a shown window
     * @param screenX screen x of the popup's left edge (typically the crumb's left edge)
     * @param screenY screen y of the popup's bottom edge (typically the bar's top edge)
     * @param children elements to list, must not be empty
     * @param selected the child to preselect (the current path descendant), or {@code null}
     *                 to select the first row
     * @param onNavigate invoked with the chosen element after the popup closes
     */
    void show(Node anchor, double screenX, double screenY, List<BreadcrumbElement> children,
              BreadcrumbElement selected, Consumer<BreadcrumbElement> onNavigate) {
        setContent(children, selected, onNavigate);
        if (isShowing()) {
            hide();
        }
        super.show(anchor, screenX, screenY);
        listView.requestFocus();
    }

    /**
     * Populates the rows, sizes the list to at most {@value #MAX_VISIBLE_ROWS} visible rows
     * and selects {@code selected} (or the first element when {@code null} or not listed).
     * Extracted from {@link #show} for testability.
     */
    void setContent(List<BreadcrumbElement> children, BreadcrumbElement selected,
                    Consumer<BreadcrumbElement> onNavigate) {
        this.onNavigate = onNavigate == null ? _ -> { } : onNavigate;
        listView.getItems().setAll(children);
        listView.setPrefHeight(Math.min(children.size(), MAX_VISIBLE_ROWS) * ROW_HEIGHT + 2);
        int index = selected == null ? -1 : children.indexOf(selected);
        if (index >= 0) {
            listView.getSelectionModel().select(index);
            listView.scrollTo(index);
        } else {
            listView.getSelectionModel().selectFirst();
        }
    }

    /** Closes the popup and navigates to the selected element, if any. */
    void commitSelection() {
        BreadcrumbElement selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        hide();
        onNavigate.accept(selected);
    }

    // Package-private for testing
    ListView<BreadcrumbElement> listView() {
        return listView;
    }

    private ListCell<BreadcrumbElement> createCell() {
        ListCell<BreadcrumbElement> cell = new ListCell<>() {
            @Override
            protected void updateItem(BreadcrumbElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.label());
                setGraphic(ElementIcons.iconViewFor(item.kind(), item.typeKind(), item.modifiers()));
            }
        };
        cell.getStyleClass().add("breadcrumbs-popup-cell");
        return cell;
    }
}
