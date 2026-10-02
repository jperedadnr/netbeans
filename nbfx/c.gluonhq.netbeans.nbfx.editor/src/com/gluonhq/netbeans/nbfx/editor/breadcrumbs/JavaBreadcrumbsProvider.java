package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsProvider;
import org.openide.util.lookup.ServiceProvider;

import java.util.List;

/**
 * Breadcrumbs provider for Java source files.
 *
 * <p>Delegates the parse and tree construction to {@link JavaBreadcrumbsScanner};
 * caching and caret descent come from {@link CachingBreadcrumbsProvider}.</p>
 */
@ServiceProvider(service = BreadcrumbsProvider.class)
public final class JavaBreadcrumbsProvider extends CachingBreadcrumbsProvider {

    public JavaBreadcrumbsProvider() {
        super("java");
    }

    @Override
    List<BreadcrumbElement> scan(BreadcrumbsContext context, Cancellation cancellation) {
        return JavaBreadcrumbsScanner.scan(context, cancellation);
    }
}
