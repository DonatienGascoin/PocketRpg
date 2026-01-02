# PocketRPG Rendering Architecture Analysis

## Executive Summary

The current rendering system has **significant architectural fragmentation** across 4 different rendering contexts. This causes visual inconsistencies, code duplication, and maintainability issues. The Game Panel preview mode bug (zoomed/moved image) is a symptom of deeper architectural problems.

**Key Finding**: There are effectively 4 separate rendering pipelines that should share a unified core.

---

## 1. Current Architecture Map

### 1.1 Rendering Contexts

| Context | Renderer | Camera | Framebuffer | Used For |
|---------|----------|--------|-------------|----------|
| **Game Application** | `OpenGLRenderer` → `RenderPipeline` → `BatchRenderer` | `Camera` (per-scene) | Screen (default) | Standalone game |
| **Game Panel (Play)** | `PlayModeRenderer` → `OpenGLRenderer` → `RenderPipeline` | `Camera` (runtime scene) | Custom FBO | Editor play mode |
| **Game Panel (Preview)** | `GamePreviewRenderer` → `BatchRenderer` | `Camera` (preview) | Custom FBO | Editor stopped state |
| **Scene Panel** | `EditorSceneRenderer` → `BatchRenderer` | `EditorCamera` | `EditorFramebuffer` | Scene editing |
| **UI Designer** | ImGui DrawLists (no OpenGL batch) | Custom canvas coords | ImGui window | UI editing |

### 1.2 Class Hierarchy

```
RenderInterface (interface)
└── OpenGLRenderer (implements)
    ├── RenderPipeline (orchestrator)
    │   └── Renderer (base)
    │       └── BatchRenderer (extends)
    │           └── SpriteBatch (batching)
    └── OverlayRenderer (transitions)

Editor-specific (separate hierarchies):
├── EditorSceneRenderer (has-a BatchRenderer)
├── GamePreviewRenderer (has-a BatchRenderer)
├── PlayModeRenderer (has-a OpenGLRenderer)
├── EntityRenderer (stateless, uses SpriteBatch)
└── UIDesignerPanel (ImGui only)
```

---

## 2. Identified Problems

### 2.1 🔴 CRITICAL: Camera Inconsistency (Root Cause of Preview Bug)

**The zoom/position bug in Game Panel preview is caused by different camera implementations.**

#### Game Camera (`Camera.java`)
```java
// Uses ViewportConfig - shared resolution/scaling
public Camera(ViewportConfig viewport) { ... }

// View matrix applies CENTER OFFSET
float halfW = viewport.getGameWidth() / zoom / 2f;
float halfH = viewport.getGameHeight() / zoom / 2f;
viewMatrix.translate(halfW, halfH, 0);
viewMatrix.translate(-position.x, -position.y, 0);

// getWorldBounds() returns centered bounds
return new float[] {
    position.x - halfW, position.y - halfH,
    position.x + halfW, position.y + halfH
};
```

#### EditorCamera (Scene Panel)
```java
// Uses different viewport calculation
public Vector3f screenToWorld(float screenX, float screenY) { ... }
public Vector2f worldToScreen(float worldX, float worldY) { ... }

// Different matrix construction
public float[] getWorldBounds() { ... }
```

#### GamePreviewRenderer Camera
```java
// Creates its own Camera with ViewportConfig
previewCamera = new Camera(viewportConfig);

// Sets orthographic size from scene settings
previewCamera.setOrthographicSize(settings.getOrthographicSize());
```

**Problem**: `orthographicSize` in `SceneCameraSettings` may not match how the runtime camera interprets zoom/scale. The debug logs show:
```java
System.out.println("[GamePreview] Setting orthoSize to: " + targetOrthoSize);
System.out.println("[GamePreview] After set, orthoSize is: " + previewCamera.getOrthographicSize());
```

This suggests the orthographic size is being set but the resulting world bounds differ from play mode.

### 2.2 🟠 HIGH: Code Duplication

**Tilemap rendering is implemented 3 times:**

1. **RenderPipeline.renderTilemap()** → calls `BatchRenderer.drawTilemap()`
2. **EditorSceneRenderer.renderTilemap()** → duplicates chunk iteration logic
3. **GamePreviewRenderer.renderTilemap()** → copy of EditorSceneRenderer's logic

```java
// EditorSceneRenderer.java (lines ~100-130)
private void renderTilemap(TilemapRenderer tilemap, EditorCamera camera, float opacity) {
    float[] bounds = camera.getWorldBounds();
    // ... chunk calculation ...
    for (int cy = startCY; cy <= endCY; cy++) {
        for (int cx = startCX; cx <= endCX; cx++) {
            if (tilemap.hasChunk(cx, cy)) {
                visibleChunks.add(new long[]{cx, cy});
            }
        }
    }
    batchRenderer.drawTilemap(tilemap, visibleChunks, tint);
}

// GamePreviewRenderer.java - IDENTICAL CODE
private void renderTilemap(TilemapRenderer tilemap) {
    float[] bounds = previewCamera.getWorldBounds();
    // ... same chunk calculation ...
    batchRenderer.drawTilemap(tilemap, visibleChunks, tint);
}
```

**Entity rendering is also duplicated:**

Both `EditorSceneRenderer` and `GamePreviewRenderer` create their own `EntityRenderer` instance and call the same pattern.

### 2.3 🟠 HIGH: BatchRenderer Instance Proliferation

Each renderer creates its own `BatchRenderer`:

| Renderer | BatchRenderer Instance | Lifetime |
|----------|----------------------|----------|
| `OpenGLRenderer` | Created in `init()` via `RenderPipeline` | Game lifetime |
| `EditorSceneRenderer` | Created in `init()` | Editor lifetime |
| `GamePreviewRenderer` | Created in `init()` | Editor lifetime |
| `PlayModeRenderer` | Via nested `OpenGLRenderer` | Play session |

**Issue**: GPU resources are duplicated (VAOs, VBOs, shaders). Memory waste and potential for state conflicts.

### 2.4 🟡 MEDIUM: Inconsistent Entity Rendering

**Runtime path** (via `RenderPipeline`):
```java
// Uses Renderable interface and instanceof dispatch
for (Renderable renderable : scene.getRenderers()) {
    if (renderable instanceof SpriteRenderer sr) {
        renderer.drawSpriteRenderer(sr);
    } else if (renderable instanceof TilemapRenderer tr) {
        batchRenderer.drawTilemap(tr, visibleChunks);
    }
}
```

**Editor path** (via `EntityRenderer`):
```java
// Uses EditorEntity.getPreviewSprite()
for (EditorEntity entity : scene.getEntities()) {
    Sprite sprite = entity.getPreviewSprite();
    batch.draw(sprite, x, y, width, height, zIndex, tint);
}
```

**Problem**: Editor entities don't go through the same component resolution as runtime. When animations are added, `SpriteRenderer.getSprite()` will need to return animated frames, but `EditorEntity.getPreviewSprite()` won't automatically benefit.

### 2.5 🟡 MEDIUM: UI Designer Uses Different Tech Stack

`UIDesignerPanel` renders entirely through ImGui draw lists:

```java
// UIDesignerPanel.java
private void renderSprite(ImDrawList drawList, Object spriteObj, ...) {
    // Loads textures manually
    Sprite sprite = Assets.load(spritePath, Sprite.class);
    int textureId = sprite.getTexture().getTextureId();
    
    // Uses ImGui for rendering instead of SpriteBatch
    drawList.addImage(textureId, left, top, right, bottom, u0, v0, u1, v1, tintColor);
}
```

**Issue**: No z-ordering, no batching, different coordinate system (screen-space vs world-space). UI preview will look different from runtime if UI batching is added later.

---

## 3. Impact on Future Features

### 3.1 Animation System Impact

When implementing animations, you'll need to update sprites per-frame. Current architecture requires changes in:

| Component | Required Change |
|-----------|-----------------|
| `SpriteRenderer` | Add `getCurrentSprite()` that returns animated frame |
| `EntityRenderer` | Must call component's current sprite, not cached preview |
| `UIDesignerPanel` | Must poll animation state for UI animations |
| `GamePreviewRenderer` | Currently uses `EditorEntity.getPreviewSprite()` - won't animate |

**Risk**: With 4 rendering paths, animation updates must be applied in 4 places.

### 3.2 Post-Processing Impact

Currently only `PlayModeRenderer` supports post-processing:

```java
// PlayModeRenderer has full pipeline
renderSceneToFbo(scene);
applyPostProcessing();  // ping-pong FBOs

// GamePreviewRenderer has NO post-processing
// EditorSceneRenderer has NO post-processing
```

**Issue**: Preview won't match play mode if post-processing is significant.

---

## 4. Root Cause Diagram

```
                    ┌─────────────────────────────────────┐
                    │    SceneCameraSettings              │
                    │    - position: Vector2f             │
                    │    - orthographicSize: float        │
                    └─────────────────┬───────────────────┘
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           │                          │                          │
           ▼                          ▼                          ▼
    ┌─────────────┐           ┌─────────────┐            ┌─────────────┐
    │ EditorCamera│           │   Camera    │            │   Camera    │
    │ (Scene View)│           │ (Preview)   │            │ (Play Mode) │
    └──────┬──────┘           └──────┬──────┘            └──────┬──────┘
           │                         │                          │
           │ screenToWorld()         │ getWorldBounds()         │ getWorldBounds()
           │ uses viewport math      │ uses orthographicSize    │ uses scene camera
           │                         │                          │
           ▼                         ▼                          ▼
    ┌─────────────────┐       ┌─────────────────┐        ┌─────────────────┐
    │EditorSceneRender│       │GamePreviewRender│        │ RenderPipeline  │
    │  - own BatchRend│       │  - own BatchRend│        │  - BatchRenderer│
    │  - own EntityRnd│       │  - own EntityRnd│        │  - CullingSystem│
    └─────────────────┘       └─────────────────┘        └─────────────────┘
                                      │                          │
                                      │                          │
                    DIFFERENT RESULTS ◄──────────────────────────►
```

**The bug**: `GamePreviewRenderer` interprets `orthographicSize` differently than `PlayModeRenderer`'s runtime scene camera.

---

## 5. Proposed Unified Architecture

### 5.1 Core Principle: Single Rendering Backend

```
                         ┌────────────────────┐
                         │  UnifiedRenderer   │
                         │  - SpriteBatch     │
                         │  - EntityRenderer  │
                         │  - TilemapRenderer │
                         └─────────┬──────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
        ▼                          ▼                          ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│ SceneContext  │          │ GameContext   │          │ UIContext     │
│ (Editor View) │          │ (Game/Preview)│          │ (UI Designer) │
└───────────────┘          └───────────────┘          └───────────────┘
```

### 5.2 Unified Camera Interface

```java
public interface RenderCamera {
    Matrix4f getProjectionMatrix();
    Matrix4f getViewMatrix();
    float[] getWorldBounds();
    Vector2f worldToScreen(float worldX, float worldY);
    Vector3f screenToWorld(float screenX, float screenY);
}

// Implementations
public class GameCamera implements RenderCamera { ... }       // Runtime
public class EditorCamera implements RenderCamera { ... }     // Scene panel
public class PreviewCamera implements RenderCamera { ... }    // Wraps GameCamera for preview
```

### 5.3 Shared Rendering Core

```java
public class SceneRenderer {
    private final SpriteBatch batch;
    private final TilemapRenderer tilemapRenderer;
    private final EntityRenderer entityRenderer;
    
    // Single entry point for all rendering contexts
    public void render(RenderCamera camera, RenderTarget target, RenderableScene scene) {
        target.bind();
        target.clear();
        
        batch.beginWithMatrices(camera.getProjectionMatrix(), camera.getViewMatrix());
        
        // Unified tilemap rendering
        renderTilemaps(scene, camera);
        
        // Unified entity rendering
        renderEntities(scene);
        
        batch.end();
        target.unbind();
    }
}
```

### 5.4 RenderableScene Abstraction

```java
public interface RenderableScene {
    List<TilemapLayer> getTilemapLayers();
    List<? extends RenderableEntity> getEntities();
}

// EditorScene implements RenderableScene
// Runtime Scene implements RenderableScene (via adapter)
```

---

## 6. Specific Bug Fix: Preview vs Play Mode

### 6.1 Immediate Fix (Low Risk)

Ensure `GamePreviewRenderer` uses the **exact same camera math** as runtime:

```java
// GamePreviewRenderer.java
public void render(EditorScene scene) {
    SceneCameraSettings settings = scene.getCameraSettings();
    
    // CURRENT (buggy):
    // previewCamera.setOrthographicSize(settings.getOrthographicSize());
    
    // FIX: Use the same formula as runtime Camera
    float zoom = settings.getOrthographicSize() > 0 
        ? gameConfig.getGameHeight() / (2f * settings.getOrthographicSize())
        : 1f;
    previewCamera.setZoom(zoom);
    previewCamera.setPosition(settings.getPosition().x, settings.getPosition().y);
}
```

**Explanation**: The runtime `Camera` uses `zoom` to scale the view, but `orthographicSize` is a different unit (half-height in world units). The conversion must match.

### 6.2 Verify Camera.getWorldBounds()

Check if `Camera.getWorldBounds()` returns consistent bounds:

```java
// Runtime path (in RenderPipeline)
Camera activeCamera = scene.getCamera();
float[] bounds = activeCamera.getWorldBounds();  // [left, bottom, right, top]

// Preview path (in GamePreviewRenderer)
Camera previewCamera = ...;
float[] bounds = previewCamera.getWorldBounds();  // Must match above!
```

If these differ, the visible chunks and entity positions will be wrong.

---

## 7. Refactoring Roadmap

### Phase 1: Unlimited Batching (Foundation)
1. Add `SpriteSubmission` and `ChunkSubmission` record classes
2. Add submission lists to `SpriteBatch`
3. Modify `submit()`/`submitChunk()` to buffer
4. Implement `processBatches()` with global sort + auto-flush
5. Update `end()` to call `processBatches()`

### Phase 2: Unify Camera (Fixes preview bug)
1. Create `RenderCamera` interface
2. Make `Camera` and `EditorCamera` implement it
3. Create `PreviewCamera` wrapper with correct conversion
4. Replace `GamePreviewRenderer`'s camera setup

### Phase 3: Extract Shared Rendering Logic
1. Move tilemap chunk calculation to `CullingSystem`
2. Create `SceneRenderingBackend` shared by editor and game
3. Remove duplicated `BatchRenderer` instances

### Phase 4: Unified Entity Rendering
1. Create `RenderableEntity` interface
2. Make `EditorEntity` provide `getCurrentSprite()` (supports animation)
3. Single `EntityRenderer` for both runtime and editor
4. Animation automatically works in all contexts

### Phase 5: UI Rendering Alignment
1. Create `UIBatchRenderer` using `SpriteBatch`
2. `UIDesignerPanel` renders to texture, displays via `ImGui.image()`
3. Match coordinate systems between preview and runtime

---

## 8. Animation System Preparation

To ensure animations work automatically across all contexts:

### Current Sprite Resolution
```java
// SpriteRenderer
Sprite sprite = this.sprite;  // Static field

// EditorEntity
Sprite sprite = this.previewSprite;  // Cached at creation
```

### Required Change
```java
// SpriteRenderer (with animation support)
public Sprite getCurrentSprite() {
    if (animator != null && animator.isPlaying()) {
        return animator.getCurrentFrame();
    }
    return this.sprite;
}

// EditorEntity (with live preview)
public Sprite getCurrentSprite() {
    // Delegate to component if animation exists
    var spriteComp = getComponentByType("SpriteRenderer");
    if (spriteComp != null && spriteComp.has("animator")) {
        return resolveAnimatedSprite(spriteComp);
    }
    return this.previewSprite;
}
```

### EntityRenderer Update
```java
// Instead of:
Sprite sprite = entity.getPreviewSprite();

// Use:
Sprite sprite = entity.getCurrentSprite();  // Supports animation
```

---

## 10. Summary of Issues by Priority

| Priority | Issue | Impact | Fix Complexity |
|----------|-------|--------|----------------|
| 🔴 Critical | Fixed batch size overflow | Large scenes crash | Medium |
| 🔴 Critical | Static/dynamic z-order broken | Static always behind dynamic | Low |
| 🔴 Critical | Camera math inconsistency | Preview doesn't match play mode | Medium |
| 🟠 High | Tilemap rendering duplication | Maintenance burden, bugs | Medium |
| 🟠 High | Multiple BatchRenderer instances | Memory waste, potential conflicts | High |
| 🟡 Medium | Entity rendering inconsistency | Animation won't work in preview | Medium |
| 🟡 Medium | UI Designer uses ImGui only | UI preview inaccurate | High |
| 🟢 Low | No post-processing in preview | Minor visual difference | Low |

---

## 11. Recommended Next Steps

1. **Immediate**: Implement unlimited batching in `SpriteBatch` (prevents crashes, enables large scenes)
2. **Short-term**: Create `RenderCamera` interface and fix preview camera math
3. **Medium-term**: Extract shared `SceneRenderingCore` class
4. **Long-term**: Unified entity rendering with animation support

---

## Appendix A: Unlimited Vertex Batching Details

### A.1 Problem

`SpriteBatch` has a fixed buffer size. Large scenes overflow:
```java
private final int maxBatchSize;  // From RenderingConfig
private final FloatBuffer vertexBuffer;  // Fixed at init
```

### A.2 Static/Dynamic Batching Bug

**Current behavior** (`SpriteBatch.end()`):
```java
// Renders static first, then dynamic
if (!staticItems.isEmpty()) {
    flushItems(staticItems);  // All static rendered
}
if (!dynamicItems.isEmpty()) {
    flushItems(dynamicItems);  // All dynamic rendered ON TOP
}
```

**Problem**: A static sprite at zIndex=100 renders BEHIND a dynamic sprite at zIndex=1.

**Fix**: Merge static and dynamic into single sorted list:
```java
private void processBatches() {
    List<Submission> all = new ArrayList<>();
    all.addAll(staticSubmissions);
    all.addAll(dynamicSubmissions);
    
    // Sort by zIndex, then static/dynamic (dynamic wins ties)
    all.sort((a, b) -> {
        int zCmp = Integer.compare(a.zIndex, b.zIndex);
        if (zCmp != 0) return zCmp;
        // Same zIndex: dynamic (false=0) after static (true=1)
        return Boolean.compare(b.isStatic, a.isStatic);
    });
    
    // Process in unified order
    for (Submission sub : all) {
        if (spriteCount >= maxBatchSize) flush();
        addToBatch(sub);
    }
    flush();
}
```

**Result**: Correct z-ordering across static/dynamic boundary. Same zIndex → dynamic on top.

### A.3 Solution: Deferred Submission with Auto-Flush

**Key Change**: Submit buffers sprites, `end()` sorts globally, then flushes in batches.

```java
public class SpriteBatch {
    // Submission buffer (unbounded)
    private final List<SpriteSubmission> submissions = new ArrayList<>();
    private final List<ChunkSubmission> chunkSubmissions = new ArrayList<>();
    
    // Existing vertex buffer stays fixed size
    private final FloatBuffer vertexBuffer;
    
    public void submit(SpriteRenderer sr, Vector4f tint) {
        // Buffer instead of immediate batch
        submissions.add(new SpriteSubmission(sr, tint));
    }
    
    public void submitChunk(TilemapRenderer tm, int cx, int cy, Vector4f tint) {
        chunkSubmissions.add(new ChunkSubmission(tm, cx, cy, tint));
    }
    
    public void end() {
        processBatches();  // Sort, batch, render
        submissions.clear();
        chunkSubmissions.clear();
    }
    
    private void processBatches() {
        // 1. Combine all submissions
        List<Submission> all = new ArrayList<>();
        all.addAll(submissions);
        all.addAll(chunkSubmissions);
        
        // 2. Global sort by zIndex
        all.sort(Comparator.comparingInt(Submission::getZIndex));
        
        // 3. Process in order, auto-flush when buffer full
        for (Submission sub : all) {
            if (spriteCount >= maxBatchSize) {
                flush();  // Render current batch, reset buffer
            }
            addToBatch(sub);
        }
        flush();  // Final batch
    }
}
```

### A.4 Z-Index Correctness

**Critical**: Without global sort, batch boundaries break z-order:
```
Batch 1: sprites 0-9999 (zIndex 0-100) → rendered
Batch 2: sprites 10000+ (zIndex 0-100) → rendered ON TOP of batch 1
```

Global sort ensures correct order regardless of batch boundaries.

### A.5 Performance

| Sprites | Sort Cost | Draw Calls | Memory Overhead |
|---------|-----------|------------|-----------------|
| 10,000 | ~0.3ms | 1 | ~320KB |
| 50,000 | ~1.5ms | 5 | ~1.6MB |
| 100,000 | ~3ms | 10 | ~3.2MB |

Acceptable for any realistic scene.

---

## Appendix A.2: UI Designer Rendering Clarification

### 9.1 Current Architecture
```
UIDesignerPanel
├── Rendering: ImGui DrawList (addImage, addRect, etc.)
├── Input: ImGui (getMousePos, isMouseClicked, etc.)
└── Logic: Java (hitTest, handleDrag, calculateBounds)
```

### 9.2 Proposed Architecture
```
UIDesignerPanel
├── Rendering: SpriteBatch → Texture → ImGui.image()
├── Input: ImGui (unchanged)
└── Logic: Java (unchanged)
```

### 9.3 Why Interaction Still Works

All interaction logic is in Java code, not ImGui:
- `hitTestHandles()` - calculates handle positions from component data
- `handleMoveDrag()` - updates UITransform.offset based on mouse delta
- `handleResizeDrag()` - updates width/height based on handle drag

ImGui only provides:
- `ImGui.getMousePos()` - mouse screen position
- `ImGui.isMouseClicked()` - click state
- `ImGui.isMouseDragging()` - drag state

These work identically whether the visuals come from `drawList.addImage()` or a rendered texture displayed via `ImGui.image()`.

---

## Appendix B: File Reference

| File | Purpose | Issues |
|------|---------|--------|
| `SpriteBatch.java` | Core batching | ✅ Well-designed |
| `BatchRenderer.java` | Batch orchestration | ✅ Good |
| `RenderPipeline.java` | Runtime scene rendering | ✅ Good |
| `OpenGLRenderer.java` | Runtime renderer | ✅ Good |
| `EditorSceneRenderer.java` | Scene panel rendering | ⚠️ Duplicates tilemap logic |
| `GamePreviewRenderer.java` | Preview rendering | ⚠️ Camera bug, duplicates code |
| `PlayModeRenderer.java` | Play mode rendering | ✅ Uses full pipeline |
| `EntityRenderer.java` | Entity rendering | ⚠️ Static sprite only |
| `UIDesignerPanel.java` | UI preview | ⚠️ Different rendering tech |
| `Camera.java` | Runtime camera | ✅ Good |
| `EditorCamera.java` | Editor camera | ⚠️ Different math |

## Appendix B: Coordinate Systems

| Context | Origin | Y Direction | Units |
|---------|--------|-------------|-------|
| Game (World) | Configurable | Up (+Y up) | World units |
| Scene Panel | Center of view | Up (+Y up) | World units |
| UI Canvas | Top-left | Down (+Y down) | Pixels |
| ImGui | Top-left | Down (+Y down) | Pixels |
