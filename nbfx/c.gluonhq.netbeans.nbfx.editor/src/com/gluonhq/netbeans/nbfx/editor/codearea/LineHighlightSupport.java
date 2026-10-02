package com.gluonhq.netbeans.nbfx.editor.codearea;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Subscription;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

/**
 * Marks one paragraph of a {@link CodeArea} with a translucent band laid over the text - the way
 * NetBeans' Find Usages preview shows the usage's line - without touching the caret or the
 * selection, and scrolls it to the middle of the viewport through the vertical scroll bar. The
 * band is a per-view overlay, so a highlight in a preview never shows in an editor of the same
 * buffer; it follows scrolling and resizing, and hides while the paragraph is out of view.
 */
final class LineHighlightSupport {

    private final CodeArea codeArea;
    private final Region band = new Region();
    private final Pane overlay = new Pane(band);

    private int paragraph = -1;
    private ScrollBar vertical;
    private Subscription subscription = Subscription.EMPTY;
    private boolean updateScheduled;

    LineHighlightSupport(CodeArea codeArea) {
        this.codeArea = codeArea;
        band.getStyleClass().add("line-highlight");
        band.setManaged(false);
        band.setVisible(false);
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);
        overlay.getStyleClass().add("overlay-highlight");
    }

    /** The node to stack over the code area. */
    Node overlay() {
        return overlay;
    }

    /** Highlights {@code paragraph} and scrolls it to the middle of the view once the area is skinned. */
    void highlight(int paragraph) {
        this.paragraph = paragraph;
        RestoreViewSupport.whenSkinned(codeArea, () -> {
            hook();
            scrollToCenter();
            scheduleUpdate();
        });
    }

    /** Removes the highlight. */
    void clear() {
        paragraph = -1;
        band.setVisible(false);
    }

    void dispose() {
        clear();
        subscription.unsubscribe();
        subscription = Subscription.EMPTY;
        vertical = null;
    }

    /** Follows the scroll bar, the size of the area and the model, once the skin exists. */
    private void hook() {
        if (vertical != null) {
            return;
        }
        vertical = codeArea.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(bar -> bar.getOrientation() == Orientation.VERTICAL)
                .findFirst()
                .orElse(null);
        Subscription all = codeArea.widthProperty().subscribe(w -> scheduleUpdate())
                .and(codeArea.heightProperty().subscribe(h -> scheduleUpdate()))
                .and(codeArea.fontProperty().subscribe(f -> scheduleUpdate()));
        if (vertical != null) {
            all = all.and(vertical.valueProperty().subscribe(v -> scheduleUpdate()));
        }
        subscription = subscription.and(all);
    }

    /**
     * Puts the paragraph in the middle of the viewport. The rich text area maps the scroll bar's
     * value to a top paragraph of about {@code value * (count - 1)}, corrected by the estimated
     * heights of the paragraphs around it, so the landing is off by a few lines: the top paragraph
     * is measured after each move and the value nudged by the difference, a few times at most.
     */
    private void scrollToCenter() {
        int count = codeArea.getParagraphCount();
        if (vertical == null || count < 2 || paragraph < 0) {
            return;
        }
        int visible = RestoreViewSupport.visibleParagraphs(codeArea);
        int wanted = Math.clamp(paragraph - visible / 2, 0, count - 1);
        double range = vertical.getMax() - vertical.getMin();
        double value = vertical.getMin() + range * wanted / (count - 1);
        for (int attempt = 0; attempt < 5; attempt++) {
            vertical.setValue(Math.clamp(value, vertical.getMin(), vertical.getMax()));
            int actual = topParagraph();
            if (actual < 0 || actual == wanted) {
                return;
            }
            value += range * (wanted - actual) / (count - 1);
        }
    }

    /** The paragraph at the top of the viewport, or -1 when it cannot be measured. */
    private int topParagraph() {
        Node vport = codeArea.lookup(".vport");
        Bounds screen = vport == null ? null : vport.localToScreen(vport.getLayoutBounds());
        if (screen == null) {
            return -1;
        }
        return indexAt(screen.getMinX() + screen.getWidth() / 2, screen.getMinY() + 1);
    }

    private void scheduleUpdate() {
        if (updateScheduled) {
            return;
        }
        updateScheduled = true;
        Platform.runLater(() -> {
            updateScheduled = false;
            update();
        });
    }

    /**
     * Places the band over the highlighted paragraph: the viewport rows whose text position falls
     * in it, found by bisection on {@link CodeArea#getTextPosition} (monotonic in {@code y}).
     */
    private void update() {
        Node vport = paragraph < 0 ? null : codeArea.lookup(".vport");
        if (vport == null || codeArea.getScene() == null || codeArea.getScene().getWindow() == null) {
            band.setVisible(false);
            return;
        }
        Bounds view = overlay.sceneToLocal(vport.localToScene(vport.getLayoutBounds()));
        Bounds screen = vport.localToScreen(vport.getLayoutBounds());
        if (view == null || screen == null || view.getHeight() <= 0) {
            band.setVisible(false);
            return;
        }
        double x = screen.getMinX() + screen.getWidth() / 2;
        double top = screen.getMinY();
        double bottom = screen.getMaxY();
        int first = indexAt(x, top);
        int last = indexAt(x, bottom - 1);
        if (first > paragraph || last < paragraph) {
            band.setVisible(false);
            return;
        }
        double from = first == paragraph ? top : firstRowAtLeast(x, top, bottom, paragraph);
        double to = last == paragraph ? bottom : firstRowAtLeast(x, from, bottom, paragraph + 1);
        band.resizeRelocate(view.getMinX(), view.getMinY() + (from - top), view.getWidth(), Math.max(1, to - from));
        band.setVisible(true);
    }

    /** The smallest {@code y} in {@code [low, high)} whose paragraph index is at least {@code index}. */
    private double firstRowAtLeast(double x, double low, double high, int index) {
        while (high - low > 0.5) {
            double mid = (low + high) / 2;
            if (indexAt(x, mid) >= index) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return high;
    }

    private int indexAt(double screenX, double screenY) {
        TextPos pos = codeArea.getTextPosition(screenX, screenY);
        return pos == null ? -1 : pos.index();
    }
}
