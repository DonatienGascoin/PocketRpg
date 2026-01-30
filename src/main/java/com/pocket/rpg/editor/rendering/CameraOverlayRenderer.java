package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.panels.HierarchyPanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Renders camera-related overlays in the editor viewport.
 */
public class CameraOverlayRenderer {

    @Setter
    private HierarchyPanel hierarchyPanel;

    @Setter
    private boolean showGameView = true;

    @Setter
    private float orthographicSize = 15f;

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

        // Show game view preview rectangle
        if (showGameView) {
            renderGameViewPreview(drawList, camera, camSettings);
        }

        drawList.popClipRect();
    }

    /**
     * Renders orange rectangle showing what the game camera will see.
     */
    private void renderGameViewPreview(ImDrawList drawList, EditorCamera camera,
                                       SceneCameraSettings camSettings) {
        Vector2f camPos = camSettings.getPosition();
        float orthoSize = this.orthographicSize;

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

        // Bright red rectangle (matches crosshair color)
        int previewColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.15f, 0.15f, 1.0f);
        drawList.addRect(x1, y1, x2, y2, previewColor, 0, 0, 3.0f);

        // Label at bottom
        drawList.addText(x1 + 4, y2 - 18, previewColor, "Game View");
    }
}
