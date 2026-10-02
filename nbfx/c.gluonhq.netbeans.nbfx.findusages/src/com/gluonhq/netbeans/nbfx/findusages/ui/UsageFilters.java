package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.findusages.model.Access;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * The filter tool bar's state: which categories of usages are shown. Every usage falls in exactly
 * one {@link Kind} category (comment, import, or its access) and one {@link Root} category
 * (source or test root), and is visible when both are enabled.
 */
final class UsageFilters implements Predicate<Usage>, Observable {

    /** The kind of a usage, in tool bar order. */
    enum Kind {
        READ("found_item_read", "FILTER_Read"),
        WRITE("found_item_write", "FILTER_Write"),
        READ_WRITE("found_item_readwrite", "FILTER_ReadWrite"),
        IMPORT("found_item_import", "FILTER_Import"),
        COMMENT("found_item_comment", "FILTER_Comment");

        final String icon;
        final String tooltipKey;

        Kind(String icon, String tooltipKey) {
            this.icon = icon;
            this.tooltipKey = tooltipKey;
        }

        static Kind of(Usage usage) {
            if (usage.inComment()) {
                return COMMENT;
            }
            if (usage.inImport()) {
                return IMPORT;
            }
            Access access = usage.access();
            if (access == Access.WRITE) {
                return WRITE;
            }
            if (access == Access.READ_WRITE) {
                return READ_WRITE;
            }
            return READ;
        }
    }

    /** The root a usage's file belongs to. */
    enum Root {
        SOURCE("found_item_source", "FILTER_Source"),
        TEST("found_item_test", "FILTER_Test");

        final String icon;
        final String tooltipKey;

        Root(String icon, String tooltipKey) {
            this.icon = icon;
            this.tooltipKey = tooltipKey;
        }

        static Root of(Usage usage) {
            return usage.inTestRoot() ? TEST : SOURCE;
        }
    }

    private final Map<Kind, BooleanProperty> kinds = new EnumMap<>(Kind.class);
    private final Map<Root, BooleanProperty> roots = new EnumMap<>(Root.class);
    private final java.util.List<InvalidationListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    UsageFilters() {
        for (Kind kind : Kind.values()) {
            kinds.put(kind, newFlag());
        }
        for (Root root : Root.values()) {
            roots.put(root, newFlag());
        }
    }

    BooleanProperty enabled(Kind kind) {
        return kinds.get(kind);
    }

    BooleanProperty enabled(Root root) {
        return roots.get(root);
    }

    @Override
    public boolean test(Usage usage) {
        return kinds.get(Kind.of(usage)).get() && roots.get(Root.of(usage)).get();
    }

    @Override
    public void addListener(InvalidationListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        listeners.remove(listener);
    }

    private BooleanProperty newFlag() {
        BooleanProperty flag = new SimpleBooleanProperty(true);
        flag.addListener(obs -> listeners.forEach(l -> l.invalidated(this)));
        return flag;
    }
}
