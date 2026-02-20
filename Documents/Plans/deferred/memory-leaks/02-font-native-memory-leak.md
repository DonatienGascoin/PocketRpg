# Plan 02: Font STB Native Memory Leak

## Overview

**Problem:** In `Font.java`, the `createAtlas()` method allocates STB native structures (`STBTTFontinfo.create()`, `STBTTPackedchar.malloc()`, `STBTTPackContext.malloc()`) but does not free them on all code paths. The `STBTTFontinfo` is never freed. Other allocations leak if exceptions occur before their cleanup lines.

**Severity:** HIGH

**Approach:** Wrap all native allocations in try-finally blocks. Free `STBTTFontinfo` after atlas creation. Ensure `STBTTPackedchar` and `STBTTPackContext` are freed on all paths.

## Phase 1: Fix createAtlas Method

- [ ] Store `STBTTFontinfo` in a local variable and free it in a `finally` block after use
- [ ] Wrap `STBTTPackedchar.malloc()` allocation in try-finally, free in finally
- [ ] Wrap `STBTTPackContext.malloc()` allocation in try-finally, free in finally
- [ ] Ensure `ByteBuffer atlasData` allocated via `BufferUtils` is handled correctly in the retry loop — if retry allocates new buffer, old one should not leak
- [ ] Verify the `MemoryUtil.memFree()` calls match the allocation method used (memAlloc vs BufferUtils)

## Phase 2: Add Null Guards to destroy()

- [ ] Ensure `destroy()` is idempotent — safe to call multiple times
- [ ] Clear glyph map in destroy to release references

## Files to Modify

| File | Change |
|------|--------|
| `ui/text/Font.java` | Wrap native allocations in try-finally; free STBTTFontinfo |

## Testing Strategy

- Verify font loading still works after changes (run editor, check text renders)
- Load and destroy fonts in a loop, monitor native memory via task manager
- Check no STB errors printed to stderr

## Code Review

- [ ] Verify every `malloc()` / `create()` has a corresponding `free()` on all paths
- [ ] Verify `finally` blocks don't mask original exceptions
- [ ] Verify no double-free scenarios
