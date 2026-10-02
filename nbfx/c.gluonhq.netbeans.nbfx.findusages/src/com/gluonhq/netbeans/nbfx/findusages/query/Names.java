package com.gluonhq.netbeans.nbfx.findusages.query;

import java.util.List;
import java.util.StringJoiner;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** Display names of elements, as the Usages tree shows them: {@code greet(String)}, {@code Counter}, {@code count}. */
final class Names {

    private Names() {
    }

    /** The simple name of {@code element}; for a constructor, the name of its class. */
    static String simpleName(Element element) {
        if (element.getKind() == ElementKind.CONSTRUCTOR) {
            return element.getEnclosingElement().getSimpleName().toString();
        }
        return element.getSimpleName().toString();
    }

    /** {@link #simpleName} followed by the parameter types for methods and constructors. */
    static String displayName(Element element) {
        if (element instanceof ExecutableElement executable
                && (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR)) {
            StringJoiner params = new StringJoiner(", ", "(", ")");
            for (VariableElement parameter : executable.getParameters()) {
                params.add(simpleTypeName(parameter.asType()));
            }
            return simpleName(element) + params;
        }
        if (element.getKind() == ElementKind.STATIC_INIT) {
            return "<static init>";
        }
        if (element.getKind() == ElementKind.INSTANCE_INIT) {
            return "<init>";
        }
        return simpleName(element);
    }

    /** The qualified name of the type declaring {@code element}, or {@code null} for a top-level type or package. */
    static String enclosingTypeName(Element element) {
        Element enclosing = element.getEnclosingElement();
        while (enclosing != null && !(enclosing instanceof TypeElement)) {
            enclosing = enclosing.getEnclosingElement();
        }
        return enclosing == null ? null : ((TypeElement) enclosing).getQualifiedName().toString();
    }

    static String simpleTypeName(TypeMirror type) {
        return switch (type.getKind()) {
            case DECLARED -> {
                DeclaredType declared = (DeclaredType) type;
                String name = declared.asElement().getSimpleName().toString();
                List<? extends TypeMirror> args = declared.getTypeArguments();
                if (args.isEmpty()) {
                    yield name;
                }
                StringJoiner joiner = new StringJoiner(", ", "<", ">");
                args.forEach(arg -> joiner.add(simpleTypeName(arg)));
                yield name + joiner;
            }
            case ARRAY -> simpleTypeName(((ArrayType) type).getComponentType()) + "[]";
            default -> type.toString();
        };
    }
}
