# Plan 13: Centralized Resource Lifecycle Management

## Overview

**Problem:** Resources (Textures, Shaders, Audio, native buffers) are destroyed individually across the codebase, but there's no guarantee that `destroy()` is called on all resources. Scene changes, exceptions during cleanup, and missing cleanup paths all contribute to leaks. There is no reference counting or centralized tracking.

**Severity:** MEDIUM (design/architectural)

**Approach:** Create a lightweight `ResourceTracker` that all native/GPU resources register with at creation. On shutdown or scene change, the tracker ensures everything is freed. This is a safety net — individual destroy() calls remain the primary mechanism.

## Phase 1: Define Destroyable Interface

- [ ] Create `Destroyable` interface with `void destroy()` method (if not already done in Plan 06)
- [ ] Have key resource classes implement it: `Texture`, `Shader`, `AudioClip`, `Font`, `SpriteBatch`, `Renderer`

## Phase 2: Create ResourceTracker

- [ ] Create `ResourceTracker` singleton class
- [ ] `register(Destroyable resource)` — adds to a `Set<WeakReference<Destroyable>>`
- [ ] `destroyAll()` — iterates and calls `destroy()` on all remaining resources
- [ ] `getActiveCount()` — returns count of tracked resources (for diagnostics)
- [ ] Use `WeakReference` so the tracker doesn't prevent GC of already-freed resources

## Phase 3: Integrate Registration

- [ ] In constructors/init methods of `Texture`, `Shader`, `AudioClip`, `Font` — call `ResourceTracker.get().register(this)`
- [ ] Call `ResourceTracker.get().destroyAll()` on application shutdown
- [ ] Log warnings for resources that reach `destroyAll()` without being manually destroyed (indicates a leak)

## Files to Modify

| File | Change |
|------|--------|
| **NEW** `resources/Destroyable.java` | Interface |
| **NEW** `resources/ResourceTracker.java` | Tracking singleton |
| `rendering/Texture.java` | Implement Destroyable, register |
| `rendering/Shader.java` | Implement Destroyable, register |
| `audio/clips/AudioClip.java` | Implement Destroyable, register |
| `ui/text/Font.java` | Implement Destroyable, register |
| Editor/game shutdown code | Call `ResourceTracker.destroyAll()` |

## Testing Strategy

- Unit test: register resources, call destroyAll, verify all destroyed
- Unit test: manually destroy a resource, verify tracker doesn't double-destroy (WeakReference cleared)
- Manual test: run editor, shut down, verify log shows no leaked resources
- Manual test: check `getActiveCount()` stays reasonable during extended use

## Code Review

- [ ] Verify WeakReference approach doesn't cause issues with GC timing
- [ ] Verify no performance impact from registration (lightweight Set operations)
- [ ] Verify thread safety of the tracker
