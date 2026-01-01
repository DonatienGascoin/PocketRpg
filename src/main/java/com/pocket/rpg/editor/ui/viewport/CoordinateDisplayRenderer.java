package com.pocket.rpg.editor.ui.viewport;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.tools.ToolManager;
import imgui.ImGui;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Renders coordinate information at the bottom of the viewport.
 */
public class CoordinateDisplayRenderer {

    @Setter
    private boolean enabled = true;

    @Setter
    private ToolManager toolManager;

    /**
     * Draws coordinate information bar.
     */
    public void render(EditorCamera camera, float viewportX, float viewportY, 
                      float viewportWidth, float viewportHeight, 
                      int hoveredTileX, int hoveredTileY, boolean isHovered) {
        if (!enabled || !isHovered) return;

        var mousePos = ImGui.getMousePos();
        float localX = mousePos.x - viewportX;
        float localY = mousePos.y - viewportY;

        Vector3f worldPos = camera.screenToWorld(localX, localY);

        var drawList = ImGui.getWindowDrawList();
        float barHeight = 20;
        float barY = viewportY + viewportHeight - barHeight;

        // Background
        drawList.addRectFilled(
                viewportX, barY,
                viewportX + viewportWidth, viewportY + viewportHeight,
                ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 0.8f)
        );

        // Text
        String toolName = (toolManager != null && toolManager.getActiveTool() != null)
                ? toolManager.getActiveTool().getName()
                : "No Tool";

        String coordInfo = String.format("%s | World: (%.2f, %.2f) | Tile: (%d, %d) | Zoom: %.0f%%",
                toolName,
                worldPos.x, worldPos.y,
                hoveredTileX, hoveredTileY,
                camera.getZoom() * 100);

        drawList.addText(viewportX + 5, barY + 3,
                ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 1.0f),
                coordInfo);
    }
}
