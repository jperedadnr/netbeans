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

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.elements.SourceModifiers;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode.Segment;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.TreeUtilities;
import org.netbeans.api.java.source.TypeUtilities;
import org.netbeans.api.java.source.TypeUtilities.TypeNameOptions;
import org.netbeans.api.java.source.support.ErrorAwareTreePathScanner;
import org.openide.filesystems.FileObject;

/**
 * Builds the Members tree of a Java source file: one {@link MemberNode} per top-level type, each
 * holding its members - the ones it declares, with their position in the document, and the ones it
 * inherits - and, nested, the members of its inner types. This is the Swing-free port of
 * {@code ElementScanningTask} in NetBeans' {@code java.navigation} module.
 * <p>
 * The file is compiled to {@link JavaSource.Phase#ELEMENTS_RESOLVED} from the editor's text rather
 * than from disk (an in-memory copy, see {@link MemorySources}), so the tree follows unsaved edits
 * and its offsets are the editor's. The scan runs on the calling thread; call it off the FX thread.
 */
public final class MembersScanner {

    private static final Logger LOG = Logger.getLogger(MembersScanner.class.getName());

    /**
     * The tree of a file.
     *
     * @param file      the scanned file
     * @param classpath the class path the file was compiled with, to resolve inherited members later
     * @param roots     the top-level types, in source order
     */
    public record Result(FileObject file, ClasspathInfo classpath, List<MemberNode> roots) {
    }

    private MembersScanner() {
    }

    /**
     * Scans {@code text}, the current content of {@code file}.
     *
     * @return the tree, or {@code null} when the scan was cancelled or the file could not be compiled
     * @throws IOException when the in-memory copy or the compilation fails
     */
    public static Result scan(FileObject file, String text, Cancellation cancellation) throws IOException {
        JavaSources sources = JavaSources.of(file, text);
        if (sources == null) {
            return null;
        }
        List<MemberNode> roots = new ArrayList<>();
        boolean[] completed = {false};
        sources.run(controller -> {
            if (cancellation.isCancelled()) {
                return;
            }
            Positions positions = new Positions(controller, cancellation);
            positions.scan(controller.getCompilationUnit(), null);
            Builder builder = new Builder(controller, positions, cancellation);
            for (TypeElement type : controller.getTopLevelElements()) {
                if (cancellation.isCancelled()) {
                    return;
                }
                MemberNode root = builder.node(type, null, false);
                if (root != null) {
                    roots.add(root);
                    builder.addMembers(type, root);
                }
            }
            completed[0] = !cancellation.isCancelled();
        });
        return completed[0] ? new Result(file, sources.classpath(), List.copyOf(roots)) : null;
    }

    /**
     * The offset of the declared name of the element {@code handle} describes in {@code file} - where
     * the caret goes when an inherited member is opened in the file that declares it - compiling
     * {@code text} when the file is open in an editor ({@code null} to compile the file on disk).
     *
     * @return the offset of the name, else of the declaration; {@code -1} when the element is not
     *         declared in the file
     * @throws IOException when the compilation fails
     */
    public static int declarationOffset(FileObject file, String text, ElementHandle<? extends Element> handle)
            throws IOException {
        return declarationOffset(file, text, handle, true);
    }

    /**
     * As {@link #declarationOffset(FileObject, String, ElementHandle)}, but the offset where the
     * declaration starts - its modifiers and type included, its documentation comment not - where
     * the Go to commands put the caret, as NetBeans' element opener does.
     */
    public static int declarationStart(FileObject file, String text, ElementHandle<? extends Element> handle)
            throws IOException {
        return declarationOffset(file, text, handle, false);
    }

    private static int declarationOffset(FileObject file, String text, ElementHandle<? extends Element> handle,
            boolean atName) throws IOException {
        JavaSource source = text == null
                ? JavaSource.forFileObject(file)
                : JavaSource.create(ClasspathInfo.create(file), MemorySources.copyOf(file, text));
        if (source == null) {
            return -1;
        }
        int[] offset = {-1};
        source.runUserActionTask(controller -> {
            controller.toPhase(JavaSource.Phase.ELEMENTS_RESOLVED);
            Element element = handle.resolve(controller);
            TreePath path = element == null ? null : controller.getTrees().getPath(element);
            if (path == null) {
                return;
            }
            int[] nameSpan = atName ? nameSpan(controller.getTreeUtilities(), path.getLeaf()) : null;
            offset[0] = nameSpan != null ? nameSpan[0]
                    : (int) controller.getTrees().getSourcePositions().getStartPosition(path.getCompilationUnit(), path.getLeaf());
        }, true);
        return offset[0];
    }

    private static int[] nameSpan(TreeUtilities treeUtilities, Tree tree) {
        if (tree instanceof ClassTree clazz) {
            return treeUtilities.findNameSpan(clazz);
        }
        if (tree instanceof MethodTree method) {
            return treeUtilities.findNameSpan(method);
        }
        if (tree instanceof VariableTree variable) {
            return treeUtilities.findNameSpan(variable);
        }
        return null;
    }

    /** Where a declared element lies in the document: its tree's span, and the span of its name. */
    private record Span(int start, int end, int nameOffset) {
    }

    /** Records the span of every class, method and field declaration; method bodies are not entered. */
    private static final class Positions extends ErrorAwareTreePathScanner<Void, Void> {

        private final Trees trees;
        private final SourcePositions sourcePositions;
        private final TreeUtilities treeUtilities;
        private final Cancellation cancellation;
        private final Map<Element, Span> spans = new HashMap<>();
        private CompilationUnitTree unit;

        Positions(CompilationController controller, Cancellation cancellation) {
            this.trees = controller.getTrees();
            this.sourcePositions = trees.getSourcePositions();
            this.treeUtilities = controller.getTreeUtilities();
            this.cancellation = cancellation;
        }

        Span of(Element element) {
            return spans.get(element);
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
            unit = node;
            return super.visitCompilationUnit(node, p);
        }

        @Override
        public Void visitClass(ClassTree node, Void p) {
            record(node, treeUtilities.findNameSpan(node));
            return super.visitClass(node, p);
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            record(node, treeUtilities.findNameSpan(node));
            return null;
        }

        @Override
        public Void visitVariable(VariableTree node, Void p) {
            record(node, treeUtilities.findNameSpan(node));
            return null;
        }

        @Override
        public Void scan(Tree tree, Void p) {
            return cancellation.isCancelled() ? null : super.scan(tree, p);
        }

        private void record(Tree tree, int[] nameSpan) {
            Element element = trees.getElement(getCurrentPath());
            if (element == null) {
                return;
            }
            int start = (int) sourcePositions.getStartPosition(unit, tree);
            int end = (int) sourcePositions.getEndPosition(unit, tree);
            spans.put(element, new Span(start, end, nameSpan == null ? -1 : nameSpan[0]));
        }
    }

    /** Turns elements into nodes, with their labels in both flavours. */
    private static final class Builder {

        private final CompilationController controller;
        private final Elements elements;
        private final TypeUtilities types;
        private final Positions positions;
        private final Cancellation cancellation;

        Builder(CompilationController controller, Positions positions, Cancellation cancellation) {
            this.controller = controller;
            this.elements = controller.getElements();
            this.types = controller.getTypeUtilities();
            this.positions = positions;
            this.cancellation = cancellation;
        }

        /** Adds every member of {@code type} - declared and inherited - under {@code node}, recursing into its inner types. */
        void addMembers(TypeElement type, MemberNode node) {
            for (Element member : elements.getAllMembers(type)) {
                if (cancellation.isCancelled()) {
                    return;
                }
                MemberNode child = node(member, type, node.isInherited());
                if (child == null) {
                    continue;
                }
                node.addChild(child);
                if (member instanceof TypeElement inner && !child.isInherited()) {
                    addMembers(inner, child);
                }
            }
        }

        /**
         * The node of {@code element} listed under {@code parent} ({@code null} for a top-level type),
         * or {@code null} for an element the tree does not show: synthetic ones, initializers,
         * anything that is not a type, a method, a constructor or a field.
         */
        MemberNode node(Element element, TypeElement parent, boolean parentInherited) {
            Elements.Origin origin = elements.getOrigin(element);
            if (origin == Elements.Origin.SYNTHETIC) {
                return null;
            }
            ElementKind kind = element.getKind();
            Element enclosing = element.getEnclosingElement();
            // Implicit members are listed (a default constructor, an enum's values()), except the
            // private constructor every enum gets, which says nothing about the enum.
            if (kind == ElementKind.CONSTRUCTOR && origin == Elements.Origin.MANDATED
                    && enclosing != null && enclosing.getKind() == ElementKind.ENUM) {
                return null;
            }
            boolean inherited = parentInherited || (parent != null && !parent.equals(enclosing));
            boolean topLevel = enclosing != null && enclosing.getKind() == ElementKind.PACKAGE;
            boolean deprecated = elements.isDeprecated(element);
            int modifiers = SourceModifiers.toModifierBits(element.getModifiers());
            Span span = inherited ? null : positions.of(element);
            String name;
            List<Segment> plain;
            List<Segment> qualified;
            if (element instanceof TypeElement type) {
                name = type.getSimpleName().toString();
                plain = typeLabel(type, false);
                qualified = typeLabel(type, true);
            } else if (element instanceof ExecutableElement executable) {
                if (kind != ElementKind.METHOD && kind != ElementKind.CONSTRUCTOR) {
                    return null;
                }
                name = kind == ElementKind.CONSTRUCTOR
                        ? executable.getEnclosingElement().getSimpleName().toString()
                        : executable.getSimpleName().toString();
                TypeElement overriddenFrom = inherited ? null : overriddenFrom(executable);
                plain = methodLabel(executable, name, overriddenFrom, false);
                qualified = methodLabel(executable, name, overriddenFrom, true);
            } else if (element instanceof VariableElement variable) {
                if (kind != ElementKind.FIELD && kind != ElementKind.ENUM_CONSTANT && kind != ElementKind.RECORD_COMPONENT) {
                    return null;
                }
                name = variable.getSimpleName().toString();
                plain = fieldLabel(variable, false);
                qualified = fieldLabel(variable, true);
            } else {
                return null;
            }
            return new MemberNode(name, kind, modifiers, inherited, topLevel, deprecated,
                    span == null ? -1 : span.start(), span == null ? -1 : span.end(),
                    span == null ? -1 : span.nameOffset(), plain, qualified, handleOf(element));
        }

        private TypeElement overriddenFrom(ExecutableElement method) {
            if (method.getKind() != ElementKind.METHOD) {
                return null;
            }
            try {
                ExecutableElement overridden = controller.getElementUtilities().getOverriddenMethod(method);
                return overridden == null ? null : (TypeElement) overridden.getEnclosingElement();
            } catch (RuntimeException ex) {
                LOG.log(Level.FINE, "Could not resolve the overridden method of " + method, ex);
                return null;
            }
        }

        private static ElementHandle<? extends Element> handleOf(Element element) {
            try {
                return ElementHandle.create(element);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        // -- labels ------------------------------------------------------------------------------

        /** {@code name(Type param, Type param) : ReturnType ↑ OverriddenFrom} */
        private List<Segment> methodLabel(ExecutableElement method, String name, TypeElement overriddenFrom, boolean fqn) {
            List<Segment> label = new ArrayList<>();
            label.add(plain(name));
            label.add(plain("("));
            for (Iterator<? extends VariableElement> it = method.getParameters().iterator(); it.hasNext();) {
                VariableElement parameter = it.next();
                boolean vararg = !it.hasNext() && method.isVarArgs();
                label.add(type(parameterType(parameter.asType(), vararg, fqn)));
                label.add(plain(" " + parameter.getSimpleName()));
                if (it.hasNext()) {
                    label.add(plain(", "));
                }
            }
            label.add(plain(")"));
            if (method.getKind() != ElementKind.CONSTRUCTOR && method.getReturnType().getKind() != TypeKind.VOID) {
                label.add(plain(" : "));
                label.add(type(typeName(method.getReturnType(), fqn)));
            }
            if (overriddenFrom != null) {
                label.add(plain(" ↑ "));
                label.add(type(typeName(overriddenFrom.asType(), fqn)));
            }
            return label;
        }

        /** {@code name : Type}; an enum constant is just its name. */
        private List<Segment> fieldLabel(VariableElement field, boolean fqn) {
            List<Segment> label = new ArrayList<>();
            label.add(plain(field.getSimpleName().toString()));
            if (field.getKind() != ElementKind.ENUM_CONSTANT) {
                label.add(plain(" : "));
                label.add(type(typeName(field.asType(), fqn)));
            }
            return label;
        }

        /** {@code Name<T extends Bound> :: Superclass : Interface, Interface} */
        private List<Segment> typeLabel(TypeElement type, boolean fqn) {
            List<Segment> label = new ArrayList<>();
            label.add(plain(fqn ? type.getQualifiedName().toString() : type.getSimpleName().toString()));
            List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
            if (!typeParameters.isEmpty()) {
                StringBuilder sb = new StringBuilder("<");
                for (Iterator<? extends TypeParameterElement> it = typeParameters.iterator(); it.hasNext();) {
                    TypeParameterElement parameter = it.next();
                    sb.append(parameter.getSimpleName());
                    sb.append(bounds(parameter.getBounds(), fqn));
                    if (it.hasNext()) {
                        sb.append(", ");
                    }
                }
                label.add(plain(sb.append('>').toString()));
            }
            ElementKind kind = type.getKind();
            if (kind == ElementKind.ANNOTATION_TYPE) {
                return label;
            }
            String superclass = null;
            TypeMirror superType = type.getSuperclass();
            if (superType != null && superType.getKind() == TypeKind.DECLARED
                    && kind != ElementKind.ENUM && kind != ElementKind.RECORD) {
                String name = typeName(superType, fqn);
                if (!"Object".equals(name) && !"java.lang.Object".equals(name)) {
                    superclass = name;
                }
            }
            List<? extends TypeMirror> interfaces = type.getInterfaces();
            if (superclass == null && interfaces.isEmpty()) {
                return label;
            }
            label.add(plain(" :: "));
            if (superclass != null) {
                label.add(type(superclass));
                if (!interfaces.isEmpty()) {
                    label.add(plain(" : "));
                }
            }
            for (Iterator<? extends TypeMirror> it = interfaces.iterator(); it.hasNext();) {
                label.add(type(typeName(it.next(), fqn)));
                if (it.hasNext()) {
                    label.add(plain(", "));
                }
            }
            return label;
        }

        /** {@code  extends Bound & Bound}, or nothing when the only bound is {@code Object}. */
        private String bounds(List<? extends TypeMirror> bounds, boolean fqn) {
            if (bounds.isEmpty() || (bounds.size() == 1 && "java.lang.Object".equals(bounds.get(0).toString()))) {
                return "";
            }
            StringBuilder sb = new StringBuilder(" extends ");
            for (Iterator<? extends TypeMirror> it = bounds.iterator(); it.hasNext();) {
                sb.append(typeName(it.next(), fqn));
                if (it.hasNext()) {
                    sb.append(" & ");
                }
            }
            return sb.toString();
        }

        private String parameterType(TypeMirror type, boolean vararg, boolean fqn) {
            if (vararg && type.getKind() == TypeKind.ARRAY) {
                return typeName(((ArrayType) type).getComponentType(), fqn) + "...";
            }
            return typeName(type, fqn);
        }

        private String typeName(TypeMirror type, boolean fqn) {
            return fqn ? types.getTypeName(type, TypeNameOptions.PRINT_FQN).toString() : types.getTypeName(type).toString();
        }

        private static Segment plain(String text) {
            return new Segment(text, false);
        }

        private static Segment type(String text) {
            return new Segment(text, true);
        }
    }
}
