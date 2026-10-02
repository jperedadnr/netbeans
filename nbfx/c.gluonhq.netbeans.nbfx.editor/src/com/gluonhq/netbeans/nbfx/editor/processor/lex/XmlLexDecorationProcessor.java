package com.gluonhq.netbeans.nbfx.editor.processor.lex;

import com.gluonhq.netbeans.nbfx.editor.decoration.LineDecoration;
import com.gluonhq.netbeans.nbfx.editor.decoration.TokenCategory;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.SourceContext;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.TextPosResult;
import jfx.incubator.scene.control.richtext.TextPos;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.xml.lexer.XMLTokenId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The XML/FXML counterpart of {@link JavaLexDecorationProcessor}: it tokenizes a full
 * XML source with the NetBeans XML lexer and produces the {@link LineDecoration}s for
 * its tokens, and resolves caret-driven tag-match requests.
 *
 * <p>Every {@link XMLTokenId} whose primary category maps to a styled
 * {@link TokenCategory} is decorated: element names (with their angle brackets),
 * attribute names, attribute values, comments, doctype declarations, CDATA
 * sections and processing instructions (with a bold target); text content and
 * whitespace stay undecorated.</p>
 *
 * <p>Tag matching replaces brace matching: a caret anywhere in a start or end tag
 * (including its angle brackets, right before {@code <} or right after {@code >})
 * highlights the tag delimiters of both tags of the pair; unpaired tags get the
 * mismatch highlight. On self-closing tags only the {@code <} and {@code />} brackets
 * trigger and get highlighted, and likewise the {@code <?} / {@code ?>} delimiters of
 * a processing instruction.</p>
 */
public class XmlLexDecorationProcessor extends BaseLexDecorationProcessor {

    private enum TagKind { START, END, SELF_CLOSING, PI }

    /** A tag or processing instruction: the bounds of its delimiter tokens and its pair. */
    private static final class TagEntry {

        TagKind kind;
        final String name;
        final int index;
        /** Bounds of the opening token: {@code <name}, {@code </name} or {@code <?}. */
        final int nameStart;
        final int nameEnd;
        /** Bounds of the closing token: {@code >}, {@code />} or {@code ?>}; -1 while unclosed. */
        int closeStart = -1;
        int closeEnd = -1;
        /** Index of the matching start/end entry, or -1 when unpaired. */
        int partner = -1;

        TagEntry(TagKind kind, String name, int index, int nameStart, int nameEnd) {
            this.kind = kind;
            this.name = name;
            this.index = index;
            this.nameStart = nameStart;
            this.nameEnd = nameEnd;
        }
    }

    /** The tags and processing instructions of the last scan, in document order. */
    private List<TagEntry> tagEntries;

    /** @param context a {@link SourceContext} with an {@link XMLTokenId} token hierarchy */
    public XmlLexDecorationProcessor(SourceContext context) {
        super(context);
    }

    @Override
    protected void onInvalidated() {
        tagEntries = null;
    }

    @Override
    protected List<List<LineDecoration>> analyze() {
        int[] lineStarts = context.lineStarts();
        int[] lineLengths = context.lineLengths();
        tagEntries = new ArrayList<>();
        List<List<LineDecoration>> results = new ArrayList<>(lineStarts.length);
        for (int i = 0; i < lineStarts.length; i++) {
            results.add(new ArrayList<>());
        }
        TokenSequence<XMLTokenId> tokenSequence = context.hierarchy()
                .tokenSequence(XMLTokenId.language());
        if (tokenSequence == null) {
            return results;
        }
        Deque<Integer> openTags = new ArrayDeque<>();
        TagEntry pending = null;
        tokenSequence.moveStart();
        while (tokenSequence.moveNext()) {
            Token<XMLTokenId> token = tokenSequence.token();
            int globalStart = tokenSequence.offset();
            int globalEnd = globalStart + token.length();
            pending = collectTagEntry(token, globalStart, globalEnd, pending, openTags);
            TokenCategory tc = TokenCategory.fromCategory(token.id().primaryCategory());
            if (tc == null || tc.style() == null) {
                continue;
            }
            TextPosResult.toLineDecorationMap(globalStart, globalEnd, lineStarts, lineLengths, tc.style())
                    .forEach((line, list) -> results.get(line).addAll(list));
        }
        return results;
    }

    /**
     * Finds the tag pair (or PI delimiters) at the caret: both tags of a start/end pair
     * with the match style, or the delimiters of an unpaired tag with the mismatch style.
     *
     * @param caret the current caret position in document coordinates
     * @return the delimiter highlight ranges, or an empty list when the caret
     *         doesn't touch a tag
     */
    @Override
    public List<TextPosResult> findBraceMatchResults(TextPos caret) {
        String source = source();
        if (source == null || source.isEmpty() || caret == null) {
            return List.of();
        }
        ensureAnalyzed();
        int caretOffset = caretOffset(caret);
        // Prefer the tag whose delimiter ends at the caret, so between two adjacent
        // tags ("...></...") the caret binds to the one it just left.
        TagEntry hit = null;
        for (TagEntry entry : tagEntries) {
            if (inZone(entry, caretOffset)
                    && (caretOffset == entry.closeEnd || caretOffset == entry.nameEnd)) {
                hit = entry;
                break;
            }
        }
        if (hit == null) {
            for (TagEntry entry : tagEntries) {
                if (inZone(entry, caretOffset)) {
                    hit = entry;
                    break;
                }
            }
        }
        return hit == null ? List.of() : resultsFor(hit);
    }

    /**
     * Records the tag / PI delimiter tokens into {@link #tagEntries}, pairing start and
     * end tags by name with a stack (skipping unclosed tags in between).
     *
     * @return the entry still waiting for its closing token, or {@code null}
     */
    private TagEntry collectTagEntry(Token<XMLTokenId> token, int globalStart, int globalEnd,
            TagEntry pending, Deque<Integer> openTags) {
        switch (token.id()) {
            case TAG -> {
                String text = token.text().toString();
                if (">".contentEquals(text) || "/>".contentEquals(text)) {
                    if (pending == null || pending.kind == TagKind.PI) {
                        return pending;
                    }
                    pending.closeStart = globalStart;
                    pending.closeEnd = globalEnd;
                    if (text.charAt(0) == '/' && pending.kind == TagKind.START) {
                        pending.kind = TagKind.SELF_CLOSING;
                    }
                    if (pending.kind == TagKind.START) {
                        openTags.push(pending.index);
                    } else if (pending.kind == TagKind.END
                            && openTags.stream().anyMatch(i -> tagEntries.get(i).name.equals(pending.name))) {
                        TagEntry start;
                        do {
                            start = tagEntries.get(openTags.pop());
                        } while (!start.name.equals(pending.name));
                        start.partner = pending.index;
                        pending.partner = start.index;
                    }
                    return null;
                }
                TagKind kind = text.startsWith("</") ? TagKind.END : TagKind.START;
                String name = text.substring(kind == TagKind.END ? 2 : 1);
                TagEntry entry = new TagEntry(kind, name, tagEntries.size(), globalStart, globalEnd);
                tagEntries.add(entry);
                return entry;
            }
            case PI_START -> {
                TagEntry entry = new TagEntry(TagKind.PI, "", tagEntries.size(), globalStart, globalEnd);
                tagEntries.add(entry);
                return entry;
            }
            case PI_END -> {
                if (pending != null && pending.kind == TagKind.PI) {
                    pending.closeStart = globalStart;
                    pending.closeEnd = globalEnd;
                    return null;
                }
                return pending;
            }
            default -> {
                return pending;
            }
        }
    }

    /** Whether the caret offset triggers the highlight for the given entry. */
    private static boolean inZone(TagEntry entry, int offset) {
        return switch (entry.kind) {
            // only the angle brackets trigger: right before/after '<' or on the "/>" token
            case SELF_CLOSING -> offset == entry.nameStart || offset == entry.nameStart + 1
                    || (offset >= entry.closeStart && offset <= entry.closeEnd);
            // only the "<?" and "?>" delimiters trigger
            case PI -> (offset >= entry.nameStart && offset <= entry.nameEnd)
                    || (entry.closeStart >= 0 && offset >= entry.closeStart && offset <= entry.closeEnd);
            // anywhere in the tag, from right before '<' to right after '>'
            case START, END -> offset >= entry.nameStart
                    && offset <= (entry.closeEnd >= 0 ? entry.closeEnd : entry.nameEnd);
        };
    }

    /** Builds the highlight ranges for the entry's delimiters and those of its pair. */
    private List<TextPosResult> resultsFor(TagEntry entry) {
        int[] lineStarts = context.lineStarts();
        String match = TokenCategory.BRACE_MATCH.style();
        String mismatch = TokenCategory.BRACE_MISMATCH.style();
        return switch (entry.kind) {
            case SELF_CLOSING -> List.of(
                    TextPosResult.from(entry.nameStart, entry.nameStart + 1, lineStarts, match),
                    TextPosResult.from(entry.closeStart, entry.closeEnd, lineStarts, match));
            case PI -> entry.closeStart < 0
                    ? List.of(TextPosResult.from(entry.nameStart, entry.nameEnd, lineStarts, mismatch))
                    : List.of(TextPosResult.from(entry.nameStart, entry.nameEnd, lineStarts, match),
                            TextPosResult.from(entry.closeStart, entry.closeEnd, lineStarts, match));
            case START, END -> {
                if (entry.partner < 0) {
                    yield entry.closeStart < 0
                            ? List.of(TextPosResult.from(entry.nameStart, entry.nameEnd, lineStarts, mismatch))
                            : List.of(TextPosResult.from(entry.nameStart, entry.nameEnd, lineStarts, mismatch),
                                    TextPosResult.from(entry.closeStart, entry.closeEnd, lineStarts, mismatch));
                }
                TagEntry partner = tagEntries.get(entry.partner);
                yield List.of(
                        TextPosResult.from(entry.nameStart, entry.nameEnd, lineStarts, match),
                        TextPosResult.from(entry.closeStart, entry.closeEnd, lineStarts, match),
                        TextPosResult.from(partner.nameStart, partner.nameEnd, lineStarts, match),
                        TextPosResult.from(partner.closeStart, partner.closeEnd, lineStarts, match));
            }
        };
    }
}
