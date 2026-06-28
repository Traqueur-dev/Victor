package fr.traqueur.victor.annotations;

/**
 * Cascade operations propagated from an entity to its related entities.
 *
 * <p>Victor has no persistence context (no first-level cache / unit of work that
 * tracks managed instances), so the JPA {@code MERGE} and {@code DETACH} cascades
 * have no meaning here and are intentionally absent.</p>
 *
 * <ul>
 *   <li>{@link #PERSIST} — honored: on {@code save}, new related entities are
 *       inserted (see owning and OneToMany cascade in the repository layer).</li>
 *   <li>{@link #REMOVE} — reserved for cascade delete; not yet honored on
 *       {@code delete}.</li>
 *   <li>{@link #ALL} — shorthand for every supported cascade above.</li>
 * </ul>
 */
public enum CascadeType {
    ALL,
    PERSIST,
    REMOVE
}
