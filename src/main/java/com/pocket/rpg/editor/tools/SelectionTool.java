package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Tool for selecting and moving entities in the scene.
 * <p>
 * Features:
 * - Click to select entity
 * - Drag to move selected entity
 * - Click empty space to deselect
 * - Renders selection highlight
 */
public class SelectionTool implements EditorTool, ViewportAwareTool {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorCamera camera;

    // Drag state
    private EditorGameObject draggedEntity = null;
    private Vector2f dragOffset = new Vector2f();
    private boolean isDragging = false;

    // Viewport bounds for overlay rendering
    private float viewportX, viewportY, viewportWidth, viewportHeight;

    // ========================================================================
    // EDITOR TOOL INTERFACE
    // ========================================================================

    @Override
    public String getName() {
        return "Select";
    }

    @Override
    public String getShortcutKey() {
        return "V";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button != 0) return; // Left click only

        if (scene == null) {
            return;
        }

        // Calculate world position (approximate - center of tile)
        float worldX = tileX + 0.5f;
        float worldY = tileY + 0.5f;

        // Find entity at position
        EditorGameObject entity = scene.findEntityAt(worldX, worldY);

        if (entity != null) {
            // Select and start drag
            scene.setSelectedEntity(entity);
            draggedEntity = entity;
            isDragging = true;

            // Calculate offset from entity position to click position
            Vector3f pos = entity.getPosition();
            dragOffset.set(pos.x - worldX, pos.y - worldY);
        } else {
            // Deselect
            scene.setSelectedEntity(null);
            draggedEntity = null;
            isDragging = false;
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (!isDragging || draggedEntity == null) {
            return;
        }

        // Calculate new position
        float worldX = tileX + 0.5f + dragOffset.x;
        float worldY = tileY + 0.5f + dragOffset.y;

        // Update entity position
        draggedEntity.setPosition(worldX, worldY);
        scene.markDirty();
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (button == 0) {
            draggedEntity = null;
            isDragging = false;
        }
    }

    @Override
    public void onActivate() {
        draggedEntity = null;
        isDragging = false;
    }

    @Override
    public void onDeactivate() {
        draggedEntity = null;
        isDragging = false;
    }

    // ========================================================================
    // OVERLAY RENDERING
    // ========================================================================

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (scene == null) {
            return;
        }

        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Draw selection highlight on selected entity
        EditorGameObject selected = scene.getSelectedEntity();
        if (selected != null) {
            renderSelectionHighlight(drawList, camera, selected);
        }

        // Draw hover highlight on hovered entity (if different from selected)
        if (hoveredTileX != Integer.MIN_VALUE && hoveredTileY != Integer.MIN_VALUE) {
            float worldX = hoveredTileX + 0.5f;
            float worldY = hoveredTileY + 0.5f;

            EditorGameObject hovered = scene.findEntityAt(worldX, worldY);
            if (hovered != null && hovered != selected) {
                renderHoverHighlight(drawList, camera, hovered);
            }
        }

        drawList.popClipRect();
    }

    /**
     * Renders selection highlight (yellow border).
     */
    private void renderSelectionHighlight(ImDrawList drawList, EditorCamera camera, EditorGameObject entity) {
        Vector3f pos = entity.getPositionRef();
        Vector2f size = entity.getCurrentSize();

        if (size == null) {
            size = new Vector2f(1f, 1f);
        }

        // Calculate bounds (assuming bottom-center origin)
        float halfW = size.x / 2f;
        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float minY = pos.y;
        float maxY = pos.y + size.y;

        // Convert to screen coordinates
        Vector2f screenMin = camera.worldToScreen(minX, maxY); // Top-left
        Vector2f screenMax = camera.worldToScreen(maxX, minY); // Bottom-right

        float x1 = viewportX + screenMin.x;
        float y1 = viewportY + screenMin.y;
        float x2 = viewportX + screenMax.x;
        float y2 = viewportY + screenMax.y;

        // Selection color (yellow)
        int selectionColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 0.0f, 0.9f);

        // Draw rectangle
        drawList.addRect(x1, y1, x2, y2, selectionColor, 0, 0, 2.5f);

        // Draw corner handles
        float handleSize = 6f;
        int handleColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f);

        // Top-left
        drawList.addRectFilled(x1 - handleSize / 2, y1 - handleSize / 2,
                x1 + handleSize / 2, y1 + handleSize / 2, handleColor);
        // Top-right
        drawList.addRectFilled(x2 - handleSize / 2, y1 - handleSize / 2,
                x2 + handleSize / 2, y1 + handleSize / 2, handleColor);
        // Bottom-left
        drawList.addRectFilled(x1 - handleSize / 2, y2 - handleSize / 2,
                x1 + handleSize / 2, y2 + handleSize / 2, handleColor);
        // Bottom-right
        drawList.addRectFilled(x2 - handleSize / 2, y2 - handleSize / 2,
                x2 + handleSize / 2, y2 + handleSize / 2, handleColor);
    }

    /**
     * Renders hover highlight (subtle cyan border).
     */
    private void renderHoverHighlight(ImDrawList drawList, EditorCamera camera, EditorGameObject entity) {
        Vector3f pos = entity.getPositionRef();
        Vector2f size = entity.getCurrentSize();

        if (size == null) {
            size = new Vector2f(1f, 1f);
        }

        float halfW = size.x / 2f;
        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float minY = pos.y;
        float maxY = pos.y + size.y;

        Vector2f screenMin = camera.worldToScreen(minX, maxY);
        Vector2f screenMax = camera.worldToScreen(maxX, minY);

        float x1 = viewportX + screenMin.x;
        float y1 = viewportY + screenMin.y;
        float x2 = viewportX + screenMax.x;
        float y2 = viewportY + screenMax.y;

        // Hover color (cyan, semi-transparent)
        int hoverColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.6f);
        drawList.addRect(x1, y1, x2, y2, hoverColor, 0, 0, 1.5f);
    }

    // ========================================================================
    // VIEWPORT AWARE
    // ========================================================================

    @Override
    public void setViewportBounds(float x, float y, float width, float height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }
}
