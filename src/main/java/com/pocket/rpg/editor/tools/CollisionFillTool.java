package com.pocket.rpg.editor.tools;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BatchCollisionCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Fill tool for flood-filling collision areas.
 * Supports undo/redo.
 */
public class CollisionFillTool implements EditorTool, ViewportAwareTool {

    private static final int MAX_FILL_TILES = 2000;

    @Setter
    private EditorScene scene;

    @Getter
    @Setter
    private CollisionType selectedType = CollisionType.SOLID;

    @Getter
    @Setter
    private int zLevel = 0;

    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;

    public CollisionFillTool(EditorScene scene) {
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
        return "Collision Fill";
    }

    @Override
    public String getShortcutKey() {
        return "G";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            fillAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
    }

    private void fillAt(int startX, int startY) {
        if (scene == null || scene.getCollisionMap() == null) return;

        CollisionMap collisionMap = scene.getCollisionMap();

        CollisionType targetType = collisionMap.get(startX, startY, zLevel);

        if (targetType == selectedType) return;

        // Create undo command
        BatchCollisionCommand command = new BatchCollisionCommand(collisionMap, zLevel, "Fill " + selectedType);

        Set<Long> filled = new HashSet<>();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startX, startY});

        while (!queue.isEmpty() && filled.size() < MAX_FILL_TILES) {
            int[] pos = queue.poll();
            int x = pos[0];
            int y = pos[1];

            long key = key(x, y);
            if (filled.contains(key)) continue;

            if (collisionMap.get(x, y, zLevel) != targetType) continue;

            // Record and apply change
            command.recordChange(x, y, selectedType);
            collisionMap.set(x, y, zLevel, selectedType);
            filled.add(key);

            queue.add(new int[]{x + 1, y});
            queue.add(new int[]{x - 1, y});
            queue.add(new int[]{x, y + 1});
            queue.add(new int[]{x, y - 1});
        }

        if (command.hasChanges()) {
            UndoManager.getInstance().execute(command);
        }

        scene.markDirty();
    }

    private long key(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        float[] color = selectedType.getOverlayColor();
        int fillColor = ToolRenderUtils.colorFromRGBA(color[0], color[1], color[2], 0.6f);
        int borderColor = ToolRenderUtils.colorFromRGBA(1.0f, 1.0f, 1.0f, 0.8f);

        ToolRenderUtils.drawTileHighlight(drawList, camera, viewportX, viewportY, hoveredTileX, hoveredTileY, fillColor, borderColor, 1.0f);

        drawList.popClipRect();
    }

    public void setViewportX(float x) { this.viewportX = x; }
    public void setViewportY(float y) { this.viewportY = y; }
    public void setViewportWidth(float w) { this.viewportWidth = w; }
    public void setViewportHeight(float h) { this.viewportHeight = h; }
}
