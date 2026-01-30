# Scene View Rendering

## Problems to Solve

1. **Visual distinction**: Triggers look the same as other collision types (just colored squares)
2. **Missing metadata warning**: No visual indication when a trigger tile lacks configuration
3. **Selection highlight**: Need to show which trigger is currently selected

---

## Enhanced CollisionOverlayRenderer

**File**: `src/main/java/com/pocket/rpg/editor/rendering/CollisionOverlayRenderer.java`

### Changes

1. Render icons for trigger tiles (instead of just color)
2. Render warning icon for unconfigured triggers
3. Render selection highlight for selected trigger

### Updated Implementation

```java
public class CollisionOverlayRenderer {

    @Getter @Setter private boolean visible = true;
    @Getter @Setter private float opacity = 0.4f;
    @Getter @Setter private int elevation = 0;
    @Getter @Setter private boolean showIcons = true;  // NEW
    @Getter @Setter private TileCoord selectedTrigger;  // NEW

    @Setter private TriggerDataMap triggerDataMap;  // NEW

    private float selectionPulse = 0f;  // For animated highlight

    // ... viewport fields ...

    public void render(CollisionMap collisionMap, EditorCamera camera) {
        if (!visible || collisionMap == null) return;

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(viewportX, viewportY,
                              viewportX + viewportWidth,
                              viewportY + viewportHeight, true);

        // Update animation
        selectionPulse += 0.08f;

        // Get visible tile bounds
        float[] worldBounds = camera.getWorldBounds();
        int minTileX = (int) Math.floor(worldBounds[0]);
        int minTileY = (int) Math.floor(worldBounds[1]);
        int maxTileX = (int) Math.ceil(worldBounds[2]);
        int maxTileY = (int) Math.ceil(worldBounds[3]);

        // First pass: render all tiles
        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                CollisionType type = collisionMap.get(tileX, tileY, elevation);
                if (type == CollisionType.NONE) continue;

                renderTile(drawList, camera, tileX, tileY, type);
            }
        }

        // Second pass: render icons on top (for triggers)
        if (showIcons) {
            for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
                for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                    CollisionType type = collisionMap.get(tileX, tileY, elevation);
                    if (type.requiresMetadata()) {
                        renderTriggerIcon(drawList, camera, tileX, tileY, type);
                    }
                }
            }
        }

        // Third pass: render selection highlight
        if (selectedTrigger != null && selectedTrigger.elevation() == elevation) {
            renderSelectionHighlight(drawList, camera);
        }

        drawList.popClipRect();
    }

    /**
     * Renders a single collision tile (colored rectangle).
     */
    private void renderTile(ImDrawList drawList, EditorCamera camera,
                            int tileX, int tileY, CollisionType type) {
        Vector2f bottomLeft = camera.worldToScreen(tileX, tileY);
        Vector2f topRight = camera.worldToScreen(tileX + 1, tileY + 1);

        float x1 = viewportX + bottomLeft.x;
        float y1 = viewportY + topRight.y;
        float x2 = viewportX + topRight.x;
        float y2 = viewportY + bottomLeft.y;

        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);

        float[] color = type.getOverlayColor();
        int fillColor = ImGui.colorConvertFloat4ToU32(
            color[0], color[1], color[2], color[3] * opacity
        );

        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        // Border
        int borderColor = ImGui.colorConvertFloat4ToU32(
            color[0] * 0.7f, color[1] * 0.7f, color[2] * 0.7f, opacity * 0.8f
        );
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.0f);
    }

    /**
     * Renders icon for trigger tiles.
     */
    private void renderTriggerIcon(ImDrawList drawList, EditorCamera camera,
                                    int tileX, int tileY, CollisionType type) {
        Vector2f bottomLeft = camera.worldToScreen(tileX, tileY);
        Vector2f topRight = camera.worldToScreen(tileX + 1, tileY + 1);

        float minX = viewportX + Math.min(bottomLeft.x, topRight.x);
        float maxX = viewportX + Math.max(bottomLeft.x, topRight.x);
        float minY = viewportY + Math.min(bottomLeft.y, topRight.y);
        float maxY = viewportY + Math.max(bottomLeft.y, topRight.y);

        float tileWidth = maxX - minX;
        float tileHeight = maxY - minY;
        float centerX = minX + tileWidth / 2;
        float centerY = minY + tileHeight / 2;

        // Check if trigger is configured
        boolean isConfigured = false;
        if (triggerDataMap != null) {
            isConfigured = triggerDataMap.has(tileX, tileY, elevation);
        }

        // Determine icon to show
        String icon = type.getIcon();
        if (!isConfigured) {
            // Show warning icon for unconfigured triggers
            icon = MaterialIcons.Warning;
        }

        if (icon == null) return;

        // Calculate font size based on tile size
        float fontSize = Math.min(tileWidth, tileHeight) * 0.6f;
        fontSize = Math.max(fontSize, 12); // Minimum readable size
        fontSize = Math.min(fontSize, 32); // Maximum size

        // Icon color
        int iconColor;
        if (!isConfigured) {
            // Warning: orange/yellow
            iconColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.2f, 1.0f);
        } else {
            // Normal: white with slight transparency
            iconColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.9f);
        }

        // Draw icon centered in tile
        // Note: ImGui text rendering is top-left aligned, need to offset
        float textX = centerX - fontSize / 2;
        float textY = centerY - fontSize / 2;

        // Draw shadow for better visibility
        int shadowColor = ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.5f);
        drawList.addText(fontSize, textX + 1, textY + 1, shadowColor, icon);

        // Draw icon
        drawList.addText(fontSize, textX, textY, iconColor, icon);

        // For unconfigured triggers, also draw small "!" badge
        if (!isConfigured && type.getIcon() != null) {
            // Draw the type icon smaller, top-left
            float smallSize = fontSize * 0.5f;
            float typeX = minX + 2;
            float typeY = minY + 2;

            int typeColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.7f);
            drawList.addText(smallSize, typeX, typeY, typeColor, type.getIcon());
        }
    }

    /**
     * Renders animated selection highlight for selected trigger.
     */
    private void renderSelectionHighlight(ImDrawList drawList, EditorCamera camera) {
        int x = selectedTrigger.x();
        int y = selectedTrigger.y();

        Vector2f bottomLeft = camera.worldToScreen(x, y);
        Vector2f topRight = camera.worldToScreen(x + 1, y + 1);

        float minX = viewportX + Math.min(bottomLeft.x, topRight.x);
        float maxX = viewportX + Math.max(bottomLeft.x, topRight.x);
        float minY = viewportY + Math.min(bottomLeft.y, topRight.y);
        float maxY = viewportY + Math.max(bottomLeft.y, topRight.y);

        // Pulsing alpha
        float pulse = 0.5f + 0.5f * (float) Math.sin(selectionPulse);

        // Yellow selection color
        int fillColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 0.0f, 0.2f * pulse);
        int borderColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 0.0f, 0.8f * pulse);

        // Draw filled highlight
        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        // Draw thick border
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 3.0f);

        // Draw corner markers
        float markerSize = 6;
        int markerColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, pulse);

        // Top-left
        drawList.addLine(minX, minY, minX + markerSize, minY, markerColor, 2);
        drawList.addLine(minX, minY, minX, minY + markerSize, markerColor, 2);

        // Top-right
        drawList.addLine(maxX, minY, maxX - markerSize, minY, markerColor, 2);
        drawList.addLine(maxX, minY, maxX, minY + markerSize, markerColor, 2);

        // Bottom-left
        drawList.addLine(minX, maxY, minX + markerSize, maxY, markerColor, 2);
        drawList.addLine(minX, maxY, minX, maxY - markerSize, markerColor, 2);

        // Bottom-right
        drawList.addLine(maxX, maxY, maxX - markerSize, maxY, markerColor, 2);
        drawList.addLine(maxX, maxY, maxX, maxY - markerSize, markerColor, 2);
    }
}
```

---

## Icon Reference

| CollisionType | Icon | Codepoint | Configured | Unconfigured |
|---------------|------|-----------|------------|--------------|
| WARP | Exit arrow | `\uE879` | White exit icon | Orange warning |
| DOOR | Door | `\uEFFC` | White door icon | Orange warning |
| STAIRS_UP | Up arrow | `\uE5D8` | White up arrow | Orange warning |
| STAIRS_DOWN | Down arrow | `\uE5DB` | White down arrow | Orange warning |
| SCRIPT_TRIGGER | Code | `\uE86F` | White code icon | Orange warning |
| (warning) | Warning | `\uE002` | - | Orange warning triangle |

---

## Visual Examples

### Configured Trigger
```
┌─────────────┐
│             │
│      ⬆      │  ← White exit/arrow icon
│             │
└─────────────┘
   Purple fill
```

### Unconfigured Trigger
```
┌─────────────┐
│ ⬆           │  ← Small type icon (top-left)
│      ⚠      │  ← Large orange warning icon (center)
│             │
└─────────────┘
   Purple fill
```

### Selected Trigger
```
╔═════════════╗  ← Pulsing yellow border (thick)
║  ┌───────┐  ║
║  │   ⬆   │  ║  ← Normal icon
║  └───────┘  ║
╚═════════════╝  ← Corner markers
    + Yellow tint overlay
```

---

## Integration with Trigger Selection

When user selects a trigger (from list or scene click):

```java
// In EditorToolController or similar

public void selectTrigger(TileCoord coord) {
    // Update overlay renderer
    collisionOverlayRenderer.setSelectedTrigger(coord);

    // Update trigger list selection
    triggerListSection.setSelectedTrigger(coord);

    // Update inspector
    triggerInspector.setSelectedTile(coord);

    // Optionally center camera
    if (shouldCenterOnSelect) {
        editorCamera.centerOn(coord.x() + 0.5f, coord.y() + 0.5f);
    }
}

public void clearTriggerSelection() {
    collisionOverlayRenderer.setSelectedTrigger(null);
    triggerListSection.setSelectedTrigger(null);
    triggerInspector.setSelectedTile(null);
}
```

---

## Click Detection in Scene View

When clicking in scene view in collision mode, detect trigger tiles:

```java
// In SceneViewPanel or CollisionSelectTool

private void onSceneClick(float worldX, float worldY, int button) {
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

    int tileX = (int) Math.floor(worldX);
    int tileY = (int) Math.floor(worldY);
    int tileElevation = collisionOverlayRenderer.getElevation();

    CollisionType type = collisionMap.get(tileX, tileY, tileElevation);

    if (type.requiresMetadata()) {
        // Select this trigger
        selectTrigger(new TileCoord(tileX, tileY, tileElevation));
    } else {
        // Clear trigger selection
        clearTriggerSelection();
    }
}
```

---

## Performance Considerations

1. **Icon rendering**: Only render icons for visible tiles (already culled)
2. **Font caching**: ImGui caches font glyphs, so repeated icon rendering is fast
3. **Selection animation**: Single float increment per frame, negligible cost
4. **TriggerDataMap lookup**: O(1) HashMap lookup per trigger tile

---

## Summary of Files

| File | Type | Description |
|------|------|-------------|
| `editor/rendering/CollisionOverlayRenderer.java` | MODIFY | Add icons, warnings, selection |
| `editor/core/MaterialIcons.java` | MODIFY | Ensure icons available |
| Scene view click handling | MODIFY | Detect trigger clicks |
