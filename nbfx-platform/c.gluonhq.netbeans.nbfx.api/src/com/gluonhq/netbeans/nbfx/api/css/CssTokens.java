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
package com.gluonhq.netbeans.nbfx.api.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lexical structure of a style sheet, read the same way wherever CSS text is analysed outside
 * the editor: Find Usages' symbols and the Navigator's rules both work on these tokens. Comments and
 * whitespace are dropped; an unterminated comment or string runs to the end of the text (a string to
 * the end of its line), so a sheet being edited still tokenizes.
 *
 * @since 1.0
 */
public final class CssTokens {

    /** What a token is. */
    public enum Type {
        /** An identifier or a number, {@code -fx-fill}, {@code 1.5em}, {@code white}. */
        WORD,
        /** A class selector, {@code .button}; the token's text is the name without the dot. */
        CLASS,
        /** A hash, {@code #root} or {@code #ff8800}; the token's text is the name without the hash. */
        ID,
        /** An at-keyword, {@code @media}; the token's text is the name without the at sign. */
        AT_KEYWORD,
        /** A quoted string, quotes included. */
        STRING,
        LBRACE, RBRACE, SEMI, COLON, COMMA, LPAREN, RPAREN, LBRACKET, RBRACKET,
        /** Any other single character: {@code >}, {@code +}, {@code ~}, {@code *}, {@code =}, {@code !}. */
        OTHER
    }

    /**
     * A token: its type, its text ({@linkplain Type#CLASS the name} for a prefixed token, else what
     * was written) and its {@code [start, end)} bounds in the text, the prefix character included.
     */
    public record Token(Type type, String text, int start, int end) {

        /** Whether this is a word made of digits (and dots), a number rather than a name. */
        public boolean isNumber() {
            return type == Type.WORD && !text.isEmpty() && Character.isDigit(text.charAt(0));
        }
    }

    private CssTokens() {
    }

    /** Whether {@code c} can be part of a name: a letter, a digit, {@code -}, {@code _} or a non-ASCII character. */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c > 127;
    }

    /** Tokenizes {@code text}. */
    public static List<Token> tokenize(CharSequence text) {
        List<Token> tokens = new ArrayList<>();
        int length = text.length();
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int close = indexOf(text, "*/", i + 2);
                i = close < 0 ? length : close + 2;
            } else if (c == '"' || c == '\'') {
                int end = stringEnd(text, i);
                tokens.add(new Token(Type.STRING, text.subSequence(i, end).toString(), i, end));
                i = end;
            } else if ((c == '.' || c == '#' || c == '@') && i + 1 < length && isNameStart(text, i + 1, c == '#')) {
                int end = wordEnd(text, i + 1);
                Type type = c == '.' ? Type.CLASS : c == '#' ? Type.ID : Type.AT_KEYWORD;
                tokens.add(new Token(type, text.subSequence(i + 1, end).toString(), i, end));
                i = end;
            } else if (c == '.' && i + 1 < length && Character.isDigit(text.charAt(i + 1))) {
                // A number without an integer part, .5em
                int end = wordEnd(text, i + 1);
                tokens.add(new Token(Type.WORD, text.subSequence(i, end).toString(), i, end));
                i = end;
            } else if (isWordChar(c) || c == '\\') {
                int end = wordEnd(text, i);
                // A number's fraction, 1.5em: the dot continues the digits before it.
                if (end < length && text.charAt(end) == '.' && allDigits(text, i, end)
                        && end + 1 < length && Character.isDigit(text.charAt(end + 1))) {
                    end = wordEnd(text, end + 1);
                }
                tokens.add(new Token(Type.WORD, text.subSequence(i, end).toString(), i, end));
                i = end;
            } else {
                Type type = switch (c) {
                    case '{' -> Type.LBRACE;
                    case '}' -> Type.RBRACE;
                    case ';' -> Type.SEMI;
                    case ':' -> Type.COLON;
                    case ',' -> Type.COMMA;
                    case '(' -> Type.LPAREN;
                    case ')' -> Type.RPAREN;
                    case '[' -> Type.LBRACKET;
                    case ']' -> Type.RBRACKET;
                    default -> Type.OTHER;
                };
                tokens.add(new Token(type, String.valueOf(c), i, i + 1));
                i++;
            }
        }
        return Collections.unmodifiableList(tokens);
    }

    /** Whether a name can start at {@code index}: a word character, an escape; a digit too for a hash. */
    private static boolean isNameStart(CharSequence text, int index, boolean digitsAllowed) {
        char c = text.charAt(index);
        if (c == '\\') {
            return true;
        }
        return isWordChar(c) && (digitsAllowed || !Character.isDigit(c));
    }

    /** The index after the word starting at {@code from}: word characters and backslash escapes. */
    private static int wordEnd(CharSequence text, int from) {
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                i += 2;
            } else if (isWordChar(c)) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean allDigits(CharSequence text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return from < to;
    }

    /** The index after the string literal opening at {@code open}: its closing quote, or the end of the line. */
    private static int stringEnd(CharSequence text, int open) {
        char quote = text.charAt(open);
        int i = open + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (c == '\n') {
                return i;
            } else {
                i++;
            }
        }
        return text.length();
    }

    private static int indexOf(CharSequence text, String what, int from) {
        return text.toString().indexOf(what, from);
    }
}
