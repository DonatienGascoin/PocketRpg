package com.pocket.rpg.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Log {

    private static final Map<String, Logger> loggers = new ConcurrentHashMap<>();
    private static final LogManager manager = LogManager.getInstance();

    private Log() {
    }

    // Logger factory
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }

    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, Logger::new);
    }

    // Static convenience methods
    public static void trace(String logger, String message) {
        log(LogLevel.TRACE, logger, message, null);
    }

    public static void debug(String logger, String message) {
        log(LogLevel.DEBUG, logger, message, null);
    }

    public static void info(String logger, String message) {
        log(LogLevel.INFO, logger, message, null);
    }

    public static void warn(String logger, String message) {
        log(LogLevel.WARN, logger, message, null);
    }

    public static void error(String logger, String message) {
        log(LogLevel.ERROR, logger, message, null);
    }

    public static void error(String logger, String message, Throwable t) {
        log(LogLevel.ERROR, logger, message, t);
    }

    // Core logging
    static void log(LogLevel level, String loggerName, String message, Throwable throwable) {
        manager.log(level, loggerName, message, throwable);
    }

    static boolean isLevelEnabled(LogLevel level) {
        return manager.isLevelEnabled(level);
    }

    // Configuration
    public static void setLevel(LogLevel level) {
        manager.setMinLevel(level);
    }

    public static void setLoggerLevel(String loggerName, LogLevel level) {
        manager.setLoggerLevel(loggerName, level);
    }

    public static LogManager getManager() {
        return manager;
    }
}
