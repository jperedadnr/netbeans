package com.gluonhq.netbeans.nbfx.findinprojects.replace;

import com.gluonhq.netbeans.nbfx.api.search.TextSearch;
import com.gluonhq.netbeans.nbfx.api.search.TextSearch.Match;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FileResult;
import com.gluonhq.netbeans.nbfx.findinprojects.model.Issue;
import com.gluonhq.netbeans.nbfx.findinprojects.model.TextMatch;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Applies a Replace in Projects search to the matches the user kept checked - NetBeans'
 * {@code ReplaceTask}. Per file, the checked matches are replaced from the last to the first, so
 * the offsets found by the search stay valid, each replacement computed by
 * {@link TextSearch#replacement} (regular-expression groups, preserve case). A file that is open
 * in an editor is edited through its buffer, so Undo works and its tab turns dirty; a file on disk
 * is rewritten with its own charset, line separator and byte order mark. A match whose text has
 * moved or changed since the search is <em>outdated</em>: it is skipped and counted, and the user
 * rescans. Archive entries and read-only files are reported as issues.
 */
public final class ReplaceTask {

    /** One replacement in a file's text: {@code [start, end)} becomes {@code text}. */
    public record Edit(int start, int end, String text) {

        public Edit {
            Objects.requireNonNull(text);
        }
    }

    /**
     * The open editors, so a file being edited is changed in its buffer rather than on disk. The
     * default one asks the editor module through the Lookup; tests supply their own.
     */
    public interface Buffers {

        /** The editor text of {@code file} with {@code '\n'} line breaks, or {@code null} when it is not open. */
        String textOf(FileObject file);

        /**
         * Applies {@code edits} - ordered from the last to the first - to the open editor of
         * {@code file}, whose text was {@code snapshot} when they were computed.
         *
         * @return {@code false} when the file is no longer open, read-only, or its text is not
         *         {@code snapshot} any more: nothing was changed
         */
        boolean replace(FileObject file, String snapshot, List<Edit> edits);
    }

    /**
     * What a replace did.
     *
     * @param replaced the matches replaced, by file, in the order they were given
     * @param outdated the checked matches skipped because their text had changed since the search
     * @param issues   the files that could not be changed, and why
     */
    public record Outcome(Map<FileResult, List<TextMatch>> replaced, int outdated, List<Issue> issues) {

        public Outcome {
            replaced = Map.copyOf(replaced);
            issues = List.copyOf(issues);
        }

        public int replacedCount() {
            return replaced.values().stream().mapToInt(List::size).sum();
        }
    }

    private static final RequestProcessor PROCESSOR = new RequestProcessor("Replace in Projects", 1, true);

    private ReplaceTask() {
    }

    /**
     * Replaces {@code selection} - the checked matches by file - for the replace search
     * {@code criteria} in the background, editing open files through the editors.
     */
    public static CompletableFuture<Outcome> run(SearchCriteria criteria, Map<FileResult, List<TextMatch>> selection) {
        Objects.requireNonNull(criteria);
        Map<FileResult, List<TextMatch>> copy = new LinkedHashMap<>();
        selection.forEach((result, matches) -> copy.put(result, List.copyOf(matches)));
        CompletableFuture<Outcome> outcome = new CompletableFuture<>();
        PROCESSOR.post(() -> {
            try {
                outcome.complete(replace(criteria, copy, new EditorBuffers()));
            } catch (RuntimeException ex) {
                outcome.completeExceptionally(ex);
            }
        });
        return outcome;
    }

    /** {@link #run} on the calling thread with the given editors ({@code null}: every file is on disk). */
    public static Outcome replace(SearchCriteria criteria, Map<FileResult, List<TextMatch>> selection, Buffers buffers) {
        if (!criteria.isReplace()) {
            throw new IllegalArgumentException("not a replace search");
        }
        TextSearch found = criteria.text().compile();
        TextSearch search = TextSearch.compile(found.getQuery(),
                found.getOptions().withPreserveCase(criteria.replacement().preserveCase()));
        String replaceWith = criteria.replacement().text();

        Map<FileResult, List<TextMatch>> replaced = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();
        int outdated = 0;
        for (Map.Entry<FileResult, List<TextMatch>> entry : selection.entrySet()) {
            FileResult result = entry.getKey();
            FileObject file = result.file();
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (FileUtil.getArchiveFile(file) != null) {
                issues.add(issue(file, "ISSUE_Archive"));
                continue;
            }
            String buffer = buffers == null ? null : buffers.textOf(file);
            TextFile disk = null;
            String text = buffer;
            if (buffer == null) {
                if (!file.canWrite()) {
                    issues.add(issue(file, "ISSUE_ReadOnly"));
                    continue;
                }
                try {
                    disk = TextFile.read(file);
                } catch (CharacterCodingException ex) {
                    issues.add(issue(file, "ISSUE_Encoding", String.valueOf(ex.getMessage())));
                    continue;
                } catch (IOException ex) {
                    issues.add(issue(file, "ISSUE_Read", String.valueOf(ex.getMessage())));
                    continue;
                }
                text = disk.text();
            }

            // the matches as they are now: a checked match not among them has moved or changed
            List<Match> current = search.findAll(text);
            List<TextMatch> matches = new ArrayList<>(entry.getValue());
            matches.sort(Comparator.comparingInt(TextMatch::start).reversed());
            List<Edit> edits = new ArrayList<>();
            List<TextMatch> done = new ArrayList<>();
            boolean failed = false;
            for (TextMatch match : matches) {
                if (TextSearch.indexOf(current, match.start(), match.end()) < 0) {
                    outdated++;
                    continue;
                }
                try {
                    edits.add(new Edit(match.start(), match.end(),
                            search.replacement(text, new Match(match.start(), match.end(), false), replaceWith)));
                    done.add(match);
                } catch (IllegalArgumentException ex) {
                    issues.add(issue(file, "ISSUE_Replacement", String.valueOf(ex.getMessage())));
                    failed = true;
                    break;
                }
            }
            if (failed || edits.isEmpty()) {
                continue;
            }

            if (disk == null) {
                if (buffers.replace(file, text, edits)) {
                    replaced.put(result, sortedByStart(done));
                } else {
                    issues.add(issue(file, "ISSUE_BufferChanged"));
                }
                continue;
            }
            StringBuilder sb = new StringBuilder(text);
            for (Edit edit : edits) {
                sb.replace(edit.start(), edit.end(), edit.text());
            }
            try {
                disk.write(file, sb.toString());
                replaced.put(result, sortedByStart(done));
            } catch (IOException ex) {
                issues.add(issue(file, "ISSUE_Write", String.valueOf(ex.getMessage())));
            }
        }
        return new Outcome(replaced, outdated, issues);
    }

    private static List<TextMatch> sortedByStart(List<TextMatch> matches) {
        List<TextMatch> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.comparingInt(TextMatch::start));
        return sorted;
    }

    private static Issue issue(FileObject file, String key, Object... args) {
        return new Issue(FileUtil.getFileDisplayName(file), NbBundle.getMessage(ReplaceTask.class, key, args));
    }
}
