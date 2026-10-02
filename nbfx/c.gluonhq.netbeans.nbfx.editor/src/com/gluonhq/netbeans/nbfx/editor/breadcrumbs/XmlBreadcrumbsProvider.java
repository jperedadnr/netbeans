package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsProvider;
import org.openide.util.lookup.ServiceProvider;

import java.util.List;

/**
 * Breadcrumbs provider for XML and FXML files.
 *
 * <p>Delegates the lexer walk and tree construction to {@link XmlBreadcrumbsScanner};
 * caching and caret descent come from {@link CachingBreadcrumbsProvider}.</p>
 */
@ServiceProvider(service = BreadcrumbsProvider.class)
public final class XmlBreadcrumbsProvider extends CachingBreadcrumbsProvider {

    public XmlBreadcrumbsProvider() {
        super("xml", "fxml");
    }

    @Override
    List<BreadcrumbElement> scan(BreadcrumbsContext context, Cancellation cancellation) {
        return XmlBreadcrumbsScanner.scan(context, cancellation);
    }
}
