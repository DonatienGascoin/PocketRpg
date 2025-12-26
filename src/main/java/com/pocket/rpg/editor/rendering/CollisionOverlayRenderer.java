package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.camera.EditorCamera;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Renders collision overlay in the editor viewport.
 * <p>
 * Shows colored semi-transparent squares for each collision tile type.
 */
public class CollisionOverlayRenderer {

    @Getter
    @Setter
    private boolean visible = true;

    /**
     * Opacity of collision overlay (0.0 to 1.0).
     */
    @Getter
    @Setter
    private float opacity = 0.4f;

    /**
     * Z-level to render (default 0 = ground).
     */
    @Getter
    @Setter
    private int zLevel = 0;

    // Viewport bounds for clipping
    @Setter
    private float viewportX, viewportY;
    @Setter
    private float viewportWidth, viewportHeight;

    /**
     * Renders collision overlay for visible tiles.
     *
     * @param collisionMap CollisionMap to render
     * @param camera       Editor camera
     */
    public void render(CollisionMap collisionMap, EditorCamera camera) {
        if (!visible || collisionMap == null) {
            return;
        }

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Get visible tile bounds
        float[] worldBounds = camera.getWorldBounds();
        int minTileX = (int) Math.floor(worldBounds[0]);
        int minTileY = (int) Math.floor(worldBounds[1]);
        int maxTileX = (int) Math.ceil(worldBounds[2]);
        int maxTileY = (int) Math.ceil(worldBounds[3]);

        // Render each visible tile
        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                CollisionType type = collisionMap.get(tileX, tileY, zLevel);

                // Skip NONE (no collision)
                if (type == CollisionType.NONE) {
                    continue;
                }

                renderTile(drawList, camera, tileX, tileY, type);
            }
        }

        drawList.popClipRect();
    }

    /**
     * Renders a single collision tile.
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

        // Get type color and apply opacity
        float[] color = type.getOverlayColor();
        int fillColor = ImGui.colorConvertFloat4ToU32(
                color[0], color[1], color[2], color[3] * opacity
        );

        // Draw filled rectangle
        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        // Draw border for clarity
        int borderColor = ImGui.colorConvertFloat4ToU32(
                color[0] * 0.8f, color[1] * 0.8f, color[2] * 0.8f, opacity * 0.8f
        );
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.0f);
    }

    /**
     * Sets viewport bounds for rendering.
     */
    public void setViewportBounds(float x, float y, float width, float height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }
}