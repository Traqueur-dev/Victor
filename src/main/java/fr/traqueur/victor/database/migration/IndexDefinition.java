package fr.traqueur.victor.database.migration;

/**
 * Represents an index to be created, derived from @VictorIndex annotations.
 *
 * @param name    the index name
 * @param columns the column names to index
 * @param unique  whether this is a unique index
 * @param where   optional partial index condition (empty string if none)
 */
public record IndexDefinition(
        String name,
        String[] columns,
        boolean unique,
        String where
) {}
