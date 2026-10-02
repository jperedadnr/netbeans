package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.editor.processor.lex.BaseLexDecorationProcessor;
import com.gluonhq.netbeans.nbfx.editor.processor.lex.XmlLexDecorationProcessor;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.SourceContext;
import org.netbeans.api.xml.lexer.XMLTokenId;
import org.openide.filesystems.FileObject;

/**
 * A {@link BaseSyntaxDecorator} for XML/FXML sources. The syntax highlighting and the
 * caret-driven start/end tag matching (shown through the brace-match highlight) are done
 * by the lex-based defaults of the base class over an {@link XmlLexDecorationProcessor};
 * mark occurrences yield no highlights.
 */
public class XmlSyntaxDecorator extends BaseSyntaxDecorator {

    public XmlSyntaxDecorator(FileObject fo) {
        super(fo);
    }

    @Override
    protected BaseLexDecorationProcessor getLexProcessor(FileObject fo) {
        return new XmlLexDecorationProcessor(new SourceContext(fo, XMLTokenId.language()));
    }
}
