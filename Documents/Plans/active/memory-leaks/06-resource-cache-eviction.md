# Plan 06: ResourceCache Eviction Policy

## Overview

**Problem:** `ResourceCache.java` has a hard cap of 1000 entries. When full, new items are silently rejected — no eviction occurs. Old resources (holding OpenGL handles, native memory) stay forever. This means the cache can't adapt to scenes with different asset needs.

**Severity:** MEDIUM

**Approach:** Implement an LRU eviction strategy. When the cache is full, evict the least recently used entry. If the evicted resource implements a `Destroyable` interface, call `destroy()` on eviction.

## Phase 1: Add Resource Cleanup Interface

- [ ] Create a `Destroyable` interface (or use an existing one) with a `destroy()` method
- [ ] Have `Texture`, `AudioClip`, and other resource types implement it (many already have `destroy()`)

## Phase 2: Implement LRU Eviction in ResourceCache

- [ ] Replace `ConcurrentHashMap` with a `LinkedHashMap` (access-ordered) wrapped in `Collections.synchronizedMap`, or use a custom LRU structure
- [ ] On `put()`, if cache is full, evict the least recently accessed entry
- [ ] On eviction, if the resource implements `Destroyable`, call `destroy()`
- [ ] Update `get()` to touch access order
- [ ] Log evictions for diagnostics

## Phase 3: Update clear() Method

- [ ] When clearing the cache, call `destroy()` on all `Destroyable` resources
- [ ] Add a `destroyAll()` method for application shutdown

## Files to Modify

| File | Change |
|------|--------|
| `resources/ResourceCache.java` | LRU eviction, destroy on evict |
| **NEW** `Destroyable.java` (or similar) | Interface for destroyable resources |
| `rendering/Texture.java` | Implement Destroyable (if not already) |
| `audio/clips/AudioClip.java` | Implement Destroyable (after Plan 03) |

## Testing Strategy

- Unit test: fill cache to capacity, add one more item, verify LRU item was evicted
- Unit test: verify `destroy()` called on evicted Destroyable resources
- Manual test: load many assets in editor, verify memory stays bounded

## Code Review

- [ ] Verify thread safety of new cache implementation
- [ ] Verify eviction doesn't break active references (consider weak references for safety)
- [ ] Verify stats tracking still works
