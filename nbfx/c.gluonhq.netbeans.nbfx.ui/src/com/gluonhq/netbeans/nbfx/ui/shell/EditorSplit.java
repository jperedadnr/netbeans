package com.gluonhq.netbeans.nbfx.ui.shell;

import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.util.Subscription;

/**
 * Splits an editor tab in two, as NetBeans' <em>Split Document</em>: the tab's content becomes a
 * {@link SplitPane} holding the document's editor and a {@linkplain EditorDocument#cloneDocument()
 * clone} of it, so two places of the same file are edited at once. The clone is the tab's private
 * business - it is not an open document of its own (not registered with the editor context, not
 * closable) and is disposed of with the split or the tab - but it becomes the active
 * document while it has the focus, so the caret status, Undo, Save and friends follow the half the
 * user is typing in. Vertically stacks the editors, Horizontally puts them side by side, as in NetBeans.
 */
public final class EditorSplit {

    private static final String SPLIT_KEY = "nbfx.editor.split";

    /** The split of a tab: its pane, the clone in its second half, and the focus subscriptions. */
    private record State(SplitPane pane, EditorDocument second, Subscription subscription) {}

    private EditorSplit() {}

    /** Whether {@code tab} is currently split. */
    public static boolean isSplit(Tab tab) {
        return tab != null && tab.getProperties().get(SPLIT_KEY) != null;
    }

    /** Whether {@code tab} holds a document that can be split. */
    public static boolean canSplit(Tab tab) {
        return NbfxTabPane.documentOf(tab) != null;
    }

    /**
     * Splits {@code tab} with the given orientation, or re-orients an existing split. Does nothing for
     * a tab without a document, or whose document cannot be cloned.
     */
    public static void split(Tab tab, Orientation orientation) {
        EditorDocument document = NbfxTabPane.documentOf(tab);
        if (document == null) {
            return;
        }
        State state = stateOf(tab);
        if (state != null) {
            state.pane().setOrientation(orientation);
            return;
        }
        EditorDocument clone = document.cloneDocument();
        if (clone == null) {
            return;
        }
        Node first = document.getNode();
        Node second = clone.getNode();
        SplitPane pane = new SplitPane(first, second);
        pane.getStyleClass().add("nbfx-editor-split-pane");
        pane.setOrientation(orientation);
        tab.setContent(pane);
        Subscription subscription = first.focusWithinProperty().subscribe(focused -> {
            if (focused) {
                NbfxTabPane.setActiveDocument(document);
            }
        }).and(second.focusWithinProperty().subscribe(focused -> {
            if (focused) {
                NbfxTabPane.setActiveDocument(clone);
            }
        }));
        tab.getProperties().put(SPLIT_KEY, new State(pane, clone, subscription));
        clone.restoreView(document.getTopParagraph(), document.getCaretParagraph(), document.getCaretColumn());
    }

    /** Removes the split of {@code tab}, keeping the document's own editor as the tab content. */
    public static void clear(Tab tab) {
        State state = stateOf(tab);
        EditorDocument document = NbfxTabPane.documentOf(tab);
        if (state == null || document == null) {
            return;
        }
        boolean cloneHadFocus = state.second().getNode().isFocusWithin();
        release(tab, state);
        tab.setContent(document.getNode());
        if (cloneHadFocus) {
            NbfxTabPane.setActiveDocument(document);
            document.requestFocus();
        }
    }

    /** The orientation of {@code tab}'s split, or {@code null} if it is not split. */
    public static Orientation orientationOf(Tab tab) {
        State state = stateOf(tab);
        return state == null ? null : state.pane().getOrientation();
    }

    /** The clone in the second half of {@code tab}'s split, or {@code null} if it is not split. */
    public static EditorDocument secondOf(Tab tab) {
        State state = stateOf(tab);
        return state == null ? null : state.second();
    }

    /** Disposes of the split of {@code tab}, if any, when the tab is closed for good. */
    static void dispose(Tab tab) {
        State state = stateOf(tab);
        if (state != null) {
            release(tab, state);
        }
    }

    /**
     * The document of {@code tab} that has (or last had) the focus: the clone while the second half
     * is focused, else the tab's own document; {@code null} for a tab without a document.
     */
    static EditorDocument focusedDocumentOf(Tab tab) {
        EditorDocument document = NbfxTabPane.documentOf(tab);
        State state = stateOf(tab);
        if (document != null && state != null && state.second().getNode().isFocusWithin()) {
            return state.second();
        }
        return document;
    }

    private static void release(Tab tab, State state) {
        tab.getProperties().remove(SPLIT_KEY);
        state.subscription().unsubscribe();
        state.pane().getItems().clear();
        state.second().dispose();
    }

    private static State stateOf(Tab tab) {
        return tab == null ? null : (State) tab.getProperties().get(SPLIT_KEY);
    }
}
