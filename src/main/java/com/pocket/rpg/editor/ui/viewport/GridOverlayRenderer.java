package com.pocket.rpg.editor.ui.viewport;

import com.pocket.rpg.editor.camera.EditorCamera;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Renders grid overlay on the viewport.
 */
public class GridOverlayRenderer {

    @Getter
    @Setter
    private boolean enabled = true;

    /**
     * Draws the grid overlay using ImGui draw lists.
     */
    public void render(EditorCamera camera, float viewportX, float viewportY, float viewportWidth, float viewportHeight) {
        if (!enabled) return;

        var drawList = ImGui.getWindowDrawList();

        // Calculate visible bounds in world space
        Vector3f worldMinVec = camera.screenToWorld(0, viewportHeight);
        Vector3f worldMaxVec = camera.screenToWorld(viewportWidth, 0);

        float worldMinX = worldMinVec.x;
        float worldMinY = worldMinVec.y;
        float worldMaxX = worldMaxVec.x;
        float worldMaxY = worldMaxVec.y;

        // Determine grid spacing based on zoom
        float baseSpacing = 1.0f;
        float pixelsPerUnit = viewportWidth / (worldMaxX - worldMinX);

        float spacing = baseSpacing;
        while (spacing * pixelsPerUnit < 20) spacing *= 5;
        while (spacing * pixelsPerUnit > 100) spacing /= 5;

        int majorInterval = 5;

        int minorColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.3f);
        int majorColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f);

        // Draw vertical lines
        float startX = (float) (Math.floor(worldMinX / spacing) * spacing);
        for (float worldX = startX; worldX <= worldMaxX; worldX += spacing) {
            Vector2f screenPos = camera.worldToScreen(worldX, 0);
            float screenX = viewportX + screenPos.x;

            boolean isMajor = Math.abs(worldX) < 0.001f ||
                    Math.abs(worldX % (spacing * majorInterval)) < 0.001f;

            drawList.addLine(
                    screenX, viewportY,
                    screenX, viewportY + viewportHeight,
                    isMajor ? majorColor : minorColor,
                    isMajor ? 1.5f : 1.0f
            );
        }

        // Draw horizontal lines
        float startY = (float) (Math.floor(worldMinY / spacing) * spacing);
        for (float worldY = startY; worldY <= worldMaxY; worldY += spacing) {
            Vector2f screenPos = camera.worldToScreen(0, worldY);
            float screenY = viewportY + screenPos.y;

            boolean isMajor = Math.abs(worldY) < 0.001f ||
                    Math.abs(worldY % (spacing * majorInterval)) < 0.001f;

            drawList.addLine(
                    viewportX, screenY,
                    viewportX + viewportWidth, screenY,
                    isMajor ? majorColor : minorColor,
                    isMajor ? 1.5f : 1.0f
            );
        }

        // Draw origin crosshair with colored axes
        Vector2f originScreen = camera.worldToScreen(0, 0);
        float originX = viewportX + originScreen.x;
        float originY = viewportY + originScreen.y;

        int xAxisColor = ImGui.colorConvertFloat4ToU32(0.8f, 0.2f, 0.2f, 0.8f);
        int yAxisColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.8f);

        // X axis (red horizontal line at Y=0)
        drawList.addLine(
                viewportX, originY,
                viewportX + viewportWidth, originY,
                xAxisColor, 2.0f
        );

        // Y axis (green vertical line at X=0)
        drawList.addLine(
                originX, viewportY,
                originX, viewportY + viewportHeight,
                yAxisColor, 2.0f
        );
    }
}
