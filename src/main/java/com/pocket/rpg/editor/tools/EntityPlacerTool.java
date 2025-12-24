package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.panels.PrefabBrowserPanel;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
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
 * Supports undo/redo for entity placement.
 */
public class EntityPlacerTool implements EditorTool, ViewportAwareTool {

    @Setter
    private EditorScene scene;

    @Setter
    private PrefabBrowserPanel prefabPanel;

    @Getter
    @Setter
    private boolean snapToGrid = true;

    private float viewportX, viewportY, viewportWidth, viewportHeight;

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
        if (button != 0) return;

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
            position = new Vector3f(tileX + 0.5f, tileY, 0);
        } else {
            position = new Vector3f(tileX + 0.5f, tileY, 0);
        }

        // Create entity
        EditorEntity entity = new EditorEntity(prefabId, position);

        // Use undo command for entity creation
        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));

        // Select the newly placed entity
        scene.setSelectedEntity(entity);
        scene.markDirty();
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
    }

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {
    }

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
            renderPlacementCursor(camera, hoveredTileX, hoveredTileY);
            return;
        }

        Sprite preview = PrefabRegistry.getInstance().getPreviewSprite(prefabId);
        if (preview == null) {
            renderPlacementCursor(camera, hoveredTileX, hoveredTileY);
            return;
        }

        float worldX = snapToGrid ? hoveredTileX + 0.5f : hoveredTileX + 0.5f;
        float worldY = snapToGrid ? hoveredTileY : hoveredTileY;

        renderGhostSprite(camera, preview, worldX, worldY);
    }

    private void renderGhostSprite(EditorCamera camera, Sprite sprite, float worldX, float worldY) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        float ppu = 16f;
        float spriteWidth = sprite.getWidth() / ppu;
        float spriteHeight = sprite.getHeight() / ppu;

        float halfWidth = spriteWidth / 2f;
        float minX = worldX - halfWidth;
        float maxX = worldX + halfWidth;
        float minY = worldY;
        float maxY = worldY + spriteHeight;

        Vector2f screenMin = camera.worldToScreen(minX, maxY);
        Vector2f screenMax = camera.worldToScreen(maxX, minY);

        float x1 = viewportX + screenMin.x;
        float y1 = viewportY + screenMin.y;
        float x2 = viewportX + screenMax.x;
        float y2 = viewportY + screenMax.y;

        int textureId = sprite.getTexture() != null ? sprite.getTexture().getTextureId() : 0;

        if (textureId > 0) {
            float u0 = sprite.getU0();
            float v0 = sprite.getV1();
            float u1 = sprite.getU1();
            float v1 = sprite.getV0();

            int ghostColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.5f);
            drawList.addImage(textureId, x1, y1, x2, y2, u0, v0, u1, v1, ghostColor);
        }

        int borderColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.8f);
        drawList.addRect(x1, y1, x2, y2, borderColor, 0, 0, 2.0f);

        drawList.popClipRect();
    }

    private void renderPlacementCursor(EditorCamera camera, int tileX, int tileY) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        float worldX = tileX + 0.5f;
        float worldY = tileY + 0.5f;

        Vector2f center = camera.worldToScreen(worldX, worldY);
        float screenX = viewportX + center.x;
        float screenY = viewportY + center.y;

        int cursorColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.8f);
        float size = 10f;

        drawList.addLine(screenX - size, screenY, screenX + size, screenY, cursorColor, 2f);
        drawList.addLine(screenX, screenY - size, screenX, screenY + size, cursorColor, 2f);
        drawList.addCircle(screenX, screenY, size, cursorColor, 12, 2f);

        drawList.popClipRect();
    }

    @Override
    public void setViewportBounds(float x, float y, float width, float height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }
}
