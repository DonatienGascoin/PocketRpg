# Plan 08: TextureLoader Placeholder Texture

## Overview

**Problem:** `TextureLoader.getPlaceholder()` returns `null` instead of creating a fallback placeholder texture (1x1 magenta). When a texture fails to load, null propagates through the rendering pipeline, causing NPEs or rendering failures instead of a visible "missing texture" indicator.

**Severity:** MEDIUM

**Approach:** Implement the placeholder texture — a 1x1 magenta pixel. Create it lazily on first access. Destroy it on shutdown.

## Phase 1: Implement Placeholder Texture

- [ ] In `getPlaceholder()`, lazily create a 1x1 magenta (0xFF00FF) OpenGL texture
- [ ] Cache it as a static or instance field so it's only created once
- [ ] Return the cached placeholder on subsequent calls
- [ ] Use `glTexImage2D` with a single-pixel ByteBuffer

## Phase 2: Cleanup

- [ ] Add a static `destroyPlaceholder()` method or integrate with application shutdown
- [ ] Ensure placeholder texture ID is freed via `glDeleteTextures`

## Files to Modify

| File | Change |
|------|--------|
| `resources/loaders/TextureLoader.java` | Implement `getPlaceholder()` with 1x1 magenta texture |

## Testing Strategy

- Delete or rename a texture file, verify magenta square renders instead of crash
- Verify placeholder is only created once (log on creation)
- Verify normal textures still load correctly

## Code Review

- [ ] Verify placeholder uses correct OpenGL format and parameters
- [ ] Verify placeholder is not accidentally destroyed by other code
