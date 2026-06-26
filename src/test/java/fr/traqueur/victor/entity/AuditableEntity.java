package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;

/**
 * Base class carrying an inherited persistent column, used to test that
 * class-based entities map fields declared on a superclass.
 */
public abstract class AuditableEntity {

    @Column(name = "created_by", length = 100)
    protected String createdBy;

    protected AuditableEntity() {}

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
