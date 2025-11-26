package fr.traqueur.victor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central logging facility for the Victor ORM framework.
 * All logging throughout the library goes through this single logger.
 */
public final class VictorLogger {

    private static final Logger logger = LoggerFactory.getLogger("Victor");

    private VictorLogger() {
        // Utility class
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void debug(String format, Object... args) {
        logger.debug(format, args);
    }

    public static void info(String message) {
        logger.info(message);
    }

    public static void info(String format, Object... args) {
        logger.info(format, args);
    }

    public static void warn(String message) {
        logger.warn(message);
    }

    public static void warn(String message, Throwable t) {
        logger.warn(message, t);
    }

    public static void warn(String format, Object... args) {
        logger.warn(format, args);
    }

    public static void error(String message) {
        logger.error(message);
    }

    public static void error(String message, Throwable t) {
        logger.error(message, t);
    }

    public static void error(String format, Object... args) {
        logger.error(format, args);
    }
}