package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.panels.PrefabBrowserPanel;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.rendering.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Tool for placing prefab instances in the scene.
 * <p>
 * Features:
 * - Click to place entity at cursor
 * - Preview ghost sprite at cursor position
 * - Snap to grid option
 * - Automatic entity selection after placement
 */
public class EntityPlacerTool implements EditorTool, ViewportAwareTool {

    @Setter
    private EditorScene scene;

    @Setter
    private PrefabBrowserPanel prefabPanel;

    @Getter
    @Setter
    private boolean snapToGrid = true;

    // Viewport bounds for overlay rendering
    private float viewportX, viewportY, viewportWidth, viewportHeight;

    // ========================================================================
    // EDITOR TOOL INTERFACE
    // ========================================================================

    @Override
    public String getName() {
        return "Place Entity";
    }

    @Override
    public String getShortcutKey() {
        return "P";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button != 0) return; // Left click only

        if (scene == null || prefabPanel == null) {
            return;
        }

        String prefabId = prefabPanel.getSelectedPrefabId();
        if (prefabId == null) {
            return;
        }

        // Calculate position
        Vector3f position;
        if (snapToGrid) {
            // Snap to tile center (bottom-center origin assumed)
            position = new Vector3f(tileX + 0.5f, tileY, 0);
        } else {
            // TODO: Use exact mouse world position for fine placement
            position = new Vector3f(tileX + 0.5f, tileY, 0);
        }

        // Create and add entity
        EditorEntity entity = new EditorEntity(prefabId, position);
        scene.addEntity(entity);

        // Select the newly placed entity
        scene.setSelectedEntity(entity);
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        // No drag behavior for placement
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        // No action needed
    }

    @Override
    public void onActivate() {
        // Could show placement cursor
    }

    @Override
    public void onDeactivate() {
        // Could hide placement cursor
    }

    // ========================================================================
    // OVERLAY RENDERING
    // ========================================================================

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) {
            return;
        }

        if (prefabPanel == null) {
            return;
        }

        String prefabId = prefabPanel.getSelectedPrefabId();
        if (prefabId == null) {
            // No prefab selected - show cursor indicator
            renderPlacementCursor(camera, hoveredTileX, hoveredTileY);
            return;
        }

        Sprite preview = PrefabRegistry.getInstance().getPreviewSprite(prefabId);
        if (preview == null) {
            // No preview - show cursor indicator
            renderPlacementCursor(camera, hoveredTileX, hoveredTileY);
            return;
        }

        // Calculate world position
        float worldX = snapToGrid ? hoveredTileX + 0.5f : hoveredTileX + 0.5f;
        float worldY = snapToGrid ? hoveredTileY : hoveredTileY;

        // Render ghost sprite
        renderGhostSprite(camera, preview, worldX, worldY);
    }

    /**
     * Renders a ghost (semi-transparent) preview of the entity.
     */
    private void renderGhostSprite(EditorCamera camera, Sprite sprite, float worldX, float worldY) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Calculate sprite size in world units (assuming PPU = 16)
        float ppu = 16f;
        float spriteWidth = sprite.getWidth() / ppu;
        float spriteHeight = sprite.getHeight() / ppu;

        // Calculate world bounds (assuming bottom-center origin)
        float halfWidth = spriteWidth / 2f;
        float minX = worldX - halfWidth;
        float maxX = worldX + halfWidth;
        float minY = worldY;
        float maxY = worldY + spriteHeight;

        // Convert to screen coordinates
        Vector2f screenMin = camera.worldToScreen(minX, maxY); // Top-left in screen
        Vector2f screenMax = camera.worldToScreen(maxX, minY); // Bottom-right in screen

        float x1 = viewportX + screenMin.x;
        float y1 = viewportY + screenMin.y;
        float x2 = viewportX + screenMax.x;
        float y2 = viewportY + screenMax.y;

        // Get texture ID
        int textureId = sprite.getTexture() != null ? sprite.getTexture().getTextureId() : 0;

        if (textureId > 0) {
            // UV coordinates - note V is flipped for OpenGL
            float u0 = sprite.getU0();
            float v0 = sprite.getV1(); // Flip V
            float u1 = sprite.getU1();
            float v1 = sprite.getV0(); // Flip V

            // Semi-transparent white tint for ghost effect
            int ghostColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.5f);
            drawList.addImage(textureId, x1, y1, x2, y2, u0, v0, u1, v1, ghostColor);
        }

        // Draw border around placement area
        int borderColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.8f); // Green
        drawList.addRect(x1, y1, x2, y2, borderColor, 0, 0, 2.0f);

        drawList.popClipRect();
    }

    /**
     * Renders a simple placement cursor when no preview is available.
     */
    private void renderPlacementCursor(EditorCamera camera, int tileX, int tileY) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Draw crosshair at tile center
        float worldX = tileX + 0.5f;
        float worldY = tileY + 0.5f;

        Vector2f center = camera.worldToScreen(worldX, worldY);
        float screenX = viewportX + center.x;
        float screenY = viewportY + center.y;

        int cursorColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.8f);
        float size = 10f;

        // Crosshair
        drawList.addLine(screenX - size, screenY, screenX + size, screenY, cursorColor, 2f);
        drawList.addLine(screenX, screenY - size, screenX, screenY + size, cursorColor, 2f);

        // Circle around center
        drawList.addCircle(screenX, screenY, size, cursorColor, 12, 2f);

        drawList.popClipRect();
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
