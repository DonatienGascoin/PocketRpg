# Plan 11: AssetManager File Stream Safety

## Overview

**Problem:** `AssetManager.scanDirectory()` uses `Files.walk()` which returns a Stream. The current code does NOT wrap it in try-with-resources — it uses it inline in a method chain. While the outer try-catch handles IOException, the Stream itself may not be closed if `forEach()` throws an unchecked exception.

**Severity:** LOW

**Approach:** Wrap `Files.walk()` in a try-with-resources block to guarantee the directory stream is closed.

## Phase 1: Fix Stream Handling

- [ ] Change the `Files.walk(dirPath).filter(...).forEach(...)` chain to:
  ```java
  try (Stream<Path> stream = Files.walk(dirPath)) {
      stream.filter(Files::isRegularFile)
            .forEach(path -> { ... });
  }
  ```
- [ ] This ensures the directory stream is closed even if forEach throws RuntimeException

## Files to Modify

| File | Change |
|------|--------|
| `resources/AssetManager.java` | Wrap Files.walk in try-with-resources |

## Testing Strategy

- Asset scanning still finds all expected files
- No file handle leaks visible in process explorer during repeated scans

## Code Review

- [ ] Verify the Stream variable is only used within the try block
- [ ] Verify IOException handling still works correctly
