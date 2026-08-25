package ma.dev.workflow.classification.models.enums;

/**
 * How big the model thinks the work is. From the class diagram.
 *
 * <p>Three values and no numbers on purpose. A model asked for story points invents a precision it
 * does not have; asked whether something is small, medium or large, it is answering a question it
 * can actually reason about.
 *
 * <p>Nothing on {@code Issue} receives this. It is a suggestion the team reads, and at M4 it is
 * what the dashboard groups by.
 */
public enum Effort {
    SMALL,
    MEDIUM,
    LARGE
}
