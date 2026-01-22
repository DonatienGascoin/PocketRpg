# Logging System Implementation Plan

## Overview

Replace all `System.out.println` calls (~300+) with a proper embedded logging system featuring:
- Structured logging API with levels and named loggers
- Thread-safe circular buffer for log storage
- Unity-style Console panel in the editor
- Configurable output handlers (console, optional file)

No external dependencies (no SLF4J/Log4j) - keeping it simple and embedded.

---

## Architecture

```
Log (static facade)
  └── LogManager (singleton coordinator)
        ├── LogBuffer (circular buffer with collapse)
        ├── ConsoleLogHandler (stdout/stderr)
        └── FileLogHandler (optional, future)

ConsolePanel (editor UI)
  └── reads from LogBuffer
```

---

## New Files

### Package: `src/main/java/com/pocket/rpg/logging/`

| File | Purpose |
|------|---------|
| `LogLevel.java` | Enum: TRACE, DEBUG, INFO, WARN, ERROR with priority |
| `LogEntry.java` | Immutable log record with timestamp, level, logger name, message, throwable |
| `Logger.java` | Named logger instance with level convenience methods |
| `Log.java` | Static facade - main API entry point |
| `LogHandler.java` | Interface for output destinations |
| `ConsoleLogHandler.java` | Outputs to System.out/err |
| `LogBuffer.java` | Thread-safe circular buffer with collapse support |
| `LogManager.java` | Singleton coordinator |

### Editor Panel

| File | Purpose |
|------|---------|
| `editor/panels/ConsolePanel.java` | Unity-style ImGui console panel |

---

## Detailed Implementation

### 1. LogLevel.java

```java
package com.pocket.rpg.logging;

public enum LogLevel {
    TRACE(0, "TRACE"),
    DEBUG(1, "DEBUG"),
    INFO(2, "INFO"),
    WARN(3, "WARN"),
    ERROR(4, "ERROR");

    private final int priority;
    private final String label;

    LogLevel(int priority, String label) {
        this.priority = priority;
        this.label = label;
    }

    public int getPriority() { return priority; }
    public String getLabel() { return label; }

    public boolean isAtLeast(LogLevel other) {
        return this.priority >= other.priority;
    }
}
```

### 2. LogEntry.java

```java
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
```

### 3. Logger.java

```java
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
```

### 4. Log.java (Static Facade)

```java
package com.pocket.rpg.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Log {

    private static final Map<String, Logger> loggers = new ConcurrentHashMap<>();
    private static final LogManager manager = LogManager.getInstance();

    private Log() {}

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
```

### 5. LogHandler.java

```java
package com.pocket.rpg.logging;

public interface LogHandler {
    void handle(LogEntry entry);
    void flush();
    void close();
}
```

### 6. ConsoleLogHandler.java

```java
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
```

### 7. LogBuffer.java

```java
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
        try { return size; }
        finally { lock.readLock().unlock(); }
    }

    public int getErrorCount() {
        lock.readLock().lock();
        try { return totalErrorCount; }
        finally { lock.readLock().unlock(); }
    }

    public int getWarnCount() {
        lock.readLock().lock();
        try { return totalWarnCount; }
        finally { lock.readLock().unlock(); }
    }

    public void setCollapseEnabled(boolean enabled) {
        lock.writeLock().lock();
        try { this.collapseEnabled = enabled; }
        finally { lock.writeLock().unlock(); }
    }

    public boolean isCollapseEnabled() {
        lock.readLock().lock();
        try { return collapseEnabled; }
        finally { lock.readLock().unlock(); }
    }

    public void addListener(Consumer<LogEntry> listener) {
        lock.writeLock().lock();
        try { listeners.add(listener); }
        finally { lock.writeLock().unlock(); }
    }

    private void notifyListeners(LogEntry entry) {
        for (Consumer<LogEntry> listener : listeners) {
            listener.accept(entry);
        }
    }
}
```

### 8. LogManager.java

```java
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

    @Getter @Setter
    private boolean paused = false;

    private LogManager() {
        this.buffer = new LogBuffer(1000);
        handlers.add(new ConsoleLogHandler());
    }

    public static LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public static void resetInstance() {
        if (instance != null) instance.shutdown();
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
        if (level == null) loggerLevels.remove(loggerName);
        else loggerLevels.put(loggerName, level);
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
            try { handler.close(); }
            catch (Exception ignored) {}
        }
        handlers.clear();
    }
}
```

---

## Console Panel Implementation

### ConsolePanel.java

Located at: `src/main/java/com/pocket/rpg/editor/panels/ConsolePanel.java`

**Features:**
- **Toolbar:**
  - Clear button
  - Level toggle buttons (T/D/I/W/E) with count badges
  - Collapse checkbox
  - Auto-scroll checkbox
  - Timestamps checkbox
  - Search filter input

- **Log list:**
  - Scrollable via `ImGui.beginChild()`
  - Color-coded by level
  - Selectable entries
  - Context menu (copy message, copy full entry)
  - Repeat count badge for collapsed messages

- **Detail pane:**
  - Shows when entry selected
  - Logger name, thread, timestamp
  - Full message (wrapped)
  - Stack trace if throwable present

**Colors:**
| Level | Color RGBA |
|-------|------------|
| TRACE | (0.5, 0.5, 0.5, 1.0) Gray |
| DEBUG | (0.4, 0.8, 1.0, 1.0) Cyan |
| INFO | (0.9, 0.9, 0.9, 1.0) White |
| WARN | (1.0, 0.85, 0.2, 1.0) Yellow |
| ERROR | (1.0, 0.4, 0.4, 1.0) Red |

**Icons (from MaterialIcons):**
- Terminal - panel icon
- Error - error level
- Warning - warn level
- Info - info level
- BugReport - debug level
- Code - trace level
- Delete - clear button
- Search - filter hint
- ContentCopy - copy menu items

---

## Integration

### EditorUIController.java

Add to fields:
```java
@Getter
private ConsolePanel consolePanel;
```

Add to `createPanels()`:
```java
consolePanel = new ConsolePanel();
```

Add to `renderPanels()`:
```java
consolePanel.render();
```

---

## Usage Examples

### Getting a Logger
```java
public class MyClass {
    private static final Logger log = Log.getLogger(MyClass.class);

    public void doSomething() {
        log.info("Starting operation");
        log.debug("Processing %d items", items.size());

        try {
            // ...
        } catch (Exception e) {
            log.error("Operation failed", e);
        }
    }
}
```

### Quick Static Logging
```java
Log.info("Application", "Starting up...");
Log.warn("Config", "Using default settings");
```

### Configuration
```java
// Set global minimum level
Log.setLevel(LogLevel.INFO);

// Set level for specific logger
Log.setLoggerLevel("Renderer", LogLevel.WARN);
```

---

## Migration Strategy

### Phase 1: Infrastructure
1. Create all logging package classes
2. Add ConsolePanel to editor
3. Test basic logging works

### Phase 2: High-Priority Migrations
Replace first:
- All `System.err.println` (errors)
- Application startup/shutdown messages
- Scene loading errors
- Rendering warnings with "[UIRenderer]" prefix

### Phase 3: Bulk Migration
By package:
- `core/` (~28 calls)
- `editor/` (~80 calls)
- `rendering/` (~50 calls)
- `scenes/` (~40 calls)
- `serialization/` (~20 calls)
- Other packages

### Migration Pattern

**Before:**
```java
System.out.println("Initializing...");
System.out.println("[Renderer] Loaded " + count + " textures");
System.err.println("Failed: " + e.getMessage());
```

**After:**
```java
private static final Logger log = Log.getLogger(MyClass.class);

log.info("Initializing...");
log.info("Loaded %d textures", count);
log.error("Failed: %s", e.getMessage());
// Or with exception:
log.error("Failed", e);
```

---

## Verification

1. Run editor: `mvn exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"`
2. Console panel appears in dock
3. Add test logs, verify they appear
4. Toggle level filters
5. Test collapse (log same message repeatedly)
6. Test clear button
7. Test search filter
8. Test copy from context menu
9. Select entry, verify detail pane shows

---

## Future Enhancements (Not in Scope)

- File logging handler with rotation
- JSON config file (logging.json)
- ANSI color support in terminal
- Remote logging
- Performance profiler integration
- Double-click to jump to source (if stack trace available)
