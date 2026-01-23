package com.pocket.rpg.editor.tools;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.function.Consumer;

/**
 * Picker tool for sampling collision types from the map.
 * <p>
 * Features:
 * - Click to pick collision type
 * - Picked type is set on collision tools via callback
 */
public class CollisionPickerTool implements EditorTool, ViewportAwareTool {

    @Setter
    private EditorScene scene;

    @Getter
    @Setter
    private int zLevel = 0;

    /**
     * Callback when a collision type is picked.
     */
    @Setter
    private Consumer<CollisionType> onCollisionPicked;

    /**
     * Callback when a trigger tile is clicked.
     */
    @Setter
    private Consumer<TileCoord> onTriggerSelected;

    // Viewport bounds
    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;

    public CollisionPickerTool(EditorScene scene) {
        this.scene = scene;
    }

    @Override
    public void setViewportBounds(float x, float y, float width, float height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    @Override
    public String getName() {
        return "Collision Picker";
    }

    @Override
    public String getShortcutKey() {
        return "V";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            pickAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        // Picker doesn't support dragging
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        // Nothing to do
    }

    private void pickAt(int tileX, int tileY) {
        if (scene == null || scene.getCollisionMap() == null) {
            return;
        }

        CollisionType pickedType = scene.getCollisionMap().get(tileX, tileY, zLevel);

        if (onCollisionPicked != null) {
            onCollisionPicked.accept(pickedType);
        }

        // If it's a trigger tile, also notify trigger selection
        if (pickedType != null && pickedType.isTrigger() && onTriggerSelected != null) {
            onTriggerSelected.accept(new TileCoord(tileX, tileY, zLevel));
        }
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Show what would be picked
        if (scene != null && scene.getCollisionMap() != null) {
            CollisionType currentType = scene.getCollisionMap().get(hoveredTileX, hoveredTileY, zLevel);
            float[] color = currentType.getOverlayColor();
            int fillColor = ToolRenderUtils.colorFromRGBA(color[0], color[1], color[2], 0.6f);

            // Draw tile with highlight
            Vector2f bottomLeft = camera.worldToScreen(hoveredTileX, hoveredTileY);
            Vector2f topRight = camera.worldToScreen(hoveredTileX + 1, hoveredTileY + 1);

            float x1 = viewportX + bottomLeft.x;
            float y1 = viewportY + topRight.y;
            float x2 = viewportX + topRight.x;
            float y2 = viewportY + bottomLeft.y;

            float minX = Math.min(x1, x2);
            float maxX = Math.max(x1, x2);
            float minY = Math.min(y1, y2);
            float maxY = Math.max(y1, y2);

            drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

            // Draw eyedropper cursor style border
            int borderColor = ToolRenderUtils.colorFromRGBA(1.0f, 1.0f, 1.0f, 1.0f);
            drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 2.0f);

            // Draw crosshair in center
            float centerX = (minX + maxX) / 2;
            float centerY = (minY + maxY) / 2;
            float crossSize = 4;
            drawList.addLine(centerX - crossSize, centerY, centerX + crossSize, centerY, borderColor, 2.0f);
            drawList.addLine(centerX, centerY - crossSize, centerX, centerY + crossSize, borderColor, 2.0f);
        }

        drawList.popClipRect();
    }

    // Legacy setters for backward compatibility
    public void setViewportX(float x) { this.viewportX = x; }
    public void setViewportY(float y) { this.viewportY = y; }
    public void setViewportWidth(float w) { this.viewportWidth = w; }
    public void setViewportHeight(float h) { this.viewportHeight = h; }
}
