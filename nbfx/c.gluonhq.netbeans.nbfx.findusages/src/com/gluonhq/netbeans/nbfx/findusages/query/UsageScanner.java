package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.findusages.model.Access;
import com.gluonhq.netbeans.nbfx.findusages.model.EnclosingElement;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import com.gluonhq.netbeans.nbfx.findusages.model.UsageContext;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.TreeUtilities;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

/**
 * Finds the usages of one element in one compilation unit: a {@link TreePathScanner} that resolves
 * every identifier, member select, member reference and {@code new} expression and keeps those that
 * denote the element - or, for a method, the method and its overriders, as a call through an
 * override is a usage of the method. Optionally also the name mentioned in comments.
 * <p>
 * A port of NetBeans' {@code FindUsagesVisitor} and of the position and text logic of
 * {@code WhereUsedElement.create}, onto javac only.
 */
final class UsageScanner extends TreePathScanner<Void, Void> {

    private static final Set<String> WRITE_METHODS = Set.of(
            "add", "addAll", "putAll", "remove", "removeAll", "retainAll", "removeIf", "clear");
    private static final Set<String> READ_METHODS = Set.of(
            "get", "getOrDefault", "first", "last", "firstKey", "lastKey",
            "contains", "containsKey", "containsValue", "containsAll", "size", "isEmpty", "indexOf");
    private static final Set<String> READ_WRITE_METHODS = Set.of("sort", "set", "put", "putIfAbsent", "replace");

    private final CompilationController info;
    private final Element target;
    private final Cancellation cancellation;
    private final FileContext fileContext;
    private final boolean searchComments;
    private final List<ExecutableElement> methods = new ArrayList<>();
    private final List<Usage> usages = new ArrayList<>();
    private final Trees trees;
    private final TreeUtilities treeUtilities;
    private final String text;

    /**
     * @param info           a compilation at phase RESOLVED
     * @param target         the element, resolved in {@code info}
     * @param fileContext    where the file lives, shared by all its usages
     * @param searchComments whether to report the name mentioned in comments
     */
    UsageScanner(CompilationController info, Element target, FileContext fileContext, boolean searchComments,
            Cancellation cancellation) {
        this.info = info;
        this.target = target;
        this.fileContext = fileContext;
        this.searchComments = searchComments;
        this.cancellation = cancellation;
        this.trees = info.getTrees();
        this.treeUtilities = info.getTreeUtilities();
        this.text = info.getText();
    }

    /** Scans the unit and returns its usages, in order of position. */
    List<Usage> scan() {
        scan(info.getCompilationUnit(), null);
        usages.sort(Comparator.comparingInt(Usage::start));
        return usages;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
        if (searchComments) {
            scanComments();
        }
        if (target.getKind() == ElementKind.METHOD || target.getKind() == ElementKind.CONSTRUCTOR) {
            methods.add((ExecutableElement) target);
        }
        return super.visitCompilationUnit(node, p);
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void p) {
        if (cancellation.isCancelled()) {
            return null;
        }
        addIfMatch(getCurrentPath(), node);
        return super.visitIdentifier(node, p);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (cancellation.isCancelled()) {
            return null;
        }
        addIfMatch(getCurrentPath(), node);
        return super.visitMemberSelect(node, p);
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree node, Void p) {
        if (cancellation.isCancelled()) {
            return null;
        }
        addIfMatch(getCurrentPath(), node);
        return super.visitMemberReference(node, p);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void p) {
        if (cancellation.isCancelled()) {
            return null;
        }
        ClassTree body = node.getClassBody();
        if (body != null && target.getKind() == ElementKind.CONSTRUCTOR) {
            // an anonymous class: the usage of the super constructor is the synthetic super() call
            // of the anonymous constructor
            for (Tree member : body.getMembers()) {
                Element element = trees.getElement(TreePath.getPath(info.getCompilationUnit(), member));
                if (element == null || element.getKind() != ElementKind.CONSTRUCTOR) {
                    continue;
                }
                List<? extends StatementTree> statements = ((MethodTree) member).getBody().getStatements();
                if (statements.isEmpty() || !(statements.get(0) instanceof ExpressionStatementTree statement)) {
                    continue;
                }
                TreePath superCall = trees.getPath(info.getCompilationUnit(), statement.getExpression());
                Element called = superCall == null ? null : trees.getElement(superCall);
                if (called != null && called.equals(target) && !treeUtilities.isSynthetic(superCall)) {
                    addUsage(superCall, null);
                }
            }
        } else {
            addIfMatch(getCurrentPath(), node);
        }
        return super.visitNewClass(node, p);
    }

    private void addIfMatch(TreePath path, Tree tree) {
        if (treeUtilities.isSynthetic(path)) {
            boolean syntheticSuper = target.getKind() == ElementKind.CONSTRUCTOR
                    && tree.getKind() == Tree.Kind.IDENTIFIER
                    && "super".contentEquals(((IdentifierTree) tree).getName());
            if (!syntheticSuper) {
                return;
            }
        }
        Element element = trees.getElement(path);
        if (element == null) {
            element = staticImportMember(path);
            if (element == null) {
                return;
            }
        }
        if (target.getKind() == ElementKind.METHOD && element.getKind() == ElementKind.METHOD) {
            TypeElement declaring = info.getElementUtilities().enclosingTypeElement(target);
            for (ExecutableElement method : methods) {
                if (element.equals(method)
                        || info.getElements().overrides((ExecutableElement) element, method, declaring)) {
                    addUsage(path, null);
                    return;
                }
            }
        } else if (element.equals(target)) {
            ElementKind kind = target.getKind();
            if (kind.isField() || kind == ElementKind.LOCAL_VARIABLE || kind == ElementKind.RESOURCE_VARIABLE
                    || kind == ElementKind.PARAMETER || kind == ElementKind.BINDING_VARIABLE
                    || kind == ElementKind.EXCEPTION_PARAMETER) {
                addUsage(path, accessOf(path, tree, element));
            } else {
                addUsage(path, null);
            }
        }
    }

    /**
     * The member a static single import ({@code import static a.B.c;}) names: javac attaches no
     * element to the {@code c} in the member select, so it is looked up among the type's members.
     */
    private Element staticImportMember(TreePath path) {
        TreePath parent = path.getParentPath();
        if (parent == null || parent.getLeaf().getKind() != Tree.Kind.IMPORT) {
            return null;
        }
        ImportTree importTree = (ImportTree) parent.getLeaf();
        if (!importTree.isStatic() || importTree.getQualifiedIdentifier().getKind() != Tree.Kind.MEMBER_SELECT) {
            return null;
        }
        MemberSelectTree select = (MemberSelectTree) importTree.getQualifiedIdentifier();
        Name id = select.getIdentifier();
        if (id.contentEquals("*")) {
            return null;
        }
        Element type = trees.getElement(trees.getPath(info.getCompilationUnit(), select.getExpression()));
        if (type == null) {
            return null;
        }
        Iterator<? extends Element> members = info.getElementUtilities()
                .getMembers(type.asType(), (e, t) -> id.equals(e.getSimpleName())).iterator();
        Element member = members.hasNext() ? members.next() : null;
        // an overloaded name imports several members at once: not one usage
        return members.hasNext() ? null : member;
    }

    private Access accessOf(TreePath path, Tree tree, Element element) {
        TypeMirror type = info.getTypes().erasure(element.asType());
        if (type.getKind() == TypeKind.ERROR) {
            // an unresolved type is a subtype of everything to javac
            return variableAccess(path, tree);
        }
        Element collection = info.getElementUtilities().findElement("java.util.Collection");
        Element map = info.getElementUtilities().findElement("java.util.Map");
        if (isSubtype(type, collection) || isSubtype(type, map)) {
            return collectionAccess(path);
        }
        return variableAccess(path, tree);
    }

    private boolean isSubtype(TypeMirror type, Element supertype) {
        return supertype != null
                && info.getTypes().isSubtype(type, info.getTypes().erasure(supertype.asType()));
    }

    private Access collectionAccess(TreePath path) {
        TreePath parent = path.getParentPath();
        if (parent == null || parent.getLeaf().getKind() != Tree.Kind.MEMBER_SELECT) {
            return null;
        }
        Element member = trees.getElement(parent);
        if (member == null || member.getKind() != ElementKind.METHOD) {
            return null;
        }
        String name = member.getSimpleName().toString();
        if (WRITE_METHODS.contains(name)) {
            return Access.WRITE;
        }
        if (READ_METHODS.contains(name)) {
            return Access.READ;
        }
        if (READ_WRITE_METHODS.contains(name)) {
            return Access.READ_WRITE;
        }
        return null;
    }

    private Access variableAccess(TreePath path, Tree tree) {
        TreePath parent = path.getParentPath();
        if (parent == null) {
            return Access.READ;
        }
        Tree parentTree = parent.getLeaf();
        if (target.asType().getKind() == TypeKind.ARRAY && parentTree.getKind() == Tree.Kind.ARRAY_ACCESS) {
            tree = parentTree;
            parent = parent.getParentPath();
            if (parent == null) {
                return Access.READ;
            }
            parentTree = parent.getLeaf();
        }
        return switch (parentTree.getKind()) {
            case POSTFIX_INCREMENT, POSTFIX_DECREMENT, PREFIX_INCREMENT, PREFIX_DECREMENT -> Access.READ_WRITE;
            case ASSIGNMENT -> ((AssignmentTree) parentTree).getVariable().equals(tree) ? Access.WRITE : Access.READ;
            case MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT, PLUS_ASSIGNMENT, MINUS_ASSIGNMENT,
                 LEFT_SHIFT_ASSIGNMENT, RIGHT_SHIFT_ASSIGNMENT, UNSIGNED_RIGHT_SHIFT_ASSIGNMENT,
                 AND_ASSIGNMENT, XOR_ASSIGNMENT, OR_ASSIGNMENT ->
                    ((CompoundAssignmentTree) parentTree).getVariable().equals(tree) ? Access.READ_WRITE : Access.READ;
            default -> Access.READ;
        };
    }

    /** The name mentioned in a comment counts as a usage (NetBeans' "search in comments"). */
    private void scanComments() {
        String name = Names.simpleName(target);
        if (name.isEmpty()) {
            return;
        }
        TokenHierarchy<String> hierarchy = TokenHierarchy.create(text, JavaTokenId.language());
        TokenSequence<JavaTokenId> tokens = hierarchy.tokenSequence(JavaTokenId.language());
        while (tokens.moveNext()) {
            if (cancellation.isCancelled()) {
                return;
            }
            Token<JavaTokenId> token = tokens.token();
            JavaTokenId id = token.id();
            if (id != JavaTokenId.BLOCK_COMMENT && id != JavaTokenId.LINE_COMMENT && id != JavaTokenId.JAVADOC_COMMENT) {
                continue;
            }
            Scanner words = new Scanner(token.text().toString()).useDelimiter("[^a-zA-Z0-9_]");
            while (words.hasNext()) {
                if (words.next().equals(name)) {
                    int start = tokens.offset() + words.match().start();
                    int end = tokens.offset() + words.match().end();
                    usages.add(usage(start, end, null, false, true, List.of()));
                }
            }
        }
    }

    private void addUsage(TreePath path, Access access) {
        CompilationUnitTree unit = path.getCompilationUnit();
        SourcePositions positions = trees.getSourcePositions();
        Tree tree = path.getLeaf();

        if (tree.getKind() == Tree.Kind.IDENTIFIER && "super".contentEquals(((IdentifierTree) tree).getName())
                && treeUtilities.isSynthetic(path)) {
            // a synthetic super() call: report the constructor or class declaring it
            path = enclosingDeclaration(path);
            if (path == null) {
                return;
            }
            if (treeUtilities.isSynthetic(path)) {
                path = enclosingDeclaration(path.getParentPath());
                if (path == null) {
                    return;
                }
            }
            tree = path.getLeaf();
        }
        boolean inImport = enclosingImport(path) != null;

        int start;
        int end;
        if (TreeUtilities.CLASS_TREE_KINDS.contains(tree.getKind())) {
            int[] span = treeUtilities.findNameSpan((ClassTree) tree);
            if (span != null) {
                start = span[0];
                end = span[1];
            } else if (path.getParentPath() != null && path.getParentPath().getLeaf() instanceof NewClassTree newClass) {
                start = (int) positions.getStartPosition(unit, newClass.getIdentifier());
                end = (int) positions.getEndPosition(unit, newClass.getIdentifier());
            } else {
                start = end = (int) positions.getStartPosition(unit, tree);
            }
        } else if (tree.getKind() == Tree.Kind.METHOD) {
            int[] span = treeUtilities.findNameSpan((MethodTree) tree);
            start = span != null ? span[0] : (int) positions.getStartPosition(unit, tree);
            end = span != null ? span[1] : start;
        } else if (tree.getKind() == Tree.Kind.NEW_CLASS) {
            Tree identifier = ((NewClassTree) tree).getIdentifier();
            if (identifier.getKind() == Tree.Kind.PARAMETERIZED_TYPE) {
                identifier = ((ParameterizedTypeTree) identifier).getType();
            }
            if (identifier.getKind() == Tree.Kind.MEMBER_SELECT) {
                int[] span = treeUtilities.findNameSpan((MemberSelectTree) identifier);
                start = span != null ? span[0] : (int) positions.getStartPosition(unit, identifier);
                end = span != null ? span[1] : start;
            } else {
                TreePath parent = path.getParentPath();
                Element parentElement = parent == null ? null : trees.getElement(parent);
                if (parentElement != null && parent.getLeaf().getKind() == Tree.Kind.VARIABLE
                        && parentElement.getKind() == ElementKind.ENUM_CONSTANT) {
                    int[] span = treeUtilities.findNameSpan((VariableTree) parent.getLeaf());
                    start = span != null ? span[0] : (int) positions.getStartPosition(unit, parent.getLeaf());
                    end = span != null ? span[1] : start;
                } else {
                    start = (int) positions.getStartPosition(unit, identifier);
                    end = (int) positions.getEndPosition(unit, identifier);
                }
            }
        } else if (tree.getKind() == Tree.Kind.MEMBER_SELECT) {
            int[] span = treeUtilities.findNameSpan((MemberSelectTree) tree);
            start = span != null ? span[0] : (int) positions.getStartPosition(unit, tree);
            end = span != null ? span[1] : start;
        } else if (tree.getKind() == Tree.Kind.MEMBER_REFERENCE) {
            // the name after "::"
            end = (int) positions.getEndPosition(unit, tree);
            start = Math.max((int) positions.getStartPosition(unit, tree),
                    end - ((MemberReferenceTree) tree).getName().length());
        } else {
            start = (int) positions.getStartPosition(unit, tree);
            end = (int) positions.getEndPosition(unit, tree);
            if (end < 0) {
                end = start;
            }
        }
        if (start < 0) {
            return;
        }
        usages.add(usage(start, end, access, inImport, false, enclosingOf(path)));
    }

    private Usage usage(int start, int end, Access access, boolean inImport, boolean inComment,
            List<EnclosingElement> enclosing) {
        LineMap lineMap = info.getCompilationUnit().getLineMap();
        long line = lineMap.getLineNumber(start);
        int lineStart = (int) lineMap.getStartPosition(line);
        long endLine = lineMap.getLineNumber(Math.max(start, end));
        int lineEnd = text.length();
        if (lineMap.getLineNumber(text.length()) > endLine) {
            lineEnd = (int) lineMap.getStartPosition(endLine + 1) - 1;
        }
        // the text begins at the first non-blank character of the line
        int textStart = lineStart;
        while (textStart < start && Character.isWhitespace(text.charAt(textStart))) {
            textStart++;
        }
        String lineText = text.substring(textStart, Math.max(textStart, lineEnd)).stripTrailing();
        UsageContext context = fileContext.context(enclosing);
        return new Usage(fileContext.file(),
                SourceSet.editorOffset(text, start), SourceSet.editorOffset(text, end), (int) line,
                SourceSet.editorOffset(text, textStart), lineText,
                access, inImport, inComment, fileContext.inTestRoot(), context);
    }

    /** The declarations around {@code path}, outermost first: types, methods, constructors, fields, initializers. */
    private List<EnclosingElement> enclosingOf(TreePath path) {
        List<EnclosingElement> result = new ArrayList<>();
        for (TreePath current = path.getParentPath(); current != null; current = current.getParentPath()) {
            Tree leaf = current.getLeaf();
            Tree.Kind kind = leaf.getKind();
            boolean declaration = TreeUtilities.CLASS_TREE_KINDS.contains(kind) || kind == Tree.Kind.METHOD
                    || (kind == Tree.Kind.VARIABLE && current.getParentPath() != null
                        && TreeUtilities.CLASS_TREE_KINDS.contains(current.getParentPath().getLeaf().getKind()))
                    || (kind == Tree.Kind.BLOCK && current.getParentPath() != null
                        && TreeUtilities.CLASS_TREE_KINDS.contains(current.getParentPath().getLeaf().getKind()));
            if (!declaration) {
                continue;
            }
            if (kind == Tree.Kind.BLOCK) {
                boolean isStatic = ((BlockTree) leaf).isStatic();
                result.add(0, new EnclosingElement(isStatic ? ElementKind.STATIC_INIT : ElementKind.INSTANCE_INIT,
                        isStatic ? "<static init>" : "<init>", Set.of()));
                continue;
            }
            Element element = trees.getElement(current);
            if (element == null) {
                continue;
            }
            String name = Names.displayName(element);
            if (name.isEmpty()) {
                name = anonymousName(current);
            }
            result.add(0, new EnclosingElement(element.getKind(), name, Set.copyOf(element.getModifiers())));
        }
        return result;
    }

    private String anonymousName(TreePath classPath) {
        TreePath parent = classPath.getParentPath();
        if (parent != null && parent.getLeaf() instanceof NewClassTree newClass) {
            return "new " + newClass.getIdentifier() + "() {...}";
        }
        return "{...}";
    }

    private static TreePath enclosingImport(TreePath path) {
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            if (current.getLeaf().getKind() == Tree.Kind.IMPORT) {
                return current;
            }
        }
        return null;
    }

    private static TreePath enclosingDeclaration(TreePath path) {
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            Tree.Kind kind = current.getLeaf().getKind();
            if (TreeUtilities.CLASS_TREE_KINDS.contains(kind) || kind == Tree.Kind.METHOD
                    || kind == Tree.Kind.IMPORT || kind == Tree.Kind.VARIABLE) {
                return current;
            }
        }
        return null;
    }
}
