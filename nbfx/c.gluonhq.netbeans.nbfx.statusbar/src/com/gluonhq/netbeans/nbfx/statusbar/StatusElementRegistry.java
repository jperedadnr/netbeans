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
package com.gluonhq.netbeans.nbfx.statusbar;

import com.gluonhq.netbeans.nbfx.annotations.FxLayer;
import com.gluonhq.netbeans.nbfx.annotations.FxStatusAlignment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Discovers the {@link FxStatusElement}s registered under {@code NbFx/Status} in the module layer.
 * <p>
 * Instances are resolved through the folder's {@link Lookup}; the registered {@code alignment} and
 * {@code position} attributes, read straight from the configuration filesystem, take precedence
 * over the values reported by the element itself, so placement is declared with
 * {@code @FxStatusRegistration} rather than in code.
 *
 * @since 1.0
 */
public final class StatusElementRegistry {

    private static final Logger LOG = Logger.getLogger(StatusElementRegistry.class.getName());

    private StatusElementRegistry() {
    }

    /**
     * Discovers and orders all registered status elements. Reads the configuration filesystem, so it
     * must be called once the platform is initialized; it does not touch the JavaFX toolkit.
     *
     * @return the elements, ordered by slot, position and id
     */
    public static List<StatusElement> discover() {
        return discover(List.of());
    }

    /**
     * Discovers the registered status elements and merges {@code additional} (for example elements a
     * window creates programmatically and keeps a reference to) into the ordered result.
     *
     * @param additional extra elements to include
     * @return all elements, ordered by slot, position and id
     */
    public static List<StatusElement> discover(List<StatusElement> additional) {
        Lookup lookup = Lookups.forPath(FxLayer.STATUS);
        FileObject folder = FileUtil.getConfigFile(FxLayer.STATUS);
        List<StatusElement> elements = new ArrayList<>(additional);
        for (FxStatusElement element : lookup.lookupAll(FxStatusElement.class)) {
            FileObject file = folder == null ? null : folder.getFileObject(element.getId(), "instance");
            FxStatusAlignment alignment = file == null
                    ? element.alignment()
                    : parseAlignment(file.getAttribute("alignment"), element.alignment());
            int position = file == null
                    ? element.position()
                    : parsePosition(file.getAttribute("position"), element.position());
            elements.add(new StatusElement(element.getId(), alignment, position, element));
        }
        return sort(elements);
    }

    /** Orders elements by slot, then position, then id, so the layout is stable. */
    static List<StatusElement> sort(List<StatusElement> elements) {
        List<StatusElement> sorted = new ArrayList<>(elements);
        sorted.sort(Comparator
                .comparingInt((StatusElement element) -> element.alignment().ordinal())
                .thenComparingInt(StatusElement::position)
                .thenComparing(StatusElement::id));
        return List.copyOf(sorted);
    }

    private static FxStatusAlignment parseAlignment(Object value, FxStatusAlignment fallback) {
        if (value instanceof String name) {
            try {
                return FxStatusAlignment.valueOf(name);
            } catch (IllegalArgumentException ex) {
                LOG.warning("Unknown status alignment: " + name);
            }
        }
        return fallback;
    }

    private static int parsePosition(Object value, int fallback) {
        return value instanceof Integer position ? position : fallback;
    }
}
