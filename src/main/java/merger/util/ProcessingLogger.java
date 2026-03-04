package merger.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that provides simple logging to console.
 * Output is automatically captured by the UI's System.out redirection.
 */
public class ProcessingLogger {

    private static final Logger logger = LoggerFactory.getLogger("merger.processing");

    public static void info(String message) {
        logger.info(message);
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void warn(String message) {
        logger.warn(message);
    }

    public static void error(String message, Throwable exception) {
        logger.error(message, exception);
    }

    public static void error(String message) {
        logger.error(message);
    }
}

