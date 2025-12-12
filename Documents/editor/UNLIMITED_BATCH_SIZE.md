# Unlimited Batch Size Support - Implementation Plan

## Problem Statement

**Current Issue:** SpriteBatch has a fixed vertex buffer size that can overflow when rendering too many sprites in a single frame.

**Symptoms:**
- Buffer overflow exceptions
- Rendering artifacts when sprite count exceeds buffer capacity
- Hard limit on scene complexity

**Root Cause:**
```java
// SpriteBatch.java - Fixed buffer size
private static final int MAX_SPRITES = 10000;
private static final int BUFFER_SIZE = MAX_SPRITES * FLOATS_PER_SPRITE;

private float[] vertexData = new float[BUFFER_SIZE];
```

When `submit()` is called for sprite #10001, array index out of bounds occurs.

---

## Solution Overview

**Approach:** Automatic batch flushing when buffer fills up.

**Key Concept:** Instead of one giant batch per frame, render multiple smaller batches as needed.

**Flow:**
1. Begin frame → start batch #1
2. Submit sprites until buffer is full
3. **Flush batch #1** → render what we have
4. Start batch #2 (clear buffer, continue submitting)
5. Repeat until all sprites submitted
6. End frame → flush final batch

---

## Critical Challenge: Z-Index Ordering

### The Problem

**Current sorting:** All sprites sorted by zIndex before any rendering.

**With multiple batches:**
```
Batch 1: Sprites 1-10000 (sorted by zIndex: 0-100)
  → Render all
Batch 2: Sprites 10001-15000 (sorted by zIndex: 0-100)
  → Render all
```

**Result:** Batch 2 renders ON TOP of Batch 1, regardless of zIndex!

A sprite with zIndex=5 in Batch 2 will appear in front of a sprite with zIndex=95 in Batch 1.

### The Solution Options

#### Option A: Sort All Sprites Globally (Recommended)
**Before any batching, sort all sprites by zIndex, then batch in that order.**

**Pros:**
- Perfect z-ordering
- Batching becomes a "dumb" operation (just fill buffers)
- Existing sorting logic still works

**Cons:**
- Requires collecting all sprites upfront
- Slight memory overhead (list of sprite data)

**Implementation:**
```java
// Pseudo-code
List<SpriteSubmission> allSprites = new ArrayList<>();

// Collection phase
for (Renderable r : renderables) {
    allSprites.add(new SpriteSubmission(sprite, transform, zIndex));
}

// Sort phase (once, globally)
allSprites.sort(Comparator.comparingInt(s -> s.zIndex));

// Batching phase
for (SpriteSubmission s : allSprites) {
    batch.submit(s);  // Auto-flushes when full
}
```

#### Option B: Per-Batch Sorting
**Sort within each batch independently.**

**Pros:**
- No global sprite collection needed
- Slightly lower memory usage

**Cons:**
- **WRONG Z-ORDER BETWEEN BATCHES**
- Only correct if all sprites in a batch have non-overlapping zIndex ranges
- Requires careful batch management

**Verdict:** Not suitable for general use.

#### Option C: Single Large Batch (Status Quo)
**Keep current approach, just increase MAX_SPRITES.**

**Pros:**
- Simple
- No code changes

**Cons:**
- Wastes memory for small scenes
- Still has a hard limit
- Doesn't scale to large worlds

**Verdict:** Not a real solution.

---

## Recommended Architecture: Option A

### Design: Deferred Submission with Auto-Flush

**Key Changes:**

1. **Submission becomes buffering** (not immediate batch add)
2. **Explicit sort phase** before rendering
3. **Auto-flush on buffer full**
4. **Transparent to caller**

---

## File Changes

### 1. SpriteBatch.java - Major Refactor

#### Add Submission Buffer

```java
/**
 * Represents a sprite submission (before batching).
 */
private static class SpriteSubmission {
    final Sprite sprite;
    final Transform transform;
    final SpriteRenderer renderer;
    final float opacity;
    final int zIndex;
    
    SpriteSubmission(Sprite sprite, Transform transform, SpriteRenderer renderer, 
                     float opacity, int zIndex) {
        this.sprite = sprite;
        this.transform = transform;
        this.renderer = renderer;
        this.opacity = opacity;
        this.zIndex = zIndex;
    }
}

// Submission buffer (unbounded)
private List<SpriteSubmission> submissions = new ArrayList<>();
```

#### Modify submit() - Buffer Instead of Batch

```java
public void submit(SpriteRenderer spriteRenderer, float opacity) {
    if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
        return;
    }
    
    // Buffer submission (doesn't touch vertex array yet)
    submissions.add(new SpriteSubmission(
        spriteRenderer.getSprite(),
        spriteRenderer.getGameObject().getTransform(),
        spriteRenderer,
        opacity,
        spriteRenderer.getZIndex()
    ));
}
```

#### Add processBatches() - Sort and Flush

```java
/**
 * Processes all submissions: sorts by zIndex, then batches with auto-flush.
 */
private void processBatches() {
    if (submissions.isEmpty()) {
        return;
    }
    
    // 1. Sort globally by zIndex (lower = render first)
    submissions.sort(Comparator.comparingInt(s -> s.zIndex));
    
    // 2. Batch in sorted order (auto-flush when full)
    for (SpriteSubmission sub : submissions) {
        addToBatch(sub);
    }
    
    // 3. Flush remaining sprites
    flush();
    
    // 4. Clear submissions for next frame
    submissions.clear();
}

/**
 * Adds a submission to the current batch, flushing if needed.
 */
private void addToBatch(SpriteSubmission sub) {
    // Check if buffer is full
    if (spriteCount >= maxSprites) {
        flush();  // Render current batch
        // Buffer is now empty, continue adding
    }
    
    // Add to vertex array (existing logic)
    buildVertexData(sub.sprite, sub.transform, sub.renderer, sub.opacity);
    spriteCount++;
}
```

#### Modify end() - Call processBatches()

```java
public void end() {
    processBatches();  // Sort, batch, and render all submissions
    
    // Reset state
    spriteCount = 0;
    currentTexture = null;
}
```

#### Keep flush() - Now Called Multiple Times

```java
private void flush() {
    if (spriteCount == 0) {
        return;
    }
    
    // Upload vertex data
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferSubData(GL_ARRAY_BUFFER, 0, vertexData);
    
    // Bind texture
    if (currentTexture != null) {
        currentTexture.bind(0);
    }
    
    // Draw
    glBindVertexArray(vao);
    glDrawArrays(GL_TRIANGLES, 0, spriteCount * 6);
    
    // Update stats
    drawCalls++;
    
    // Reset for next batch
    spriteCount = 0;
    vertexOffset = 0;
}
```

---

### 2. TilemapRenderer Chunks - Same Approach

#### Problem: Chunks Can Also Overflow

A single chunk with 256x256 tiles = 65,536 sprites. If MAX_SPRITES = 10,000, this overflows in one chunk!

#### Solution: Track Chunk Submissions Too

```java
// In SpriteBatch
private static class ChunkSubmission {
    final TilemapRenderer tilemap;
    final int cx;
    final int cy;
    final float opacity;
    final int zIndex;
    
    ChunkSubmission(TilemapRenderer tilemap, int cx, int cy, 
                    float opacity, int zIndex) {
        this.tilemap = tilemap;
        this.cx = cx;
        this.cy = cy;
        this.opacity = opacity;
        this.zIndex = zIndex;
    }
}

private List<ChunkSubmission> chunkSubmissions = new ArrayList<>();
```

#### Modify submitChunk()

```java
public void submitChunk(TilemapRenderer tilemap, int cx, int cy, float opacity) {
    chunkSubmissions.add(new ChunkSubmission(
        tilemap, cx, cy, opacity, tilemap.getZIndex()
    ));
}
```

#### Update processBatches() - Handle Both Types

```java
private void processBatches() {
    // Combine sprite and chunk submissions into one sorted list
    List<Object> allSubmissions = new ArrayList<>();
    allSubmissions.addAll(submissions);
    allSubmissions.addAll(chunkSubmissions);
    
    // Sort by zIndex (need to extract zIndex from each type)
    allSubmissions.sort((a, b) -> {
        int zIndexA = getSubmissionZIndex(a);
        int zIndexB = getSubmissionZIndex(b);
        return Integer.compare(zIndexA, zIndexB);
    });
    
    // Process in order
    for (Object sub : allSubmissions) {
        if (sub instanceof SpriteSubmission s) {
            addToBatch(s);
        } else if (sub instanceof ChunkSubmission c) {
            addChunkToBatch(c);
        }
    }
    
    flush();
    
    submissions.clear();
    chunkSubmissions.clear();
}

private int getSubmissionZIndex(Object submission) {
    if (submission instanceof SpriteSubmission s) {
        return s.zIndex;
    } else if (submission instanceof ChunkSubmission c) {
        return c.zIndex;
    }
    return 0;
}
```

#### Handle Large Chunks - Tile-by-Tile Submission

```java
private void addChunkToBatch(ChunkSubmission chunk) {
    TileChunk tileChunk = chunk.tilemap.getChunk(chunk.cx, chunk.cy);
    if (tileChunk == null || tileChunk.isEmpty()) {
        return;
    }
    
    // Iterate all tiles in chunk
    for (int ty = 0; ty < CHUNK_SIZE; ty++) {
        for (int tx = 0; tx < CHUNK_SIZE; tx++) {
            Tile tile = tileChunk.get(tx, ty);
            if (tile == null || tile.sprite() == null) {
                continue;
            }
            
            // Check if buffer full (flush if needed)
            if (spriteCount >= maxSprites) {
                flush();
            }
            
            // Add tile as sprite
            buildTileVertexData(tile, chunk.tilemap, 
                               chunk.cx, chunk.cy, tx, ty, chunk.opacity);
            spriteCount++;
        }
    }
}
```

---

### 3. BatchRenderer.java - No Changes

**Reason:** All batching logic is encapsulated in SpriteBatch. BatchRenderer just calls submit methods, which now buffer instead of immediately batch.

---

### 4. RenderPipeline.java - No Changes

**Reason:** Same as above. The buffering/flushing is transparent to callers.

---

## Performance Considerations

### Memory Impact

**Before:**
```
Fixed: 10,000 sprites * 48 floats = 480,000 floats = ~1.8 MB
```

**After:**
```
Vertex buffer: Same (10,000 sprites)
Submission buffer: N sprites * ~32 bytes = variable
  Example: 50,000 sprites = ~1.5 MB (temporary, per frame)
```

**Total overhead:** ~1.5 MB for large scenes (acceptable).

### CPU Impact

**Sorting cost:** O(N log N) where N = total sprites per frame.
- 10,000 sprites: ~130K comparisons
- 50,000 sprites: ~800K comparisons
- 100,000 sprites: ~1.6M comparisons

**Modern CPU:** ~1-2ms for 100K sprite sort (negligible).

**Flush overhead:** Minimal (already batched, just multiple draw calls).

### GPU Impact

**Draw calls:**
- Before: 1 draw call (if under limit)
- After: ceil(N / 10,000) draw calls
  - 10,000 sprites: 1 call
  - 50,000 sprites: 5 calls
  - 100,000 sprites: 10 calls

**Modern GPU:** 10 draw calls is trivial (~0.1ms overhead).

---

## Alternative: Dynamic Buffer Resizing

**Idea:** Instead of multiple batches, grow the vertex buffer as needed.

```java
if (spriteCount >= maxSprites) {
    // Double buffer size
    maxSprites *= 2;
    float[] newData = new float[maxSprites * FLOATS_PER_SPRITE];
    System.arraycopy(vertexData, 0, newData, 0, vertexData.length);
    vertexData = newData;
    
    // Reallocate GPU buffer
    glBufferData(GL_ARRAY_BUFFER, vertexData.length * Float.BYTES, GL_DYNAMIC_DRAW);
}
```

**Pros:**
- Still single draw call
- No sorting overhead

**Cons:**
- Requires GPU buffer reallocation (expensive!)
- Wastes memory for small scenes
- Can cause frame stutter during resize
- Hard to predict memory usage

**Verdict:** Not recommended. Multi-batch approach is cleaner.

---

## Implementation Order

1. **Add SpriteSubmission and ChunkSubmission classes**
2. **Add submission buffers** (ArrayList fields)
3. **Modify submit() methods** to buffer instead of batch
4. **Add processBatches() method**
5. **Update end() to call processBatches()**
6. **Test with small scene** (verify correctness)
7. **Test with large scene** (verify auto-flush)
8. **Test z-index ordering** (verify sprites render in correct order)
9. **Profile performance** (ensure no regression)

---

## Testing Scenarios

### Test 1: Small Scene (< 10,000 sprites)
**Expected:** Single batch, same as before.

### Test 2: Exactly 10,000 Sprites
**Expected:** Single batch, no flush.

### Test 3: 10,001 Sprites
**Expected:** Two batches (10,000 + 1).

### Test 4: Z-Index Ordering
**Setup:**
- Sprite A: zIndex=5, submitted first
- Sprite B: zIndex=10, submitted second (causes flush)
- Sprite C: zIndex=1, submitted third

**Expected Render Order:** C, A, B (correct z-order despite batching).

### Test 5: Mixed Sprites and Tilemaps
**Setup:**
- Tilemap layer at zIndex=0 (background)
- Sprite entities at zIndex=5 (characters)
- Tilemap layer at zIndex=10 (foreground)

**Expected:** Background → characters → foreground.

### Test 6: Large Tilemap Chunk
**Setup:**
- Single chunk with 65,536 tiles (256x256)

**Expected:** Multiple batches, all tiles rendered.

---

## Summary

| Aspect | Current | With Multi-Batch |
|--------|---------|------------------|
| Max sprites | 10,000 (hard limit) | Unlimited |
| Draw calls | 1 per frame | ceil(N / 10,000) |
| Memory | Fixed ~2 MB | ~2 MB + N*32 bytes |
| Z-ordering | Correct | Correct (global sort) |
| Performance | Fast | Fast (minor sort overhead) |
| Complexity | Simple | Moderate |

**Recommendation:** Implement multi-batch with global sorting (Option A). It's the only approach that guarantees correct z-ordering while supporting unlimited sprite counts.
