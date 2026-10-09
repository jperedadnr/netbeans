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

import com.gluonhq.netbeans.nbfx.api.elements.SourceModifiers;
import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver.Collector;
import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver.Implementation;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.TreeUtilities;
import org.openide.filesystems.FileObject;

/**
 * Finds what the declarations of a file override and what overrides them, for the badges in the
 * editor's gutter, after {@code ComputeOverriding} / {@code ComputeOverriders} of NetBeans'
 * {@code java.editor} module: for each method, the methods of the supertypes it overrides or
 * implements, and the methods of the subtypes - in the file and in the sources of the open
 * projects - overriding or implementing it; for each named class, its subtypes. Runs on the
 * calling thread; call it off the FX thread.
 */
public final class OverriddenScanner {

    /**
     * A declaration with an override relation, at its name's line.
     *
     * @param line        the line of the declaration's name
     * @param ancestors   the methods the declaration overrides, as entries named by their type;
     *                    empty for a class
     * @param implemented whether the ancestors are implemented rather than overridden: all of
     *                    them are abstract
     * @param descendants the subtypes of a class, or the methods overriding the method
     * @param implementor whether the descendants implement rather than override: the declaration
     *                    is an interface, or an abstract method
     */
    public record Mark(int line, List<Implementation> ancestors, boolean implemented,
            List<Implementation> descendants, boolean implementor) {
    }

    /** A declaration found in the file, before its descendants are searched. */
    private record Declaration(int line, TypeElement type, ElementHandle<TypeElement> typeHandle, boolean searchable,
            boolean implementor, List<Method> methods) {
    }

    private record Method(int line, ElementHandle<ExecutableElement> handle, boolean abstractLike,
            List<Implementation> ancestors, boolean implemented) {
    }

    private OverriddenScanner() {
    }

    /**
     * The marks of {@code file} with {@code text} as its content, searching the subtypes in the
     * sources of {@code sourceRoots}; stops, returning what it has, once {@code cancelled} says so.
     */
    public static List<Mark> scan(FileObject file, String text, Collection<FileObject> sourceRoots, BooleanSupplier cancelled)
            throws IOException {
        JavaSources sources = JavaSources.of(file, text);
        if (sources == null) {
            return List.of();
        }
        List<Declaration> declarations = new ArrayList<>();
        List<Collector> collectors = new ArrayList<>();
        sources.run(JavaSource.Phase.RESOLVED, controller -> {
            int[] lineStarts = lineStarts(text);
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    if (cancelled.getAsBoolean()) {
                        return null;
                    }
                    Element element = controller.getTrees().getElement(getCurrentPath());
                    if (element instanceof TypeElement type) {
                        Declaration declaration = describe(controller, sources.classpath(), file, getCurrentPath(), type, lineStarts);
                        declarations.add(declaration);
                        if (declaration.searchable()) {
                            Collector collector = new Collector();
                            List<ExecutableElement> methods = new ArrayList<>();
                            for (Element member : type.getEnclosedElements()) {
                                if (member instanceof ExecutableElement method && isOverridable(method)) {
                                    methods.add(method);
                                }
                            }
                            collector.scanFile(controller, sources.classpath(), file, type, true, methods);
                            collectors.add(collector);
                        } else {
                            collectors.add(null);
                        }
                    }
                    return super.visitClass(tree, unused);
                }
            }.scan(controller.getCompilationUnit(), null);
        });
        // One search per named class, run together: the file's searches share each root's compilation.
        List<Collector.Search> searches = new ArrayList<>();
        for (int i = 0; i < declarations.size(); i++) {
            if (collectors.get(i) != null) {
                searches.add(new Collector.Search(collectors.get(i), declarations.get(i).typeHandle(), true,
                        declarations.get(i).methods().stream().map(Method::handle).toList()));
            }
        }
        if (!cancelled.getAsBoolean()) {
            Collector.searchRoots(sourceRoots, searches);
        }
        List<Mark> marks = new ArrayList<>();
        for (int i = 0; i < declarations.size(); i++) {
            Declaration declaration = declarations.get(i);
            Collector collector = collectors.get(i);
            if (collector != null && declaration.line() >= 0) {
                List<Implementation> subtypes = collector.items();
                if (!subtypes.isEmpty()) {
                    marks.add(new Mark(declaration.line(), List.of(), false, subtypes, declaration.implementor()));
                }
            }
            for (Method method : declaration.methods()) {
                List<Implementation> overriders = collector == null ? List.of() : collector.itemsOf(method.handle());
                if (!method.ancestors().isEmpty() || !overriders.isEmpty()) {
                    marks.add(new Mark(method.line(), method.ancestors(), method.implemented(), overriders, method.abstractLike()));
                }
            }
        }
        marks.sort((a, b) -> Integer.compare(a.line(), b.line()));
        return marks;
    }

    private static Declaration describe(CompilationController controller, ClasspathInfo classpath, FileObject file,
            TreePath path, TypeElement type, int[] lineStarts) {
        TreeUtilities treeUtilities = controller.getTreeUtilities();
        boolean anonymous = type.getSimpleName().isEmpty() || type.getNestingKind().isNested() && type.getEnclosingElement() instanceof ExecutableElement;
        int[] nameSpan = anonymous ? null : treeUtilities.findNameSpan((ClassTree) path.getLeaf());
        int line = nameSpan == null ? -1 : lineOf(lineStarts, nameSpan[0]);
        ElementHandle<TypeElement> typeHandle = null;
        try {
            typeHandle = ElementHandle.create(type);
        } catch (IllegalArgumentException noHandle) {
            // A local class: searchable in its file only, which the file scan covers.
        }
        boolean implementor = type.getKind().isInterface();
        List<Method> methods = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (!(member instanceof ExecutableElement method) || !isOverridable(method)) {
                continue;
            }
            MethodTree tree = (MethodTree) controller.getTrees().getTree(method);
            int[] span = tree == null ? null : treeUtilities.findNameSpan(tree);
            if (span == null) {
                continue;
            }
            List<Implementation> ancestors = new ArrayList<>();
            boolean allAbstract = true;
            for (ExecutableElement overridden : GoToResolver.overriddenAll(controller, method)) {
                if (!(overridden.getEnclosingElement() instanceof TypeElement owner)) {
                    continue;
                }
                ElementHandle<? extends Element> handle;
                try {
                    handle = ElementHandle.create(overridden);
                } catch (IllegalArgumentException noHandle) {
                    continue;
                }
                allAbstract &= isAbstract(overridden);
                ancestors.add(new Implementation(owner.getQualifiedName().toString(), "", ElementKind.METHOD,
                        SourceModifiers.toModifierBits(overridden.getModifiers()), handle, null, -1,
                        controller.getElements().getBinaryName(owner).toString(), classpath, true));
            }
            methods.add(new Method(lineOf(lineStarts, span[0]), ElementHandle.create(method), isAbstract(method),
                    List.copyOf(ancestors), !ancestors.isEmpty() && allAbstract));
        }
        return new Declaration(line, type, typeHandle, typeHandle != null && !anonymous, implementor, List.copyOf(methods));
    }

    private static boolean isOverridable(ExecutableElement method) {
        return method.getKind() == ElementKind.METHOD
                && !method.getModifiers().contains(Modifier.PRIVATE)
                && !method.getModifiers().contains(Modifier.STATIC);
    }

    /** Whether {@code method} has no body: abstract, or an interface's neither default nor static method. */
    private static boolean isAbstract(ExecutableElement method) {
        return method.getModifiers().contains(Modifier.ABSTRACT);
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int line = 0;
        while (line + 1 < lineStarts.length && lineStarts[line + 1] <= offset) {
            line++;
        }
        return line;
    }
}
