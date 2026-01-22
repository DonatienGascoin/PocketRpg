package com.pocket.rpg.logging;

import java.io.PrintStream;

public class ConsoleLogHandler implements LogHandler {

    @Override
    public void handle(LogEntry entry) {
        PrintStream stream = entry.getLevel().getPriority() >= LogLevel.WARN.getPriority()
            ? System.err : System.out;

        stream.println(entry.getFormattedMessage());

        if (entry.getThrowable() != null) {
            entry.getThrowable().printStackTrace(stream);
        }
    }

    @Override
    public void flush() {
        System.out.flush();
        System.err.flush();
    }

    @Override
    public void close() {
        flush();
    }
}
