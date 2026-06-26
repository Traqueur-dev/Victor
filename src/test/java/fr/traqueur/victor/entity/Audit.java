package fr.traqueur.victor.entity;

/** Embeddable value record: flattened into the owning table's columns. */
public record Audit(String createdBy, Integer revision) {
}
