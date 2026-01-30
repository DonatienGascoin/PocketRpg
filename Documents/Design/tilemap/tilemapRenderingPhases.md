# Tilemap Rendering Implementation

## Overview

This document outlines the phased implementation plan for adding `Tilemap` rendering support to the engine. The goal is to allow efficient rendering of tile-based maps without requiring a `GameObject` per tile.

### Design Decisions

- **Renderable interface**: Common contract for all renderable components (`SpriteRenderer`, `Tilemap`)
- **Unified render list**: `Scene` maintains a single sorted list of `Renderable` instances
- **instanceof dispatch**: `RenderPipeline` uses instanceof checks for type-specific rendering (simpler than visitor pattern for 2-3 types)
- **Chunk-based culling**: Tilemaps cull at chunk level, not per-tile

---

## Phase 1: Renderable Interface + Scene Refactor

**Goal**: Introduce `Renderable` interface and refactor `Scene` to use unified renderer list.

### Files to Create

| File | Description |
|------|-------------|
| `rendering/Renderable.java` | Interface defining `getZIndex()`, `isRenderVisible()`, `getGameObject()` |

### Files to Modify

| File | Changes |
|------|---------|
| `components/SpriteRenderer.java` | Implement `Renderable`, add `isRenderVisible()` method |
| `scenes/Scene.java` | Replace `List<SpriteRenderer>` with `List<Renderable>`, add `getRenderers()`, update registration logic |

### Renderable Interface

```java
public interface Renderable {
    int getZIndex();
    boolean isRenderVisible();
    GameObject getGameObject();
}
```

### Scene Changes

- Replace `spriteRenderers` field with `renderables`
- Add `getRenderers()` returning sorted list by zIndex
- Update `registerCachedComponent()` to handle `Renderable` instances
- Keep sorting deferred (dirty flag) for performance

### Testing

- Existing `SpriteRenderer` tests should pass unchanged
- Verify render order matches previous behavior

---

## Phase 2: RenderPipeline Dispatch Update

**Goal**: Update `RenderPipeline` to consume `getRenderers()` and dispatch by type.

### Files to Modify

| File | Changes |
|------|---------|
| `rendering/RenderPipeline.java` | Use `scene.getRenderers()`, add instanceof dispatch for `SpriteRenderer` |
| `rendering/renderers/BatchRenderer.java` | No changes yet (still only handles sprites) |

### Pipeline Flow

```
1. scene.getRenderers()  // Returns List<Renderable> sorted by zIndex
2. For each renderable:
   a. Check isRenderVisible()
   b. instanceof SpriteRenderer → existing sprite path
   c. instanceof Tilemap → (Phase 4)
3. Culling applied per-type
```

### Backward Compatibility

- `SpriteRenderer` rendering behavior unchanged
- Performance should be identical (same iteration, just through interface)

---

## Phase 3: Tilemap Component Enhancement

**Goal**: Expand `Tilemap` to implement `Renderable` with proper metadata for rendering.

### Files to Modify

| File | Changes |
|------|---------|
| `components/Tilemap.java` | Implement `Renderable`, add `tileSize`, `isStatic`, chunk bounds calculation |

### New Tilemap Fields

```java
@Getter @Setter
private float tileSize = 1.0f;  // World units, defaults to config PPU-derived

@Getter @Setter  
private boolean isStatic = true;  // Enable chunk pre-batching

@Getter @Setter
private int zIndex = 0;
```

### Chunk Bounds Calculation

Each chunk needs AABB for culling:
```java
public float[] getChunkWorldBounds(int cx, int cy) {
    Transform t = getGameObject().getTransform();
    Vector3f pos = t.getPosition();
    
    float minX = pos.x + (cx * CHUNK_SIZE * tileSize);
    float minY = pos.y + (cy * CHUNK_SIZE * tileSize);
    float maxX = minX + (CHUNK_SIZE * tileSize);
    float maxY = minY + (CHUNK_SIZE * tileSize);
    
    return new float[]{minX, minY, maxX, maxY};
}
```

### Tile Data Expansion (Future)

Current `Tile` record:
```java
public record Tile(String name, Sprite sprite) {}
```

Future expansion path:
```java
public record Tile(
    String name, 
    Sprite sprite,
    boolean flipX,
    boolean flipY,
    float rotation,  // 0, 90, 180, 270
    int collisionMask
) {}
```

---

## Phase 4: Tilemap Rendering in BatchRenderer

**Goal**: Implement actual tilemapRenderer rendering with chunk-level culling and batching.

### Files to Modify

| File | Changes |
|------|---------|
| `rendering/RenderPipeline.java` | Add Tilemap dispatch branch |
| `rendering/renderers/BatchRenderer.java` | Add `drawTilemap(Tilemap)` method |
| `rendering/culling/CullingSystem.java` | Add `getVisibleChunks(Tilemap)` method |
| `rendering/culling/FrustumCuller.java` | Add chunk AABB intersection test |

### Rendering Flow

```
1. RenderPipeline receives Tilemap
2. CullingSystem.getVisibleChunks(tilemapRenderer) → List of (cx, cy) pairs
3. For each visible chunk:
   a. If static && chunk has cached batch → draw cached
   b. Else iterate tiles, batch sprites
4. Submit batch to GPU
```

### Chunk Batching Strategy

For static tilemaps:
- Pre-build vertex data per chunk on first render or when dirty
- Store in `Map<Long, ChunkBatch>` keyed by chunk coordinates
- Invalidate on tile change via `markChunkDirty(cx, cy)`

For dynamic tilemaps:
- Rebuild visible chunks each frame
- Still benefits from chunk-level culling

### CullingSystem Extension

```java
public List<long[]> getVisibleChunks(Tilemap tilemapRenderer) {
    List<long[]> visible = new ArrayList<>();
    for (Long key : tilemapRenderer.chunkKeys()) {
        int cx = (int)(key >> 32);
        int cy = key.intValue();
        float[] bounds = tilemapRenderer.getChunkWorldBounds(cx, cy);
        if (culler.isAABBVisible(bounds)) {
            visible.add(new long[]{cx, cy});
        }
    }
    return visible;
}
```

---

## Phase 5: Static Chunk Pre-batching (Optional)

**Goal**: Optimize static tilemaps by pre-computing GPU vertex data.

### Concept

When `isStatic = true`:
1. On first render of chunk, build vertex array
2. Store in VBO per chunk (or shared VBO with offsets)
3. On subsequent frames, just bind and draw
4. Invalidate via `markChunkDirty()` on tile modification

### Trade-offs

| Approach | Memory | CPU/frame | Flexibility |
|----------|--------|-----------|-------------|
| Per-frame batching | Low | Medium | High |
| Pre-baked chunks | Medium | Low | Requires dirty tracking |
| Hybrid (LRU cache) | Bounded | Adaptive | Balanced |

Recommendation: Start with per-frame batching (simpler), add pre-baking as optimization later.

---

## Testing Checklist

### Phase 1
- [ ] `SpriteRenderer` implements `Renderable`
- [ ] `Scene.getRenderers()` returns sorted list
- [ ] Existing sprite rendering unchanged

### Phase 2
- [ ] `RenderPipeline` uses `getRenderers()`
- [ ] instanceof dispatch works for `SpriteRenderer`
- [ ] No regression in render output

### Phase 3
- [ ] `Tilemap` implements `Renderable`
- [ ] `tileSize` defaults correctly
- [ ] Chunk bounds calculation correct for negative coords

### Phase 4
- [ ] Tilemap renders at correct zIndex relative to sprites
- [ ] Chunk culling works (only visible chunks rendered)
- [ ] Tile positions correct in world space

### Phase 5 (if implemented)
- [ ] Static chunks cached correctly
- [ ] Dirty flag invalidates cache
- [ ] Memory usage acceptable

---

## Open Questions

1. **Tile tinting**: Per-tile color modulation? Requires vec4 color in vertex data.
2. **Animated tiles**: Timer-based sprite swap? Managed by Tilemap or separate system?
3. **Multi-layer tilemaps**: Single Tilemap with layers, or multiple Tilemap components?
4. **Collision integration**: How does physics system query tile collision data?
