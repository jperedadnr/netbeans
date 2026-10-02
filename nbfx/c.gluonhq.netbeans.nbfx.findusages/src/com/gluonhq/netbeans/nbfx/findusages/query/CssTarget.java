package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.findusages.query.CssScanner.Kind;
import com.gluonhq.netbeans.nbfx.findusages.query.CssScanner.Symbol;
import java.io.IOException;
import java.util.List;
import org.openide.filesystems.FileObject;

/**
 * A symbol of a style sheet a query looks for: a class selector, an id selector or a colour - hex,
 * as NetBeans' {@code CssRefactoringInfo} knows (its resource references are not ported), or one of
 * JavaFX's colour names, which it does not.
 * Occurrences are found by scanning every style sheet under the sources' roots with the
 * {@link CssScanner}; nothing else than CSS is searched.
 */
public final class CssTarget implements SearchTarget {

    private final FileObject file;
    private final int offset;
    private final Kind kind;
    private final String name;

    private CssTarget(FileObject file, int offset, Kind kind, String name) {
        this.file = file;
        this.offset = offset;
        this.kind = kind;
        this.name = name;
    }

    /**
     * The symbol at {@code offset} (an editor offset) of the style sheet {@code file}, read through
     * {@code sources} so unsaved edits count.
     *
     * @return the target, or {@code null} when the caret is not on a class, id or colour
     */
    public static CssTarget at(FileObject file, int offset, SourceSet sources) throws IOException {
        Symbol symbol = CssScanner.at(CssScanner.scan(CssSearch.textOf(sources, file)), offset);
        return symbol == null ? null : new CssTarget(file, offset, symbol.kind(), symbol.name());
    }

    /** Whether {@code file} is a style sheet a target can be picked in. */
    public static boolean isStyleSheet(FileObject file) {
        return file != null && "css".equalsIgnoreCase(file.getExt());
    }

    @Override
    public FileObject getFile() {
        return file;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    /** Whether this is a class selector, an id selector or a colour. */
    public String getKindName() {
        return kind.name();
    }

    /** The symbol as written, prefix included: {@code .button}, {@code #root}, {@code #ff8800}, {@code white}. */
    @Override
    public String getSimpleName() {
        return kind.prefix + name;
    }

    @Override
    public String getDisplayName() {
        return getSimpleName();
    }

    @Override
    public boolean isFilterable() {
        return false;
    }

    @Override
    public Search newSearch(SourceSet sources, boolean searchComments) {
        return new CssSearch(this, sources);
    }

    /** The symbols of {@code symbols} that are occurrences of this target. */
    List<Symbol> occurrencesIn(List<Symbol> symbols) {
        return symbols.stream().filter(symbol -> symbol.matches(kind, name)).toList();
    }

    @Override
    public String toString() {
        return kind + " " + getSimpleName();
    }
}
