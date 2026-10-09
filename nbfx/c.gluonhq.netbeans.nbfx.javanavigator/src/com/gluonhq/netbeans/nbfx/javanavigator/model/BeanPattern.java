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

import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode.Segment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One row of the Navigator's Bean Patterns tree: a class or interface of the file, or one of the
 * JavaBeans patterns its methods form - a property ({@code getX} / {@code isX} / {@code setX}), an
 * indexed property ({@code getX(int)} / {@code setX(int, T)}) or an event set
 * ({@code addXListener} / {@code removeXListener}) - as NetBeans' {@code beans} module
 * ({@code PatternAnalyser}) finds them. Rows are immutable once their scan has completed.
 */
public final class BeanPattern {

    public enum Kind {
        PROPERTY, INDEXED_PROPERTY, EVENT_SET, CLASS
    }

    /** Which accessors a property has. */
    public enum Mode {
        READ_WRITE, READ_ONLY, WRITE_ONLY
    }

    private final Kind kind;
    private final String name;
    private final String type;
    private final String indexedType;
    private final Mode mode;
    private final boolean unicast;
    private final boolean interfaceType;
    private final int start;
    private final int end;
    private final int openOffset;
    private final List<BeanPattern> children = new ArrayList<>();

    /**
     * @param kind          what the row is
     * @param name          the property / event set / type name
     * @param type          the property type or the listener type, {@code null} for a class
     * @param indexedType   the element type of an indexed property, {@code null} otherwise
     * @param mode          a property's accessors, {@code null} otherwise
     * @param unicast       whether an event set admits one listener ({@code addXListener} throws
     *                      {@code TooManyListenersException})
     * @param interfaceType whether a class row is an interface
     * @param start         the offset where the pattern's declarations begin, {@code -1} when unknown
     * @param end           the offset where they end, {@code -1} when unknown
     * @param openOffset    where the caret goes on open - the getter's name, else the setter's, the
     *                      add method's, the type's - or {@code -1}
     */
    BeanPattern(Kind kind, String name, String type, String indexedType, Mode mode, boolean unicast,
            boolean interfaceType, int start, int end, int openOffset) {
        this.kind = Objects.requireNonNull(kind);
        this.name = Objects.requireNonNull(name);
        this.type = type;
        this.indexedType = indexedType;
        this.mode = mode;
        this.unicast = unicast;
        this.interfaceType = interfaceType;
        this.start = start;
        this.end = end;
        this.openOffset = openOffset;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    /** The property type (simple names, {@code int[]} for arrays) or the listener type; {@code null} for a class. */
    public String getType() {
        return type;
    }

    /** The element type of an indexed property, {@code null} for other rows. */
    public String getIndexedType() {
        return indexedType;
    }

    /** The accessors of a property, {@code null} for other rows. */
    public Mode getMode() {
        return mode;
    }

    /** Whether an event set admits a single listener. */
    public boolean isUnicast() {
        return unicast;
    }

    /** Whether a class row is an interface. */
    public boolean isInterface() {
        return interfaceType;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    /** Whether {@code offset} lies in one of the declarations forming this pattern. */
    public boolean contains(int offset) {
        return start >= 0 && offset >= start && offset <= end;
    }

    /** The offset the caret goes to when the row is opened, or {@code -1} when unknown. */
    public int getOpenOffset() {
        return openOffset;
    }

    /** The patterns of a class, in display order; empty for other rows. */
    public List<BeanPattern> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** The label: the name, then the type (and the indexed type) muted, as NetBeans shows them. */
    public List<Segment> getLabel() {
        List<Segment> label = new ArrayList<>();
        label.add(new Segment(name, false));
        if (kind == Kind.PROPERTY || kind == Kind.INDEXED_PROPERTY) {
            label.add(new Segment(" : ", false));
            String types = type == null ? "" : type;
            if (indexedType != null) {
                types = types.isEmpty() ? indexedType : types + ", " + indexedType;
            }
            label.add(new Segment(types, true));
        }
        return label;
    }

    /** The label as one string, e.g. {@code count : int}. */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        getLabel().forEach(segment -> sb.append(segment.text()));
        return sb.toString();
    }

    /** Identifies the row across scans of the same file, so the tree keeps a class row's expansion. */
    public String getKey() {
        return kind.name() + ":" + name;
    }

    void addChild(BeanPattern child) {
        children.add(child);
    }

    @Override
    public String toString() {
        return getText();
    }
}
