package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.editor.processor.lex.BaseLexDecorationProcessor;
import com.gluonhq.netbeans.nbfx.editor.processor.lex.CssLexDecorationProcessor;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.SourceContext;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.openide.filesystems.FileObject;

/**
 * A {@link BaseSyntaxDecorator} for CSS sources. All the work — syntax highlighting,
 * brace matching and mark-occurrences of class/selector names — is done by the
 * lex-based defaults of the base class over a {@link CssLexDecorationProcessor}.
 */
public class CssSyntaxDecorator extends BaseSyntaxDecorator {

    public CssSyntaxDecorator(FileObject fo) {
        super(fo);
    }

    @Override
    protected BaseLexDecorationProcessor getLexProcessor(FileObject fo) {
        return new CssLexDecorationProcessor(new SourceContext(fo, JavaTokenId.language()));
    }
}
