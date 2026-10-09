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
package com.gluonhq.netbeans.nbfx.api.actions;

import com.gluonhq.netbeans.nbfx.annotations.FxLayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Reads the action references contributed to a UI surface under the {@code NbFx} layer, the way
 * NetBeans menus are assembled from {@code Menu/} folders.
 * <p>
 * References are written by the nbfx annotation processor as {@code <id>.ref} files carrying
 * {@code actionId}, {@code position} and {@code separatorBefore} attributes; a submenu is a nested
 * folder carrying {@code displayName}, {@code position} and {@code separatorBefore}.
 *
 * @since 1.0
 */
public final class ActionLayerReader {

    private static final String REF_EXT = FxLayer.REF_SUFFIX.startsWith(".")
            ? FxLayer.REF_SUFFIX.substring(1) : FxLayer.REF_SUFFIX;

    private ActionLayerReader() {
    }

    /**
     * Reads the action references directly under {@code folder}, ordered by position then id.
     *
     * @param folder the surface folder (for example {@code NbFx/Menus/File}); may be {@code null}
     * @return the ordered references, never {@code null}
     */
    public static List<FxActionRef> read(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<FxActionRef> refs = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (!child.isData() || !child.hasExt(REF_EXT)) {
                continue;
            }
            if (child.getAttribute("actionId") instanceof String actionId && !actionId.isBlank()) {
                int position = child.getAttribute("position") instanceof Integer p ? p : Integer.MAX_VALUE;
                boolean separatorBefore = Boolean.TRUE.equals(child.getAttribute("separatorBefore"));
                refs.add(new FxActionRef(actionId, position, separatorBefore));
            }
        }
        refs.sort(Comparator.comparingInt(FxActionRef::position).thenComparing(FxActionRef::actionId));
        return List.copyOf(refs);
    }

    /**
     * Reads the entries directly under {@code folder} - its action references and its submenus,
     * each with their own entries - ordered by position then name.
     *
     * @param folder the surface folder (for example {@code NbFx/ContextMenus/Editor}); may be {@code null}
     * @return the ordered entries, never {@code null}
     * @since 1.0
     */
    public static List<FxMenuEntry> readEntries(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<FxMenuEntry> entries = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                String displayName = child.getAttribute("displayName") instanceof String s && !s.isEmpty()
                        ? s : child.getName();
                entries.add(new FxSubmenu(child.getName(), displayName, position(child), separatorBefore(child),
                        readEntries(child)));
            } else if (child.isData() && child.hasExt(REF_EXT)
                    && child.getAttribute("actionId") instanceof String actionId && !actionId.isBlank()) {
                entries.add(new FxActionRef(actionId, position(child), separatorBefore(child)));
            }
        }
        entries.sort(Comparator.comparingInt(FxMenuEntry::position).thenComparing(ActionLayerReader::nameOf));
        return List.copyOf(entries);
    }

    /**
     * Reads the entries of the layer folder at {@code layerPath} (for example
     * {@code "NbFx/ContextMenus/Editor"}), submenus included.
     *
     * @param layerPath the layer path; may be {@code null}
     * @return the ordered entries, never {@code null}
     * @since 1.0
     */
    public static List<FxMenuEntry> readEntries(String layerPath) {
        return readEntries(layerPath == null ? null : FileUtil.getConfigFile(layerPath));
    }

    private static int position(FileObject file) {
        return file.getAttribute("position") instanceof Integer p ? p : Integer.MAX_VALUE;
    }

    private static boolean separatorBefore(FileObject file) {
        return Boolean.TRUE.equals(file.getAttribute("separatorBefore"));
    }

    private static String nameOf(FxMenuEntry entry) {
        return switch (entry) {
            case FxActionRef ref -> ref.actionId();
            case FxSubmenu submenu -> submenu.id();
        };
    }

    /**
     * Reads the action references from the layer folder at {@code layerPath} (for example
     * {@code "NbFx/Menus/File"}).
     *
     * @param layerPath the layer path; may be {@code null}
     * @return the ordered references, never {@code null}
     */
    public static List<FxActionRef> read(String layerPath) {
        return read(layerPath == null ? null : FileUtil.getConfigFile(layerPath));
    }
}
