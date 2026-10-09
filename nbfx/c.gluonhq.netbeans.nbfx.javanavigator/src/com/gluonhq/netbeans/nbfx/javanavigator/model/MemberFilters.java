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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.util.Subscription;
import javax.lang.model.element.ElementKind;

/**
 * The state of the Navigator's filter bar - which members are shown, whether type names are fully
 * qualified and how the rows are sorted - and its application to a list of {@link MemberNode}s.
 * Mirrors {@code ClassMemberFilters} of NetBeans' {@code java.navigation} module, with its defaults.
 */
public final class MemberFilters {

    /** The member categories that can be hidden, in filter bar order. */
    public enum Filter {
        /** Members declared in a supertype (hidden by default, as in NetBeans). */
        INHERITED(false),
        FIELDS(true),
        STATIC(true),
        /** Members that are not {@code public}; top-level types are always shown. */
        NON_PUBLIC(true),
        INNER_CLASSES(true);

        private final boolean shownByDefault;

        Filter(boolean shownByDefault) {
            this.shownByDefault = shownByDefault;
        }

        public boolean isShownByDefault() {
            return shownByDefault;
        }
    }

    private final Map<Filter, BooleanProperty> showing = new EnumMap<>(Filter.class);
    private final BooleanProperty fullyQualifiedNames = new SimpleBooleanProperty(this, "fullyQualifiedNames", false);
    private final BooleanProperty sortByName = new SimpleBooleanProperty(this, "sortByName", true);

    public MemberFilters() {
        for (Filter filter : Filter.values()) {
            showing.put(filter, new SimpleBooleanProperty(this, filter.name(), filter.isShownByDefault()));
        }
    }

    /** Whether the members of {@code filter}'s category are shown. */
    public BooleanProperty showing(Filter filter) {
        return showing.get(filter);
    }

    /** Whether type names are printed fully qualified. */
    public BooleanProperty fullyQualifiedNames() {
        return fullyQualifiedNames;
    }

    /** Whether rows are sorted by name ({@code true}) or by their position in the source. */
    public BooleanProperty sortByName() {
        return sortByName;
    }

    /** Runs {@code onChange} whenever any of the properties changes, until the subscription is released. */
    public Subscription subscribe(Runnable onChange) {
        Subscription subscription = fullyQualifiedNames.subscribe(onChange).and(sortByName.subscribe(onChange));
        for (BooleanProperty property : showing.values()) {
            subscription = subscription.and(property.subscribe(onChange));
        }
        return subscription;
    }

    /** Whether {@code node} passes the enabled filters. */
    public boolean accepts(MemberNode node) {
        if (node.isInherited() && !showing(Filter.INHERITED).get()) {
            return false;
        }
        if (!node.isPublic() && !node.isTopLevel() && !showing(Filter.NON_PUBLIC).get()) {
            return false;
        }
        if (node.isStatic() && !showing(Filter.STATIC).get()) {
            return false;
        }
        if (node.getKind() == ElementKind.FIELD && !showing(Filter.FIELDS).get()) {
            return false;
        }
        return showing(Filter.INNER_CLASSES).get() || !node.isInnerType();
    }

    /** The order of the rows under the current sort setting. */
    public Comparator<MemberNode> comparator() {
        return sortByName.get() ? MemberNode.BY_NAME : MemberNode.BY_POSITION;
    }

    /** The nodes of {@code nodes} that pass the filters, in the current order. */
    public List<MemberNode> apply(Collection<MemberNode> nodes) {
        List<MemberNode> result = new ArrayList<>(nodes.size());
        for (MemberNode node : nodes) {
            if (accepts(node)) {
                result.add(node);
            }
        }
        result.sort(comparator());
        return result;
    }
}
