package com.gluonhq.netbeans.nbfx.api.editor;

/**
 * The caret position of a document, together with the size of its selection, as shown in the
 * status bar.
 * <p>
 * {@code row} and {@code column} are one-based, so they can be displayed as they are. When there
 * is no selection, {@code selectedRows} and {@code selectedColumns} are both zero.
 *
 * @param row             the one-based line the caret is on
 * @param column          the one-based column the caret is at
 * @param selectedRows    the number of lines the selection spans, or 0 when nothing is selected
 * @param selectedColumns the number of characters selected, or 0 when nothing is selected
 */
public record CaretInfo(int row, int column, int selectedRows, int selectedColumns) {

    /**
     * A caret at {@code row}:{@code column} with no selection.
     *
     * @param row    the one-based line the caret is on
     * @param column the one-based column the caret is at
     * @return the caret position without a selection
     */
    public static CaretInfo at(int row, int column) {
        return new CaretInfo(row, column, 0, 0);
    }

    /**
     * Whether a non-empty selection is held.
     *
     * @return {@code true} when at least one character is selected
     */
    public boolean hasSelection() {
        return selectedRows > 0 && selectedColumns > 0;
    }
}
