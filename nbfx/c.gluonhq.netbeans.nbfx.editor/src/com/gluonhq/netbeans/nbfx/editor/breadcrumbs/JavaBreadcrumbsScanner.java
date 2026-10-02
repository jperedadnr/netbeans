package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.SimpleBreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.elements.SourceElementKind;
import com.gluonhq.netbeans.nbfx.api.elements.SourceTypeKind;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.JavaSourceContext;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DirectiveTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExportsTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.OpensTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UsesTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.gluonhq.netbeans.nbfx.api.elements.SourceModifiers.toModifierBits;

/**
 * Builds the breadcrumb tree of a Java source file, following the shapes produced by the
 * NetBeans IDE breadcrumbs scanner ({@code BreadCrumbsNodeImpl} in {@code java.navigation}).
 *
 * <p>The scanner parses the document text with the shared javac pipeline
 * ({@link JavaSourceContext#runSemanticTask}) and eagerly converts each relevant tree node
 * into an immutable {@link SimpleBreadcrumbElement}:</p>
 * <ul>
 *   <li>type declarations (class / interface / enum / record / annotation) — top-level ones
 *       become roots labeled with their fully qualified name;</li>
 *   <li>methods and constructors (constructors labeled with the class simple name);</li>
 *   <li>fields and local variables;</li>
 *   <li>flow statements with a condensed header: {@code if} / {@code else} sections (the
 *       combined-span split ported from NetBeans, including flattened {@code else if} chains),
 *       {@code for}, enhanced {@code for}, {@code while}, {@code do … while}, {@code switch},
 *       {@code case}, {@code try} / {@code catch} / {@code finally}, {@code synchronized};</li>
 *   <li>initializer blocks ({@code <init>} / {@code <static init>}) and lambda bodies;</li>
 *   <li>module descriptors — the {@code module} declaration becomes the root, with one child
 *       per directive ({@code requires} / {@code exports} / {@code opens} / {@code provides} /
 *       {@code uses}).</li>
 * </ul>
 *
 * <p>Since the tree is built eagerly inside a single parse, the resulting elements are safe to
 * cache and share across threads; caret moves only need the offset-based descent implemented by
 * {@link #select(List, int)}.</p>
 */
final class JavaBreadcrumbsScanner {

    private static final String CONSTRUCTOR_NAME = "<init>";
    private static final String ERROR_NAME = "<error>";

    private JavaBreadcrumbsScanner() {
    }

    /**
     * Parses {@code context.documentText()} and returns one breadcrumb root per top-level type
     * declaration, or {@code null} when the scan was canceled or the source could not be parsed.
     */
    static List<BreadcrumbElement> scan(BreadcrumbsContext context, Cancellation cancellation) {
        List<BreadcrumbElement> roots = new ArrayList<>();
        try {
            JavaSourceContext sourceContext = new JavaSourceContext(context.fileObject());
            CancellableTask<CompilationController> task = new CancellableTask<>() {
                @Override
                public void run(CompilationController controller) throws Exception {
                    if (cancellation.isCancelled()) {
                        return;
                    }
                    controller.toPhase(JavaSource.Phase.PARSED);
                    CompilationUnitTree unit = controller.getCompilationUnit();
                    TreePath unitPath = new TreePath(unit);
                    ModuleTree module = unit.getModule();
                    if (module != null) {
                        Builder builder = new Builder(controller, context.documentText(), cancellation);
                        SimpleBreadcrumbElement root =
                                builder.createModuleRoot(new TreePath(unitPath, module));
                        if (root != null) {
                            roots.add(root);
                        }
                        return;
                    }
                    for (Tree typeDecl : unit.getTypeDecls()) {
                        if (cancellation.isCancelled()) {
                            return;
                        }
                        if (!isClassKind(typeDecl.getKind())) {
                            continue;
                        }
                        TreePath typePath = new TreePath(unitPath, typeDecl);
                        Builder builder = new Builder(controller, context.documentText(), cancellation);
                        SimpleBreadcrumbElement root = builder.createRoot(typePath);
                        if (root != null) {
                            builder.scanChildren(typePath, root);
                            roots.add(root);
                        }
                    }
                }

                @Override
                public void cancel() {
                }
            };
            if (!sourceContext.runSemanticTask(context.documentText(), task, true) || cancellation.isCancelled()) {
                return null;
            }
        } catch (IOException | RuntimeException ex) {
            // Exception already logged by runSemanticTask
            return null;
        }
        return List.copyOf(roots);
    }

    /**
     * Descends from the root whose span contains {@code caretOffset} to the deepest element whose
     * span still contains it — the port of NetBeans' {@code rootAndSelection} loop.
     */
    static Optional<BreadcrumbElement> select(List<BreadcrumbElement> roots, int caretOffset) {
        return BreadcrumbsElements.select(roots, caretOffset);
    }

    private static boolean isClassKind(Tree.Kind kind) {
        return kind == Tree.Kind.CLASS || kind == Tree.Kind.INTERFACE || kind == Tree.Kind.ENUM
                || kind == Tree.Kind.RECORD || kind == Tree.Kind.ANNOTATION_TYPE;
    }

    /** Stateful helper carrying the parse artifacts through the recursive descent. */
    private static final class Builder {

        private final CompilationController controller;
        private final SourcePositions positions;
        private final String source;
        private final Cancellation cancellation;

        Builder(CompilationController controller, String source, Cancellation cancellation) {
            this.controller = controller;
            this.positions = controller.getTrees().getSourcePositions();
            this.source = source;
            this.cancellation = cancellation;
        }

        /** Creates the root element for a top-level type declaration, labeled with its FQN. */
        SimpleBreadcrumbElement createRoot(TreePath typePath) {
            ClassTree classTree = (ClassTree) typePath.getLeaf();
            int[] pos = spanOf(typePath.getLeaf(), typePath);
            if (pos == null) {
                return null;
            }
            return SimpleBreadcrumbElement.root(className(typePath), pos[0], pos[1],
                    SourceElementKind.TYPE, SourceTypeKind.from(classTree.getKind().name()),
                    toModifierBits(classTree.getModifiers().getFlags()));
        }

        /**
         * Creates the root element for a module descriptor, labeled with the module name, with
         * one child per directive ({@code requires} / {@code exports} / {@code opens} /
         * {@code provides} / {@code uses}).
         */
        SimpleBreadcrumbElement createModuleRoot(TreePath modulePath) {
            ModuleTree moduleTree = (ModuleTree) modulePath.getLeaf();
            int[] pos = spanOf(moduleTree, modulePath);
            if (pos == null) {
                return null;
            }
            SimpleBreadcrumbElement root = SimpleBreadcrumbElement.root(
                    moduleTree.getName().toString(), pos[0], pos[1],
                    SourceElementKind.MODULE, null, 0);
            for (DirectiveTree directive : moduleTree.getDirectives()) {
                if (cancellation.isCancelled()) {
                    break;
                }
                String label = directiveLabel(directive);
                int[] directivePos = spanOf(directive, modulePath);
                if (label != null && directivePos != null) {
                    root.addChild(label, directivePos[0], directivePos[1],
                            SourceElementKind.OTHER, null, 0);
                }
            }
            return root;
        }

        /** Plain-text label for a module directive, matching the NetBeans breadcrumb labels. */
        private String directiveLabel(DirectiveTree directive) {
            return switch (directive.getKind()) {
                case REQUIRES -> "requires " + ((RequiresTree) directive).getModuleName();
                case EXPORTS -> "exports " + ((ExportsTree) directive).getPackageName();
                case OPENS -> "opens " + ((OpensTree) directive).getPackageName();
                case PROVIDES -> "provides " + simpleName(((ProvidesTree) directive).getServiceName());
                case USES -> "uses " + simpleName(((UsesTree) directive).getServiceName());
                default -> null;
            };
        }

        /**
         * Scans the direct children of {@code parentPath}, appending an element to
         * {@code parentElement} for every breadcrumb-able node found and recursing into it;
         * non-breadcrumb nodes are traversed transparently.
         */
        void scanChildren(TreePath parentPath, SimpleBreadcrumbElement parentElement) {
            if (cancellation.isCancelled()) {
                return;
            }
            Tree leaf = parentPath.getLeaf();
            TreeScanner<Void, TreePath> scanner = new TreeScanner<>() {
                @Override
                public Void scan(Tree node, TreePath path) {
                    if (node == null || cancellation.isCancelled()) {
                        return null;
                    }
                    if (node.getKind() == Tree.Kind.IF) {
                        addIfChain((IfTree) node, path, parentElement);
                        return null;
                    }
                    TreePath childPath = new TreePath(path, node);
                    if (controller.getTreeUtilities().isSynthetic(childPath)) {
                        return null;
                    }
                    SimpleBreadcrumbElement child = createElement(childPath, parentElement, false);
                    if (child != null) {
                        scanChildren(childPath, child);
                        return null;
                    }
                    return super.scan(node, childPath);
                }

                @Override
                public Void visitMethod(MethodTree node, TreePath path) {
                    // Skip the method header (parameters, throws, …); only the body nests.
                    return scan(node.getBody(), path);
                }

                @Override
                public Void visitLambdaExpression(LambdaExpressionTree node, TreePath path) {
                    return scan(node.getBody(), path);
                }
            };
            leaf.accept(scanner, parentPath);
        }

        /**
         * Adds the elements of an {@code if} statement: the {@code if} section, the optional
         * {@code else} section, and — when the else-branch is itself an {@code if} — the flattened
         * {@code else if} chain as further siblings (the NetBeans behavior).
         */
        private void addIfChain(IfTree ifTree, TreePath parentPath, SimpleBreadcrumbElement parentElement) {
            TreePath ifPath = new TreePath(parentPath, ifTree);
            SimpleBreadcrumbElement thenElement = createElement(ifPath, parentElement, false);
            if (thenElement != null) {
                scanChildren(new TreePath(ifPath, ifTree.getThenStatement()), thenElement);
            }
            StatementTree elseStatement = ifTree.getElseStatement();
            if (elseStatement == null) {
                return;
            }
            if (elseStatement.getKind() == Tree.Kind.IF) {
                addIfChain((IfTree) elseStatement, ifPath, parentElement);
                return;
            }
            SimpleBreadcrumbElement elseElement = createElement(ifPath, parentElement, true);
            if (elseElement != null) {
                scanChildren(new TreePath(ifPath, elseStatement), elseElement);
            }
        }

        /**
         * Creates the element for a single tree node, or returns {@code null} when the node kind
         * does not produce a breadcrumb. Ported from NetBeans'
         * {@code BreadCrumbsNodeImpl.createBreadcrumbs}, with plain-text labels.
         */
        private SimpleBreadcrumbElement createElement(TreePath path, SimpleBreadcrumbElement parent,
                                                      boolean elseSection) {
            Tree leaf = path.getLeaf();
            int[] pos = spanOf(leaf, path);
            if (pos == null) {
                return null;
            }
            switch (leaf.getKind()) {
                case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> {
                    ClassTree ct = (ClassTree) leaf;
                    return parent.addChild(className(path), pos[0], pos[1],
                            SourceElementKind.TYPE, SourceTypeKind.from(leaf.getKind().name()),
                            toModifierBits(ct.getModifiers().getFlags()));
                }
                case METHOD -> {
                    MethodTree mt = (MethodTree) leaf;
                    boolean constructor = mt.getName().contentEquals(CONSTRUCTOR_NAME);
                    String name = constructor
                            ? ((ClassTree) path.getParentPath().getLeaf()).getSimpleName().toString()
                            : mt.getName().toString();
                    return parent.addChild(name, pos[0], pos[1],
                            SourceElementKind.METHOD,
                            constructor ? SourceTypeKind.CONSTRUCTOR : null,
                            toModifierBits(mt.getModifiers().getFlags()));
                }
                case VARIABLE -> {
                    VariableTree vt = (VariableTree) leaf;
                    boolean field = isClassKind(path.getParentPath().getLeaf().getKind());
                    return parent.addChild(vt.getName().toString(), pos[0], pos[1],
                            field ? SourceElementKind.FIELD : SourceElementKind.VARIABLE, null,
                            toModifierBits(vt.getModifiers().getFlags()));
                }
                case CASE -> {
                    CaseTree ct = (CaseTree) leaf;
                    List<? extends Tree> labels = ct.getLabels();
                    String label = labels.isEmpty()
                            || labels.stream().allMatch(l -> l.getKind() == Tree.Kind.DEFAULT_CASE_LABEL)
                            ? "default:"
                            : "case " + condense(labels.stream().map(Object::toString)
                                    .collect(Collectors.joining(", "))) + ":";
                    return parent.addChild(label, pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case CATCH -> {
                    CatchTree ct = (CatchTree) leaf;
                    return parent.addChild("catch " + condense(ct.getParameter().toString()),
                            pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case DO_WHILE_LOOP -> {
                    DoWhileLoopTree dt = (DoWhileLoopTree) leaf;
                    return parent.addChild("do ... while " + condense(dt.getCondition().toString()),
                            pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case ENHANCED_FOR_LOOP -> {
                    EnhancedForLoopTree ft = (EnhancedForLoopTree) leaf;
                    String label = "for (" + ft.getVariable() + " : " + ft.getExpression() + ")";
                    return parent.addChild(condenseKeepPrefix(label, "for "), pos[0], pos[1],
                            SourceElementKind.OTHER, null, 0);
                }
                case FOR_LOOP -> {
                    return parent.addChild(forLoopLabel((ForLoopTree) leaf), pos[0], pos[1],
                            SourceElementKind.OTHER, null, 0);
                }
                case IF -> {
                    int[] ifPos = ifSectionSpan((IfTree) leaf, pos, elseSection);
                    return parent.addChild(ifChainLabel(path, elseSection), ifPos[0], ifPos[1],
                            SourceElementKind.OTHER, null, 0);
                }
                case SWITCH -> {
                    SwitchTree st = (SwitchTree) leaf;
                    return parent.addChild("switch " + condense(st.getExpression().toString()),
                            pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case SWITCH_EXPRESSION -> {
                    SwitchExpressionTree st = (SwitchExpressionTree) leaf;
                    return parent.addChild("switch " + condense(st.getExpression().toString()),
                            pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case SYNCHRONIZED -> {
                    SynchronizedTree st = (SynchronizedTree) leaf;
                    return parent.addChild("synchronized " + condense(st.getExpression().toString()),
                            pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case TRY -> {
                    return parent.addChild("try", pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case WHILE_LOOP -> {
                    WhileLoopTree wt = (WhileLoopTree) leaf;
                    return parent.addChild("while " + condense(wt.getCondition().toString()),
                            pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                }
                case LAMBDA_EXPRESSION -> {
                    LambdaExpressionTree lt = (LambdaExpressionTree) leaf;
                    String params = lt.getParameters().stream()
                            .map(p -> p.getName().toString())
                            .collect(Collectors.joining(", "));
                    return parent.addChild(condense("(" + params + ") ->"), pos[0], pos[1],
                            SourceElementKind.OTHER, null, 0);
                }
                case BLOCK -> {
                    Tree parentLeaf = path.getParentPath().getLeaf();
                    if (isClassKind(parentLeaf.getKind())) {
                        String label = ((BlockTree) leaf).isStatic() ? "<static init>" : "<init>";
                        return parent.addChild(label, pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                    }
                    if (parentLeaf.getKind() == Tree.Kind.TRY
                            && ((com.sun.source.tree.TryTree) parentLeaf).getFinallyBlock() == leaf) {
                        return parent.addChild("finally", pos[0], pos[1], SourceElementKind.OTHER, null, 0);
                    }
                    return null;
                }
                default -> {
                    return null;
                }
            }
        }

        /** {@code for (init; cond; update)} with initializer/update lists condensed. */
        private String forLoopLabel(ForLoopTree loop) {
            StringBuilder sb = new StringBuilder("(");
            boolean first = true;
            for (StatementTree init : loop.getInitializer()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(init.getKind() == Tree.Kind.EXPRESSION_STATEMENT
                        ? ((ExpressionStatementTree) init).getExpression().toString()
                        : init.toString());
                first = false;
            }
            sb.append("; ");
            if (loop.getCondition() != null) {
                sb.append(loop.getCondition());
            }
            sb.append("; ");
            first = true;
            for (ExpressionStatementTree update : loop.getUpdate()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(update.getExpression());
                first = false;
            }
            sb.append(")");
            return condenseKeepPrefix("for " + sb, "for ");
        }

        /**
         * Builds the {@code if c1 else if c2 …} label by walking up the enclosing {@code if}
         * chain, appending {@code else} when this element covers the else-section.
         */
        private String ifChainLabel(TreePath path, boolean elseSection) {
            Tree leaf = path.getLeaf();
            StringBuilder sb = new StringBuilder();
            Tree last = leaf;
            TreePath walker = path;
            while (walker != null && walker.getLeaf().getKind() == Tree.Kind.IF) {
                IfTree current = (IfTree) walker.getLeaf();
                StringBuilder part = new StringBuilder("if ")
                        .append(condense(current.getCondition().toString()));
                if (current.getElseStatement() == last || (walker.getLeaf() == leaf && elseSection)) {
                    part.append(" else");
                }
                part.append(' ');
                sb.insert(0, part);
                last = walker.getLeaf();
                walker = walker.getParentPath();
            }
            return sb.toString().trim();
        }

        /**
         * Splits the whole-statement span of an {@code if} into its {@code if}- or
         * {@code else}-section span, locating the {@code else} keyword between the then-branch
         * end and the else-branch start.
         */
        private int[] ifSectionSpan(IfTree ifTree, int[] pos, boolean elseSection) {
            StatementTree elseStatement = ifTree.getElseStatement();
            if (elseStatement == null) {
                return pos;
            }
            CompilationUnitTree unit = controller.getCompilationUnit();
            int elseBranchStart = (int) positions.getStartPosition(unit, elseStatement);
            int thenEnd = (int) positions.getEndPosition(unit, ifTree.getThenStatement());
            int elseStart = elseBranchStart >= 0 ? source.lastIndexOf("else", elseBranchStart) : -1;
            if (elseStart < thenEnd) {
                elseStart = elseBranchStart;
            }
            if (elseSection) {
                int endPos = (int) positions.getEndPosition(unit, elseStatement);
                return new int[]{elseStart, endPos};
            }
            return new int[]{pos[0], elseStart - 1};
        }

        /** Fully qualified name for top-level types, simple name otherwise (anonymous → parent type). */
        private String className(TreePath path) {
            ClassTree ct = (ClassTree) path.getLeaf();
            Tree parentLeaf = path.getParentPath().getLeaf();
            if (parentLeaf.getKind() == Tree.Kind.NEW_CLASS
                    && ((NewClassTree) parentLeaf).getClassBody() == ct) {
                return condense(((NewClassTree) parentLeaf).getIdentifier().toString());
            }
            if (parentLeaf == path.getCompilationUnit()) {
                Tree pkg = path.getCompilationUnit().getPackageName();
                String pkgName = pkg != null ? pkg.toString() : null;
                if (pkgName != null && !pkgName.contentEquals(ERROR_NAME)) {
                    return pkgName + '.' + ct.getSimpleName();
                }
            }
            return ct.getSimpleName().toString();
        }

        /** Start/end offsets of {@code tree}, or {@code null} when javac has no position for it. */
        private int[] spanOf(Tree tree, TreePath path) {
            CompilationUnitTree unit = path.getCompilationUnit();
            int start = (int) positions.getStartPosition(unit, tree);
            int end = (int) positions.getEndPosition(unit, tree);
            if (start < 0 || end < start) {
                return null;
            }
            return new int[]{start, end};
        }
    }

    /** Rightmost name segment of a (possibly qualified or parameterized) type reference. */
    private static String simpleName(Tree tree) {
        return switch (tree.getKind()) {
            case PARAMETERIZED_TYPE -> simpleName(((ParameterizedTypeTree) tree).getType());
            case IDENTIFIER -> ((IdentifierTree) tree).getName().toString();
            case MEMBER_SELECT -> ((MemberSelectTree) tree).getIdentifier().toString();
            default -> "";
        };
    }

    /** Collapses whitespace runs and truncates over-long labels with an ellipsis. */
    private static String condense(String text) {
        return BreadcrumbsElements.condense(text);
    }

    /** As {@link #condense(String)} but never truncates into {@code prefix}. */
    private static String condenseKeepPrefix(String text, String prefix) {
        String condensed = condense(text);
        return condensed.startsWith(prefix) ? condensed : prefix + condense(text.substring(prefix.length()));
    }
}
