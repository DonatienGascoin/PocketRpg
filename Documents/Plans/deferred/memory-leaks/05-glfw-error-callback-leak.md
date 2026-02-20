# Plan 05: GLFW Error Callback Leak

## Overview

**Problem:** `EditorWindow.java` creates a GLFW error callback at initialization (`GLFWErrorCallback.createPrint(System.err).set()`). In `destroy()`, it retrieves the current callback via `glfwSetErrorCallback(null)` and frees it, but if initialization fails before reaching destroy, the callback leaks.

**Severity:** HIGH

**Approach:** Store the error callback reference as a field. Free it in destroy regardless of initialization success. Ensure cleanup order is correct (free callback after GLFW terminate, or before — check GLFW docs).

## Phase 1: Track and Free Callback

- [ ] Store the `GLFWErrorCallback` returned by `.set()` as a class field
- [ ] In `destroy()`, free the stored reference instead of relying on `glfwSetErrorCallback(null)` retrieval
- [ ] Add null guard for the callback field
- [ ] Ensure callback is freed even if window handle was never created (partial init failure)

## Phase 2: Verify Cleanup Order

- [ ] Verify that `glfwTerminate()` is called before freeing the error callback (GLFW requires this order)
- [ ] Current destroy order: `glfwFreeCallbacks` → `glfwDestroyWindow` → `glfwTerminate` → free error callback — this is correct

## Files to Modify

| File | Change |
|------|--------|
| `editor/core/EditorWindow.java` | Store callback reference; robust cleanup |

## Testing Strategy

- Start and stop the editor multiple times, verify no callback warnings
- Force an init failure (e.g., invalid GL version hint) to verify callback is still freed

## Code Review

- [ ] Verify callback is only freed once
- [ ] Verify GLFW cleanup order is maintained
