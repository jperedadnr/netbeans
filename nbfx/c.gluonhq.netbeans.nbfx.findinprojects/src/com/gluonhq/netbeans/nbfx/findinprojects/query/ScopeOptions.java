package com.gluonhq.netbeans.nbfx.findinprojects.query;

/**
 * The scope options of NetBeans' search form.
 *
 * @param searchInArchives  descend into jar / zip files
 * @param searchInGenerated also search the folders the project marks as not sharable - the
 *                          build output, where generated sources live (NetBeans drops its
 *                          sharability filter for this)
 * @param useIgnoreList     skip the folders and path patterns of the {@link IgnoreList}
 */
public record ScopeOptions(boolean searchInArchives, boolean searchInGenerated, boolean useIgnoreList) {

    /** NetBeans' defaults: no archives, no generated sources, ignore list on. */
    public static final ScopeOptions DEFAULT = new ScopeOptions(false, false, true);

    public ScopeOptions withSearchInArchives(boolean on) {
        return new ScopeOptions(on, searchInGenerated, useIgnoreList);
    }

    public ScopeOptions withSearchInGenerated(boolean on) {
        return new ScopeOptions(searchInArchives, on, useIgnoreList);
    }

    public ScopeOptions withUseIgnoreList(boolean on) {
        return new ScopeOptions(searchInArchives, searchInGenerated, on);
    }
}
