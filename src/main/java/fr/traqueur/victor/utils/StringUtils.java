package fr.traqueur.victor.utils;

public final class StringUtils {

    private StringUtils() {
        // Utility class
    }

    public static String camelToSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}