package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.findusages.model.EnclosingElement;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import com.gluonhq.netbeans.nbfx.findusages.model.UsageContext;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsageNode.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.scene.control.TreeItem;
import org.openide.filesystems.FileObject;

/**
 * Builds the children of the usages tree root from a query's usages, in one of the two shapes of
 * NetBeans' Find Usages window.
 */
final class UsageTreeBuilder {

    /** The shape of the tree. */
    enum Flavour {
        /** project → source root → package → file → enclosing types and members → usage. */
        LOGICAL,
        /** project → file → usage. */
        PHYSICAL
    }

    private static final Comparator<TreeItem<UsageNode>> BY_TEXT =
            Comparator.comparing(item -> item.getValue().text(), String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<TreeItem<UsageNode>> BY_POSITION =
            Comparator.comparingInt(item -> item.getValue().usage().start());

    private UsageTreeBuilder() {
    }

    /**
     * Groups the usages accepted by {@code filter} into a forest, sorted by name at every level and
     * by position among the usages of a file or element; every group starts expanded.
     */
    static List<TreeItem<UsageNode>> build(List<Usage> usages, Flavour flavour, Predicate<Usage> filter) {
        Group root = new Group(null);
        for (Usage usage : usages) {
            if (!filter.test(usage)) {
                continue;
            }
            Group group = flavour == Flavour.LOGICAL ? logicalParent(root, usage) : physicalParent(root, usage);
            group.usages.add(new TreeItem<>(UsageNode.of(usage)));
        }
        return root.toItems();
    }

    private static Group physicalParent(Group root, Usage usage) {
        UsageContext context = usage.context();
        return root.child(projectKey(context), () -> projectNode(context))
                .child("file:" + path(usage.file()), () -> fileNode(usage.file()));
    }

    private static Group logicalParent(Group root, Usage usage) {
        UsageContext context = usage.context();
        Group group = root.child(projectKey(context), () -> projectNode(context))
                .child("root:" + context.sourceRootName(),
                        () -> UsageNode.group(Kind.SOURCE_ROOT, context.sourceRootName(),
                                () -> UsagesIcons.view("sourceRoot")))
                .child("pkg:" + context.packageName(),
                        () -> UsageNode.group(Kind.PACKAGE, packageLabel(context.packageName()), UsagesIcons::packageIcon))
                .child("file:" + path(usage.file()), () -> fileNode(usage.file()));
        for (EnclosingElement element : context.enclosing()) {
            group = group.child("el:" + element.kind() + ":" + element.name(),
                    () -> UsageNode.group(Kind.ELEMENT, element.name(),
                            () -> UsagesIcons.elementIcon(element.kind(), element.modifiers())));
        }
        return group;
    }

    private static String projectKey(UsageContext context) {
        return "project:" + (context.projectRoot() != null ? path(context.projectRoot()) : context.projectName());
    }

    private static UsageNode projectNode(UsageContext context) {
        return UsageNode.group(Kind.PROJECT, context.projectName(), () -> UsagesIcons.projectIcon(context.projectRoot()));
    }

    private static UsageNode fileNode(FileObject file) {
        return UsageNode.group(Kind.FILE, file.getNameExt(), () -> UsagesIcons.fileIcon(file));
    }

    private static String packageLabel(String packageName) {
        return packageName == null || packageName.isEmpty() ? "<default package>" : packageName;
    }

    private static String path(FileObject file) {
        return file == null ? "" : file.getPath();
    }

    /** A grouping level under construction: its node, its sub-groups by key, and its direct usages. */
    private static final class Group {

        private final Supplier<UsageNode> node;
        private final Map<String, Group> children = new LinkedHashMap<>();
        private final List<TreeItem<UsageNode>> usages = new ArrayList<>();

        Group(Supplier<UsageNode> node) {
            this.node = node;
        }

        Group child(String key, Supplier<UsageNode> node) {
            return children.computeIfAbsent(key, k -> new Group(node));
        }

        List<TreeItem<UsageNode>> toItems() {
            List<TreeItem<UsageNode>> groups = new ArrayList<>();
            for (Group child : children.values()) {
                TreeItem<UsageNode> item = new TreeItem<>(child.node.get());
                item.setExpanded(true);
                item.getChildren().setAll(child.toItems());
                groups.add(item);
            }
            groups.sort(BY_TEXT);
            usages.sort(BY_POSITION);
            groups.addAll(usages);
            return groups;
        }
    }
}
