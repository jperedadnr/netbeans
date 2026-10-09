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

import com.sun.source.util.TreePath;
import java.io.IOException;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.SourceUtils;
import org.openide.filesystems.FileObject;

/**
 * Finds the type to inspect, after {@code Resolvers} of NetBeans' {@code java.navigation} module:
 * the main type of a file, or the type at the caret - the type itself, the declared type of the
 * variable or the return type of the method there, the class of a constructor, and the file's main
 * type when the caret is on nothing typed. The type is then located in the source file that
 * declares it, which may be another file of the project, or of a library with sources. Runs on
 * the calling thread; call it off the FX thread.
 */
public final class TypeResolver {

    private TypeResolver() {
    }

    /**
     * The main type of {@code file} - the top-level type named like it, else the first - with
     * {@code text} as its content; {@code null} when the file declares no type.
     */
    public static InspectedType mainType(FileObject file, String text) throws IOException {
        JavaSources sources = JavaSources.of(file, text);
        if (sources == null) {
            return null;
        }
        ElementHandle<TypeElement>[] handle = handleHolder();
        sources.run(controller -> handle[0] = mainType(controller, file.getName()));
        return handle[0] == null ? null : new InspectedType(file, handle[0]);
    }

    /**
     * The type at {@code offset} of {@code file}, with {@code text} as its content, in the file
     * declaring it; {@code null} when nothing typed is there or the declaring source is not
     * available (a library without sources).
     */
    public static InspectedType atCaret(FileObject file, String text, int offset) throws IOException {
        JavaSources sources = JavaSources.of(file, text);
        if (sources == null) {
            return null;
        }
        ElementHandle<TypeElement>[] handle = handleHolder();
        sources.run(JavaSource.Phase.RESOLVED, controller -> {
            TypeElement type = typeAt(controller, offset);
            if (type == null && offset < text.length()) {
                // On the first character of a name, pathFor answers the enclosing tree: look one
                // character in, as NetBeans' caret task does.
                type = typeAt(controller, offset + 1);
            }
            handle[0] = type != null ? ElementHandle.create(type) : mainType(controller, file.getName());
        });
        if (handle[0] == null) {
            return null;
        }
        FileObject declaring = SourceUtils.getFile(handle[0], sources.classpath());
        return declaring == null ? null : new InspectedType(declaring, handle[0]);
    }

    private static TypeElement typeAt(CompilationController controller, int offset) {
        TreePath path = controller.getTreeUtilities().pathFor(offset);
        Element element = path == null ? null : controller.getTrees().getElement(path);
        return typeOf(element);
    }

    /** The type {@code element} stands for, or {@code null}: itself, its declared type, its return type, its class. */
    private static TypeElement typeOf(Element element) {
        if (element instanceof TypeElement type) {
            return type;
        }
        if (element instanceof VariableElement variable) {
            return declared(variable.asType());
        }
        if (element instanceof ExecutableElement executable) {
            if (executable.getKind() == ElementKind.METHOD) {
                return declared(executable.getReturnType());
            }
            if (executable.getKind() == ElementKind.CONSTRUCTOR && executable.getEnclosingElement() instanceof TypeElement owner) {
                return owner;
            }
        }
        return null;
    }

    private static TypeElement declared(TypeMirror type) {
        return type != null && type.getKind() == TypeKind.DECLARED && ((DeclaredType) type).asElement() instanceof TypeElement element
                ? element : null;
    }

    private static ElementHandle<TypeElement> mainType(CompilationController controller, String fileName) {
        List<? extends TypeElement> topLevels = controller.getTopLevelElements();
        if (topLevels.isEmpty()) {
            return null;
        }
        TypeElement candidate = topLevels.get(0);
        for (TypeElement type : topLevels) {
            if (type.getSimpleName().contentEquals(fileName)) {
                candidate = type;
                break;
            }
        }
        return ElementHandle.create(candidate);
    }

    @SuppressWarnings("unchecked")
    private static ElementHandle<TypeElement>[] handleHolder() {
        return new ElementHandle[1];
    }
}
