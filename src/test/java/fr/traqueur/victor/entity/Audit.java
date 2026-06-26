package fr.traqueur.victor.entity;

import java.time.LocalDateTime;

/** Embeddable value record: flattened into the owning table's columns. */
public record Audit(LocalDateTime createdAt, String createdBy) {
}
