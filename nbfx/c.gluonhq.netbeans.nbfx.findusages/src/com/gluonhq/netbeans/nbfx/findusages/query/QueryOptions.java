package com.gluonhq.netbeans.nbfx.findusages.query;

/**
 * How a query searches: whether the target's name is also looked for in comments (Java only) and
 * which files are searched - NetBeans' Find Usages dialog offers these two, among more; the query
 * tab offers them in a small menu instead.
 *
 * @param searchComments look for the simple name in comments too
 * @param scope          the files searched
 */
public record QueryOptions(boolean searchComments, Scope scope) {

    /** The files a query searches. */
    public enum Scope {
        /** Every source root of every open project. */
        ALL_PROJECTS,
        /** The source roots of the project owning the target's file. */
        CURRENT_PROJECT,
        /** The files in the target's package - the folder of its file. */
        CURRENT_PACKAGE,
        /** The target's file alone. */
        CURRENT_FILE,
        /** The files open in editors. */
        OPEN_FILES
    }

    public static final QueryOptions DEFAULT = new QueryOptions(true, Scope.ALL_PROJECTS);

    public QueryOptions withSearchComments(boolean value) {
        return new QueryOptions(value, scope);
    }

    public QueryOptions withScope(Scope value) {
        return new QueryOptions(searchComments, value);
    }
}
