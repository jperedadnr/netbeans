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
package com.gluonhq.netbeans.nbfx.structure.xml;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.structure.xml.XmlNode.Kind;
import java.util.ArrayDeque;
import java.util.Deque;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.xml.lexer.XMLTokenId;

/**
 * Reads the structure of an XML text with the NetBeans XML lexer, tolerating what an edited
 * document has: unclosed and stray tags are kept and marked as errors rather than stopping the
 * scan, so the Navigator keeps showing the rest.
 *
 * @since 1.0
 */
public final class XmlScanner {

    private static final String CDATA_START = "<![CDATA[";
    private static final String CDATA_END = "]]>";
    private static final String DOCTYPE_START = "<!DOCTYPE";

    private final Cancellation cancellation;
    private final XmlNode document;
    private final Deque<XmlNode> open = new ArrayDeque<>();
    /** The element whose start tag is being read, until its {@code >} or {@code />}. */
    private XmlNode pendingTag;
    /** The element whose end tag is being read, until its {@code >}. */
    private XmlNode closingTag;
    private String attributeName;
    private StringBuilder attributeValue;
    private XmlNode pi;
    private XmlNode doctype;
    /** The text run being read between two nodes: trimmed as a whole when the next node starts. */
    private final StringBuilder textRun = new StringBuilder();

    private XmlScanner(String text, Cancellation cancellation) {
        this.cancellation = cancellation;
        document = new XmlNode(Kind.DOCUMENT, "", 0);
        document.setEnd(text.length());
        open.push(document);
    }

    /**
     * Scans {@code text}.
     *
     * @param text         the XML text, with {@code '\n'} line breaks
     * @param cancellation polled now and then; a cancelled scan returns {@code null}
     * @return the document node, whose children are the top-level nodes; {@code null} when cancelled
     */
    public static XmlNode scan(String text, Cancellation cancellation) {
        XmlScanner scanner = new XmlScanner(text, cancellation);
        TokenHierarchy<String> hierarchy = TokenHierarchy.create(text, XMLTokenId.language());
        TokenSequence<XMLTokenId> tokens = hierarchy.tokenSequence(XMLTokenId.language());
        if (tokens == null || !scanner.read(tokens)) {
            return null;
        }
        return scanner.finish(text.length());
    }

    private boolean read(TokenSequence<XMLTokenId> tokens) {
        int count = 0;
        while (tokens.moveNext()) {
            if ((++count & 255) == 0 && cancellation.isCancelled()) {
                return false;
            }
            Token<XMLTokenId> token = tokens.token();
            int start = tokens.offset();
            int end = start + token.length();
            CharSequence text = token.text();
            if (doctype != null) {
                // A declaration runs until the '>' closing it; its strings, internal subset and
                // whitespace are all its own, a tag cuts it short.
                if (token.id() == XMLTokenId.TAG) {
                    doctype = null;
                } else {
                    doctype.setEnd(end);
                    doctype.setHeadEnd(end);
                    if (token.id() == XMLTokenId.DECLARATION && endsWith(text, '>')) {
                        doctype = null;
                    } else if (token.id() == XMLTokenId.DECLARATION || token.id() == XMLTokenId.VALUE) {
                        doctype.appendText(text);
                    }
                    continue;
                }
            }
            switch (token.id()) {
                case TAG -> tag(text, start, end);
                case ARGUMENT -> {
                    finishAttribute();
                    if (pendingTag != null) {
                        attributeName = text.toString();
                        attributeValue = new StringBuilder();
                    }
                }
                case VALUE -> {
                    if (attributeValue != null) {
                        attributeValue.append(text);
                    }
                }
                case CHARACTER -> {
                    if (attributeValue != null) {
                        attributeValue.append(text);
                    } else if (pendingTag == null && closingTag == null) {
                        textRun.append(text);
                    }
                }
                case TEXT -> {
                    if (pendingTag == null && closingTag == null) {
                        textRun.append(text);
                    }
                }
                case CDATA_SECTION -> {
                    flushText();
                    XmlNode cdata = new XmlNode(Kind.CDATA, "", start);
                    cdata.setEnd(end);
                    cdata.setHeadEnd(end);
                    cdata.appendText(cdataText(text));
                    open.peek().add(cdata);
                }
                case PI_START -> {
                    flushText();
                    pi = new XmlNode(Kind.PI, "", start);
                    pi.setEnd(end);
                    pi.setHeadEnd(end);
                    open.peek().add(pi);
                }
                case PI_TARGET -> {
                    if (pi != null) {
                        pi.setName(text.toString());
                        pi.setEnd(end);
                    }
                }
                case PI_CONTENT -> {
                    if (pi != null) {
                        pi.appendText(text.toString().trim());
                        pi.setEnd(end);
                    }
                }
                case PI_END -> {
                    if (pi != null) {
                        pi.setEnd(end);
                        pi.setHeadEnd(end);
                        pi = null;
                    }
                }
                case DECLARATION -> {
                    if (startsWith(text, DOCTYPE_START)) {
                        flushText();
                        doctype = new XmlNode(Kind.DOCTYPE, "", start);
                        doctype.setEnd(end);
                        doctype.setHeadEnd(end);
                        doctype.appendText(text.subSequence(DOCTYPE_START.length(), text.length()));
                        open.peek().add(doctype);
                        if (endsWith(text, '>')) {
                            doctype = null;
                        }
                    }
                }
                case ERROR -> {
                    XmlNode current = pendingTag != null ? pendingTag : open.peek();
                    if (current != document) {
                        current.markError();
                    }
                }
                default -> {
                }
            }
        }
        return true;
    }

    private void tag(CharSequence text, int start, int end) {
        if (">".contentEquals(text) || "/>".contentEquals(text)) {
            flushText();
            if (pendingTag != null) {
                finishAttribute();
                pendingTag.setHeadEnd(end);
                pendingTag.setEnd(end);
                if (text.charAt(0) != '/') {
                    open.push(pendingTag);
                }
                pendingTag = null;
            } else if (closingTag != null) {
                closingTag.setEnd(end);
                closingTag = null;
            }
            return;
        }
        flushText();
        if (startsWith(text, "</")) {
            if (pendingTag != null) {
                cutShort(pendingTag, start);
                pendingTag = null;
            }
            String name = text.subSequence(2, text.length()).toString().trim();
            XmlNode target = findOpen(name);
            if (target == null) {
                // A stray end tag: the innermost open element is malformed.
                if (open.peek() != document) {
                    open.peek().markError();
                }
                closingTag = null;
                return;
            }
            while (open.peek() != target) {
                cutShort(open.pop(), start);
            }
            open.pop();
            target.setEnd(end);
            closingTag = target;
            return;
        }
        if (pendingTag != null) {
            cutShort(pendingTag, start);
        }
        String name = text.subSequence(1, text.length()).toString().trim();
        XmlNode element = new XmlNode(Kind.ELEMENT, name, start);
        element.setEnd(end);
        element.setHeadEnd(end);
        open.peek().add(element);
        pendingTag = element;
    }

    /** {@code element} never got its end tag (or its start tag's {@code >}): it ends at {@code offset}. */
    private static void cutShort(XmlNode element, int offset) {
        element.markError();
        element.setEnd(Math.max(element.end(), offset));
        if (element.headEnd() > element.end()) {
            element.setHeadEnd(element.end());
        }
    }

    private XmlNode findOpen(String name) {
        for (XmlNode node : open) {
            if (node != document && node.name().equals(name)) {
                return node;
            }
        }
        return null;
    }

    private void finishAttribute() {
        if (attributeName != null && pendingTag != null) {
            pendingTag.putAttribute(attributeName, unquote(attributeValue));
        }
        attributeName = null;
        attributeValue = null;
    }

    /** Appends the text run read so far, trimmed, to the innermost open node. */
    private void flushText() {
        if (textRun.length() > 0) {
            open.peek().appendText(textRun.toString().trim());
            textRun.setLength(0);
        }
    }

    private XmlNode finish(int length) {
        flushText();
        if (pendingTag != null) {
            finishAttribute();
            cutShort(pendingTag, length);
        }
        while (open.peek() != document) {
            cutShort(open.pop(), length);
        }
        document.countErrors();
        return document;
    }

    private static String unquote(StringBuilder value) {
        String text = value.toString();
        if (text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '\'')
                && text.charAt(text.length() - 1) == text.charAt(0)) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static CharSequence cdataText(CharSequence text) {
        String data = text.toString();
        if (data.startsWith(CDATA_START)) {
            data = data.substring(CDATA_START.length());
        }
        if (data.endsWith(CDATA_END)) {
            data = data.substring(0, data.length() - CDATA_END.length());
        }
        return data.trim();
    }

    private static boolean startsWith(CharSequence text, String prefix) {
        return text.length() >= prefix.length() && prefix.contentEquals(text.subSequence(0, prefix.length()));
    }

    private static boolean endsWith(CharSequence text, char c) {
        return text.length() > 0 && text.charAt(text.length() - 1) == c;
    }
}
