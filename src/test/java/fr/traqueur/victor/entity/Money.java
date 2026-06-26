package fr.traqueur.victor.entity;

/** Embeddable value record used twice (with prefixes) to test column disambiguation. */
public record Money(long amount, String currency) {
}
