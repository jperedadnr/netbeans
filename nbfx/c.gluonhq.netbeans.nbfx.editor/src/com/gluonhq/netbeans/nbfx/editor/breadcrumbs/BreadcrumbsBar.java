package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.elements.ElementIcons;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Horizontal breadcrumbs bar shown below the code area of an editor.
 *
 * <p>The left side renders the current {@linkplain BreadcrumbElement#path() breadcrumb path},
 * one crumb per element followed by a separator (as in the NetBeans IDE, where the trailing
 * separator doubles as the affordance for the children popup); the right side holds an "X" button that
 * closes the bar (by turning off the shared "show breadcrumbs" setting through the
 * {@code onClose} callback, which also unchecks the {@code View → Show Breadcrumbs} menu item).</p>
 *
 * <p>Left-clicking a crumb navigates to its element; right-clicking a crumb — or clicking its
 * trailing separator — opens a {@link BreadcrumbsPopup} listing the element's children, and
 * choosing one navigates to that child.</p>
 *
 * <p>All methods must be called on the JavaFX Application Thread.</p>
 */
public class BreadcrumbsBar extends HBox {

    private final HBox pathBox = new HBox();
    // Package-private alias for testing
    final Button closeButton = new Button("\u2715");

    private List<BreadcrumbElement> path = List.of();
    private Consumer<BreadcrumbElement> onNavigate;
    private BreadcrumbsPopup popup;
    // Package-private hook so tests can intercept popup requests without a shown window
    BiConsumer<BreadcrumbElement, Node> childrenPopupHandler = this::showChildrenPopup;

    /**
     * Creates an empty breadcrumbs bar.
     *
     * @param onClose invoked when the user presses the close button
     */
    public BreadcrumbsBar(Runnable onClose) {
        Objects.requireNonNull(onClose, "onClose must not be null");
        getStyleClass().add("breadcrumb-bar");

        pathBox.getStyleClass().add("path");
        HBox.setHgrow(pathBox, Priority.ALWAYS);
        // A long path must never widen its editor (a narrow preview would lose its gutter): the bar
        // takes the width it is given and the crumbs that do not fit are clipped.
        setMinWidth(0);
        pathBox.setMinWidth(0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(pathBox.widthProperty());
        clip.heightProperty().bind(pathBox.heightProperty());
        pathBox.setClip(clip);

        closeButton.getStyleClass().add("breadcrumb-close");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(_ -> onClose.run());

        getChildren().addAll(pathBox, closeButton);
    }

    /**
     * Replaces the rendered breadcrumb path. An empty list clears the bar.
     *
     * @param path elements from root (first) to the selected element (last)
     */
    public void setPath(List<BreadcrumbElement> path) {
        this.path = List.copyOf(Objects.requireNonNull(path, "path must not be null"));
        pathBox.getChildren().clear();
        for (int i = 0; i < this.path.size(); i++) {
            BreadcrumbElement element = this.path.get(i);
            Label crumb = createCrumb(element);
            if (i < this.path.size() - 1) {
                // Only the last crumb may shrink (ellipsize) when the bar runs out of room.
                crumb.setMinWidth(Region.USE_PREF_SIZE);
            }
            Label separator = createSeparator(element);
            separator.setMinWidth(Region.USE_PREF_SIZE);
            pathBox.getChildren().addAll(crumb, separator);
        }
    }

    /**
     * Returns the currently rendered path.
     *
     * @return elements from root (first) to the selected element (last)
     */
    public List<BreadcrumbElement> getPath() {
        return path;
    }

    /**
     * Sets the handler invoked when the user navigates to an element, either by
     * left-clicking a crumb or by choosing a child in the children popup.
     *
     * @param onNavigate navigation handler, or {@code null} to disable navigation
     */
    public void setOnNavigate(Consumer<BreadcrumbElement> onNavigate) {
        this.onNavigate = onNavigate;
    }

    private Label createCrumb(BreadcrumbElement element) {
        Label crumb = new Label(element.label());
        crumb.getStyleClass().add("breadcrumb-item");
        crumb.setGraphic(ElementIcons.iconViewFor(element.kind(), element.typeKind(), element.modifiers()));
        crumb.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onNavigate != null) {
                onNavigate.accept(element);
            } else if (e.getButton() == MouseButton.SECONDARY) {
                requestChildrenPopup(element, crumb);
            }
        });
        return crumb;
    }

    private Label createSeparator(BreadcrumbElement element) {
        Label separator = new Label("\u203a");
        separator.getStyleClass().add("breadcrumb-separator");
        separator.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                requestChildrenPopup(element, separator);
            }
        });
        return separator;
    }

    /** Routes a popup request through the (test-replaceable) handler; leaves have no popup. */
    private void requestChildrenPopup(BreadcrumbElement element, Node anchor) {
        if (!element.children().isEmpty()) {
            childrenPopupHandler.accept(element, anchor);
        }
    }

    /** Shows the children popup above the bar, aligned with {@code anchor}; choosing a child navigates to it. */
    private void showChildrenPopup(BreadcrumbElement element, Node anchor) {
        if (popup == null) {
            popup = new BreadcrumbsPopup();
        }
        Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        Bounds barBounds = localToScreen(getBoundsInLocal());
        popup.show(anchor, anchorBounds.getMinX(), barBounds.getMinY(),
                element.children(), currentChildOf(element), child -> {
            if (onNavigate != null) {
                onNavigate.accept(child);
            }
        });
    }

    /**
     * Returns the child of {@code element} the rendered path currently descends through,
     * or {@code null} when {@code element} is the last path element.
     */
    // Package-private for testing
    BreadcrumbElement currentChildOf(BreadcrumbElement element) {
        int index = path.indexOf(element);
        return index >= 0 && index + 1 < path.size() ? path.get(index + 1) : null;
    }
}
