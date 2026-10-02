package com.gluonhq.netbeans.nbfx.findusages.query;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.TreeUtilities;
import org.openide.filesystems.FileObject;

/**
 * The element a query looks for, captured in a form that survives across compilations: an
 * {@link ElementHandle} for the members and types javac can name by signature, or the position of
 * the declaration for the locals it cannot (local variables, parameters), which are only ever used
 * in their own file.
 * <p>
 * Ported from {@code WhereUsedQueryUI.create}: the caret on an anonymous class body stands for the
 * constructor being called, packages are not searchable.
 */
public final class UsagesTarget implements SearchTarget {

    /** The kinds a query can be started on, besides the locals. */
    private static final Set<ElementKind> HANDLED = EnumSet.of(
            ElementKind.CLASS, ElementKind.INTERFACE, ElementKind.ENUM, ElementKind.RECORD, ElementKind.ANNOTATION_TYPE,
            ElementKind.METHOD, ElementKind.CONSTRUCTOR, ElementKind.FIELD, ElementKind.ENUM_CONSTANT,
            ElementKind.RECORD_COMPONENT, ElementKind.TYPE_PARAMETER);

    private static final Set<ElementKind> LOCALS = EnumSet.of(
            ElementKind.LOCAL_VARIABLE, ElementKind.PARAMETER, ElementKind.RESOURCE_VARIABLE,
            ElementKind.EXCEPTION_PARAMETER, ElementKind.BINDING_VARIABLE);

    private final FileObject file;
    private final ElementKind kind;
    private final Set<Modifier> modifiers;
    private final String simpleName;
    private final String displayName;
    private final String enclosingTypeName;
    private final ElementHandle<? extends Element> handle;
    private final int declarationOffset;
    private final int offset;

    private UsagesTarget(FileObject file, int offset, Element element, ElementHandle<? extends Element> handle,
            int declarationOffset) {
        this.file = file;
        this.offset = offset;
        this.kind = element.getKind();
        this.modifiers = Set.copyOf(element.getModifiers());
        this.simpleName = Names.simpleName(element);
        this.displayName = Names.displayName(element);
        this.enclosingTypeName = Names.enclosingTypeName(element);
        this.handle = handle;
        this.declarationOffset = declarationOffset;
    }

    /**
     * Resolves the element at {@code offset} of {@code file}, read through {@code sources} so unsaved
     * edits count.
     *
     * @return the target, or {@code null} when there is no searchable element at the caret
     */
    public static UsagesTarget at(FileObject file, int offset, SourceSet sources) throws IOException {
        JavaSource javaSource = sources.javaSourceOf(file);
        if (javaSource == null) {
            return null;
        }
        AtomicReference<UsagesTarget> result = new AtomicReference<>();
        javaSource.runUserActionTask(info -> {
            if (info.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
                return;
            }
            result.set(resolveAt(file, offset, info, SourceSet.textOffset(info, offset)));
        }, true);
        return result.get();
    }

    /**
     * The top-level type declared in {@code file} - the one named after the file, else the first -
     * as a target: what Find Usages on the file's node in the Projects / Files views searches for.
     *
     * @return the target, or {@code null} when the file declares no type
     */
    public static UsagesTarget topLevelTypeOf(FileObject file, SourceSet sources) throws IOException {
        JavaSource javaSource = sources.javaSourceOf(file);
        if (javaSource == null) {
            return null;
        }
        AtomicReference<UsagesTarget> result = new AtomicReference<>();
        javaSource.runUserActionTask(info -> {
            if (info.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
                return;
            }
            CompilationUnitTree unit = info.getCompilationUnit();
            ClassTree type = null;
            for (Tree declaration : unit.getTypeDecls()) {
                if (declaration instanceof ClassTree candidate) {
                    if (type == null || candidate.getSimpleName().contentEquals(file.getName())) {
                        type = candidate;
                    }
                }
            }
            if (type == null) {
                return;
            }
            int[] span = info.getTreeUtilities().findNameSpan(type);
            int offset = span != null ? span[0] : (int) info.getTrees().getSourcePositions().getStartPosition(unit, type);
            result.set(resolveAt(file, SourceSet.editorOffset(info.getText(), offset), info, offset));
        }, true);
        return result.get();
    }

    private static UsagesTarget resolveAt(FileObject file, int editorOffset, CompilationController info, int offset) {
        Trees trees = info.getTrees();
        TreeUtilities treeUtilities = info.getTreeUtilities();
        for (int candidate : new int[] {offset, offset - 1}) {
            if (candidate < 0) {
                continue;
            }
            TreePath path = treeUtilities.pathFor(candidate);
            Element element = path == null ? null : trees.getElement(path);
            if (element == null) {
                continue;
            }
            if (element.getKind() == ElementKind.CLASS && path.getParentPath() != null
                    && path.getParentPath().getLeaf().getKind() == Tree.Kind.NEW_CLASS) {
                // the body of an anonymous class stands for the constructor it calls
                Element constructor = trees.getElement(path.getParentPath());
                if (constructor != null) {
                    path = path.getParentPath();
                    element = constructor;
                }
            }
            if (LOCALS.contains(element.getKind())) {
                int declaration = declarationOffsetOf(info, path, element);
                return declaration < 0 ? null : new UsagesTarget(file, editorOffset, element, null, declaration);
            }
            if (!HANDLED.contains(element.getKind())) {
                continue;
            }
            try {
                return new UsagesTarget(file, editorOffset, element, ElementHandle.create(element), -1);
            } catch (IllegalArgumentException ex) {
                // a kind ElementHandle cannot describe: nothing to search for
                return null;
            }
        }
        return null;
    }

    /** The offset of the name in the declaration of a local {@code element}, or -1 when it is not in this unit. */
    private static int declarationOffsetOf(CompilationController info, TreePath usage, Element element) {
        Trees trees = info.getTrees();
        TreePath declaration = trees.getPath(element);
        if (declaration == null || declaration.getCompilationUnit() != usage.getCompilationUnit()) {
            return -1;
        }
        if (declaration.getLeaf() instanceof VariableTree variable) {
            int[] span = info.getTreeUtilities().findNameSpan(variable);
            if (span != null) {
                return span[0];
            }
        }
        return (int) trees.getSourcePositions().getStartPosition(declaration.getCompilationUnit(), declaration.getLeaf());
    }

    /**
     * The element in the compilation {@code info}, or {@code null} when it does not exist there:
     * the file cannot see the declaration, or the declaration is gone.
     *
     * @param original the on-disk file {@code info} compiles (its in-memory substitute stands for it)
     */
    Element resolveIn(CompilationController info, FileObject original) {
        if (handle != null) {
            return handle.resolve(info);
        }
        if (!file.equals(original)) {
            return null;
        }
        TreePath path = info.getTreeUtilities().pathFor(declarationOffset);
        Element element = path == null ? null : info.getTrees().getElement(path);
        return element != null && element.getKind() == kind && element.getSimpleName().contentEquals(simpleName)
                ? element : null;
    }

    /** The file the query started in; for a local, the only file it can be used in. */
    @Override
    public FileObject getFile() {
        return file;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    public ElementKind getKind() {
        return kind;
    }

    public Set<Modifier> getModifiers() {
        return modifiers;
    }

    /** The name occurrences of the element are written with (the class name for a constructor). */
    @Override
    public String getSimpleName() {
        return simpleName;
    }

    /** The name the query is titled with: {@code greet(String)}, {@code Counter}, {@code count}. */
    @Override
    public String getDisplayName() {
        return displayName;
    }

    /** The qualified name of the declaring type, {@code null} for top-level types. */
    public String getEnclosingTypeName() {
        return enclosingTypeName;
    }

    /** {@code true} for a local variable or parameter, only used in {@link #getFile()}. */
    public boolean isLocal() {
        return handle == null;
    }

    /**
     * {@code true} when only the declaring file can use the element: locals and private members
     * (a private member of a nested type is still visible to its whole top-level file).
     */
    public boolean isFileLocal() {
        return isLocal() || modifiers.contains(Modifier.PRIVATE);
    }

    /** Anonymous or local classes have no source name to search by; the query then scans every candidate. */
    public boolean isSearchableByName() {
        return !simpleName.isEmpty();
    }

    @Override
    public boolean isFilterable() {
        return true;
    }

    @Override
    public Search newSearch(SourceSet sources, boolean searchComments) {
        return new JavaSearch(this, sources, searchComments);
    }

    @Override
    public String toString() {
        return kind + " " + displayName;
    }
}
