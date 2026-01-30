# Plan 04: Renderer Native Memory Safety

## Overview

**Problem:** `Renderer.java` allocates off-heap memory via `MemoryUtil.memAllocFloat(24)` in its constructor/init. The `destroy()` method frees it, but if an exception occurs during initialization (e.g. shader compilation failure), `destroy()` may never be called, leaking native memory.

**Severity:** HIGH

**Approach:** Make initialization exception-safe by cleaning up partial resources on failure. Apply the same pattern to `SpriteBatch.java` which has identical concerns.

## Phase 1: Exception-Safe Initialization in Renderer

- [ ] Wrap the `init()` method body in try-catch. On failure, call `destroy()` to free any partially allocated resources, then rethrow
- [ ] Ensure `destroy()` handles partially initialized state (some fields null/zero, some allocated)
- [ ] Verify `destroy()` null-checks are already present (they are — `shader != null`, `quadVAO != 0`, etc.)

## Phase 2: Same Fix for SpriteBatch

- [ ] Apply identical try-catch pattern to `SpriteBatch` initialization
- [ ] Verify `SpriteBatch.destroy()` handles partial state correctly

## Files to Modify

| File | Change |
|------|--------|
| `rendering/batch/Renderer.java` | Wrap init in try-catch, cleanup on failure |
| `rendering/batch/SpriteBatch.java` | Wrap init in try-catch, cleanup on failure |

## Testing Strategy

- Verify normal rendering still works after changes
- Optionally force a shader compilation failure to verify cleanup runs
- No native memory growth visible after failed init attempts

## Code Review

- [ ] Verify catch block re-throws after cleanup (don't silently swallow)
- [ ] Verify destroy is safe to call on partially initialized state
- [ ] Verify no double-free if destroy is called after failed init then again on normal shutdown
