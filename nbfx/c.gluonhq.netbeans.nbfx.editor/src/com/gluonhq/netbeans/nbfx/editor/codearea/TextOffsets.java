package com.gluonhq.netbeans.nbfx.editor.codearea;

import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;

/**
 * Conversions between a {@link StyledTextModel}'s paragraph/column positions and character offsets
 * from the start of the content, counting one character per line break regardless of the line
 * separator - the offsets of a {@code javax.swing.text.Document} and of the NetBeans source model
 * ({@code TreePathHandle}, {@code PositionBounds}).
 */
public final class TextOffsets {

    private TextOffsets() {
    }

    /** The offset of {@code pos}, clamped to the content; {@code 0} for {@code null}. */
    public static int offsetOf(StyledTextModel model, TextPos pos) {
        if (pos == null) {
            return 0;
        }
        int count = model.size();
        int paragraph = Math.clamp(pos.index(), 0, Math.max(0, count - 1));
        int offset = 0;
        for (int i = 0; i < paragraph; i++) {
            offset += model.getPlainText(i).length() + 1;
        }
        int length = count == 0 ? 0 : model.getPlainText(paragraph).length();
        return offset + Math.clamp(pos.offset(), 0, length);
    }

    /** The position at {@code offset}, clamped to the content. */
    public static TextPos positionOf(StyledTextModel model, int offset) {
        int count = model.size();
        int remaining = Math.max(0, offset);
        for (int i = 0; i < count; i++) {
            int length = model.getPlainText(i).length();
            if (remaining <= length) {
                return TextPos.ofLeading(i, remaining);
            }
            remaining -= length + 1;
        }
        return count == 0 ? TextPos.ZERO : model.getDocumentEnd();
    }

    /** The whole content with paragraphs joined by {@code '\n'}, i.e. the text these offsets index. */
    public static String text(StyledTextModel model) {
        StringBuilder sb = new StringBuilder();
        int count = model.size();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(model.getPlainText(i));
        }
        return sb.toString();
    }
}
