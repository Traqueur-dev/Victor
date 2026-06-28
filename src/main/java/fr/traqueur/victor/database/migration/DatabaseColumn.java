package fr.traqueur.victor.database.migration;

/**
 * Represents a column as it exists in the physical database.
 * Used for comparing actual schema against expected EntityMetadata.
 *
 * @param columnName    the column name (lowercase for consistent comparison)
 * @param dataType      the SQL data type as reported by the database
 * @param isNullable    whether the column allows NULL
 * @param columnDefault the default value expression, or null if none
 */
public record DatabaseColumn(
        String columnName,
        String dataType,
        boolean isNullable,
        String columnDefault
) {}
