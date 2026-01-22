package com.pocket.rpg.logging;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogManager {

    private static LogManager instance;

    @Getter
    private LogLevel minLevel = LogLevel.DEBUG;

    private final Map<String, LogLevel> loggerLevels = new ConcurrentHashMap<>();
    private final List<LogHandler> handlers = new ArrayList<>();

    @Getter
    private final LogBuffer buffer;

    @Getter
    @Setter
    private boolean paused = false;

    private LogManager() {
        this.buffer = new LogBuffer(1000);
        handlers.add(new ConsoleLogHandler());
    }

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
        }
        instance = null;
    }

    public void log(LogLevel level, String loggerName, String message, Throwable throwable) {
        if (!isLevelEnabled(level)) return;

        LogLevel loggerLevel = loggerLevels.get(loggerName);
        if (loggerLevel != null && !level.isAtLeast(loggerLevel)) return;

        LogEntry entry = new LogEntry(level, loggerName, message, throwable);
        buffer.add(entry);

        if (!paused) {
            for (LogHandler handler : handlers) {
                try {
                    handler.handle(entry);
                } catch (Exception e) {
                    System.err.println("LogHandler error: " + e.getMessage());
                }
            }
        }
    }

    public boolean isLevelEnabled(LogLevel level) {
        return level.isAtLeast(minLevel);
    }

    public void setMinLevel(LogLevel level) {
        this.minLevel = level;
    }

    public void setLoggerLevel(String loggerName, LogLevel level) {
        if (level == null) {
            loggerLevels.remove(loggerName);
        } else {
            loggerLevels.put(loggerName, level);
        }
    }

    public void addHandler(LogHandler handler) {
        handlers.add(handler);
    }

    public void removeHandler(LogHandler handler) {
        handlers.remove(handler);
    }

    public void clearHandlers() {
        handlers.clear();
    }

    public void shutdown() {
        for (LogHandler handler : handlers) {
            try {
                handler.close();
            } catch (Exception ignored) {
            }
        }
        handlers.clear();
    }
}
