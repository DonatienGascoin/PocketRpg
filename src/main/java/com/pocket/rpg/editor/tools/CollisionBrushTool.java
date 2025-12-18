package com.pocket.rpg.editor.tools;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

/**
 * Brush tool for painting collision types on the collision map.
 */
public class CollisionBrushTool implements EditorTool, ViewportAwareTool {

    @Setter
    private EditorScene scene;

    @Getter
    @Setter
    private CollisionType selectedType = CollisionType.SOLID;

    @Getter
    @Setter
    private int brushSize = 1;

    @Getter
    @Setter
    private int zLevel = 0;

    private boolean isPainting = false;

    // Viewport bounds
    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;

    public CollisionBrushTool(EditorScene scene) {
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
        return "Collision Brush";
    }

    @Override
    public String getShortcutKey() {
        return "C";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            isPainting = true;
            paintAt(tileX, tileY);
        } else if (button == 1) {
            isPainting = true;
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (!isPainting) return;

        if (button == 0) {
            paintAt(tileX, tileY);
        } else if (button == 1) {
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        isPainting = false;
    }

    private void paintAt(int centerX, int centerY) {
        if (scene == null || scene.getCollisionMap() == null) {
            return;
        }

        int halfSize = brushSize / 2;

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                scene.getCollisionMap().set(tx, ty, zLevel, selectedType);
            }
        }

        scene.markDirty();
    }

    private void eraseAt(int centerX, int centerY) {
        if (scene == null || scene.getCollisionMap() == null) {
            return;
        }

        int halfSize = brushSize / 2;

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
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

        int halfSize = brushSize / 2;
        float[] color = selectedType.getOverlayColor();

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = hoveredTileX + dx;
                int ty = hoveredTileY + dy;

                boolean isCenter = (dx == 0 && dy == 0);
                int fillColor = isCenter
                        ? ToolRenderUtils.colorFromRGBA(color[0], color[1], color[2], 0.5f)
                        : ToolRenderUtils.colorFromRGBA(color[0], color[1], color[2], 0.3f);

                int borderColor = ToolRenderUtils.colorFromRGBA(1.0f, 1.0f, 1.0f, 0.6f);
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
