# Plan 07: ImGui Font Config Exception Safety

## Overview

**Problem:** In `ImGuiLayer.java`, `ImFontConfig` objects are created and later destroyed, but if an exception occurs between creation and destruction, they leak. The ImGui context creation and font atlas building are not wrapped in try-finally.

**Severity:** MEDIUM

**Approach:** Wrap font config creation and ImGui initialization in try-finally blocks to guarantee cleanup.

## Phase 1: Exception-Safe Font Loading

- [ ] Wrap `ImFontConfig` creation in try-finally:
  ```java
  ImFontConfig config = new ImFontConfig();
  try {
      // ... use config to add fonts ...
  } finally {
      config.destroy();
  }
  ```
- [ ] Apply to all ImFontConfig instances in the font loading section
- [ ] Ensure `ImGui.destroyContext()` is called if initialization fails partway through

## Phase 2: Robust Initialization Flag

- [ ] Set `initialized = true` only after ALL initialization succeeds
- [ ] In `destroy()`, handle partial initialization gracefully (check each subsystem before shutting it down)

## Files to Modify

| File | Change |
|------|--------|
| `editor/core/ImGuiLayer.java` | try-finally around font configs; robust init/destroy |

## Testing Strategy

- Editor starts and shuts down cleanly
- Font rendering works correctly after changes
- Optionally test with a missing font file to verify cleanup on failure

## Code Review

- [ ] Verify no double-destroy of font configs
- [ ] Verify initialization order matches destroy order (reverse)
