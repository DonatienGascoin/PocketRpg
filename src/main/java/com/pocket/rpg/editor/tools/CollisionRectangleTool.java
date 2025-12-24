package com.pocket.rpg.editor.tools;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BatchCollisionCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Rectangle tool for filling rectangular collision areas.
 * Supports undo/redo.
 */
public class CollisionRectangleTool implements EditorTool, ViewportAwareTool {

    @Setter
    private EditorScene scene;

    @Getter
    @Setter
    private CollisionType selectedType = CollisionType.SOLID;

    @Getter
    @Setter
    private int zLevel = 0;

    private boolean isDragging = false;
    private int startX, startY;
    private int endX, endY;

    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;

    public CollisionRectangleTool(EditorScene scene) {
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
        return "Collision Rectangle";
    }

    @Override
    public String getShortcutKey() {
        return "H";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            isDragging = true;
            startX = tileX;
            startY = tileY;
            endX = tileX;
            endY = tileY;
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (isDragging && button == 0) {
            endX = tileX;
            endY = tileY;
        }
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (isDragging && button == 0) {
            endX = tileX;
            endY = tileY;
            fillRectangle();
            isDragging = false;
        }
    }

    private void fillRectangle() {
        if (scene == null || scene.getCollisionMap() == null) return;

        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minY = Math.min(startY, endY);
        int maxY = Math.max(startY, endY);

        // Create undo command
        BatchCollisionCommand command = new BatchCollisionCommand(
                scene.getCollisionMap(), zLevel, "Rectangle " + selectedType);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                command.recordChange(x, y, selectedType);
                scene.getCollisionMap().set(x, y, zLevel, selectedType);
            }
        }

        if (command.hasChanges()) {
            UndoManager.getInstance().execute(command);
        }

        scene.markDirty();
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        float[] color = selectedType.getOverlayColor();

        if (isDragging) {
            int minX = Math.min(startX, endX);
            int maxX = Math.max(startX, endX);
            int minY = Math.min(startY, endY);
            int maxY = Math.max(startY, endY);

            int fillColor = ToolRenderUtils.colorFromRGBA(color[0], color[1], color[2], 0.4f);
            int borderColor = ToolRenderUtils.colorFromRGBA(0.4f, 0.8f, 1.0f, 0.8f);

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    ToolRenderUtils.drawTileHighlight(drawList, camera, viewportX, viewportY, x, y, fillColor, borderColor, 1.0f);
                }
            }

            // Draw rectangle outline
            Vector2f bottomLeft = camera.worldToScreen(minX, minY);
            Vector2f topRight = camera.worldToScreen(maxX + 1, maxY + 1);

            float x1 = viewportX + bottomLeft.x;
            float y1 = viewportY + topRight.y;
            float x2 = viewportX + topRight.x;
            float y2 = viewportY + bottomLeft.y;

            float rectMinX = Math.min(x1, x2);
            float rectMaxX = Math.max(x1, x2);
            float rectMinY = Math.min(y1, y2);
            float rectMaxY = Math.max(y1, y2);

            int outlineColor = ToolRenderUtils.colorFromRGBA(1.0f, 1.0f, 1.0f, 1.0f);
            drawList.addRect(rectMinX, rectMinY, rectMaxX, rectMaxY, outlineColor, 0, 0, 2.0f);

        } else {
            int fillColor = ToolRenderUtils.colorFromRGBA(color[0], color[1], color[2], 0.5f);
            int borderColor = ToolRenderUtils.colorFromRGBA(0.4f, 0.8f, 1.0f, 0.8f);
            ToolRenderUtils.drawTileHighlight(drawList, camera, viewportX, viewportY, hoveredTileX, hoveredTileY, fillColor, borderColor, 1.0f);
        }

        drawList.popClipRect();
    }

    public void setViewportX(float x) { this.viewportX = x; }
    public void setViewportY(float y) { this.viewportY = y; }
    public void setViewportWidth(float w) { this.viewportWidth = w; }
    public void setViewportHeight(float h) { this.viewportHeight = h; }
}
