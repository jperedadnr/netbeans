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
package com.gluonhq.netbeans.nbfx.javanavigator.ui;

import com.gluonhq.netbeans.nbfx.javanavigator.model.InspectedType;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The Inspect Members history: the last types the user inspected, after {@code HistorySupport} of
 * NetBeans' {@code java.navigation} module - at most {@value #LENGTH}, each once, listed by simple
 * name then by enclosing name for the header combo. Kept for the session; FX thread.
 */
final class InspectHistory {

    static final int LENGTH = 25;

    private static final Comparator<InspectedType> BY_NAME = Comparator
            .comparing(InspectedType::getSimpleName)
            .thenComparing(InspectedType::getEnclosingName);

    private final Deque<InspectedType> recent = new ArrayDeque<>();
    private final ObservableList<InspectedType> sorted = FXCollections.observableArrayList();

    /** The entries, by name; observable, read-only. */
    ObservableList<InspectedType> entries() {
        return FXCollections.unmodifiableObservableList(sorted);
    }

    /** Records {@code type}, dropping the oldest entry once the history is full; a type already recorded is left as is. */
    void add(InspectedType type) {
        if (recent.contains(type)) {
            return;
        }
        if (recent.size() == LENGTH) {
            recent.removeLast();
        }
        recent.addFirst(type);
        sorted.setAll(recent.stream().sorted(BY_NAME).toList());
    }

    boolean isEmpty() {
        return recent.isEmpty();
    }
}
