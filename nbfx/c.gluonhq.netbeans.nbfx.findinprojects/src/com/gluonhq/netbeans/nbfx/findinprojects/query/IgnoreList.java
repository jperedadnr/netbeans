package com.gluonhq.netbeans.nbfx.findinprojects.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.filesystems.FileObject;
import org.openide.util.NbPreferences;

/**
 * The folders and path patterns "Use Ignore List" skips - NetBeans' {@code IgnoreListPanel} data,
 * persisted as {@code FindDialogMemory} does ({@code ignore_list_<i>}). A folder entry hides the
 * folder and everything below it; a pattern entry is a regular expression that hides every file or
 * folder whose path matches it.
 */
public final class IgnoreList {

    public enum Kind {
        FOLDER, PATTERN
    }

    /** One entry: a folder path or a path regular expression. */
    public record Entry(Kind kind, String value) {

        public Entry {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(value);
        }

        public static Entry folder(FileObject folder) {
            return new Entry(Kind.FOLDER, folder.getPath());
        }

        public static Entry pattern(String regex) {
            Pattern.compile(regex);
            return new Entry(Kind.PATTERN, regex);
        }

        String serialize() {
            return (kind == Kind.FOLDER ? "folder:" : "pattern:") + value;
        }

        static Entry parse(String text) {
            if (text.startsWith("folder:")) {
                return new Entry(Kind.FOLDER, text.substring("folder:".length()));
            }
            if (text.startsWith("pattern:")) {
                return new Entry(Kind.PATTERN, text.substring("pattern:".length()));
            }
            return null;
        }
    }

    private static final String KEY_PREFIX = "ignore_list_";

    /** Created on first use, so tests with their own preferences never touch the user's. */
    private static final class Default {
        static final IgnoreList INSTANCE = new IgnoreList(NbPreferences.forModule(IgnoreList.class));
    }

    private final Preferences preferences;
    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private volatile List<Pattern> patterns = List.of();
    private volatile List<String> folders = List.of();

    /** The shared, persisted list. */
    public static IgnoreList getDefault() {
        return Default.INSTANCE;
    }

    /** A list backed by {@code preferences} (tests use an in-memory node). */
    public IgnoreList(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        load();
        entries.addListener((javafx.collections.ListChangeListener<Entry>) change -> {
            compile();
            store();
        });
    }

    /** The entries, modifiable; changes are persisted and applied at once. */
    public ObservableList<Entry> getEntries() {
        return entries;
    }

    /** Whether {@code fo} - a file or a folder - is hidden by this list. Safe to call from any thread. */
    public boolean matches(FileObject fo) {
        String path = fo.getPath();
        for (String folder : folders) {
            if (path.equals(folder) || path.startsWith(folder + "/")) {
                return true;
            }
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(path).find()) {
                return true;
            }
        }
        return false;
    }

    private void load() {
        List<Entry> loaded = new ArrayList<>();
        for (int i = 0; ; i++) {
            String value = preferences.get(KEY_PREFIX + i, null);
            if (value == null) {
                break;
            }
            Entry entry = Entry.parse(value);
            if (entry != null) {
                loaded.add(entry);
            }
        }
        entries.setAll(loaded);
        compile();
    }

    private void compile() {
        List<String> newFolders = new ArrayList<>();
        List<Pattern> newPatterns = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.kind() == Kind.FOLDER) {
                newFolders.add(entry.value().replace('\\', '/'));
            } else {
                try {
                    newPatterns.add(Pattern.compile(entry.value()));
                } catch (PatternSyntaxException ignored) {
                    // A malformed persisted pattern hides nothing.
                }
            }
        }
        folders = List.copyOf(newFolders);
        patterns = List.copyOf(newPatterns);
    }

    private void store() {
        try {
            for (String key : preferences.keys()) {
                if (key.startsWith(KEY_PREFIX)) {
                    preferences.remove(key);
                }
            }
        } catch (BackingStoreException ignored) {
            // Best effort: the entries below overwrite what they can.
        }
        for (int i = 0; i < entries.size(); i++) {
            preferences.put(KEY_PREFIX + i, entries.get(i).serialize());
        }
    }
}
