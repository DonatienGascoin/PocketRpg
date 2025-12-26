package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.panels.HierarchyPanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Renders camera-related overlays in the editor viewport.
 */
public class CameraOverlayRenderer {

    @Setter
    private HierarchyPanel hierarchyPanel;
    @Setter
    private boolean showBounds = true;

    @Setter
    private boolean showGameView = true;

    // Viewport bounds (set before rendering)
    private float viewportX, viewportY, viewportWidth, viewportHeight;

    /**
     * Sets viewport bounds for overlay rendering.
     */
    public void setViewportBounds(float x, float y, float width, float height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    /**
     * Renders camera overlays.
     */
    public void render(EditorCamera camera, EditorScene scene) {
        if (scene == null || viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        if (!hierarchyPanel.isCameraSelected()) return;

        SceneCameraSettings camSettings = scene.getCameraSettings();
        ImDrawList drawList = ImGui.getWindowDrawList();

        drawList.pushClipRect(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Render bounds if enabled
        if (showBounds && camSettings.isUseBounds()) {
            renderCameraBounds(drawList, camera, camSettings);
        }

        // Show game view preview rectangle
        if (showGameView) {
            renderGameViewPreview(drawList, camera, camSettings);
        }

        drawList.popClipRect();
    }

    /**
     * Renders dashed rectangle showing camera bounds.
     */
    private void renderCameraBounds(ImDrawList drawList, EditorCamera camera,
                                    SceneCameraSettings camSettings) {
        Vector4f bounds = camSettings.getBounds();

        // Convert world bounds to screen (bounds = minX, minY, maxX, maxY)
        Vector2f topLeft = camera.worldToScreen(bounds.x, bounds.w);     // minX, maxY
        Vector2f bottomRight = camera.worldToScreen(bounds.z, bounds.y); // maxX, minY

        float x1 = viewportX + topLeft.x;
        float y1 = viewportY + topLeft.y;
        float x2 = viewportX + bottomRight.x;
        float y2 = viewportY + bottomRight.y;

        // Dashed cyan rectangle
        int boundsColor = ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 0.8f, 0.8f);

        // Draw dashed lines
        float dashLength = 10f;
        float gapLength = 5f;

        drawDashedLine(drawList, x1, y1, x2, y1, boundsColor, dashLength, gapLength); // Top
        drawDashedLine(drawList, x2, y1, x2, y2, boundsColor, dashLength, gapLength); // Right
        drawDashedLine(drawList, x2, y2, x1, y2, boundsColor, dashLength, gapLength); // Bottom
        drawDashedLine(drawList, x1, y2, x1, y1, boundsColor, dashLength, gapLength); // Left

        // Label
        drawList.addText(x1 + 4, y1 + 4, boundsColor, "Camera Bounds");
    }

    /**
     * Renders orange rectangle showing what the game camera will see.
     */
    private void renderGameViewPreview(ImDrawList drawList, EditorCamera camera,
                                       SceneCameraSettings camSettings) {
        Vector2f camPos = camSettings.getPosition();
        float orthoSize = camSettings.getOrthographicSize();

        // Calculate game view rectangle (assuming 16:9 aspect)
        float aspectRatio = 16f / 9f;
        float halfHeight = orthoSize;
        float halfWidth = orthoSize * aspectRatio;

        float minX = camPos.x - halfWidth;
        float maxX = camPos.x + halfWidth;
        float minY = camPos.y - halfHeight;
        float maxY = camPos.y + halfHeight;

        // Convert to screen
        Vector2f topLeft = camera.worldToScreen(minX, maxY);
        Vector2f bottomRight = camera.worldToScreen(maxX, minY);

        float x1 = viewportX + topLeft.x;
        float y1 = viewportY + topLeft.y;
        float x2 = viewportX + bottomRight.x;
        float y2 = viewportY + bottomRight.y;

        // Orange rectangle
        int previewColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.2f, 0.7f);
        drawList.addRect(x1, y1, x2, y2, previewColor, 0, 0, 2.0f);

        // Label at bottom
        drawList.addText(x1 + 4, y2 - 18, previewColor, "Game View");
    }

    /**
     * Draws a dashed line between two points.
     */
    private void drawDashedLine(ImDrawList drawList, float x1, float y1, float x2, float y2,
                                int color, float dashLength, float gapLength) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.001f) return;

        float nx = dx / length;
        float ny = dy / length;

        float pos = 0;
        boolean drawing = true;

        while (pos < length) {
            float segmentLength = drawing ? dashLength : gapLength;
            float endPos = Math.min(pos + segmentLength, length);

            if (drawing) {
                float sx = x1 + nx * pos;
                float sy = y1 + ny * pos;
                float ex = x1 + nx * endPos;
                float ey = y1 + ny * endPos;
                drawList.addLine(sx, sy, ex, ey, color, 2.0f);
            }

            pos = endPos;
            drawing = !drawing;
        }
    }
}