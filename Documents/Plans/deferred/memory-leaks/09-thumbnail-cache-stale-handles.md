# Plan 09: ThumbnailCache Stale Texture Handles

## Overview

**Problem:** `ThumbnailCache` stores OpenGL texture IDs but has no mechanism to invalidate entries when the underlying textures are destroyed. Using stale texture IDs in ImGui can cause rendering corruption or crashes.

**Severity:** MEDIUM

**Approach:** Add an invalidation mechanism. When textures are destroyed or reloaded, the ThumbnailCache should remove stale entries. Leverage the EditorEventBus for notification.

## Phase 1: Add Invalidation API

- [ ] Add `invalidate(String assetPath)` method that removes the entry for a specific asset
- [ ] Add `invalidateAll()` method to clear entire cache when needed (e.g., on scene change)

## Phase 2: Hook Into Asset Lifecycle

- [ ] Subscribe to asset reload/unload events via EditorEventBus (or a new event type if needed)
- [ ] When a texture asset is reloaded, call `invalidate()` for that path
- [ ] Ensure the subscription is properly cleaned up (per Plan 01)

## Phase 3: Validation Guard

- [ ] Optionally, before using a cached texture ID, check if it's still a valid OpenGL texture via `glIsTexture(id)` — this is a safety net, not a primary mechanism
- [ ] If invalid, remove from cache and return a placeholder

## Files to Modify

| File | Change |
|------|--------|
| `editor/assets/ThumbnailCache.java` | Add invalidate methods, optional GL validation |
| Asset reload code (TextureLoader or AssetManager) | Fire invalidation event or call invalidate directly |

## Testing Strategy

- Modify a texture file while editor is running, verify thumbnail updates
- Verify no rendering glitches from stale IDs
- Verify cache miss triggers re-generation

## Code Review

- [ ] Verify `glIsTexture()` is called on the GL thread
- [ ] Verify invalidation doesn't cause flickering during normal operation
