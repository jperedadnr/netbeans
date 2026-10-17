/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.structure.css;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.css.CssTokens;
import com.gluonhq.netbeans.nbfx.api.css.CssTokens.Token;
import com.gluonhq.netbeans.nbfx.api.css.CssTokens.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the structure of a style sheet the way NetBeans' CSS navigator presents it: its rules (by
 * selector group), the classes, ids and element names its selectors use, its at-rules and imports.
 * The scan is a tolerant pass over the sheet's {@link CssTokens tokens} - braces are matched, what is
 * left open at the end still counts - so a sheet being edited still yields what is well-formed.
 *
 * @since 1.0
 */
public final class CssScanner {

    /**
     * A named item of a sheet and where it is: a rule from its selector to its closing brace, a
     * class / id / element name's first occurrence, an at-rule or an import statement.
     */
    public record Item(String name, int start, int end) {
    }

    /** What a scan found; the lists are in source order, each name once in the name lists. */
    public record Structure(List<Item> rules, List<Item> classes, List<Item> ids, List<Item> elements,
            List<Item> atRules, List<Item> imports) {

        /** The innermost rule enclosing {@code offset}, or {@code null}. */
        public Item ruleAt(int offset) {
            Item found = null;
            for (Item rule : rules) {
                if (rule.start() <= offset && offset < rule.end()
                        && (found == null || rule.end() - rule.start() < found.end() - found.start())) {
                    found = rule;
                }
            }
            return found;
        }
    }

    /** The pseudo-classes whose argument is a selector list. */
    private static final Set<String> SELECTOR_FUNCTIONS = Set.of("not", "is", "where", "has", "matches");

    /** An open brace block: a rule, an at-rule or the body of a keyframes-like at-rule. */
    private record Block(String name, int start, boolean rule, boolean opaque) {
    }

    private final String text;
    private final List<Token> tokens;
    private final Cancellation cancellation;
    private final List<Item> rules = new ArrayList<>();
    private final Map<String, Item> classes = new LinkedHashMap<>();
    private final Map<String, Item> ids = new LinkedHashMap<>();
    private final Map<String, Item> elements = new LinkedHashMap<>();
    private final List<Item> atRules = new ArrayList<>();
    private final List<Item> imports = new ArrayList<>();
    private final Deque<Block> blocks = new ArrayDeque<>();

    private CssScanner(String text, Cancellation cancellation) {
        this.text = text;
        this.tokens = CssTokens.tokenize(text);
        this.cancellation = cancellation;
    }

    /**
     * Scans {@code text}.
     *
     * @param text         the style sheet
     * @param cancellation polled now and then; a cancelled scan returns {@code null}
     * @return the structure, or {@code null} when cancelled
     */
    public static Structure scan(String text, Cancellation cancellation) {
        return new CssScanner(text, cancellation).scan();
    }

    private Structure scan() {
        // The prelude is what precedes a '{' or ';' since the last delimiter: a selector group, an
        // at-rule's head, or a declaration.
        int preludeStart = 0;
        int preludeToken = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if ((i & 255) == 0 && cancellation.isCancelled()) {
                return null;
            }
            Token token = tokens.get(i);
            switch (token.type()) {
                case LBRACE -> {
                    String prelude = normalize(text.substring(preludeStart, token.start()));
                    int start = preludeToken < i ? tokens.get(preludeToken).start() : token.start();
                    Block enclosing = blocks.peek();
                    boolean opaque = enclosing != null && enclosing.opaque();
                    if (prelude.startsWith("@")) {
                        blocks.push(new Block(prelude, start, false, opaque || isOpaque(prelude)));
                    } else if (!prelude.isEmpty() && !opaque) {
                        blocks.push(new Block(prelude, start, true, false));
                        collectSelectorNames(preludeToken, i);
                    } else {
                        blocks.push(new Block(prelude, start, false, opaque));
                    }
                }
                case RBRACE -> {
                    Block block = blocks.poll();
                    if (block != null) {
                        close(block, token.end());
                    }
                }
                case SEMI -> {
                    if (blocks.isEmpty()) {
                        statement(preludeStart, preludeToken, i, token.end());
                    }
                }
                default -> {
                    continue;
                }
            }
            preludeStart = token.end();
            preludeToken = i + 1;
        }
        if (blocks.isEmpty()) {
            statement(preludeStart, preludeToken, tokens.size(), text.length());
        }
        // Blocks left open at the end of a sheet being edited still count, up to the end.
        while (!blocks.isEmpty()) {
            close(blocks.poll(), text.length());
        }
        rules.sort((a, b) -> Integer.compare(a.start(), b.start()));
        atRules.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return new Structure(Collections.unmodifiableList(rules), List.copyOf(classes.values()),
                List.copyOf(ids.values()), List.copyOf(elements.values()),
                Collections.unmodifiableList(atRules), Collections.unmodifiableList(imports));
    }

    private void close(Block block, int end) {
        if (block.rule()) {
            rules.add(new Item(block.name(), block.start(), end));
        } else if (block.name().startsWith("@")) {
            atRules.add(new Item(block.name(), block.start(), end));
        }
    }

    /** A top-level statement ending in {@code ;}: {@code @import}, {@code @charset}, {@code @namespace}. */
    private void statement(int preludeStart, int preludeToken, int endToken, int end) {
        if (preludeToken >= endToken) {
            return;
        }
        String prelude = normalize(text.substring(preludeStart, tokens.get(endToken - 1).end()));
        if (!prelude.startsWith("@")) {
            return;
        }
        int start = tokens.get(preludeToken).start();
        if (prelude.startsWith("@import")) {
            imports.add(new Item(importTarget(prelude.substring("@import".length()).trim()), start, end));
        } else {
            atRules.add(new Item(prelude, start, end));
        }
    }

    /** The resource an {@code @import} names: the string or {@code url()} argument, unquoted. */
    private static String importTarget(String argument) {
        String target = argument;
        if (target.startsWith("url(")) {
            int close = target.indexOf(')');
            target = (close < 0 ? target.substring(4) : target.substring(4, close)).trim();
        } else {
            int space = target.indexOf(' ');
            if (space > 0) {
                target = target.substring(0, space);
            }
        }
        if (target.length() >= 2 && (target.charAt(0) == '"' || target.charAt(0) == '\'')
                && target.charAt(target.length() - 1) == target.charAt(0)) {
            target = target.substring(1, target.length() - 1);
        }
        return target;
    }

    /** Whether the body of the at-rule {@code name} holds no rules of the sheet (keyframe selectors). */
    private static boolean isOpaque(String name) {
        return name.contains("keyframes") || name.startsWith("@font-feature-values");
    }

    /**
     * Records the classes, ids and element names of the selector group in the tokens
     * {@code [from, to)}: {@code .name}, {@code #name}, and a word that is neither a pseudo-class nor
     * a number. Attribute selectors are skipped; the argument of a pseudo-class function
     * ({@code :not(.a)}) is read like any selector, that of the others ({@code :nth-child(2n)}) is not.
     */
    private void collectSelectorNames(int from, int to) {
        int i = from;
        while (i < to) {
            Token token = tokens.get(i);
            switch (token.type()) {
                case LBRACKET -> i = skipTo(i, to, Type.LBRACKET, Type.RBRACKET);
                case COLON -> {
                    int j = i + 1;
                    while (j < to && tokens.get(j).type() == Type.COLON) {
                        j++;
                    }
                    if (j < to && tokens.get(j).type() == Type.WORD) {
                        String pseudo = tokens.get(j).text().toLowerCase();
                        j++;
                        if (j < to && tokens.get(j).type() == Type.LPAREN && !SELECTOR_FUNCTIONS.contains(pseudo)) {
                            j = skipTo(j, to, Type.LPAREN, Type.RPAREN);
                        }
                    }
                    i = j;
                }
                case CLASS -> {
                    String name = "." + token.text();
                    classes.putIfAbsent(name, new Item(name, token.start(), token.end()));
                    i++;
                }
                case ID -> {
                    String name = "#" + token.text();
                    ids.putIfAbsent(name, new Item(name, token.start(), token.end()));
                    i++;
                }
                case WORD -> {
                    if (!token.isNumber()) {
                        elements.putIfAbsent(token.text(), new Item(token.text(), token.start(), token.end()));
                    }
                    i++;
                }
                default -> i++;
            }
        }
    }

    /** The index after the {@code close} matching the {@code open} at {@code i}, or {@code to}. */
    private int skipTo(int i, int to, Type open, Type close) {
        int depth = 0;
        for (int j = i; j < to; j++) {
            Type type = tokens.get(j).type();
            if (type == open) {
                depth++;
            } else if (type == close && --depth == 0) {
                return j + 1;
            }
        }
        return to;
    }

    /** Collapses the whitespace runs of {@code prelude} and drops its comments. */
    private static String normalize(String prelude) {
        StringBuilder out = new StringBuilder(prelude.length());
        boolean space = false;
        for (int i = 0; i < prelude.length(); i++) {
            char c = prelude.charAt(i);
            if (c == '/' && i + 1 < prelude.length() && prelude.charAt(i + 1) == '*') {
                int close = prelude.indexOf("*/", i + 2);
                i = close < 0 ? prelude.length() : close + 1;
                continue;
            }
            if (Character.isWhitespace(c)) {
                space = true;
                continue;
            }
            if (space && out.length() > 0) {
                out.append(' ');
            }
            space = false;
            out.append(c);
        }
        return out.toString();
    }
}
