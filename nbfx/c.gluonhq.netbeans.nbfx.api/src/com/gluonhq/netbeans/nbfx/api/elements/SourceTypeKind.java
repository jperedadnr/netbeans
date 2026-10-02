package com.gluonhq.netbeans.nbfx.api.elements;

import java.util.Objects;

/**
 * Subcategories for {@link SourceElementKind#TYPE}, shared by the completion and
 * breadcrumbs APIs.
 *
 * <p>Renderers use this value to choose more specific icons and labels than the generic {@code TYPE} kind.
 */
public enum SourceTypeKind {
    /** Ordinary class. */
    CLASS,
    /** Interface type. */
    INTERFACE,
    /** Enum type. */
    ENUM,
    /** Record type. */
    RECORD,
    /** Annotation type. */
    ANNOTATION,
    /** Constructor. */
    CONSTRUCTOR,
    /** Type with no specific subtype information. */
    OTHER;

    /**
     * Converts a language-model element kind by enum name.
     *
     * @param kindName element kind enum name
     * @return mapped source type kind
     */
    public static SourceTypeKind from(String kindName) {
        if (kindName == null || kindName.isBlank()) {
            return OTHER;
        }
        return switch (kindName) {
            case "CLASS" -> CLASS;
            case "INTERFACE" -> INTERFACE;
            case "ENUM" -> ENUM;
            case "RECORD" -> RECORD;
            case "ANNOTATION_TYPE" -> ANNOTATION;
            case "CONSTRUCTOR" -> CONSTRUCTOR;
            default -> OTHER;
        };
    }

    /**
     * Converts a typed code element kind.
     *
     * @param source element kind enum name
     * @return mapped source type kind
     */
    public static SourceTypeKind fromSource(String source) {
        if (source == null || source.isBlank()) {
            return OTHER;
        }
        return switch (source) {
            case "class" -> CLASS;
            case "interface" -> INTERFACE;
            case "enum" -> ENUM;
            case "record" -> RECORD;
            case "@interface" -> ANNOTATION;
            default -> OTHER;
        };
    }

    /**
     * Converts a runtime class into a source type kind.
     *
     * @param clazz runtime class
     * @return mapped source type kind
     */
    public static SourceTypeKind from(Class<?> clazz) {
        if (Objects.requireNonNull(clazz).isAnnotation()) {
            return ANNOTATION;
        }
        if (clazz.isInterface()) {
            return INTERFACE;
        }
        if (clazz.isEnum()) {
            return ENUM;
        }
        if (clazz.isRecord()) {
            return RECORD;
        }
        return CLASS;
    }
}
