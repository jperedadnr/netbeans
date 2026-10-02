package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import com.gluonhq.netbeans.nbfx.findusages.query.CssScanner.Symbol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import org.openide.filesystems.FileObject;

/**
 * The search for a {@link CssTarget}: every style sheet under the sources' roots (that the sources
 * accept) is scanned, the target's own first, and each symbol matching the target becomes a usage. All the sheets count,
 * as NetBeans does with "Find all occurrences" - there is no CSS dependency graph to narrow them.
 */
final class CssSearch implements SearchTarget.Search {

    private final CssTarget target;
    private final SourceSet sources;

    CssSearch(CssTarget target, SourceSet sources) {
        this.target = target;
        this.sources = sources;
    }

    @Override
    public boolean targetExists() throws IOException {
        return target.getFile().isValid()
                && !target.occurrencesIn(CssScanner.scan(textOf(sources, target.getFile()))).isEmpty();
    }

    @Override
    public void run(Cancellation cancellation, Consumer<List<Usage>> sink, DoubleConsumer progress) throws IOException {
        List<FileObject> candidates = candidates(cancellation);
        if (candidates.isEmpty()) {
            progress.accept(1);
            return;
        }
        int scanned = 0;
        for (FileObject file : candidates) {
            if (cancellation.isCancelled()) {
                return;
            }
            List<Usage> found = scan(file);
            if (!found.isEmpty()) {
                sink.accept(found);
            }
            scanned++;
            progress.accept((double) scanned / candidates.size());
        }
    }

    private List<FileObject> candidates(Cancellation cancellation) {
        LinkedHashSet<FileObject> result = new LinkedHashSet<>();
        if (sources.accepts(target.getFile())) {
            result.add(target.getFile());
        }
        for (FileObject root : sources.roots()) {
            Enumeration<? extends FileObject> children = root.getChildren(true);
            while (children.hasMoreElements()) {
                if (cancellation.isCancelled()) {
                    break;
                }
                FileObject file = children.nextElement();
                if (file.isData() && CssTarget.isStyleSheet(file) && sources.accepts(file)) {
                    result.add(file);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private List<Usage> scan(FileObject file) throws IOException {
        String text = textOf(sources, file);
        List<Symbol> occurrences = target.occurrencesIn(CssScanner.scan(text));
        if (occurrences.isEmpty()) {
            return List.of();
        }
        FileContext context = FileContext.ofResource(file, sources.rootOf(file));
        List<Usage> usages = new ArrayList<>(occurrences.size());
        for (Symbol symbol : occurrences) {
            usages.add(usage(text, symbol, context));
        }
        return usages;
    }

    private static Usage usage(String text, Symbol symbol, FileContext context) {
        int lineStart = text.lastIndexOf('\n', symbol.start() - 1) + 1;
        int lineEnd = text.indexOf('\n', symbol.end());
        lineEnd = lineEnd < 0 ? text.length() : lineEnd;
        int line = 1;
        for (int i = 0; i < lineStart; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        // the text begins at the first non-blank character of the line
        int textStart = lineStart;
        while (textStart < symbol.start() && Character.isWhitespace(text.charAt(textStart))) {
            textStart++;
        }
        String lineText = text.substring(textStart, lineEnd).stripTrailing();
        return new Usage(context.file(), symbol.start(), symbol.end(), line, textStart, lineText,
                null, false, false, context.inTestRoot(), context.context(List.of()));
    }

    /**
     * The text of {@code file} in the editor's convention - one {@code '\n'} per line break - so
     * every offset the scan yields is an editor offset, whether the text came from an open editor
     * or from a disk file with {@code \r\n} line breaks.
     */
    static String textOf(SourceSet sources, FileObject file) throws IOException {
        return sources.textOf(file).replace("\r", "");
    }
}
