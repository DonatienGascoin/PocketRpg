package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.rendering.resources.Sprite;
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
        // Selection color (yellow)
        int selectionColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 0.0f, 0.9f);
        int handleColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f);

        Vector2f[] corners = getEntityScreenCorners(entity, camera);
        if (corners == null) return;

        // Draw quad outline
        drawList.addQuad(
                corners[0].x, corners[0].y,
                corners[1].x, corners[1].y,
                corners[2].x, corners[2].y,
                corners[3].x, corners[3].y,
                selectionColor, 2.5f
        );

        // Draw corner handles
        float handleSize = 6f;
        for (Vector2f corner : corners) {
            drawList.addRectFilled(
                    corner.x - handleSize / 2, corner.y - handleSize / 2,
                    corner.x + handleSize / 2, corner.y + handleSize / 2,
                    handleColor
            );
        }
    }

    /**
     * Renders hover highlight (subtle cyan border).
     */
    private void renderHoverHighlight(ImDrawList drawList, EditorCamera camera, EditorGameObject entity) {
        // Hover color (cyan, semi-transparent)
        int hoverColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.6f);

        Vector2f[] corners = getEntityScreenCorners(entity, camera);
        if (corners == null) return;

        // Draw quad outline
        drawList.addQuad(
                corners[0].x, corners[0].y,
                corners[1].x, corners[1].y,
                corners[2].x, corners[2].y,
                corners[3].x, corners[3].y,
                hoverColor, 1.5f
        );
    }

    /**
     * Gets the four screen-space corners of an entity's bounding box,
     * accounting for pivot, scale, and rotation.
     *
     * @return Array of 4 corners [topLeft, topRight, bottomRight, bottomLeft] in screen space,
     *         or null if entity has no renderable sprite
     */
    private Vector2f[] getEntityScreenCorners(EditorGameObject entity, EditorCamera camera) {
        Vector3f pos = entity.getPosition();
        Vector3f scale = entity.getScale();
        Vector3f rotation = entity.getRotation();
        Vector2f size = entity.getCurrentSize();

        if (size == null) {
            size = new Vector2f(1f, 1f);
        }

        // Get pivot from sprite (default to center if no sprite)
        float pivotX = 0.5f;
        float pivotY = 0.5f;
        SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
        if (sr != null) {
            Sprite sprite = sr.getSprite();
            if (sprite != null) {
                pivotX = sprite.getPivotX();
                pivotY = sprite.getPivotY();
            }
        }

        // Calculate scaled size
        float width = size.x * scale.x;
        float height = size.y * scale.y;

        // Calculate corner offsets from position (based on pivot)
        // pivot (0,0) = bottom-left, (0.5,0.5) = center, (1,1) = top-right
        float left = -pivotX * width;
        float right = (1f - pivotX) * width;
        float bottom = -pivotY * height;
        float top = (1f - pivotY) * height;

        // Local corners (before rotation)
        float[][] localCorners = {
                {left, top},      // top-left
                {right, top},     // top-right
                {right, bottom},  // bottom-right
                {left, bottom}    // bottom-left
        };

        // Apply rotation
        float rotZ = (float) Math.toRadians(rotation.z);
        float cos = (float) Math.cos(rotZ);
        float sin = (float) Math.sin(rotZ);

        Vector2f[] screenCorners = new Vector2f[4];
        for (int i = 0; i < 4; i++) {
            float lx = localCorners[i][0];
            float ly = localCorners[i][1];

            // Rotate around origin (which is at entity position)
            float worldX = pos.x + lx * cos - ly * sin;
            float worldY = pos.y + lx * sin + ly * cos;

            // Convert to screen
            Vector2f screen = camera.worldToScreen(worldX, worldY);
            screenCorners[i] = new Vector2f(viewportX + screen.x, viewportY + screen.y);
        }

        return screenCorners;
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
