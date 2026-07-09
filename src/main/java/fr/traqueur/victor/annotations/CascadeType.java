package fr.traqueur.victor.annotations;

/**
 * Cascade operations propagated from an entity to its related entities.
 *
 * <ul>
 *   <li>{@link #PERSIST} — on {@code save}, related entities are cascaded:
 *       <ul>
 *         <li>new owning relations (ManyToOne / OneToOne owning) are inserted first so the foreign
 *             key column can be written;</li>
 *         <li>inverse OneToMany children are <b>synchronized</b> — present children are upserted
 *             (inserted if new, updated if they already exist), recursively for nested collections,
 *             and children removed from the collection are deleted (orphan removal).</li>
 *       </ul>
 *       On {@code deleteById}/{@code delete}, cascaded OneToMany children are deleted first (bottom-up)
 *       so foreign-key constraints are satisfied — i.e. cascade delete.</li>
 *   <li>{@link #ALL} — shorthand for every supported cascade above (currently just {@code PERSIST}).</li>
 * </ul>
 *
 * <p>Without {@code PERSIST}, related entities are neither persisted nor deleted with the owner and
 * must be managed independently.</p>
 */
public enum CascadeType {
    ALL,
    PERSIST
}
