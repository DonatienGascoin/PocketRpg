package com.pocket.rpg.logging;

public class Logger {
    private final String name;

    Logger(String name) {
        this.name = name;
    }

    public void trace(String message) {
        Log.log(LogLevel.TRACE, name, message, null);
    }

    public void trace(String format, Object... args) {
        if (Log.isLevelEnabled(LogLevel.TRACE)) {
            Log.log(LogLevel.TRACE, name, String.format(format, args), null);
        }
    }

    public void debug(String message) {
        Log.log(LogLevel.DEBUG, name, message, null);
    }

    public void debug(String format, Object... args) {
        if (Log.isLevelEnabled(LogLevel.DEBUG)) {
            Log.log(LogLevel.DEBUG, name, String.format(format, args), null);
        }
    }

    public void info(String message) {
        Log.log(LogLevel.INFO, name, message, null);
    }

    public void info(String format, Object... args) {
        if (Log.isLevelEnabled(LogLevel.INFO)) {
            Log.log(LogLevel.INFO, name, String.format(format, args), null);
        }
    }

    public void warn(String message) {
        Log.log(LogLevel.WARN, name, message, null);
    }

    public void warn(String format, Object... args) {
        if (Log.isLevelEnabled(LogLevel.WARN)) {
            Log.log(LogLevel.WARN, name, String.format(format, args), null);
        }
    }

    public void warn(String message, Throwable t) {
        Log.log(LogLevel.WARN, name, message, t);
    }

    public void error(String message) {
        Log.log(LogLevel.ERROR, name, message, null);
    }

    public void error(String format, Object... args) {
        if (Log.isLevelEnabled(LogLevel.ERROR)) {
            Log.log(LogLevel.ERROR, name, String.format(format, args), null);
        }
    }

    public void error(String message, Throwable t) {
        Log.log(LogLevel.ERROR, name, message, t);
    }

    public String getName() {
        return name;
    }
}
