package com.pocket.rpg.logging;

public interface LogHandler {
    void handle(LogEntry entry);
    void flush();
    void close();
}
