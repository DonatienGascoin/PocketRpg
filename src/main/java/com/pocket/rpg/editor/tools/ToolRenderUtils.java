package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;
import imgui.ImDrawList;
import imgui.ImGui;
import org.joml.Vector2f;

/**
 * Shared rendering utilities for editor tools.
 * Eliminates duplicate drawTileHighlight code across tools.
 */
public final class ToolRenderUtils {

    private ToolRenderUtils() {
        // Utility class
    }

    /**
     * Draws a highlighted rectangle for a tile.
     *
     * @param drawList   ImGui draw list
     * @param camera     Editor camera for coordinate conversion
     * @param viewportX  Viewport X offset
     * @param viewportY  Viewport Y offset
     * @param tileX      Tile X coordinate
     * @param tileY      Tile Y coordinate
     * @param fillColor  Fill color (ImGui U32)
     */
    public static void drawTileHighlight(ImDrawList drawList, EditorCamera camera,
                                          float viewportX, float viewportY,
                                          int tileX, int tileY, int fillColor) {
        drawTileHighlight(drawList, camera, viewportX, viewportY, tileX, tileY, fillColor,
                ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.6f), 1.0f);
    }

    /**
     * Draws a highlighted rectangle for a tile with custom border.
     *
     * @param drawList    ImGui draw list
     * @param camera      Editor camera for coordinate conversion
     * @param viewportX   Viewport X offset
     * @param viewportY   Viewport Y offset
     * @param tileX       Tile X coordinate
     * @param tileY       Tile Y coordinate
     * @param fillColor   Fill color (ImGui U32)
     * @param borderColor Border color (ImGui U32)
     * @param borderWidth Border thickness
     */
    public static void drawTileHighlight(ImDrawList drawList, EditorCamera camera,
                                          float viewportX, float viewportY,
                                          int tileX, int tileY,
                                          int fillColor, int borderColor, float borderWidth) {
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

        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, borderWidth);
    }

    /**
     * Creates a fill color from RGBA components.
     */
    public static int colorFromRGBA(float r, float g, float b, float a) {
        return ImGui.colorConvertFloat4ToU32(r, g, b, a);
    }

    /**
     * Creates a fill color from a CollisionType's overlay color with custom alpha.
     */
    public static int colorFromOverlay(float[] overlayColor, float alpha) {
        return ImGui.colorConvertFloat4ToU32(
                overlayColor[0], overlayColor[1], overlayColor[2], alpha
        );
    }
}
