# Plan 03: AudioClip OpenAL Buffer Leak

## Overview

**Problem:** `AudioClip` holds an OpenAL `bufferId` but has no `destroy()` method. When audio assets are unloaded or replaced, the OpenAL buffer is never freed via `alDeleteBuffers()`. Over time this exhausts OpenAL buffer resources.

**Severity:** HIGH

**Approach:** Add a `destroy()` method to `AudioClip` that frees the OpenAL buffer. Ensure the audio loader and cache call it during cleanup/reload.

## Phase 1: Add Cleanup to AudioClip

- [ ] Add `destroy()` method to `AudioClip` that calls `AL10.alDeleteBuffers(bufferId)` and sets `bufferId` to 0
- [ ] Add a guard so `destroy()` is idempotent

## Phase 2: Integrate with Asset Lifecycle

- [ ] In the audio asset loader's `reload()` method, call `existing.destroy()` before loading the new clip (mirroring the pattern in `TextureLoader.reload()`)
- [ ] Ensure `ResourceCache.clear()` or any asset unload path calls `destroy()` on AudioClip resources
- [ ] Check if there's an audio system shutdown path that should free all clips

## Files to Modify

| File | Change |
|------|--------|
| `audio/clips/AudioClip.java` | Add `destroy()` method |
| Audio loader class (find via AudioClip usage) | Call `destroy()` on reload/unload |

## Testing Strategy

- Load and unload audio clips, verify OpenAL buffer IDs are freed (log alDeleteBuffers calls)
- Run editor, play sounds, verify no OpenAL errors accumulate
- Check `alGetError()` after destroy for validation

## Code Review

- [ ] Verify no code holds stale references to destroyed AudioClip buffer IDs
- [ ] Verify destroy is called before any re-assignment of bufferId
