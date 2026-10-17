package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.api.css.CssTokens;
import com.gluonhq.netbeans.nbfx.api.css.CssTokens.Token;
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
 * {@code transparent}), which NetBeans does not know. The text is read through {@link CssTokens}.
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
        for (Token token : CssTokens.tokenize(source)) {
            switch (token.type()) {
                case LBRACE -> {
                    // The run was a selector: its classes and ids are symbols.
                    for (Pending word : run) {
                        if (word.prefix() != '\0') {
                            symbols.add(new Symbol(word.prefix() == '.' ? Kind.CLASS : Kind.ID, word.name(), word.start(), word.end()));
                        }
                    }
                    run.clear();
                    colonSeen = false;
                }
                case RBRACE, SEMI -> {
                    // The run was a declaration: the words of its value may be colours.
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
                }
                case COLON -> colonSeen = true;
                case CLASS -> run.add(new Pending('.', token.text(), token.start(), token.end(), colonSeen));
                case ID -> run.add(new Pending('#', token.text(), token.start(), token.end(), colonSeen));
                case WORD -> {
                    if (colonSeen) {
                        run.add(new Pending('\0', token.text(), token.start(), token.end(), true));
                    }
                }
                default -> {
                }
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
}
