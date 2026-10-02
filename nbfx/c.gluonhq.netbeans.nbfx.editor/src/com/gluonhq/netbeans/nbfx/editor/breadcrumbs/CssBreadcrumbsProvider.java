package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsProvider;
import org.openide.util.lookup.ServiceProvider;

import java.util.List;

/**
 * Breadcrumbs provider for CSS files.
 *
 * <p>Delegates the character scan and tree construction to {@link CssBreadcrumbsScanner};
 * caching and caret descent come from {@link CachingBreadcrumbsProvider}.</p>
 */
@ServiceProvider(service = BreadcrumbsProvider.class)
public final class CssBreadcrumbsProvider extends CachingBreadcrumbsProvider {

    public CssBreadcrumbsProvider() {
        super("css");
    }

    @Override
    List<BreadcrumbElement> scan(BreadcrumbsContext context, Cancellation cancellation) {
        return CssBreadcrumbsScanner.scan(context, cancellation);
    }
}
