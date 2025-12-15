## Phase 6: UX Polish

**Goal:** Make editor pleasant to use.

### 6.1 Undo/Redo System

```java
public interface EditorCommand {
    void execute();
    void undo();
    String getDescription();
}

public class CommandHistory {
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();
    private final int maxHistory = 100;

    public void execute(EditorCommand command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();

        while (undoStack.size() > maxHistory) {
            undoStack.removeLast();
        }
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        EditorCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        EditorCommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);
    }
}

// Example commands
public class PaintTilesCommand implements EditorCommand {
    private final TilemapRenderer tilemap;
    private final Map<Long, TilemapRenderer.Tile> oldTiles = new HashMap<>();
    private final Map<Long, TilemapRenderer.Tile> newTiles = new HashMap<>();

    public void recordChange(int x, int y, Tile oldTile, Tile newTile) {
        long key = packCoord(x, y);
        if (!oldTiles.containsKey(key)) oldTiles.put(key, oldTile);
        newTiles.put(key, newTile);
    }

    public void execute() {
        for (var entry : newTiles.entrySet()) {
            tilemap.set(unpackX(entry.getKey()), unpackY(entry.getKey()), entry.getValue());
        }
    }

    public void undo() {
        for (var entry : oldTiles.entrySet()) {
            tilemap.set(unpackX(entry.getKey()), unpackY(entry.getKey()), entry.getValue());
        }
    }

    public String getDescription() {
        return "Paint " + newTiles.size() + " tiles";
    }
}
```

### 6.2 Logging System

```java
public class EditorLogger {
    private static final int MAX_LOG_ENTRIES = 1000;
    private static final Deque<LogEntry> logBuffer = new ArrayDeque<>();

    public static void info(String message) { log(LogLevel.INFO, message); }
    public static void warn(String message) { log(LogLevel.WARN, message); }
    public static void error(String message) { log(LogLevel.ERROR, message); }

    private static void log(LogLevel level, String message) {
        LogEntry entry = new LogEntry(level, message, System.currentTimeMillis());

        synchronized (logBuffer) {
            logBuffer.addLast(entry);
            while (logBuffer.size() > MAX_LOG_ENTRIES) {
                logBuffer.removeFirst();
            }
        }

        System.out.println("[" + level + "] " + message);
    }

    public static List<LogEntry> getRecentLogs(int count) {
        synchronized (logBuffer) {
            int start = Math.max(0, logBuffer.size() - count);
            return new ArrayList<>(logBuffer).subList(start, logBuffer.size());
        }
    }
}

public record LogEntry(LogLevel level, String message, long timestamp) {}
public enum LogLevel { DEBUG, INFO, WARN, ERROR }
```

### 6.3 Log Panel

```java
public class LogPanel {
    private LogLevel filterLevel = LogLevel.INFO;
    private boolean autoScroll = true;

    public void render() {
        ImGui.begin("Log");

        // Filter buttons
        if (ImGui.button("Clear")) EditorLogger.clear();
        ImGui.sameLine();
        ImGui.checkbox("Auto-scroll", autoScroll);
        ImGui.sameLine();
        ImGui.combo("Filter", filterLevel.ordinal(), LogLevel.values());

        ImGui.separator();

        // Log entries
        ImGui.beginChild("log_scroll");
        for (LogEntry entry : EditorLogger.getRecentLogs(500)) {
            if (entry.level().ordinal() < filterLevel.ordinal()) continue;

            int color = switch (entry.level()) {
                case DEBUG -> 0xFF888888;
                case INFO -> 0xFFFFFFFF;
                case WARN -> 0xFF00FFFF;
                case ERROR -> 0xFF0000FF;
            };

            ImGui.textColored(color, formatEntry(entry));
        }

        if (autoScroll) ImGui.setScrollHereY(1.0f);
        ImGui.endChild();

        ImGui.end();
    }
}
```

### 6.4 Keyboard Shortcuts

```java
public class EditorShortcuts {
    public void setupDefaults() {
        // File
        bind(KeyCode.S, Modifier.CTRL, () -> editor.saveScene());
        bind(KeyCode.O, Modifier.CTRL, () -> editor.openScene());
        bind(KeyCode.N, Modifier.CTRL, () -> editor.newScene());

        // Edit
        bind(KeyCode.Z, Modifier.CTRL, () -> editor.undo());
        bind(KeyCode.Y, Modifier.CTRL, () -> editor.redo());

        // Tools (already implemented)
        bind(KeyCode.B, () -> editor.setTool("brush"));
        bind(KeyCode.E, () -> editor.setTool("eraser"));
        bind(KeyCode.F, () -> editor.setTool("fill"));
        bind(KeyCode.R, () -> editor.setTool("rectangle"));
        bind(KeyCode.I, () -> editor.setTool("picker"));
        bind(KeyCode.V, () -> editor.setTool("selection"));
        bind(KeyCode.C, () -> editor.setTool("collision"));

        // Edit modes
        bind(KeyCode.NUM_1, () -> editor.setEditMode(EditMode.TILEMAP));
        bind(KeyCode.NUM_2, () -> editor.setEditMode(EditMode.COLLISION));
        bind(KeyCode.NUM_3, () -> editor.setEditMode(EditMode.ENTITY));

        // View
        bind(KeyCode.G, () -> editor.toggleGrid());
        bind(KeyCode.HOME, () -> editor.resetCamera());

        // Selection
        bind(KeyCode.DELETE, () -> editor.deleteSelected());
        bind(KeyCode.D, Modifier.CTRL, () -> editor.duplicateSelected());
    }
}
```

### 6.5 Files to Create

```
src/main/java/com/pocket/rpg/editor/
├── commands/
│   ├── EditorCommand.java
│   ├── CommandHistory.java
│   ├── PaintTilesCommand.java
│   ├── PaintCollisionCommand.java
│   ├── PlaceEntityCommand.java
│   ├── MoveEntityCommand.java
│   └── DeleteEntityCommand.java
├── logging/
│   ├── EditorLogger.java
│   ├── LogEntry.java
│   └── LogLevel.java
└── panels/
    └── LogPanel.java
```

---