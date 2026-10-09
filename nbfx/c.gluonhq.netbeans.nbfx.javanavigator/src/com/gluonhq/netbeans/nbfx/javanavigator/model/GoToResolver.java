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

import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.api.elements.SourceModifiers;
import com.gluonhq.netbeans.nbfx.api.elements.SourceLocation;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocSourcePositions;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.TreeUtilities;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * Finds where the Go to commands take the caret, after {@code GoToSupport} of NetBeans'
 * {@code java.editor} module: the declaration of the element at the caret (<em>Go to
 * Declaration</em>, also Shortcut+Click), the source of the element or, for a variable, of its type
 * (<em>Go to Source</em>), or the method the one at the caret overrides and the supertype of the
 * type there (<em>Go to Super Implementation</em>). In a Javadoc comment, the element is the one a
 * reference names. The target is located in the source file
 * declaring it - the file itself, another file of the project, or a library with sources - at the
 * start of its declaration. Runs on the calling thread; call it off the FX thread.
 */
public final class GoToResolver {

    /** The Go to commands. */
    public enum Kind {
        /** The source of the element at the caret; of its type, for a variable. */
        SOURCE,
        /** The declaration of the element at the caret. */
        DECLARATION,
        /** The method the one at the caret overrides, or the supertype of the type there. */
        SUPER_IMPLEMENTATION,
        /** The subtypes of the type at the caret, or the methods overriding the method there; see {@link #implementations}. */
        IMPLEMENTATION
    }

    /**
     * A subtype of the type at the caret, or a method overriding the method there, as listed in
     * the Implementors/Overridders popup.
     *
     * @param name      the type's name: its simple name, or "anonymous in" its enclosing member
     * @param enclosing what encloses the type: its package, or its outer type
     * @param kind      the element's kind, for the icon: a type kind, or {@code METHOD}
     * @param modifiers the element's modifiers, for the icon ({@code SourceModifiers} bits)
     * @param handle    the element, to locate in its source
     * @param file      the file declaring it when it is the compiled file, else {@code null}
     * @param offset    where its declaration starts in {@code file}, else {@code -1}
     * @param owner     the binary name of the type (the method's, for an overrider), to find it in
     *                  its file when its handle does not resolve - an anonymous class
     * @param classpath the class path it was found on, to locate its source
     * @param ancestor  whether it is the other way round: a method the one at the caret overrides,
     *                  named by its type, listed with an up arrow
     */
    public record Implementation(String name, String enclosing, ElementKind kind, int modifiers,
            ElementHandle<? extends Element> handle, FileObject file, int offset, String owner, ClasspathInfo classpath,
            boolean ancestor) {
    }

    /**
     * The implementations of the element at the caret.
     *
     * @param name  the element's name, for messages
     * @param items the implementations, by name
     */
    public record Implementations(String name, List<Implementation> items) {
    }

    /**
     * The element the caret resolves to and where it is declared.
     *
     * @param name     the element's name, for messages
     * @param location its declaration, or {@code null} when its source is not available (a
     *                 library without sources)
     */
    public record Target(String name, SourceLocation location) {

        /** The caret is on the name of the declaration itself: there is nowhere to go, and nothing to report. */
        public static final Target HERE = new Target("", null);
    }

    /** What a compilation found: a location in the compiled file itself, else a handle to resolve elsewhere. */
    private record Found(String name, SourceLocation location, ElementHandle<? extends Element> handle) {
        static final Found HERE = new Found("", null, null);
    }

    private GoToResolver() {
    }

    /**
     * The target of {@code kind} at {@code offset} of {@code file}, with {@code text} as its
     * content; {@code null} when nothing to navigate to is there, {@link Target#HERE} when the caret
     * is on the name of the declaration itself. A target in another file is
     * located in that file's editor text when it is open ({@code openSources}, may be
     * {@code null}), else in the file on disk.
     */
    public static Target resolve(Kind kind, FileObject file, String text, int offset, OpenSources openSources)
            throws IOException {
        JavaSources sources = JavaSources.of(file, text);
        if (sources == null) {
            return null;
        }
        Found[] found = new Found[1];
        sources.run(JavaSource.Phase.RESOLVED, controller -> found[0] = find(kind, controller, file, text, offset));
        if (found[0] == null) {
            return null;
        }
        Found target = found[0];
        if (target == Found.HERE) {
            return Target.HERE;
        }
        if (target.location() != null || target.handle() == null) {
            return new Target(target.name(), target.location());
        }
        FileObject declaring = SourceUtils.getFile(target.handle(), sources.classpath());
        if (declaring == null) {
            return new Target(target.name(), null);
        }
        String declaringText = openSources == null ? null : openSources.textOf(declaring);
        int declaration = MembersScanner.declarationStart(declaring, declaringText, target.handle());
        // The declaring file is worth opening even when the declaration was not found in it.
        return new Target(target.name(), new SourceLocation(declaring, Math.max(declaration, 0)));
    }

    /**
     * The implementations of the element at {@code offset} of {@code file}, with {@code text} as
     * its content - the subtypes of a type, the overriding methods of a method, all the way down
     * - in the sources of {@code sourceRoots} (the open projects') through their class indexes,
     * whichever file the caret is in, as NetBeans searches them; and in the compiled text itself,
     * whose unsaved edits the index does not know. Libraries are not searched, as in NetBeans.
     * {@code null} when the caret is on neither a type nor a method. Call it off the FX thread.
     */
    public static Implementations implementations(FileObject file, String text, int offset,
            Collection<FileObject> sourceRoots) throws IOException {
        JavaSources sources = JavaSources.of(file, text);
        if (sources == null) {
            return null;
        }
        Collector collector = new Collector();
        String[] name = new String[1];
        List<ElementHandle<TypeElement>> typeHandle = new ArrayList<>();
        List<ElementHandle<ExecutableElement>> methodHandle = new ArrayList<>();
        sources.run(JavaSource.Phase.RESOLVED, controller -> {
            Element element = elementAt(controller, text, offset);
            TypeElement type;
            ExecutableElement method = null;
            if (element instanceof TypeElement t) {
                type = t;
            } else if (element instanceof ExecutableElement m && m.getKind() == ElementKind.METHOD
                    && m.getEnclosingElement() instanceof TypeElement owner) {
                type = owner;
                method = m;
            } else {
                return;
            }
            name[0] = nameOf(element);
            if (method != null && method.getModifiers().contains(Modifier.PRIVATE)) {
                return;
            }
            typeHandle.add(ElementHandle.create(type));
            if (method != null) {
                methodHandle.add(ElementHandle.create(method));
            }
            collector.scanFile(controller, sources.classpath(), file, type, method == null, List.of(method == null ? new ExecutableElement[0] : new ExecutableElement[] {method}));
        });
        if (name[0] == null) {
            return null;
        }
        if (!typeHandle.isEmpty()) {
            collector.searchRoots(sourceRoots, typeHandle.get(0), methodHandle.isEmpty(), methodHandle);
        }
        return new Implementations(name[0], methodHandle.isEmpty() ? collector.items() : collector.itemsOf(methodHandle.get(0)));
    }

    /**
     * Where {@code implementation} is declared: in the compiled file when found there, else in
     * the source file declaring it, at the start of its declaration; {@code null} when that
     * source is not available. Call it off the FX thread.
     */
    public static SourceLocation locate(Implementation implementation, OpenSources openSources) throws IOException {
        if (implementation.file() != null && implementation.offset() >= 0) {
            return new SourceLocation(implementation.file(), implementation.offset());
        }
        FileObject declaring = SourceUtils.getFile(implementation.handle(), implementation.classpath());
        if (declaring == null) {
            return null;
        }
        String declaringText = openSources == null ? null : openSources.textOf(declaring);
        int declaration = MembersScanner.declarationStart(declaring, declaringText, implementation.handle());
        if (declaration < 0) {
            // An anonymous class, which javac does not resolve by name: found by scanning the file.
            declaration = findInFile(declaring, declaringText, implementation);
        }
        // The declaring file is worth opening even when the declaration was not found in it.
        return new SourceLocation(declaring, Math.max(declaration, 0));
    }

    /**
     * The start of the declaration of {@code implementation} in {@code file} - the class whose
     * binary name is its owner (at its {@code new}, for an anonymous class), or the method of
     * that class with the handle's signature - compiling {@code text} when given; {@code -1}
     * when not found.
     */
    private static int findInFile(FileObject file, String text, Implementation implementation) throws IOException {
        JavaSource source = text == null
                ? JavaSource.forFileObject(file)
                : JavaSource.create(ClasspathInfo.create(file), MemorySources.copyOf(file, text));
        if (source == null) {
            return -1;
        }
        int[] offset = {-1};
        source.runUserActionTask(controller -> {
            controller.toPhase(JavaSource.Phase.RESOLVED);
            Trees trees = controller.getTrees();
            Elements elements = controller.getElements();
            CompilationUnitTree unit = controller.getCompilationUnit();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    if (offset[0] >= 0) {
                        return null;
                    }
                    Element element = trees.getElement(getCurrentPath());
                    if (element instanceof TypeElement type && implementation.owner().contentEquals(elements.getBinaryName(type))) {
                        Tree target = null;
                        if (implementation.kind() == ElementKind.METHOD) {
                            for (Element member : type.getEnclosedElements()) {
                                if (member.getKind() == ElementKind.METHOD
                                        && implementation.handle().signatureEquals(ElementHandle.create(member))) {
                                    target = trees.getTree(member);
                                    break;
                                }
                            }
                        } else {
                            TreePath parent = getCurrentPath().getParentPath();
                            target = parent != null && parent.getLeaf().getKind() == Tree.Kind.NEW_CLASS ? parent.getLeaf() : tree;
                        }
                        if (target != null) {
                            offset[0] = (int) trees.getSourcePositions().getStartPosition(unit, target);
                            return null;
                        }
                    }
                    return super.visitClass(tree, unused);
                }
            }.scan(unit, null);
        }, true);
        return offset[0];
    }

    private static Found find(Kind kind, CompilationController controller, FileObject file, String text, int offset) {
        if (kind == Kind.IMPLEMENTATION) {
            throw new IllegalArgumentException("Use implementations()");
        }
        Element element = kind == Kind.SUPER_IMPLEMENTATION
                ? superOf(controller, offset)
                : elementAt(controller, text, offset);
        if (element == null) {
            return null;
        }
        if (kind == Kind.SOURCE && element instanceof VariableElement variable && declared(variable.asType()) != null) {
            // Go to Source on a variable opens its type, as NetBeans' does.
            element = declared(variable.asType());
        }
        return describe(controller, file, element, offset);
    }

    /**
     * The element named by the identifier at {@code offset} - the caret may be at either end of it
     * - or {@code null} when the caret is not on an identifier, or on one that names nothing to
     * go to (a keyword, an unresolved name, a package).
     */
    private static Element elementAt(CompilationController controller, String text, int offset) {
        int start = Math.min(offset, text.length());
        int end = start;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        if (start == end || !Character.isJavaIdentifierStart(text.charAt(start))) {
            return null;
        }
        String word = text.substring(start, end);
        if (SourceVersion.isKeyword(word) && !"this".equals(word) && !"super".equals(word)) {
            return null;
        }
        // pathFor answers the enclosing tree on the first character of a name: look one in.
        TreePath path = controller.getTreeUtilities().pathFor(start + 1);
        if (path == null) {
            return null;
        }
        Documented documented = documentedElementAt(controller, path, start + 1);
        if (documented != null) {
            // In a documentation comment: only a reference names something to go to.
            return isNavigable(documented.element()) ? documented.element() : null;
        }
        TreePath parent = path.getParentPath();
        if (parent != null && parent.getLeaf().getKind() == Tree.Kind.NEW_CLASS) {
            // The class name of an instantiation stands for the constructor called.
            Element constructor = controller.getTrees().getElement(parent);
            if (constructor != null && constructor.getKind() == ElementKind.CONSTRUCTOR) {
                return constructor;
            }
        }
        Element element = controller.getTrees().getElement(path);
        return isNavigable(element) ? element : null;
    }

    /**
     * The element a Javadoc reference at {@code offset} names ({@code {@link Base#other()}},
     * {@code @param name}, {@code @see}...), when the offset is in the documentation comment of
     * one of the declarations {@code enclosing} holds - the comment lies between members, so
     * {@code pathFor} answers their class, or the compilation unit; {@code null} outside any comment.
     */
    /** What a documentation comment names at an offset: an element, or {@code null} for plain text. */
    private record Documented(Element element) {
    }

    private static Documented documentedElementAt(CompilationController controller, TreePath enclosing, int offset) {
        List<? extends Tree> declarations;
        if (enclosing.getLeaf() instanceof ClassTree type) {
            declarations = type.getMembers();
        } else if (enclosing.getLeaf() instanceof CompilationUnitTree unit) {
            declarations = unit.getTypeDecls();
        } else {
            return null;
        }
        DocTrees docTrees = controller.getDocTrees();
        DocSourcePositions positions = docTrees.getSourcePositions();
        CompilationUnitTree unit = controller.getCompilationUnit();
        for (Tree declaration : declarations) {
            TreePath path = new TreePath(enclosing, declaration);
            DocCommentTree comment = docTrees.getDocCommentTree(path);
            if (comment == null) {
                continue;
            }
            long start = positions.getStartPosition(unit, comment, comment);
            long end = positions.getEndPosition(unit, comment, comment);
            if (start <= offset && offset <= end) {
                DocTreePath docPath = controller.getTreeUtilities().pathFor(path, comment, offset);
                return new Documented(docPath == null ? null : docTrees.getElement(docPath));
            }
        }
        return null;
    }

    private static boolean isNavigable(Element element) {
        if (element == null) {
            return false;
        }
        ElementKind kind = element.getKind();
        if (kind.isDeclaredType()) {
            return element.asType().getKind() != TypeKind.ERROR;
        }
        return kind.isExecutable() || kind.isVariable() || kind == ElementKind.TYPE_PARAMETER;
    }

    /**
     * What the method enclosing {@code offset} overrides, or the supertype of the type enclosing it
     * when the caret is in no method; {@code null} when there is none.
     */
    private static Element superOf(CompilationController controller, int offset) {
        for (TreePath path = controller.getTreeUtilities().pathFor(offset); path != null; path = path.getParentPath()) {
            Tree.Kind kind = path.getLeaf().getKind();
            if (kind == Tree.Kind.METHOD) {
                Element element = controller.getTrees().getElement(path);
                return element instanceof ExecutableElement method && method.getKind() == ElementKind.METHOD
                        ? overridden(controller, method) : null;
            }
            if (TreeUtilities.CLASS_TREE_KINDS.contains(kind)) {
                Element element = controller.getTrees().getElement(path);
                return element instanceof TypeElement type ? supertypeOf(type) : null;
            }
        }
        return null;
    }

    /** The nearest method {@code method} overrides: up the superclasses first, then the interfaces. */
    private static ExecutableElement overridden(CompilationController controller, ExecutableElement method) {
        List<ExecutableElement> all = overriddenAll(controller, method);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every method {@code method} overrides, nearest first: up the superclasses, then the interfaces. */
    static List<ExecutableElement> overriddenAll(CompilationController controller, ExecutableElement method) {
        List<ExecutableElement> all = new ArrayList<>();
        if (method.getModifiers().contains(Modifier.STATIC) || method.getModifiers().contains(Modifier.PRIVATE)
                || !(method.getEnclosingElement() instanceof TypeElement owner)) {
            return all;
        }
        Elements elements = controller.getElements();
        Types types = controller.getTypes();
        Deque<TypeElement> pending = new ArrayDeque<>();
        Set<TypeElement> seen = new HashSet<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            TypeElement type = pending.poll();
            for (TypeMirror supertype : types.directSupertypes(type.asType())) {
                TypeElement superElement = declared(supertype);
                if (superElement == null || !seen.add(superElement)) {
                    continue;
                }
                for (Element member : superElement.getEnclosedElements()) {
                    if (member instanceof ExecutableElement candidate && candidate.getKind() == ElementKind.METHOD
                            && elements.overrides(method, candidate, owner)) {
                        all.add(candidate);
                        break;
                    }
                }
                pending.add(superElement);
            }
        }
        return all;
    }

    /** The superclass of {@code type} other than {@code Object}, else its first interface, else {@code null}. */
    private static TypeElement supertypeOf(TypeElement type) {
        TypeElement superclass = declared(type.getSuperclass());
        if (superclass != null && !superclass.getQualifiedName().contentEquals("java.lang.Object")) {
            return superclass;
        }
        for (TypeMirror implemented : type.getInterfaces()) {
            TypeElement element = declared(implemented);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    /**
     * Where {@code element} is declared: at the start of its declaration in the compiled file when
     * it is declared there, else as a handle to resolve in the file declaring it. The caret goes
     * to the start - the modifiers and the type, not the name - as in NetBeans; it also keeps the
     * caret off the name, so the occurrence highlighting does not light up on arrival.
     */
    private static Found describe(CompilationController controller, FileObject file, Element element, int offset) {
        String name = nameOf(element);
        TreePath path = controller.getTrees().getPath(element);
        if (path != null && path.getCompilationUnit() == controller.getCompilationUnit()) {
            int[] nameSpan = nameSpan(controller.getTreeUtilities(), path.getLeaf());
            if (nameSpan != null && nameSpan[0] <= offset && offset <= nameSpan[1]) {
                // On the name of the declaration itself: stay, as NetBeans does.
                return Found.HERE;
            }
            int at = (int) controller.getTrees().getSourcePositions().getStartPosition(path.getCompilationUnit(), path.getLeaf());
            return new Found(name, new SourceLocation(file, Math.max(at, 0)), null);
        }
        try {
            return new Found(name, null, ElementHandle.create(element));
        } catch (IllegalArgumentException noHandle) {
            // A local of another compilation unit, or another kind a handle cannot describe.
            return new Found(name, null, null);
        }
    }

    private static int[] nameSpan(TreeUtilities treeUtilities, Tree tree) {
        if (tree instanceof ClassTree type) {
            return treeUtilities.findNameSpan(type);
        }
        if (tree instanceof MethodTree method) {
            return treeUtilities.findNameSpan(method);
        }
        if (tree instanceof VariableTree variable) {
            return treeUtilities.findNameSpan(variable);
        }
        return null;
    }

    /**
     * Collects the subtypes of a type and the overriders of its methods: from the compiled file
     * (every class declared in it, anonymous ones included, that is a subtype) and from the class
     * indexes of the source roots (the direct implementors of each type found, transitively, in
     * sources only), without duplicates; the subtypes in one group, each method's overriders in
     * its own.
     */
    static final class Collector {

        private static final Object SUBTYPES = new Object();
        private final Map<Object, Map<String, Implementation>> found = new LinkedHashMap<>();

        /** The subtypes, by name. */
        List<Implementation> items() {
            return itemsOf(SUBTYPES);
        }

        /** The overriders of {@code method}, by name. */
        List<Implementation> itemsOf(ElementHandle<ExecutableElement> method) {
            return itemsOf((Object) method);
        }

        private List<Implementation> itemsOf(Object group) {
            Map<String, Implementation> entries = found.get(group);
            if (entries == null) {
                return List.of();
            }
            List<Implementation> items = new ArrayList<>(entries.values());
            items.sort(Comparator.comparing(Implementation::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Implementation::enclosing));
            return items;
        }

        /**
         * The subtypes of {@code type} declared in the compiled {@code file} (when {@code subtypes}),
         * and their overriders of {@code methods}.
         */
        void scanFile(CompilationController controller, ClasspathInfo classpath, FileObject file,
                TypeElement type, boolean subtypes, List<ExecutableElement> methods) {
            Types types = controller.getTypes();
            CompilationUnitTree unit = controller.getCompilationUnit();
            TypeMirror target = types.erasure(type.asType());
            Map<ElementHandle<ExecutableElement>, ExecutableElement> resolved = handlesOf(methods);
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    Element element = controller.getTrees().getElement(getCurrentPath());
                    if (element instanceof TypeElement candidate && !candidate.equals(type)
                            && types.isSubtype(types.erasure(candidate.asType()), target)) {
                        add(controller, classpath, candidate, subtypes, resolved, file, getCurrentPath());
                    }
                    return super.visitClass(tree, unused);
                }
            }.scan(unit, null);
        }

        /**
         * The subtypes of {@code type} in the sources of {@code roots}, and their subtypes in turn,
         * with their overriders of {@code methods}; see {@link #searchRoots(Collection, List)}.
         */
        void searchRoots(Collection<FileObject> roots, ElementHandle<TypeElement> type, boolean subtypes,
                List<ElementHandle<ExecutableElement>> methods) throws IOException {
            searchRoots(roots, List.of(new Search(this, type, subtypes, methods)));
        }

        /**
         * A type whose subtypes are searched into a collector: as it is searched, {@code frontier}
         * holds the types found in the last round, whose direct implementors the next round asks.
         *
         * @param collector where the subtypes (when {@code subtypes}) and the overriders of {@code methods} go
         */
        record Search(Collector collector, ElementHandle<TypeElement> type, boolean subtypes,
                List<ElementHandle<ExecutableElement>> methods, Set<String> visited,
                List<ElementHandle<TypeElement>> frontier) {

            Search(Collector collector, ElementHandle<TypeElement> type, boolean subtypes,
                    List<ElementHandle<ExecutableElement>> methods) {
                this(collector, type, subtypes, methods, new HashSet<>(Set.of(type.getBinaryName())), new ArrayList<>(List.of(type)));
            }
        }

        /**
         * Runs {@code searches} together over the sources of {@code roots}: each root's class index
         * answers the direct implementors of the types found so far for every search, and the
         * types found are resolved on that root's class path in one compilation task per round -
         * a javac context is costly, so the searches of a whole file share it - until no root
         * adds a type to any search.
         */
        static void searchRoots(Collection<FileObject> roots, List<Search> searches) throws IOException {
            List<ClasspathInfo> classpaths = new ArrayList<>();
            for (FileObject root : new LinkedHashSet<>(roots)) {
                classpaths.add(ClasspathInfo.create(root));
            }
            Map<Search, List<ElementHandle<TypeElement>>> next = new LinkedHashMap<>();
            boolean more = searches.stream().anyMatch(search -> !search.frontier().isEmpty());
            while (more) {
                for (ClasspathInfo classpath : classpaths) {
                    ClassIndex index = classpath.getClassIndex();
                    Map<Search, Set<ElementHandle<TypeElement>>> found = new LinkedHashMap<>();
                    for (Search search : searches) {
                        Set<ElementHandle<TypeElement>> subtypes = new LinkedHashSet<>();
                        for (ElementHandle<TypeElement> handle : search.frontier()) {
                            Set<ElementHandle<TypeElement>> direct = index.getElements(handle,
                                    EnumSet.of(ClassIndex.SearchKind.IMPLEMENTORS), EnumSet.of(ClassIndex.SearchScope.SOURCE));
                            if (direct == null) {
                                continue;
                            }
                            for (ElementHandle<TypeElement> subtype : direct) {
                                if (search.visited().add(subtype.getBinaryName())) {
                                    subtypes.add(subtype);
                                }
                            }
                        }
                        if (!subtypes.isEmpty()) {
                            found.put(search, subtypes);
                            next.computeIfAbsent(search, s -> new ArrayList<>()).addAll(subtypes);
                        }
                    }
                    if (found.isEmpty()) {
                        continue;
                    }
                    JavaSource.create(classpath, List.of()).runUserActionTask(controller -> {
                        for (Map.Entry<Search, Set<ElementHandle<TypeElement>>> entry : found.entrySet()) {
                            Search search = entry.getKey();
                            Map<ElementHandle<ExecutableElement>, ExecutableElement> resolved = new LinkedHashMap<>();
                            for (ElementHandle<ExecutableElement> method : search.methods()) {
                                ExecutableElement element = method.resolve(controller);
                                if (element != null) {
                                    resolved.put(method, element);
                                }
                            }
                            for (ElementHandle<TypeElement> subtype : entry.getValue()) {
                                TypeElement element = subtype.resolve(controller);
                                if (element != null) {
                                    search.collector().add(controller, classpath, element, search.subtypes(), resolved, null, null);
                                }
                            }
                        }
                    }, true);
                }
                more = false;
                for (Search search : searches) {
                    search.frontier().clear();
                    List<ElementHandle<TypeElement>> found = next.remove(search);
                    if (found != null) {
                        search.frontier().addAll(found);
                        more = true;
                    }
                }
            }
        }

        private static Map<ElementHandle<ExecutableElement>, ExecutableElement> handlesOf(List<ExecutableElement> methods) {
            Map<ElementHandle<ExecutableElement>, ExecutableElement> handles = new LinkedHashMap<>();
            for (ExecutableElement method : methods) {
                handles.put(ElementHandle.create(method), method);
            }
            return handles;
        }

        /**
         * Adds {@code subtype} (when {@code subtypes}) and its methods overriding {@code methods},
         * if any; {@code path} is its tree in the compiled {@code file}.
         */
        private void add(CompilationController controller, ClasspathInfo classpath, TypeElement subtype, boolean subtypes,
                Map<ElementHandle<ExecutableElement>, ExecutableElement> methods, FileObject file, TreePath path) {
            Elements elements = controller.getElements();
            String typeKey = elements.getBinaryName(subtype).toString();
            // An anonymous class's tree starts at its body: the caret goes to the "new" instead.
            Tree typeTree = path == null ? null
                    : path.getParentPath() != null && path.getParentPath().getLeaf().getKind() == Tree.Kind.NEW_CLASS
                    ? path.getParentPath().getLeaf() : path.getLeaf();
            if (subtypes) {
                put(SUBTYPES, typeKey, controller, classpath, subtype, subtype, typeTree, file, path != null);
            }
            for (Map.Entry<ElementHandle<ExecutableElement>, ExecutableElement> method : methods.entrySet()) {
                for (Element member : subtype.getEnclosedElements()) {
                    if (member instanceof ExecutableElement candidate && candidate.getKind() == ElementKind.METHOD
                            && elements.overrides(candidate, method.getValue(), subtype)) {
                        Tree tree = path == null ? null : controller.getTrees().getTree(candidate);
                        put(method.getKey(), typeKey + "#" + candidate, controller, classpath, subtype, candidate, tree, file, path != null);
                        break;
                    }
                }
            }
        }

        private void put(Object group, String key, CompilationController controller, ClasspathInfo classpath,
                TypeElement subtype, Element element, Tree tree, FileObject file, boolean inFile) {
            Map<String, Implementation> entries = found.computeIfAbsent(group, g -> new LinkedHashMap<>());
            if (entries.containsKey(key)) {
                return;
            }
            ElementHandle<? extends Element> handle;
            try {
                handle = ElementHandle.create(element);
            } catch (IllegalArgumentException noHandle) {
                return;
            }
            int offset = tree == null ? -1
                    : (int) controller.getTrees().getSourcePositions().getStartPosition(controller.getCompilationUnit(), tree);
            entries.put(key, new Implementation(displayName(subtype), enclosingName(subtype), element.getKind(),
                    SourceModifiers.toModifierBits(element.getModifiers()), handle,
                    inFile ? file : null, offset, controller.getElements().getBinaryName(subtype).toString(), classpath, false));
        }

        /** The type's simple name, or "anonymous in" the member holding an anonymous class. */
        static String displayName(TypeElement type) {
            if (!type.getSimpleName().isEmpty()) {
                return type.getSimpleName().toString();
            }
            Element holder = type.getEnclosingElement();
            while (holder != null && !(holder instanceof ExecutableElement) && !(holder instanceof VariableElement)
                    && !(holder instanceof TypeElement named && !named.getSimpleName().isEmpty())) {
                holder = holder.getEnclosingElement();
            }
            String in = holder == null ? "" : holder.getKind() == ElementKind.CONSTRUCTOR
                    ? holder.getEnclosingElement().getSimpleName().toString() : holder.getSimpleName().toString();
            return NbBundle.getMessage(GoToResolver.class, "LBL_AnonymousIn", in);
        }

        /** The package of a top-level type, else the nearest named type enclosing it, qualified. */
        static String enclosingName(TypeElement type) {
            for (Element enclosing = type.getEnclosingElement(); enclosing != null; enclosing = enclosing.getEnclosingElement()) {
                if (enclosing instanceof PackageElement pkg) {
                    return pkg.getQualifiedName().toString();
                }
                if (enclosing instanceof TypeElement named && !named.getSimpleName().isEmpty()) {
                    return named.getQualifiedName().toString();
                }
            }
            return "";
        }
    }

    private static String nameOf(Element element) {
        if (element.getKind() == ElementKind.CONSTRUCTOR) {
            return element.getEnclosingElement().getSimpleName() + "()";
        }
        if (element.getKind() == ElementKind.METHOD) {
            return element.getSimpleName() + "()";
        }
        return element.getSimpleName().toString();
    }

    private static TypeElement declared(TypeMirror type) {
        return type != null && type.getKind() == TypeKind.DECLARED && ((DeclaredType) type).asElement() instanceof TypeElement element
                ? element : null;
    }
}
