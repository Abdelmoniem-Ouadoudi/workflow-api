package ma.dev.workflow.project.models.enums;

/**
 * What somebody is <em>inside one project</em>. Not the same axis as {@code Role}, which says what
 * they are on the platform.
 *
 * <p>The two are deliberately separate. A global role cannot express "manager of this project,
 * ordinary member of that one", and that is the normal case: the person who started a project runs
 * it, and is just a pair of hands on somebody else's.
 *
 * <p>This value is never a token claim. It changes the moment a manager adds or removes somebody,
 * and a token lives an hour with no way to recall it. Authorization data that changes is read from
 * the database on the request that needs it.
 */
public enum ProjectRole {

    /** The chef de projet. Manages this project's people, its join code and its settings. */
    PROJECT_MANAGER,

    /** Works in the project: sees it, files issues, comments. Changes nothing about the project. */
    MEMBER
}
