package com.pocket.rpg.editor.tools;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

/**
 * Eraser tool for removing collision (setting to NONE).
 */
public class CollisionEraserTool implements EditorTool, ViewportAwareTool {

    @Setter
    private EditorScene scene;

    @Getter
    @Setter
    private int eraserSize = 1;

    @Getter
    @Setter
    private int zLevel = 0;

    private boolean isErasing = false;

    // Viewport bounds
    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;

    public CollisionEraserTool(EditorScene scene) {
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
        return "Collision Eraser";
    }

    @Override
    public String getShortcutKey() {
        return "X";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            isErasing = true;
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (!isErasing) return;

        if (button == 0) {
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        isErasing = false;
    }

    private void eraseAt(int centerX, int centerY) {
        if (scene == null || scene.getCollisionMap() == null) {
            return;
        }

        int halfSize = eraserSize / 2;

        for (int dy = -halfSize; dy < eraserSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < eraserSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                scene.getCollisionMap().set(tx, ty, zLevel, CollisionType.NONE);
            }
        }

        scene.markDirty();
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        int halfSize = eraserSize / 2;
        for (int dy = -halfSize; dy < eraserSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < eraserSize - halfSize; dx++) {
                int tx = hoveredTileX + dx;
                int ty = hoveredTileY + dy;

                boolean isCenter = (dx == 0 && dy == 0);
                int fillColor = isCenter
                        ? ToolRenderUtils.colorFromRGBA(1.0f, 0.3f, 0.3f, 0.5f)
                        : ToolRenderUtils.colorFromRGBA(1.0f, 0.3f, 0.3f, 0.3f);

                int borderColor = ToolRenderUtils.colorFromRGBA(1.0f, 0.5f, 0.5f, 0.8f);
                ToolRenderUtils.drawTileHighlight(drawList, camera, viewportX, viewportY, tx, ty, fillColor, borderColor, 1.0f);
            }
        }

        drawList.popClipRect();
    }

    // Legacy setters for backward compatibility
    public void setViewportX(float x) { this.viewportX = x; }
    public void setViewportY(float y) { this.viewportY = y; }
    public void setViewportWidth(float w) { this.viewportWidth = w; }
    public void setViewportHeight(float h) { this.viewportHeight = h; }
}
