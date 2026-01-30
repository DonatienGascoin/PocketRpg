# Plan 12: FileDialogs NFD Cleanup

## Overview

**Problem:** `FileDialogs.java` calls `NFD_Init()` in a static initializer but the `cleanup()` method that calls `NFD_Quit()` may not be called on application shutdown. This leaks NFD native resources.

**Severity:** LOW-MEDIUM

**Approach:** Ensure `FileDialogs.cleanup()` is called during application shutdown. The cleanup method already exists — it just needs to be wired into the shutdown sequence.

## Phase 1: Wire Cleanup Into Shutdown

- [ ] Find the editor application shutdown path (likely in `EditorApplication` or similar)
- [ ] Add a call to `FileDialogs.cleanup()` in the shutdown sequence
- [ ] Ensure it's called even if other shutdown steps fail (try-finally)

## Phase 2: Add Shutdown Hook as Fallback

- [ ] Optionally register a JVM shutdown hook in `FileDialogs` static initializer:
  ```java
  Runtime.getRuntime().addShutdownHook(new Thread(FileDialogs::cleanup));
  ```
- [ ] This ensures cleanup even if the normal shutdown path is skipped

## Files to Modify

| File | Change |
|------|--------|
| Editor application main class | Call `FileDialogs.cleanup()` on shutdown |
| `editor/core/FileDialogs.java` | Optionally add shutdown hook |

## Testing Strategy

- Start and stop the editor, verify `NFD_Quit()` is called (add a log line)
- Kill the editor process, verify shutdown hook fires

## Code Review

- [ ] Verify `NFD_Quit()` is only called once (idempotent guard)
- [ ] Verify shutdown hook doesn't conflict with normal shutdown path
