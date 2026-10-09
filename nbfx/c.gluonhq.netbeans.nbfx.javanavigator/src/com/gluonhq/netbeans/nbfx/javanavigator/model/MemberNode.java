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
package com.gluonhq.netbeans.nbfx.javanavigator.model;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import org.netbeans.api.java.source.ElementHandle;

/**
 * One row of the Navigator's Members tree: a type, constructor, method or field of the edited file,
 * or a member a type of the file inherits. It carries what the tree needs to show and filter the
 * row - name, kind, modifiers, where the member is declared in the document - and an
 * {@link ElementHandle} that resolves the element again in a later compilation (to open an
 * inherited member in its own file).
 * <p>
 * The label is kept as {@link Segment}s in two flavours - with simple and with fully qualified type
 * names - so toggling the <em>Fully Qualified Names</em> filter needs no rescan. Nodes are immutable
 * once their scan has completed; a scan appends the {@link #children()} of a type while it runs.
 * <p>
 * This is the Swing-free counterpart of {@code ElementNode.Description} in NetBeans'
 * {@code java.navigation} module.
 */
public final class MemberNode {

    /** A piece of a row's label: {@code type} pieces (parameter, return and super types) are drawn muted. */
    public record Segment(String text, boolean type) {
    }

    /** The source order of the rows: declared members by position, inherited ones after them, by name. */
    public static final Comparator<MemberNode> BY_POSITION = (a, b) -> {
        if (a.inherited != b.inherited) {
            return a.inherited ? 1 : -1;
        }
        if (a.inherited) {
            return alphaCompare(a, b);
        }
        return Integer.compare(a.start, b.start);
    };

    /** The alphabetical order of the rows: constructors, methods, fields, then types, each by name and signature. */
    public static final Comparator<MemberNode> BY_NAME = MemberNode::alphaCompare;

    private final String name;
    private final ElementKind kind;
    private final int modifiers;
    private final boolean inherited;
    private final boolean topLevel;
    private final boolean deprecated;
    private final int start;
    private final int end;
    private final int nameOffset;
    private final List<Segment> plain;
    private final List<Segment> qualified;
    private final ElementHandle<? extends Element> handle;
    private final List<MemberNode> children = new ArrayList<>();

    /**
     * @param name       the simple name (a constructor is named after its class)
     * @param kind       the element kind
     * @param modifiers  the {@link Modifier} bits (visibility, static, ...)
     * @param inherited  whether the member is declared in a supertype rather than in the file
     * @param topLevel   whether the member is a top-level type of the file
     * @param deprecated whether the element is deprecated
     * @param start      the offset of the declaration in the document, {@code -1} when not declared in it
     * @param end        the offset after the declaration, {@code -1} when not declared in the document
     * @param nameOffset the offset of the declared name - where the caret goes on open - or {@code -1}
     * @param plain      the label with simple type names
     * @param qualified  the label with fully qualified type names
     * @param handle     the element, or {@code null} when it cannot be described by a handle
     */
    MemberNode(String name, ElementKind kind, int modifiers, boolean inherited, boolean topLevel, boolean deprecated,
            int start, int end, int nameOffset, List<Segment> plain, List<Segment> qualified,
            ElementHandle<? extends Element> handle) {
        this.name = Objects.requireNonNull(name);
        this.kind = Objects.requireNonNull(kind);
        this.modifiers = modifiers;
        this.inherited = inherited;
        this.topLevel = topLevel;
        this.deprecated = deprecated;
        this.start = start;
        this.end = end;
        this.nameOffset = nameOffset;
        this.plain = List.copyOf(plain);
        this.qualified = List.copyOf(qualified);
        this.handle = handle;
    }

    /** The simple name; a constructor is named after its class. */
    public String getName() {
        return name;
    }

    public ElementKind getKind() {
        return kind;
    }

    /** The {@link Modifier} bits of the element. */
    public int getModifiers() {
        return modifiers;
    }

    public boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    /** Whether the member is declared in a supertype of the type it is listed under. */
    public boolean isInherited() {
        return inherited;
    }

    /** Whether the row is a top-level type of the file. */
    public boolean isTopLevel() {
        return topLevel;
    }

    /** Whether the row is a class, interface, enum, record or annotation type. */
    public boolean isType() {
        return kind.isClass() || kind.isInterface();
    }

    /** Whether the row is a type nested in another one of the file. */
    public boolean isInnerType() {
        return isType() && !topLevel;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    /** The offset of the declaration in the document, or {@code -1} when it is not declared there (inherited, implicit). */
    public int getStart() {
        return start;
    }

    /** The offset after the declaration in the document, or {@code -1} when it is not declared there. */
    public int getEnd() {
        return end;
    }

    /** Whether {@code offset} lies in the declaration of this member in the document. */
    public boolean contains(int offset) {
        return start >= 0 && offset >= start && offset <= end;
    }

    /**
     * The offset the caret goes to when the member is opened: its declared name, else the start of
     * the declaration; {@code -1} when the member is not declared in the document.
     */
    public int getOpenOffset() {
        return nameOffset >= 0 ? nameOffset : start;
    }

    /** The label, with simple ({@code fqn == false}) or fully qualified type names. */
    public List<Segment> getLabel(boolean fqn) {
        return fqn ? qualified : plain;
    }

    /** The label with simple type names as one string, e.g. {@code greet(String name) : void}. */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        plain.forEach(segment -> sb.append(segment.text()));
        return sb.toString();
    }

    /** The element, for resolving it again in another compilation; {@code null} when it has no handle. */
    public ElementHandle<? extends Element> getHandle() {
        return handle;
    }

    /** The members of a type, in the order the scan found them; empty for other rows. */
    public List<MemberNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Identifies the row across scans of the same file - the kind, the name and the plain label -
     * so the tree keeps a row's expansion when the file is edited.
     */
    public String getKey() {
        return kind.name() + ":" + (inherited ? "^" : "") + getText();
    }

    void addChild(MemberNode child) {
        children.add(child);
    }

    private static int alphaCompare(MemberNode a, MemberNode b) {
        int byKind = Integer.compare(kindOrder(a.kind), kindOrder(b.kind));
        if (byKind != 0) {
            return byKind;
        }
        int byName = a.name.compareTo(b.name);
        return byName != 0 ? byName : a.getText().compareTo(b.getText());
    }

    private static int kindOrder(ElementKind kind) {
        return switch (kind) {
            case CONSTRUCTOR -> 1;
            case METHOD -> 2;
            case FIELD -> 3;
            case CLASS, INTERFACE, RECORD, ENUM, ANNOTATION_TYPE, MODULE -> 4;
            default -> 100;
        };
    }

    @Override
    public String toString() {
        return getText();
    }
}
