package com.pocket.rpg.logging;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
public class LogEntry {
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String loggerName;
    private final String message;
    private final String threadName;
    private final Throwable throwable;
    private int repeatCount = 1;

    public LogEntry(LogLevel level, String loggerName, String message, Throwable throwable) {
        this.timestamp = LocalDateTime.now();
        this.level = level;
        this.loggerName = loggerName;
        this.message = message;
        this.threadName = Thread.currentThread().getName();
        this.throwable = throwable;
    }

    public String getFormattedTime() {
        return timestamp.format(TIME_FORMAT);
    }

    public String getFormattedMessage() {
        return String.format("[%s] [%s] [%s] %s",
            getFormattedTime(), level.getLabel(), loggerName, message);
    }

    public void incrementRepeatCount() {
        repeatCount++;
    }

    public boolean canCollapseWith(LogEntry other) {
        return other != null &&
               this.level == other.level &&
               this.loggerName.equals(other.loggerName) &&
               this.message.equals(other.message);
    }
}
