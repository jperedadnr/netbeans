package com.gluonhq.netbeans.nbfx.editor.codearea;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.control.Skin;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

/**
 * Restores a previously persisted view of a {@link CodeArea}: scroll position and caret, clamped
 * to the current content in case the file changed since the view was persisted.
 */
final class RestoreViewSupport {

    private RestoreViewSupport() {
    }

    /** Restores the given view once the code area has a skin, on the FX thread. */
    static void restore(CodeArea codeArea, int topParagraph, int caretParagraph, int caretColumn) {
        if (topParagraph <= 0 && caretParagraph <= 0 && caretColumn <= 0) {
            return;
        }
        whenSkinned(codeArea, () -> apply(codeArea, topParagraph, caretParagraph, caretColumn));
    }

    /**
     * Runs {@code action} now if the code area has a skin (so selecting scrolls the caret into
     * view), else once it gets one, in a later pulse.
     */
    static void whenSkinned(CodeArea codeArea, Runnable action) {
        if (codeArea.getSkin() != null) {
            action.run();
        } else {
            codeArea.skinProperty().addListener(new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends Skin<?>> obs, Skin<?> old, Skin<?> skin) {
                    if (skin != null) {
                        codeArea.skinProperty().removeListener(this);
                        Platform.runLater(action);
                    }
                }
            });
        }
    }

    private static void apply(CodeArea codeArea, int topParagraph, int caretParagraph, int caretColumn) {
        int paragraphCount = codeArea.getParagraphCount();
        if (caretParagraph < 0 || caretParagraph >= paragraphCount) {
            return;
        }
        // Clamp the column to the paragraph length, in case the file changed since it was persisted.
        TextPos caretEnd = codeArea.getParagraphEnd(caretParagraph);
        int maxColumn = caretEnd != null ? caretEnd.offset() : 0;
        int col = Math.clamp(caretColumn, 0, maxColumn);
        TextPos caret = TextPos.ofLeading(caretParagraph, col);

        scrollThenSelect(codeArea, topParagraph, caret);
    }

    /** The number of paragraphs the viewport shows, or 0 when it cannot be measured (not laid out yet). */
    static int visibleParagraphs(CodeArea codeArea) {
        if (codeArea.getScene() == null || codeArea.getScene().getWindow() == null || codeArea.getHeight() <= 0) {
            return 0;
        }
        Point2D top = codeArea.localToScreen(codeArea.getWidth() / 2, 2);
        Point2D bottom = codeArea.localToScreen(codeArea.getWidth() / 2, codeArea.getHeight() - 2);
        if (top == null || bottom == null) {
            return 0;
        }
        TextPos first = codeArea.getTextPosition(top.getX(), top.getY());
        TextPos last = codeArea.getTextPosition(bottom.getX(), bottom.getY());
        if (first == null || last == null) {
            return 0;
        }
        return last.index() - first.index();
    }

    /** Scrolls so {@code topParagraph} (clamped) is the first visible one, then places the caret. */
    private static void scrollThenSelect(CodeArea codeArea, int topParagraph, TextPos caret) {
        int top = Math.clamp(topParagraph, 0, codeArea.getParagraphCount() - 1);
        if (top <= 0) {
            codeArea.select(caret);
            return;
        }
        // scroll to the end first, bring it back to the top, then place the caret
        codeArea.select(codeArea.getDocumentEnd());
        Platform.runLater(() -> {
            codeArea.select(TextPos.ofLeading(top, 0));
            Platform.runLater(() -> codeArea.select(caret));
        });
    }
}
