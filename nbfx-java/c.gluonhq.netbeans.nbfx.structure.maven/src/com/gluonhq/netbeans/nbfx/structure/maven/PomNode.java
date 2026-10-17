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
package com.gluonhq.netbeans.nbfx.structure.maven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.model.InputLocation;

/**
 * A row of the POM model tree: a POM element seen across the lineage, with its value (or presence)
 * in each layer - the project's POM first, then its parents - and where each layer declares it.
 */
final class PomNode {

    /** What a row stands for. */
    enum Kind {
        /** An element with a text value, shown as {@code Name : value}. */
        FIELD,
        /** An element with child elements. */
        OBJECT,
        /** An element listing items of one kind (dependencies, plugins, modules). */
        LIST
    }

    private final Kind kind;
    private final String displayName;
    private final String elementName;
    private final Object[] values;
    private final InputLocation[] locations;
    private final boolean listItem;
    private final List<PomNode> children = new ArrayList<>();
    private PomNode parent;

    PomNode(Kind kind, String displayName, String elementName, Object[] values, InputLocation[] locations, boolean listItem) {
        this.kind = kind;
        this.displayName = displayName;
        this.elementName = elementName;
        this.values = values;
        this.locations = locations;
        this.listItem = listItem;
    }

    Kind kind() {
        return kind;
    }

    /** The label: the element's name in words, or the item's name in a list. */
    String displayName() {
        return displayName;
    }

    /** The XML element the row stands for. */
    String elementName() {
        return elementName;
    }

    /** Whether the row is an item of a list, so it is sorted by name when lists are sorted. */
    boolean isListItem() {
        return listItem;
    }

    /** The number of layers: the POM and its parents. */
    int layerCount() {
        return values.length;
    }

    /** The field's value in {@code layer}, or {@code null} when the layer does not define it. */
    String valueAt(int layer) {
        Object value = values[layer];
        return value instanceof String string ? string : null;
    }

    /** Whether {@code layer} defines the element. */
    boolean definedIn(int layer) {
        return layer < values.length && values[layer] != null;
    }

    /** Whether the project's own POM defines the element. */
    boolean definedInCurrent() {
        return definedIn(0);
    }

    /** Whether any layer defines the element. */
    boolean definesValue() {
        for (Object value : values) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether the project's own POM defines the element and so does one of its parents. */
    boolean overridesParent() {
        if (!definedInCurrent()) {
            return false;
        }
        for (int i = 1; i < values.length; i++) {
            if (values[i] != null) {
                return true;
            }
        }
        return false;
    }

    /** The first layer defining the element (0 for the project's POM), or {@code -1}. */
    int firstDefinedLayer() {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                return i;
            }
        }
        return -1;
    }

    /** The effective value of a field: the first layer's, or {@code null} when none defines it. */
    String firstValue() {
        int layer = firstDefinedLayer();
        return layer < 0 ? null : valueAt(layer);
    }

    /** Where {@code layer} declares the element, or {@code null} when it does not, or the reader did not record it. */
    InputLocation locationAt(int layer) {
        return layer < locations.length ? locations[layer] : null;
    }

    PomNode parent() {
        return parent;
    }

    List<PomNode> children() {
        return Collections.unmodifiableList(children);
    }

    void add(PomNode child) {
        child.parent = this;
        children.add(child);
    }

    /** A key identifying the row among those of a scan by its element path, so expansion survives rescans. */
    String pathKey() {
        String own = elementName + (listItem ? "=" + displayName : "");
        return parent == null ? own : parent.pathKey() + "/" + own;
    }

    @Override
    public String toString() {
        return kind + " " + displayName + " <" + elementName + ">";
    }
}
