package com.gluonhq.netbeans.nbfx.editor.codearea;

import org.openide.filesystems.FileObject;

import java.util.Objects;

/**
 * Factory of the language-specific {@link BaseSyntaxDecorator}s, selected by the
 * file extension: CSS files get the lightweight CSS highlighter, XML/FXML files
 * the XML lexer highlighter, everything else the combined lex + Java analysis
 * decorator.
 */
public final class SyntaxDecorators {

    private SyntaxDecorators() {
    }

    /**
     * Creates the syntax decorator for the given file.
     *
     * @param fo the file being edited, never {@code null}
     * @return a new decorator matching the file's language
     */
    public static BaseSyntaxDecorator forFile(FileObject fo) {
        return switch (Objects.requireNonNull(fo).getExt().toLowerCase()) {
            case "css" -> new CssSyntaxDecorator(fo);
            case "xml", "fxml" -> new XmlSyntaxDecorator(fo);
            default -> new JavaSyntaxDecorator(fo);
        };
    }
}
