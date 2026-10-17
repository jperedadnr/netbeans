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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node of the structure an {@link XmlScanner} reads from an XML text: the document, or one of its
 * elements, processing instructions, document type declarations and CDATA sections, with its
 * offsets in the text, its attributes and the text content of its direct text children. Comments
 * and whitespace are not nodes.
 *
 * @since 1.0
 */
public final class XmlNode {

    /** What a node stands for. */
    public enum Kind {
        /** The whole text; its children are the top-level nodes. */
        DOCUMENT,
        /** An element, from its start tag to the end of its end tag. */
        ELEMENT,
        /** A processing instruction, the XML declaration included. */
        PI,
        /** A {@code <!DOCTYPE>} declaration, its internal subset included. */
        DOCTYPE,
        /** A CDATA section. */
        CDATA
    }

    private final Kind kind;
    private String name;
    private final int start;
    private int end;
    private int headEnd;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final StringBuilder text = new StringBuilder();
    private boolean error;
    private int descendantErrors;
    private XmlNode parent;
    private final List<XmlNode> children = new ArrayList<>();

    XmlNode(Kind kind, String name, int start) {
        this.kind = kind;
        this.name = name;
        this.start = start;
        this.end = start;
        this.headEnd = start;
    }

    /** What this node stands for. */
    public Kind kind() {
        return kind;
    }

    /**
     * The element's tag name, the processing instruction's target, the root element name a document
     * type declaration names; the empty string for the document and a CDATA section.
     */
    public String name() {
        return name;
    }

    /** The offset of the node's first character ({@code <}). */
    public int start() {
        return start;
    }

    /** The offset after the node's last character: after its end tag, or where it was cut short. */
    public int end() {
        return end;
    }

    /**
     * The offset after the node's head: an element's start tag, a processing instruction, a
     * declaration or a CDATA section as a whole - what the Maven reader records as an element's
     * position.
     */
    public int headEnd() {
        return headEnd;
    }

    /** An element's attributes in source order, values with their entity references as written. */
    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * The text content of the node's direct text children, each chunk trimmed, as written (entity
     * references included); a processing instruction's content, a declaration's body or a CDATA
     * section's data.
     */
    public String text() {
        return text.toString();
    }

    /** Whether the node itself is malformed: unclosed, or holding a stray end tag or a lexer error. */
    public boolean hasError() {
        return error;
    }

    /** How many of the node's descendants {@linkplain #hasError() are malformed}. */
    public int descendantErrors() {
        return descendantErrors;
    }

    /** The enclosing node; {@code null} for the document. */
    public XmlNode parent() {
        return parent;
    }

    /** The child nodes in source order: elements, processing instructions, declarations, CDATA. */
    public List<XmlNode> children() {
        return Collections.unmodifiableList(children);
    }

    /** The first child element named {@code name}, or {@code null}. */
    public XmlNode child(String name) {
        for (XmlNode child : children) {
            if (child.kind == Kind.ELEMENT && child.name.equals(name)) {
                return child;
            }
        }
        return null;
    }

    /**
     * The deepest node enclosing {@code offset}: this node when no child does, {@code null} for the
     * document when no top-level node does.
     */
    public XmlNode nodeAt(int offset) {
        for (XmlNode child : children) {
            if (offset < child.start) {
                break;
            }
            if (offset < child.end) {
                return child.nodeAt(offset);
            }
        }
        return kind == Kind.DOCUMENT ? null : this;
    }

    /**
     * A key identifying the node among the nodes of a scan by its path: each step a name and the
     * index among the siblings of that name, so the key survives rescans that keep the shape.
     */
    public String pathKey() {
        if (parent == null) {
            return "";
        }
        int index = 0;
        for (XmlNode sibling : parent.children) {
            if (sibling == this) {
                break;
            }
            if (sibling.kind == kind && sibling.name.equals(name)) {
                index++;
            }
        }
        String parentKey = parent.pathKey();
        return (parentKey.isEmpty() ? "" : parentKey + "/") + kind + ":" + name + "#" + index;
    }

    @Override
    public String toString() {
        return kind + " " + name + " [" + start + "," + end + ")";
    }

    // -- building ------------------------------------------------------------------------------

    void setName(String name) {
        this.name = name;
    }

    void setEnd(int end) {
        this.end = end;
    }

    void setHeadEnd(int headEnd) {
        this.headEnd = headEnd;
    }

    void putAttribute(String attributeName, String value) {
        attributes.put(attributeName, value);
    }

    void appendText(CharSequence chunk) {
        text.append(chunk);
    }

    void markError() {
        error = true;
    }

    void add(XmlNode child) {
        child.parent = this;
        children.add(child);
    }

    /** Counts the malformed descendants of this node and of every node below it. */
    int countErrors() {
        int count = 0;
        for (XmlNode child : children) {
            count += child.countErrors() + (child.error ? 1 : 0);
        }
        descendantErrors = count;
        return count;
    }
}
