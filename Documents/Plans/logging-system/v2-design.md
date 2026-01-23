# Logging System V2 Design

## Overview

This document describes the V2 improvements to the logging system:
1. **System.out/err Migration** - Replace all print statements with proper logging
2. **File Logging** - Optional file handler with rotation support
3. **EditorConfig Integration** - Configure logging from editor settings

## Current State

- **V1 Implementation**: Core logging API (`Log`, `Logger`, `LogLevel`, `LogEntry`, `LogBuffer`, `LogManager`)
- **ConsoleLogHandler**: Outputs to System.out/err
- **ConsolePanel**: Unity-style log viewer in editor
- **Migration Needed**: 397 `System.out/err` calls across 92 files

---

## 1. File Logging

### 1.1 FileLogHandler

New handler that writes logs to a file with rotation support.

```java
package com.pocket.rpg.logging;

public class FileLogHandler implements LogHandler {

    private final Path logDirectory;
    private final String filePrefix;
    private final long maxFileSize;      // bytes, 0 = no limit
    private final int maxFiles;          // max rotated files to keep
    private final LogLevel minLevel;

    private BufferedWriter writer;
    private Path currentFile;
    private long currentFileSize;

    public FileLogHandler(FileLogConfig config) { ... }

    @Override
    public void handle(LogEntry entry) {
        if (!entry.getLevel().isAtLeast(minLevel)) return;

        checkRotation();
        writeEntry(entry);
    }

    private void checkRotation() {
        if (maxFileSize > 0 && currentFileSize >= maxFileSize) {
            rotate();
        }
    }

    private void rotate() {
        // Close current file
        // Rename: log.txt -> log.1.txt, log.1.txt -> log.2.txt, etc.
        // Delete oldest if > maxFiles
        // Open new log.txt
    }
}
```

### 1.2 Log File Format

```
[2024-01-23 14:32:15.123] [INFO ] [EditorApplication] Scene Editor initialized successfully
[2024-01-23 14:32:15.456] [DEBUG] [AssetManager] Loaded texture: sprites/player.png
[2024-01-23 14:32:16.789] [ERROR] [SceneLoader] Failed to load scene
    java.io.FileNotFoundException: scene.json not found
        at com.pocket.rpg.scenes.SceneLoader.load(SceneLoader.java:45)
        at com.pocket.rpg.editor.EditorSceneController.openScene(EditorSceneController.java:78)
```

### 1.3 File Rotation Strategy

| Setting | Default | Description |
|---------|---------|-------------|
| `maxFileSize` | 10 MB | Rotate when file exceeds this size |
| `maxFiles` | 5 | Keep this many rotated files |
| `filePrefix` | `pocketrpg` | Log file name prefix |

Files: `pocketrpg.log`, `pocketrpg.1.log`, `pocketrpg.2.log`, ...

---

## 2. EditorConfig Integration

### 2.1 New Configuration Section

Add to `EditorConfig.java`:

```java
// ===== LOGGING SETTINGS =====

/**
 * Enable file logging.
 */
@Builder.Default
private boolean fileLoggingEnabled = false;

/**
 * Directory for log files. Relative to project root.
 */
@Builder.Default
private String logDirectory = "logs";

/**
 * Minimum log level for file output.
 * Options: TRACE, DEBUG, INFO, WARN, ERROR
 */
@Builder.Default
private String fileLogLevel = "DEBUG";

/**
 * Maximum log file size in MB before rotation.
 * 0 = no rotation.
 */
@Builder.Default
private int maxLogFileSizeMB = 10;

/**
 * Maximum number of rotated log files to keep.
 */
@Builder.Default
private int maxLogFiles = 5;
```

### 2.2 JSON Config Example

`editor/config/editor.json`:
```json
{
  "title": "PocketRPG Scene Editor",
  "fullscreen": true,

  "fileLoggingEnabled": true,
  "logDirectory": "logs",
  "fileLogLevel": "DEBUG",
  "maxLogFileSizeMB": 10,
  "maxLogFiles": 5
}
```

### 2.3 Initialization

In `EditorApplication.init()`:

```java
private void initLogging() {
    EditorConfig config = ConfigLoader.getConfig(ConfigLoader.ConfigType.EDITOR);

    if (config.isFileLoggingEnabled()) {
        FileLogConfig fileConfig = FileLogConfig.builder()
            .directory(Path.of(config.getLogDirectory()))
            .minLevel(LogLevel.valueOf(config.getFileLogLevel()))
            .maxFileSize(config.getMaxLogFileSizeMB() * 1024L * 1024L)
            .maxFiles(config.getMaxLogFiles())
            .build();

        Log.getManager().addHandler(new FileLogHandler(fileConfig));
        LOG.info("File logging enabled: " + config.getLogDirectory());
    }
}
```

---

## 3. System.out Migration

### 3.1 Migration Categories

| Category | Count | Log Level | Example |
|----------|-------|-----------|---------|
| Startup/Shutdown | ~30 | INFO | "Scene Editor initialized" |
| Asset Loading | ~40 | DEBUG | "Loaded texture: player.png" |
| Errors/Exceptions | ~50 | ERROR | "Failed to load scene" |
| Debug/Diagnostic | ~80 | DEBUG | "Component registered: Transform" |
| Warnings | ~20 | WARN | "Missing asset, using placeholder" |
| Performance Stats | ~30 | TRACE | "Frame time: 16.2ms" |
| Test Utilities | ~50 | DEBUG | Test scaffolding (low priority) |
| Temporary Debug | ~100 | Remove | `println` left during development |

### 3.2 Migration Strategy

**Phase 1: High-Value Files** (Core systems)
- `EditorApplication.java` (13 calls)
- `GameApplication.java` (19 calls)
- `AssetManager.java` (12 calls)
- `SceneManager.java` (6 calls)
- `ConfigLoader.java` (7 calls)

**Phase 2: Editor Files**
- `EditorSceneController.java`
- `PlayModeController.java`
- `RuntimeSceneLoader.java`
- All `editor/` package files

**Phase 3: Rendering & Core**
- `Renderer.java`, `RenderPipeline.java`
- `PostProcessor.java`
- `GLFWWindow.java`
- `GameObject.java`

**Phase 4: Cleanup**
- Remove temporary debug prints
- Convert test utilities (optional)
- `ConsoleStatisticsReporter.java` (special case - keep System.out for console mode)

### 3.3 Migration Pattern

Before:
```java
System.out.println("Loading scene: " + path);
try {
    // ...
} catch (Exception e) {
    System.err.println("Failed to load scene: " + e.getMessage());
    e.printStackTrace();
}
```

After:
```java
private static final Logger LOG = Log.getLogger(SceneLoader.class);

LOG.info("Loading scene: " + path);
try {
    // ...
} catch (Exception e) {
    LOG.error("Failed to load scene: " + path, e);
}
```

### 3.4 Special Cases

| Case | Handling |
|------|----------|
| `ConsoleStatisticsReporter` | Keep System.out - designed for terminal output |
| Test classes | Low priority, can keep System.out |
| `LogManager` error fallback | Keep System.err for handler errors |
| Shader compilation errors | Use LOG.error with full shader source |

---

## 4. New Classes

### 4.1 FileLogHandler.java

```java
package com.pocket.rpg.logging;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLogHandler implements LogHandler {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path logDirectory;
    private final String filePrefix;
    private final long maxFileSize;
    private final int maxFiles;
    private final LogLevel minLevel;

    private BufferedWriter writer;
    private Path currentFile;
    private long currentFileSize;

    public FileLogHandler(FileLogConfig config) {
        this.logDirectory = config.getDirectory();
        this.filePrefix = config.getFilePrefix();
        this.maxFileSize = config.getMaxFileSize();
        this.maxFiles = config.getMaxFiles();
        this.minLevel = config.getMinLevel();

        initLogFile();
    }

    private void initLogFile() {
        try {
            Files.createDirectories(logDirectory);
            currentFile = logDirectory.resolve(filePrefix + ".log");

            // Append to existing file
            writer = new BufferedWriter(new FileWriter(currentFile.toFile(), true));
            currentFileSize = Files.exists(currentFile) ? Files.size(currentFile) : 0;

        } catch (IOException e) {
            System.err.println("Failed to initialize log file: " + e.getMessage());
        }
    }

    @Override
    public synchronized void handle(LogEntry entry) {
        if (writer == null) return;
        if (!entry.getLevel().isAtLeast(minLevel)) return;

        try {
            checkRotation();
            String line = formatEntry(entry);
            writer.write(line);
            writer.newLine();
            writer.flush();
            currentFileSize += line.length() + 1;

        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }

    private String formatEntry(LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(TIMESTAMP_FORMAT.format(entry.getTimestamp())).append("] ");
        sb.append("[").append(String.format("%-5s", entry.getLevel().name())).append("] ");
        sb.append("[").append(entry.getLoggerName()).append("] ");
        sb.append(entry.getMessage());

        if (entry.getThrowable() != null) {
            sb.append("\n");
            StringWriter sw = new StringWriter();
            entry.getThrowable().printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString().trim());
        }

        return sb.toString();
    }

    private void checkRotation() throws IOException {
        if (maxFileSize <= 0 || currentFileSize < maxFileSize) return;

        writer.close();

        // Rotate existing files
        for (int i = maxFiles - 1; i >= 1; i--) {
            Path older = logDirectory.resolve(filePrefix + "." + i + ".log");
            Path newer = (i == 1)
                ? currentFile
                : logDirectory.resolve(filePrefix + "." + (i - 1) + ".log");

            if (Files.exists(newer)) {
                if (i == maxFiles - 1 && Files.exists(older)) {
                    Files.delete(older);
                }
                Files.move(newer, older, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Start fresh
        writer = new BufferedWriter(new FileWriter(currentFile.toFile()));
        currentFileSize = 0;
    }

    @Override
    public void flush() {
        try {
            if (writer != null) writer.flush();
        } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (IOException ignored) {}
    }
}
```

### 4.2 FileLogConfig.java

```java
package com.pocket.rpg.logging;

import lombok.Builder;
import lombok.Value;
import java.nio.file.Path;

@Value
@Builder
public class FileLogConfig {

    @Builder.Default
    Path directory = Path.of("logs");

    @Builder.Default
    String filePrefix = "pocketrpg";

    @Builder.Default
    long maxFileSize = 10 * 1024 * 1024; // 10 MB

    @Builder.Default
    int maxFiles = 5;

    @Builder.Default
    LogLevel minLevel = LogLevel.DEBUG;
}
```

---

## 5. Implementation Phases

### Phase 1: File Handler Infrastructure
- [ ] Create `FileLogConfig.java`
- [ ] Create `FileLogHandler.java`
- [ ] Add logging settings to `EditorConfig.java`
- [ ] Update `editor.json` schema
- [ ] Initialize file handler in `EditorApplication`

### Phase 2: High-Priority Migration
- [ ] `EditorApplication.java`
- [ ] `GameApplication.java`
- [ ] `AssetManager.java`
- [ ] `ConfigLoader.java`
- [ ] `SceneManager.java`

### Phase 3: Editor Migration
- [ ] `EditorSceneController.java`
- [ ] `PlayModeController.java`
- [ ] `RuntimeSceneLoader.java`
- [ ] Editor panels and tools

### Phase 4: Core Systems Migration
- [ ] Rendering pipeline
- [ ] Platform/window code
- [ ] Serialization
- [ ] Components

### Phase 5: Cleanup
- [ ] Remove dead debug prints
- [ ] Review test utilities
- [ ] Update documentation

---

## 6. Testing

### Unit Tests
- `FileLogHandlerTest` - Write, rotation, level filtering
- `LogManager` integration with multiple handlers

### Manual Testing
- Enable file logging, verify files created
- Generate enough logs to trigger rotation
- Verify log format is readable
- Check performance impact with file logging enabled

---

## 7. Future Considerations (Out of Scope)

- **Log aggregation**: Ship logs to external service
- **Structured logging**: JSON format option
- **Async file writing**: Background thread for I/O
- **Console panel filtering by logger name**: Already have level filtering
- **Runtime log level changes**: Hot-reload config
