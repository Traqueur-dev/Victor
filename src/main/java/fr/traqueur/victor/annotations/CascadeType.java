package fr.traqueur.victor.annotations;

/**
 * Cascade operations propagated from an entity to its related entities.
 *
 * <p>Victor has no persistence context (no first-level cache / unit of work that
 * tracks managed instances), so the JPA {@code MERGE}/{@code DETACH} cascades have
 * no meaning here. Cascade delete ({@code REMOVE}) is not implemented either, so
 * only the persist cascade is offered:</p>
 *
 * <ul>
 *   <li>{@link #PERSIST} — on {@code save}, new related entities on an owning
 *       relationship (ManyToOne / OneToOne owning) or in a OneToMany collection
 *       are inserted automatically.</li>
 *   <li>{@link #ALL} — shorthand for every supported cascade above (currently
 *       just {@code PERSIST}).</li>
 * </ul>
 *
 * <p>Without {@code PERSIST}, related entities must be persisted beforehand.</p>
 */
public enum CascadeType {
    ALL,
    PERSIST
}
