package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.editor.CaretInfo;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.util.Duration;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.SelectionSegment;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;

/**
 * Publishes the {@link CaretInfo} of a {@link CodeArea}, delaying it while a selection is being
 * made: a plain caret move is shown right away, whereas a growing selection is only published
 * once it stays still for {@link #CARET_INFO_DELAY} (or via {@link #flush()}, e.g. when the
 * mouse button is released).
 */
final class CaretInfoSupport {

    // Package-private for testing
    static final Duration CARET_INFO_DELAY = Duration.millis(250);

    private final CodeArea codeArea;
    private final ReadOnlyObjectWrapper<CaretInfo> caretInfo = new ReadOnlyObjectWrapper<>(this, "caretInfo", null);
    private final PauseTransition delay = new PauseTransition(CARET_INFO_DELAY);

    CaretInfoSupport(CodeArea codeArea) {
        this.codeArea = codeArea;
        delay.setOnFinished(_ -> update());
    }

    ObservableValue<CaretInfo> property() {
        return caretInfo.getReadOnlyProperty();
    }

    /** Schedules a caret info update, debouncing it while a selection is growing. */
    void schedule() {
        if (codeArea.hasNonEmptySelection()) {
            delay.playFromStart();
        } else {
            flush();
        }
    }

    /** Publishes the caret info right away, cancelling any pending debounce. */
    void flush() {
        delay.stop();
        update();
    }

    /** Recomputes the caret position and selection size published by {@link #property()}. */
    private void update() {
        TextPos caret = codeArea.getCaretPosition();
        if (caret == null) {
            caretInfo.set(null);
            return;
        }
        CaretInfo at = CaretInfo.at(caret.index() + 1, caret.offset() + 1);
        SelectionSegment selection = codeArea.getSelection();
        if (selection == null || selection.isCollapsed()) {
            caretInfo.set(at);
            return;
        }
        TextPos min = selection.getMin();
        TextPos max = selection.getMax();
        caretInfo.set(new CaretInfo(at.row(), at.column(),
                max.index() - min.index() + 1, selectedCharacters(min, max)));
    }

    /** The number of characters between {@code min} and {@code max}, line separators included. */
    private int selectedCharacters(TextPos min, TextPos max) {
        if (min.index() == max.index()) {
            return max.offset() - min.offset();
        }
        StyledTextModel model = codeArea.getModel();
        if (model == null) {
            return 0;
        }
        // First (partial) line, then the whole lines in between, and finally the last (partial)
        // one, counting the line separator that precedes each of them.
        int characters = Math.max(0, model.getParagraphLength(min.index()) - min.offset());
        for (int i = min.index() + 1; i < max.index(); i++) {
            characters += model.getParagraphLength(i) + 1;
        }
        return characters + max.offset() + 1;
    }
}
