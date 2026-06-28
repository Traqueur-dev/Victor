package fr.traqueur.victor.annotations;

/**
 * Controls when a relationship is loaded.
 *
 * <p>Victor entities are immutable records, so there is no lazy proxy that could
 * populate a relationship on first access. Consequently:</p>
 *
 * <ul>
 *   <li>{@link #EAGER} — the relationship is loaded while mapping the row.</li>
 *   <li>{@link #LAZY} — the relationship is <em>not</em> loaded and the field is
 *       left at its default ({@code null} / empty); fetch it explicitly through
 *       the repository when needed. (This is "skip loading", not the JPA
 *       "load on access" semantics, which immutable records cannot provide.)</li>
 * </ul>
 */
public enum FetchType {
    EAGER,
    LAZY
}
