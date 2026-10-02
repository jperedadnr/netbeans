package com.gluonhq.netbeans.nbfx.api.actions;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The ordered command ids of the editor's context menu, shared by every editor.
 * <p>
 * The menu starts with Cut / Copy / Paste; a module that wants an entry in it (e.g. Find Usages)
 * {@linkplain #add adds} the id of a command it registered through its {@link CommandsProvider}, a
 * {@link #SEPARATOR} first when it opens a new section. The editor builds the items each time the
 * menu opens, so ids may be added at any time, and ids with no registered command are skipped.
 */
public final class EditorContextMenuIds {

    /** A pseudo id standing for a separator between two groups of items. */
    public static final String SEPARATOR = "-";

    private static final ObservableList<String> IDS = FXCollections.observableArrayList(
            ActionIds.CUT, ActionIds.COPY, ActionIds.PASTE);

    private EditorContextMenuIds() {
    }

    /** The current ids, in menu order; observable, read-only. */
    public static ObservableList<String> ids() {
        return FXCollections.unmodifiableObservableList(IDS);
    }

    /** Appends {@code ids} (command ids or {@link #SEPARATOR}) to the menu, skipping those already present. */
    public static void add(String... ids) {
        for (String id : List.of(ids)) {
            if (SEPARATOR.equals(id) || !IDS.contains(id)) {
                IDS.add(id);
            }
        }
    }
}
