package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.editor.CaretInfo;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.EditorSettings;
import com.gluonhq.netbeans.nbfx.editor.breadcrumbs.BreadcrumbsBar;
import com.gluonhq.netbeans.nbfx.editor.codearea.search.SearchSupport;
import com.gluonhq.netbeans.nbfx.editor.breadcrumbs.BreadcrumbsSupport;
import com.gluonhq.netbeans.nbfx.editor.completion.CompletionController;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.SelectionSegment;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

import java.io.IOException;
import java.util.Objects;

/**
 * A fully configured code-editor component backed by a {@link CodeArea}: one <em>view</em> of an
 * open file. The file's content, undo history, dirty state and saving live in an
 * {@link EditorBuffer} that every editor of the same file shares, so a {@link #cloneDocument()
 * clone} edits the very same text; what belongs to the view is the caret and selection, the
 * line-number gutter, breadcrumbs and completion.
 */
public class CodeEditor implements EditorDocument {

    // Package-private aliases for testing
    static final String LINE_SEPARATOR_ATTR = LineSeparatorSupport.ATTR;

    /** Pseudoclass active on the editor root while the editor does not accept edits. */
    private static final PseudoClass READONLY = PseudoClass.getPseudoClass("readonly");

    private final BorderPane rootPane;
    // Package-private aliases for testing
    final CodeArea codeArea;
    final BreadcrumbsBar breadcrumbsBar;

    private final EditorBuffer buffer;
    private final ReadOnlyBooleanWrapper hasSelection = new ReadOnlyBooleanWrapper(this, "hasSelection", false);
    private final CaretInfoSupport caretInfo;

    private final MarkLineNumberDecorator lineDecorator;
    private final LineHighlightSupport lineHighlight;
    private final BreadcrumbsSupport breadcrumbs;
    /** Find in this view; {@code null} for a preview. Package-private for tests. */
    final SearchSupport search;
    private EditorSettings editorSettings;
    private ChangeListener<Boolean> lineNumbersListener;

    /** Refreshes the gutter after style changes (analysis results); removed on {@link #dispose()}. */
    private final StyledTextModel.Listener styleListener = change -> {
        if (!change.isEdit()) {
            refreshLineDecorator();
        }
    };

    /** Every property subscription made by this editor, released together on {@link #dispose()}. */
    private Subscription subscription;

    /** Whether this editor is a read-only preview (no completion or context menu, never editable). */
    private final boolean preview;

    /**
     * Creates a new code editor for the given file. When the file is already open in another editor,
     * the new one shares its content, like a {@link #cloneDocument() clone}.
     *
     * @param fo the file object to open in the editor
     */
    public CodeEditor(FileObject fo) {
        this(EditorBuffer.open(fo), false);
    }

    /**
     * Creates a read-only preview of {@code fo}: an editor without completion or context menu that
     * never accepts edits, showing the live content of the file's open editors, if any.
     */
    public static CodeEditor preview(FileObject fo) {
        return new CodeEditor(EditorBuffer.open(fo), true);
    }

    /** Creates another view of {@code buffer}: a clone of the editors already showing it. */
    private CodeEditor(EditorBuffer buffer, boolean preview) {
        this.buffer = Objects.requireNonNull(buffer);
        this.preview = preview;
        FileObject fo = buffer.fileObject;
        CodeTextModel model = buffer.model;
        BaseSyntaxDecorator decorator = buffer.decorator;

        codeArea = new CodeArea(model);
        codeArea.setTabSize(4);
        codeArea.setWrapText(false);
        codeArea.setHighlightCurrentParagraph(true);
        WordSelectionSupport.install(codeArea);

        lineHighlight = new LineHighlightSupport(codeArea);
        StackPane areaWrapper = new StackPane(codeArea, lineHighlight.overlay());
        areaWrapper.getStyleClass().add("code-area-wrapper");
        rootPane = new BorderPane(areaWrapper);
        rootPane.getStyleClass().add("code-editor");
        if (preview) {
            rootPane.getStyleClass().add("preview");
        }
        rootPane.getStylesheets().add(
                Objects.requireNonNull(CodeEditor.class.getResource("codeeditor.css")).toExternalForm());

        // Custom left decorator that shows line numbers + error indicators with tooltips.
        lineDecorator = new MarkLineNumberDecorator(decorator, codeArea.fontProperty());
        bindLineNumbers();

        // caret info support
        caretInfo = new CaretInfoSupport(codeArea);

        codeArea.select(TextPos.ZERO);

        // Analysis results arrive as style changes: refresh the gutter of this view
        model.addListener(styleListener);

        // Highlight matching braces and mark occurrences as the caret moves, only in the next pulse,
        // after edit events, in order to prevent concurrent modification exceptions.
        // Clear highlight if there is a selection - unless the selection is exactly one identifier
        // (a double-clicked word): the occurrences of that identifier stay marked, as in NetBeans,
        // which also avoids a blink between the first click and the second. The highlights are
        // styles of the shared model, so they follow whichever view was last touched: a view
        // refreshes them again for its own caret when it gains focus (e.g. its tab is selected),
        // taking them over from a clone.
        Runnable refreshCaretHighlights = () -> Platform.runLater(() -> {
            if (codeArea.hasNonEmptySelection()) {
                decorator.clearBraceMatch(model);
                SelectionSegment selection = codeArea.getSelection();
                if (WordSelectionSupport.isIdentifierSelection(codeArea, selection)) {
                    decorator.setOccurrenceExclusion(model, selection);
                    decorator.updateOccurrencesInBackground(model, selection.getMin());
                } else {
                    decorator.setOccurrenceExclusion(model, null);
                    decorator.clearOccurrences(model);
                }
            } else {
                TextPos caret = codeArea.getCaretPosition();
                decorator.setOccurrenceExclusion(model, null);
                decorator.updateBraceMatch(model, caret);
                decorator.updateOccurrencesInBackground(model, caret);
            }
        });

        // subscriptions: the buffer locks every view at once when the file changes under unsaved edits
        subscription = buffer.editableProperty().subscribe(editable -> codeArea.setEditable(editable && !preview));
        subscription = subscription
                .and(codeArea.editableProperty().subscribe(editable ->
                        rootPane.pseudoClassStateChanged(READONLY, !Boolean.TRUE.equals(editable))))
                .and(codeArea.caretPositionProperty().subscribe((_, _) -> refreshCaretHighlights.run()))
                .and(codeArea.focusedProperty().subscribe(focused -> {
                    if (focused) {
                        refreshCaretHighlights.run();
                    }
                }))
                .and(codeArea.anchorPositionProperty().subscribe((_, _) -> refreshCaretHighlights.run())
                .and(codeArea.selectionProperty().subscribe(_ -> {
                        hasSelection.set(codeArea.hasNonEmptySelection());
                        caretInfo.schedule();
                    })));

        codeArea.addEventFilter(MouseEvent.MOUSE_RELEASED, _ -> Platform.runLater(caretInfo::flush));

        // Bottom side bars, as NetBeans' South editor side bar: the search bars (added on demand,
        // above) and the breadcrumbs bar, whose visibility is driven by the shared "show
        // breadcrumbs" setting - its close button turns the setting off, which also unchecks the
        // View menu toggle.
        breadcrumbsBar = new BreadcrumbsBar(() -> {
            if (editorSettings != null) {
                editorSettings.showBreadcrumbs().set(false);
            }
        });
        VBox bottom = new VBox(breadcrumbsBar);
        bottom.getStyleClass().add("editor-side-bars");
        rootPane.setBottom(bottom);
        search = preview ? null : new SearchSupport(codeArea, model, decorator, bottom);
        if (editorSettings != null) {
            subscription = subscription.and(editorSettings.showBreadcrumbs().subscribe(show -> {
                breadcrumbsBar.setVisible(show);
                breadcrumbsBar.setManaged(show);
            }));
        }

        // Debounced caret-driven breadcrumbs queries plus crumb-click navigation
        breadcrumbs = new BreadcrumbsSupport(fo, codeArea, breadcrumbsBar, editorSettings);
        subscription = subscription.and(breadcrumbs.install());

        // Tooltip for squiggly error/warning diagnostics on mouse hover
        DiagnosticsSupport.install(codeArea, decorator, model);

        if (!preview) {
            // Install completion controller (manual trigger: Ctrl + Space)
            new CompletionController(fo, codeArea, model).install();
            // The registry-driven context menu (Cut / Copy / Paste and whatever modules add)
            EditorContextMenu.install(codeArea);
        }

        buffer.attach(this);
    }

    @Override
    public EditorDocument cloneDocument() {
        return new CodeEditor(buffer, preview);
    }

    @Override
    public boolean sharesContentWith(EditorDocument other) {
        return other instanceof CodeEditor editor && editor.buffer == buffer;
    }

    /** The shared buffer's decorator - the brace / occurrence / search highlights live there. Package-private for tests. */
    BaseSyntaxDecorator decorator() {
        return buffer.decorator;
    }

    /**
     * Returns the root node of this editor, suitable for adding to a scene graph.
     *
     * @return the editor's root node
     */
    @Override
    public Node getNode() {
        return rootPane;
    }

    @Override
    public FileObject getFileObject() {
        return buffer.fileObject;
    }

    @Override
    public String getProjectPath() {
        String resolved = buffer.projectPath;
        if (resolved == null) {
            resolved = EditorDocument.super.getProjectPath();
            buffer.projectPath = resolved;
        }
        return resolved;
    }

    @Override
    public String getTitle() {
        return buffer.fileObject.getNameExt();
    }

    @Override
    public ObservableValue<String> lineSeparatorProperty() {
        return buffer.lineSeparator().property();
    }

    @Override
    public void setLineSeparator(String separator) {
        buffer.setLineSeparator(separator);
    }

    @Override
    public void dispose() {
        if (search != null) {
            search.dispose();
        }
        lineHighlight.dispose();
        buffer.model.removeListener(styleListener);
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        buffer.detach(this);
    }

    @Override
    public boolean isModified() {
        return buffer.isModified();
    }

    @Override
    public ReadOnlyBooleanProperty modifiedProperty() {
        return buffer.modifiedProperty();
    }

    @Override
    public void undo() {
        TextInputControl field = focusedField();
        if (field != null) {
            field.undo();
        } else {
            codeArea.undo();
        }
    }

    @Override
    public void redo() {
        TextInputControl field = focusedField();
        if (field != null) {
            field.redo();
        } else {
            codeArea.redo();
        }
    }

    @Override
    public void copy() {
        TextInputControl field = focusedField();
        if (field != null) {
            field.copy();
        } else {
            codeArea.copy();
        }
    }

    @Override
    public void cut() {
        TextInputControl field = focusedField();
        if (field != null) {
            field.cut();
        } else {
            codeArea.cut();
        }
    }

    @Override
    public void paste() {
        TextInputControl field = focusedField();
        if (field != null) {
            field.paste();
        } else {
            codeArea.paste();
        }
    }

    /**
     * The text field of one of this editor's side bars (the search field) when it owns the focus:
     * the menu's Cut / Copy / Paste / Undo / Redo accelerators reach the document, so they are
     * routed to the field the user is typing in, as they would be in a plain text field.
     */
    private TextInputControl focusedField() {
        if (rootPane.getScene() == null) {
            return null;
        }
        Node owner = rootPane.getScene().getFocusOwner();
        if (owner instanceof TextInputControl field) {
            Node n = owner;
            while (n != null) {
                if (n == rootPane) {
                    return field;
                }
                n = n.getParent();
            }
        }
        return null;
    }

    @Override
    public ReadOnlyBooleanProperty canUndoProperty() {
        return codeArea.undoableProperty();
    }

    @Override
    public ReadOnlyBooleanProperty canRedoProperty() {
        return codeArea.redoableProperty();
    }

    @Override
    public ReadOnlyBooleanProperty hasSelectionProperty() {
        return hasSelection.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty editableProperty() {
        return codeArea.editableProperty();
    }

    @Override
    public int getCaretParagraph() {
        TextPos caret = codeArea.getCaretPosition();
        return caret == null ? 0 : caret.index();
    }

    @Override
    public int getCaretColumn() {
        TextPos caret = codeArea.getCaretPosition();
        return caret == null ? 0 : caret.offset();
    }

    @Override
    public int getTopParagraph() {
        // Map the top-left of the visible text area (in screen coordinates) to a paragraph index.
        if (codeArea.getScene() == null || codeArea.getScene().getWindow() == null) {
            return getCaretParagraph();
        }
        Point2D top = codeArea.localToScreen(codeArea.getWidth() / 2, 2);
        if (top == null) {
            return getCaretParagraph();
        }
        TextPos pos = codeArea.getTextPosition(top.getX(), top.getY());
        return pos == null ? getCaretParagraph() : pos.index();
    }

    @Override
    public void restoreView(int topParagraph, int caretParagraph, int caretColumn) {
        RestoreViewSupport.restore(codeArea, topParagraph, caretParagraph, caretColumn);
    }

    @Override
    public int getCaretOffset() {
        return TextOffsets.offsetOf(buffer.model, codeArea.getCaretPosition());
    }

    @Override
    public void selectRange(int start, int end) {
        TextPos anchor = TextOffsets.positionOf(buffer.model, start);
        TextPos caret = TextOffsets.positionOf(buffer.model, end);
        RestoreViewSupport.whenSkinned(codeArea, () -> codeArea.select(anchor, caret));
    }

    @Override
    public boolean replaceRange(int start, int end, String text) {
        if (!codeArea.isEditable()) {
            return false;
        }
        TextPos from = TextOffsets.positionOf(buffer.model, start);
        TextPos to = TextOffsets.positionOf(buffer.model, end);
        codeArea.replaceText(from, to, text);
        return true;
    }

    @Override
    public void revealRange(int start, int end) {
        TextPos position = TextOffsets.positionOf(buffer.model, start);
        lineHighlight.highlight(position.index());
        breadcrumbs.showFor(position);
    }

    @Override
    public void requestFocus() {
        EditorFocusSupport.requestWhenReady(codeArea);
    }

    @Override
    public void showFind() {
        if (search != null) {
            search.showFind();
        }
    }

    @Override
    public void findNext() {
        if (search != null) {
            search.findNext();
        }
    }

    @Override
    public void findPrevious() {
        if (search != null) {
            search.findPrevious();
        }
    }

    @Override
    public void showReplace() {
        if (search != null) {
            search.showReplace();
        }
    }

    @Override
    public void findSelection() {
        if (search != null) {
            search.findSelection();
        }
    }

    @Override
    public String getSelectedText() {
        SelectionSegment selection = codeArea.getSelection();
        if (selection == null || selection.isCollapsed()) {
            return "";
        }
        TextPos min = selection.getMin();
        TextPos max = selection.getMax();
        StringBuilder text = new StringBuilder();
        for (int i = min.index(); i <= max.index(); i++) {
            String line = buffer.model.getPlainText(i);
            int from = i == min.index() ? Math.min(min.offset(), line.length()) : 0;
            int to = i == max.index() ? Math.min(max.offset(), line.length()) : line.length();
            text.append(line, from, to);
            if (i < max.index()) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    @Override
    public ObservableValue<CaretInfo> caretInfoProperty() {
        return caretInfo.property();
    }

    /**
     * Persists the content back to the {@link FileObject}, through the shared buffer.
     * <p>
     * Must be invoked on the JavaFX Application Thread, since it reads the editor content.
     * If the file was modified externally since it was opened (or last saved), the save is
     * aborted with an {@link IOException} to avoid overwriting those changes.
     *
     * @throws IOException if a write conflict is detected or writing the content fails
     */
    @Override
    public void save() throws IOException {
        buffer.save();
    }

    /** Binds the line-number gutter to the shared {@link EditorSettings#showLineNumbers()} setting. */
    private void bindLineNumbers() {
        editorSettings = Lookup.getDefault().lookup(EditorSettings.class);
        refreshLineDecorator();
        if (editorSettings != null) {
            lineNumbersListener = (_, _, _) -> refreshLineDecorator();
            editorSettings.showLineNumbers().addListener(new WeakChangeListener<>(lineNumbersListener));
        }
    }

    /** Refreshes the line-number gutter for the current setting. */
    private void refreshLineDecorator() {
        lineDecorator.setShowLineNumbers(editorSettings == null || editorSettings.showLineNumbers().get());
        codeArea.setLeftDecorator(null);
        codeArea.setLeftDecorator(lineDecorator);
    }

    /** Places the caret back at (or as close as possible to) its position before an external reload. */
    void restoreCaretAfterReload(TextPos caret) {
        if (caret == null) {
            codeArea.select(TextPos.ZERO);
            return;
        }
        int paragraph = Math.clamp(caret.index(), 0, Math.max(0, codeArea.getParagraphCount() - 1));
        TextPos end = codeArea.getParagraphEnd(paragraph);
        int column = Math.clamp(caret.offset(), 0, end != null ? end.offset() : 0);
        codeArea.select(TextPos.ofLeading(paragraph, column));
    }

}
