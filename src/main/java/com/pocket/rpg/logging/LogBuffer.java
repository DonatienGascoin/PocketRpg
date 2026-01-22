package com.pocket.rpg.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class LogBuffer {

    private final LogEntry[] buffer;
    private final int capacity;
    private int head = 0;
    private int size = 0;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private boolean collapseEnabled = true;
    private LogEntry lastEntry = null;

    private int totalErrorCount = 0;
    private int totalWarnCount = 0;

    private final List<Consumer<LogEntry>> listeners = new ArrayList<>();

    public LogBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new LogEntry[capacity];
    }

    public void add(LogEntry entry) {
        lock.writeLock().lock();
        try {
            if (entry.getLevel() == LogLevel.ERROR) totalErrorCount++;
            if (entry.getLevel() == LogLevel.WARN) totalWarnCount++;

            if (collapseEnabled && lastEntry != null && lastEntry.canCollapseWith(entry)) {
                lastEntry.incrementRepeatCount();
                notifyListeners(lastEntry);
                return;
            }

            buffer[head] = entry;
            head = (head + 1) % capacity;
            if (size < capacity) size++;

            lastEntry = entry;
            notifyListeners(entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<LogEntry> getEntries() {
        lock.readLock().lock();
        try {
            List<LogEntry> result = new ArrayList<>(size);
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                result.add(buffer[(start + i) % capacity]);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<LogEntry> getFilteredEntries(LogLevel minLevel, String textFilter) {
        lock.readLock().lock();
        try {
            List<LogEntry> result = new ArrayList<>();
            int start = (head - size + capacity) % capacity;
            String lowerFilter = textFilter != null ? textFilter.toLowerCase() : null;

            for (int i = 0; i < size; i++) {
                LogEntry entry = buffer[(start + i) % capacity];

                if (!entry.getLevel().isAtLeast(minLevel)) continue;

                if (lowerFilter != null && !lowerFilter.isEmpty()) {
                    if (!entry.getMessage().toLowerCase().contains(lowerFilter) &&
                        !entry.getLoggerName().toLowerCase().contains(lowerFilter)) {
                        continue;
                    }
                }

                result.add(entry);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < capacity; i++) buffer[i] = null;
            head = 0;
            size = 0;
            lastEntry = null;
            totalErrorCount = 0;
            totalWarnCount = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getSize() {
        lock.readLock().lock();
        try {
            return size;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getErrorCount() {
        lock.readLock().lock();
        try {
            return totalErrorCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWarnCount() {
        lock.readLock().lock();
        try {
            return totalWarnCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setCollapseEnabled(boolean enabled) {
        lock.writeLock().lock();
        try {
            this.collapseEnabled = enabled;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isCollapseEnabled() {
        lock.readLock().lock();
        try {
            return collapseEnabled;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addListener(Consumer<LogEntry> listener) {
        lock.writeLock().lock();
        try {
            listeners.add(listener);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeListener(Consumer<LogEntry> listener) {
        lock.writeLock().lock();
        try {
            listeners.remove(listener);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void notifyListeners(LogEntry entry) {
        for (Consumer<LogEntry> listener : listeners) {
            listener.accept(entry);
        }
    }
}
