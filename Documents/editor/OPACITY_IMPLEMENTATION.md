# Layer Opacity Implementation Plan

## Overview
Implement per-sprite opacity support to enable layer dimming in the editor. This allows the SELECTED_DIMMED visibility mode to render non-active layers at reduced opacity (default 0.3f).

## Architecture Decision
**Approach:** Add RGBA vertex colors to the batch rendering system.

**Benefits:**
- Enables per-sprite opacity control
- Future-proof for per-sprite tinting/color modulation
- Standard approach in 2D engines
- Minimal performance impact

---

## File Changes

### 1. SpriteBatch.java

#### Current Vertex Format
```java
// Per vertex: [posX, posY, u, v] = 4 floats
// Per sprite: 6 vertices = 24 floats
```

#### New Vertex Format
```java
// Per vertex: [posX, posY, u, v, r, g, b, a] = 8 floats
// Per sprite: 6 vertices = 48 floats
```

#### Method Signature Changes

**Add opacity parameter to submit methods:**
```java
// Current
public void submit(SpriteRenderer spriteRenderer)
public void submitChunk(TilemapRenderer tilemap, int cx, int cy)

// New
public void submit(SpriteRenderer spriteRenderer, float opacity)
public void submitChunk(TilemapRenderer tilemap, int cx, int cy, float opacity)
```

**Add backward-compatible overloads:**
```java
public void submit(SpriteRenderer spriteRenderer) {
    submit(spriteRenderer, 1.0f);
}

public void submitChunk(TilemapRenderer tilemap, int cx, int cy) {
    submitChunk(tilemap, cx, cy, 1.0f);
}
```

#### Implementation Changes

**Update constants:**
```java
// Old
private static final int FLOATS_PER_VERTEX = 4; // pos(2) + uv(2)
private static final int FLOATS_PER_SPRITE = 24; // 6 vertices * 4

// New
private static final int FLOATS_PER_VERTEX = 8; // pos(2) + uv(2) + color(4)
private static final int FLOATS_PER_SPRITE = 48; // 6 vertices * 8
```

**Modify buildVertexData() - add color data:**
```java
private void buildVertexData(Sprite sprite, Transform transform, 
                             SpriteRenderer renderer, float opacity) {
    // ... existing position/UV calculations ...
    
    // Color/opacity (same for all 6 vertices)
    float r = 1.0f;  // White (future: tint color)
    float g = 1.0f;
    float b = 1.0f;
    float a = opacity;  // Apply opacity
    
    // For each vertex, after UV coordinates:
    vertexData[offset++] = r;
    vertexData[offset++] = g;
    vertexData[offset++] = b;
    vertexData[offset++] = a;
}
```

**Update VAO setup in init():**
```java
// Position attribute (location 0)
glVertexAttribPointer(0, 2, GL_FLOAT, false, 8 * Float.BYTES, 0);

// UV attribute (location 1)
glVertexAttribPointer(1, 2, GL_FLOAT, false, 8 * Float.BYTES, 2 * Float.BYTES);

// Color attribute (location 2) - NEW
glEnableVertexAttribArray(2);
glVertexAttribPointer(2, 4, GL_FLOAT, false, 8 * Float.BYTES, 4 * Float.BYTES);
```

---

### 2. batch_sprite.glsl

#### Vertex Shader Changes

**Add color attribute and varying:**
```glsl
layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec4 aColor;  // NEW

out vec2 TexCoord;
out vec4 Color;  // NEW

void main() {
    gl_Position = projection * view * model * vec4(aPos, 0.0, 1.0);
    TexCoord = aTexCoord;
    Color = aColor;  // Pass through to fragment shader
}
```

#### Fragment Shader Changes

**Use color in final output:**
```glsl
in vec2 TexCoord;
in vec4 Color;  // NEW

out vec4 FragColor;

uniform sampler2D textureSampler;

void main() {
    vec4 texColor = texture(textureSampler, TexCoord);
    FragColor = texColor * Color;  // Multiply by vertex color (includes opacity)
}
```

---

### 3. BatchRenderer.java

#### Method Signature Changes

**Update tilemap rendering:**
```java
// Current
public void drawTilemap(TilemapRenderer tilemap, List<long[]> visibleChunks)

// New (with backward compatibility)
public void drawTilemap(TilemapRenderer tilemap, List<long[]> visibleChunks, float opacity) {
    if (tilemap == null || visibleChunks == null || visibleChunks.isEmpty()) {
        return;
    }
    
    for (long[] chunkCoord : visibleChunks) {
        int cx = (int) chunkCoord[0];
        int cy = (int) chunkCoord[1];
        
        batch.submitChunk(tilemap, cx, cy, opacity);  // Pass opacity
        
        if (tilemap.isStatic()) {
            tilemap.clearChunkDirty(cx, cy);
        }
    }
}

// Backward-compatible overload for game rendering
public void drawTilemap(TilemapRenderer tilemap, List<long[]> visibleChunks) {
    drawTilemap(tilemap, visibleChunks, 1.0f);
}
```

**Update sprite rendering:**
```java
// Current
@Override
public void drawSpriteRenderer(SpriteRenderer spriteRenderer)

// Add overload with opacity
public void drawSpriteRenderer(SpriteRenderer spriteRenderer, float opacity) {
    if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
        return;
    }
    batch.submit(spriteRenderer, opacity);
}

// Keep existing method for backward compatibility
@Override
public void drawSpriteRenderer(SpriteRenderer spriteRenderer) {
    drawSpriteRenderer(spriteRenderer, 1.0f);
}
```

---

### 4. EditorSceneRenderer.java

#### Update renderTilemap method

**Current:**
```java
private void renderTilemap(TilemapRenderer tilemap, EditorCamera camera, float opacity) {
    // ... culling code ...
    
    // TODO: Apply opacity when rendering (requires BatchRenderer modification)
    if (!visibleChunks.isEmpty()) {
        batchRenderer.drawTilemap(tilemap, visibleChunks);
    }
}
```

**New:**
```java
private void renderTilemap(TilemapRenderer tilemap, EditorCamera camera, float opacity) {
    // ... culling code ...
    
    if (!visibleChunks.isEmpty()) {
        batchRenderer.drawTilemap(tilemap, visibleChunks, opacity);  // Pass opacity
    }
}
```

#### Future: Update for individual sprites

When rendering non-tilemap entities with opacity:
```java
private void renderSpriteRenderer(SpriteRenderer sr, float opacity) {
    if (cullingSystem.shouldRender(sr)) {
        batchRenderer.drawSpriteRenderer(sr, opacity);
    }
}
```

---

### 5. RenderPipeline.java

#### No Changes Required

The game rendering pipeline already calls the backward-compatible methods:
```java
// This still works - uses default opacity of 1.0f
batchRenderer.drawTilemap(tilemapRenderer, visibleChunks);
```

---

## Implementation Order

1. **Update shader first** (batch_sprite.glsl)
   - Add color attribute
   - Test with hardcoded white color
   
2. **Update SpriteBatch constants** (FLOATS_PER_VERTEX, etc.)
   
3. **Update SpriteBatch VAO setup** (add color attribute)
   
4. **Update SpriteBatch.buildVertexData()** (add color data to vertices)
   
5. **Add opacity parameters to SpriteBatch.submit methods**
   
6. **Update BatchRenderer method signatures**
   
7. **Update EditorSceneRenderer to pass opacity**
   
8. **Test with SELECTED_DIMMED mode**

---

## Testing Checklist

- [ ] Game rendering still works (full opacity)
- [ ] Editor ALL mode works (full opacity)
- [ ] Editor SELECTED_ONLY mode works
- [ ] Editor SELECTED_DIMMED mode shows dimmed non-active layers
- [ ] Dimmed opacity adjustable (EditorScene.dimmedOpacity)
- [ ] No visual artifacts (z-fighting, flickering)
- [ ] Performance unchanged (vertex buffer is larger but still efficient)

---

## Future Enhancements

With RGBA vertex colors in place, future features become trivial:

1. **Per-sprite tinting** - Modify color components (r, g, b)
2. **Flash effects** - Temporarily change sprite color
3. **Damage indicators** - Red tint on hit
4. **Status effects** - Color coding (poison=green, frozen=blue)
5. **Selection highlights** - Tint selected sprites

All without additional vertex attributes or shader changes.
