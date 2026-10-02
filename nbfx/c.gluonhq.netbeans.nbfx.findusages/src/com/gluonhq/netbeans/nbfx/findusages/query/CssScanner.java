package com.gluonhq.netbeans.nbfx.findusages.query;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.paint.Color;

/**
 * Finds the refactorable symbols of a style sheet - the ones NetBeans' CSS refactoring knows:
 * class selectors, id selectors and hex colours - in a single character pass like the editor's
 * CSS lexer: words are buffered between structural delimiters and classified when one is reached.
 * A run ended by <code>{</code> is a selector, whose <code>.</code>-prefixed words are classes and
 * <code>#</code>-prefixed words ids; a run ended by <code>;</code> or <code>}</code> is a
 * declaration, whose <code>#</code>-prefixed hex words after the colon are colours - as are the
 * plain words there naming one of JavaFX's {@link Color} constants ({@code white},
 * {@code transparent}), which NetBeans does not know. Comments and strings are skipped.
 */
final class CssScanner {

    /** The kinds of symbol Find Usages can be started on in a style sheet. */
    enum Kind {
        CLASS(".", false), ID("#", false), COLOR("#", true), NAMED_COLOR("", true);

        final String prefix;
        /** Whether names compare case-insensitively, as CSS colours do. */
        final boolean ignoreCase;

        Kind(String prefix, boolean ignoreCase) {
            this.prefix = prefix;
            this.ignoreCase = ignoreCase;
        }
    }

    /** The colour names JavaFX CSS accepts: the {@link Color} constants, lower-cased. */
    private static final Set<String> COLOR_NAMES = Arrays.stream(Color.class.getFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == Color.class)
            .map(field -> field.getName().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * One symbol, with the {@code [start, end)} bounds of its text including the prefix character.
     *
     * @param name the name without the prefix; {@link #matches} compares colours case-insensitively
     */
    record Symbol(Kind kind, String name, int start, int end) {

        boolean matches(Kind otherKind, String otherName) {
            return kind == otherKind
                    && (kind.ignoreCase ? name.equalsIgnoreCase(otherName) : name.equals(otherName));
        }

        /** The symbol's text as written, prefix included. */
        String text() {
            return kind.prefix + name;
        }
    }

    private CssScanner() {
    }

    /** A prefixed word waiting for its run's delimiter to tell whether it is a selector or a value. */
    private record Pending(char prefix, String name, int start, int end, boolean afterColon) {
    }

    static List<Symbol> scan(String source) {
        List<Symbol> symbols = new ArrayList<>();
        List<Pending> run = new ArrayList<>();
        boolean colonSeen = false;
        int i = 0;
        int length = source.length();
        while (i < length) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
            } else if (c == '"' || c == '\'') {
                i = skipString(source, i);
            } else if (c == '{') {
                for (Pending word : run) {
                    if (word.prefix() == '\0') {
                        continue;
                    }
                    symbols.add(new Symbol(word.prefix() == '.' ? Kind.CLASS : Kind.ID, word.name(), word.start(), word.end()));
                }
                run.clear();
                colonSeen = false;
                i++;
            } else if (c == '}' || c == ';') {
                for (Pending word : run) {
                    if (!word.afterColon()) {
                        continue;
                    }
                    if (word.prefix() == '#' && isHex(word.name())) {
                        symbols.add(new Symbol(Kind.COLOR, word.name(), word.start(), word.end()));
                    } else if (word.prefix() == '\0' && COLOR_NAMES.contains(word.name().toLowerCase(Locale.ROOT))) {
                        symbols.add(new Symbol(Kind.NAMED_COLOR, word.name(), word.start(), word.end()));
                    }
                }
                run.clear();
                colonSeen = false;
                i++;
            } else if (c == ':') {
                colonSeen = true;
                i++;
            } else if ((c == '.' || c == '#') && i + 1 < length && isWordChar(source.charAt(i + 1))) {
                int start = i;
                i++;
                while (i < length && isWordChar(source.charAt(i))) {
                    i++;
                }
                run.add(new Pending(c, source.substring(start + 1, i), start, i, colonSeen));
            } else if (isWordChar(c)) {
                int start = i;
                while (i < length && isWordChar(source.charAt(i))) {
                    i++;
                }
                if (colonSeen && !isNameChar(source, start - 1)) {
                    run.add(new Pending('\0', source.substring(start, i), start, i, true));
                }
            } else {
                i++;
            }
        }
        return symbols;
    }

    /** The symbol whose text spans {@code offset} (its end inclusive), or {@code null}. */
    static Symbol at(List<Symbol> symbols, int offset) {
        for (Symbol symbol : symbols) {
            if (symbol.start() <= offset && offset <= symbol.end()) {
                return symbol;
            }
        }
        return null;
    }

    private static boolean isHex(String name) {
        int length = name.length();
        if (length != 3 && length != 4 && length != 6 && length != 8) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (Character.digit(name.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Whether the character at {@code index} continues a name, so a word after it is a sub-word (e.g. {@code -fx-white}). */
    private static boolean isNameChar(String source, int index) {
        return index >= 0 && (isWordChar(source.charAt(index)) || source.charAt(index) == '.' || source.charAt(index) == '#');
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    /** The index after the string literal opening at {@code start}, honouring backslash escapes. */
    private static int skipString(String source, int start) {
        char quote = source.charAt(start);
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return i + 1;
            }
            i++;
        }
        return source.length();
    }
}
