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

import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberFilters;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberFilters.Filter;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The user's Navigator settings, persisted in the module preferences: the enabled filters, the
 * fully-qualified-names and sort toggles, the way NetBeans' {@code java.navigation} keeps them, and
 * the selected view (Members or Bean Patterns).
 */
final class NavigatorSettings {

    private static final String FILTER = "filter.";
    private static final String FQN = "fqn";
    private static final String SORT_BY_NAME = "sortByName";
    private static final String VIEW = "view";

    private final Preferences prefs;

    NavigatorSettings() {
        this(NbPreferences.forModule(NavigatorSettings.class));
    }

    NavigatorSettings(Preferences prefs) {
        this.prefs = prefs;
    }

    /** Loads the persisted state into {@code filters}; a setting missing from the store keeps its default. */
    void load(MemberFilters filters) {
        for (Filter filter : Filter.values()) {
            filters.showing(filter).set(prefs.getBoolean(FILTER + filter.name(), filter.isShownByDefault()));
        }
        filters.fullyQualifiedNames().set(prefs.getBoolean(FQN, false));
        filters.sortByName().set(prefs.getBoolean(SORT_BY_NAME, true));
    }

    /** The view selected last, {@code MEMBERS} by default or for an unknown name. */
    NavigatorView.View getView() {
        try {
            return NavigatorView.View.valueOf(prefs.get(VIEW, NavigatorView.View.MEMBERS.name()));
        } catch (IllegalArgumentException ex) {
            return NavigatorView.View.MEMBERS;
        }
    }

    void setView(NavigatorView.View view) {
        prefs.put(VIEW, view.name());
    }

    /** Persists every change of {@code filters} from now on. */
    void watch(MemberFilters filters) {
        for (Filter filter : Filter.values()) {
            filters.showing(filter).subscribe(on -> prefs.putBoolean(FILTER + filter.name(), on));
        }
        filters.fullyQualifiedNames().subscribe(on -> prefs.putBoolean(FQN, on));
        filters.sortByName().subscribe(on -> prefs.putBoolean(SORT_BY_NAME, on));
    }
}
