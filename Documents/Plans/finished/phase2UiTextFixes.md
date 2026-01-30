# Phase 2: UIText Alignment and Font Rendering Fixes

## Problems

1. **UIText alignment ignored in UIDesigner** - Text ignores CENTER/MIDDLE alignment settings
2. **Font mismatch in UIPreviewRenderer** - Uses ImGui's default font instead of game font

---

## Issue 1: UIText Alignment

### Debug Output Analysis

```
naturalWidth=127.5, naturalHeight=60.0
boxWidth=128.0, boxHeight=40.0
horizontalAlignment=RIGHT
verticalAlignment=MIDDLE
calculateVerticalStart: boxY=440.0, boxHeight=40.0, textHeight=60.0 -> result=430.0
```

### Finding

**The alignment math is CORRECT.** The issue is that `naturalHeight=60` but `boxHeight=40` - the text is TALLER than the container.

- Vertical alignment: `boxY + (boxHeight - textHeight) / 2 = 440 + (40 - 60) / 2 = 430`
- Result = 430, which is 10 pixels ABOVE the box (box starts at Y=440)
- Text overflows the container vertically, making it appear misaligned

### Solutions

1. **Enable autoFit** - Scale text to fit within bounds
2. **Increase box height** - Make UITransform height >= naturalHeight
3. **Clip to bounds** - Add scissor rect to prevent overflow (cosmetic only)
4. **Use smaller font** - Reduce font size to fit

### Recommended Implementation

Add visual debugging to show text bounds in UIDesigner:

```java
// In UIDesignerRenderer, after rendering text:
if (showTextBounds) {
    uiBackend.drawRectOutline(x, y, width, height, DEBUG_COLOR, 1f, zIndex + 0.1f);
}
```

---

## Issue 2: UIPreviewRenderer Font Rendering

### Problem

`UIPreviewRenderer.java` line 230:
```java
drawList.addText(lineX, lineY, color, line);  // Uses ImGui's default font!
```

The `Font` object is loaded via `loadFontFromComponent()` but only used for width calculations. Actual rendering uses ImGui's built-in font.

### Solution: Replace with RenderPipeline

Replace UIPreviewRenderer's custom ImGui rendering with the same `RenderPipeline` used by the game.

### Architecture

```
GamePanel / UIPreviewPanel
    |
    v
RenderPipeline.render(scene)  <-- Same pipeline as game runtime
    |
    v
Framebuffer texture
    |
    v
drawList.addImage(textureId, ...)
```

### Implementation

```java
public class UIPreviewPanel {
    private EditorFramebuffer previewFramebuffer;
    private RenderPipeline renderPipeline;

    public void render(EditorScene scene, ImDrawList drawList,
                       float viewportX, float viewportY, float scale) {
        int width = (int)(gameConfig.getGameWidth() * scale);
        int height = (int)(gameConfig.getGameHeight() * scale);

        // Initialize/resize framebuffer
        if (previewFramebuffer == null || needsResize(width, height)) {
            previewFramebuffer = new EditorFramebuffer(width, height);
            previewFramebuffer.init();
        }

        // Render scene using full pipeline
        previewFramebuffer.bind();
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT);

        renderPipeline.render(scene);  // Uses same code path as game

        previewFramebuffer.unbind();

        // Display in ImGui
        int textureId = previewFramebuffer.getTextureId();
        drawList.addImage(textureId, viewportX, viewportY,
                          viewportX + width, viewportY + height,
                          0, 1, 1, 0);  // Flipped UVs for OpenGL
    }
}
```

### Benefits

- Exact visual match with game runtime
- Font rendering uses actual game fonts
- All UI effects (shadows, tints) rendered correctly
- No duplicate rendering code to maintain

---

## Files to Modify

1. `UIPreviewRenderer.java` - Replace with RenderPipeline-based rendering
2. `UIText.java` - **DONE**: Changed alignment to pivot-based for CENTER/MIDDLE

---

## Issue 1 Fix: Pivot-Based Alignment (Implemented)

**Change:** CENTER/MIDDLE alignment now centers text at the pivot point, not within box bounds.

**Files changed:**
- `UIText.java`:
  - Added `currentPivotX`, `currentPivotY` instance variables
  - `renderInternal()` stores pivot at start
  - `calculateHorizontalStart()`: CENTER now uses `pivotX - lineWidth / 2`
  - `calculateVerticalStart()`: MIDDLE now uses `pivotY - textHeight / 2`

**Behavior:**
- LEFT/RIGHT/TOP/BOTTOM: Align to box edges (unchanged)
- CENTER: Text center at pivotX
- MIDDLE: Text center at pivotY

**Example with 128x40 box, pivot (0.5, 0.5), text 127.5x60:**
- pivotX = boxX + 64, pivotY = boxY + 20
- CENTER: text starts at pivotX - 63.75 = boxX + 0.25
- MIDDLE: text starts at pivotY - 30 = boxY - 10 (overflows upward)

---

## Verification

1. Create UIText element in UI Designer
2. Set horizontalAlignment = CENTER, verticalAlignment = MIDDLE
3. Verify text appears centered (or understand why it overflows if box is too small)
4. Create UIText with a custom Font asset
5. Verify font in Preview panel matches the game font
