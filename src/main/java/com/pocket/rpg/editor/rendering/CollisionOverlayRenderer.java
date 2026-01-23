package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.collision.trigger.TriggerDataMap;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorFonts;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Renders collision overlay in the editor viewport.
 * <p>
 * Shows colored semi-transparent squares for each collision tile type,
 * with icons for trigger tiles and visual feedback for selection/configuration state.
 */
public class CollisionOverlayRenderer {

    @Getter
    @Setter
    private boolean visible = true;

    /**
     * Opacity of collision overlay (0.0 to 1.0).
     */
    @Getter
    @Setter
    private float opacity = 0.4f;

    /**
     * Z-level to render (default 0 = ground).
     */
    @Getter
    @Setter
    private int zLevel = 0;

    /**
     * Whether to show icons on trigger tiles.
     */
    @Getter
    @Setter
    private boolean showIcons = true;

    /**
     * Currently selected trigger tile (for highlight).
     */
    @Getter
    @Setter
    private TileCoord selectedTrigger;

    /**
     * TriggerDataMap for checking configuration status.
     */
    @Setter
    private TriggerDataMap triggerDataMap;

    // Viewport bounds for clipping
    @Setter
    private float viewportX, viewportY;
    @Setter
    private float viewportWidth, viewportHeight;

    // Animation state for selection pulse
    private float selectionPulse = 0f;

    /**
     * Renders collision overlay for visible tiles (non-trigger tiles only).
     * Triggers are rendered separately via renderTriggersOnly().
     *
     * @param collisionMap CollisionMap to render
     * @param camera       Editor camera
     */
    public void render(CollisionMap collisionMap, EditorCamera camera) {
        if (!visible || collisionMap == null) {
            return;
        }

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Get visible tile bounds
        float[] worldBounds = camera.getWorldBounds();
        int minTileX = (int) Math.floor(worldBounds[0]);
        int minTileY = (int) Math.floor(worldBounds[1]);
        int maxTileX = (int) Math.ceil(worldBounds[2]);
        int maxTileY = (int) Math.ceil(worldBounds[3]);

        // Render non-trigger tile backgrounds only
        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                CollisionType type = collisionMap.get(tileX, tileY, zLevel);

                if (type == CollisionType.NONE || type.isTrigger()) {
                    continue; // Skip triggers - they're rendered separately
                }

                renderTile(drawList, camera, tileX, tileY, type);
            }
        }

        drawList.popClipRect();
    }

    /**
     * Renders trigger tiles only (always visible regardless of toggle).
     * Includes tile backgrounds, icons, and selection highlight.
     *
     * @param collisionMap CollisionMap to render
     * @param camera       Editor camera
     */
    public void renderTriggersOnly(CollisionMap collisionMap, EditorCamera camera) {
        if (collisionMap == null) {
            return;
        }

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Update selection animation (slow pulse)
        selectionPulse += 0.03f;

        // Get visible tile bounds
        float[] worldBounds = camera.getWorldBounds();
        int minTileX = (int) Math.floor(worldBounds[0]);
        int minTileY = (int) Math.floor(worldBounds[1]);
        int maxTileX = (int) Math.ceil(worldBounds[2]);
        int maxTileY = (int) Math.ceil(worldBounds[3]);

        // First pass: render trigger tile backgrounds
        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                CollisionType type = collisionMap.get(tileX, tileY, zLevel);

                if (type != null && type.isTrigger()) {
                    renderTile(drawList, camera, tileX, tileY, type);
                }
            }
        }

        // Second pass: render icons on top (for triggers)
        if (showIcons) {
            for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
                for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                    CollisionType type = collisionMap.get(tileX, tileY, zLevel);

                    if (type != null && type.isTrigger()) {
                        renderTriggerIcon(drawList, camera, tileX, tileY, type);
                    }
                }
            }
        }

        // Third pass: render selection highlight
        if (selectedTrigger != null && selectedTrigger.elevation() == zLevel) {
            renderSelectionHighlight(drawList, camera);
        }

        drawList.popClipRect();
    }

    /**
     * Renders a single collision tile (colored rectangle).
     */
    private void renderTile(ImDrawList drawList, EditorCamera camera,
                            int tileX, int tileY, CollisionType type) {
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

        // Get type color and apply opacity
        float[] color = type.getOverlayColor();
        int fillColor = ImGui.colorConvertFloat4ToU32(
                color[0], color[1], color[2], color[3] * opacity
        );

        // Draw filled rectangle
        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        // Draw border for clarity
        int borderColor = ImGui.colorConvertFloat4ToU32(
                color[0] * 0.8f, color[1] * 0.8f, color[2] * 0.8f, opacity * 0.8f
        );
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.0f);
    }

    /**
     * Renders icon for trigger tiles.
     */
    private void renderTriggerIcon(ImDrawList drawList, EditorCamera camera,
                                   int tileX, int tileY, CollisionType type) {
        Vector2f bottomLeft = camera.worldToScreen(tileX, tileY);
        Vector2f topRight = camera.worldToScreen(tileX + 1, tileY + 1);

        float minX = viewportX + Math.min(bottomLeft.x, topRight.x);
        float maxX = viewportX + Math.max(bottomLeft.x, topRight.x);
        float minY = viewportY + Math.min(bottomLeft.y, topRight.y);
        float maxY = viewportY + Math.max(bottomLeft.y, topRight.y);

        float tileWidth = maxX - minX;
        float tileHeight = maxY - minY;
        float centerX = minX + tileWidth / 2;
        float centerY = minY + tileHeight / 2;

        // Skip if tile is too small to show icons
        if (tileWidth < 14 || tileHeight < 14) return;

        // Check if trigger is configured
        boolean isConfigured = triggerDataMap != null && triggerDataMap.has(tileX, tileY, zLevel);

        // Determine icon to show
        String icon;
        if (!isConfigured) {
            // Show warning icon for unconfigured triggers
            icon = MaterialIcons.Warning;
        } else if (type.hasIcon()) {
            icon = type.getIcon();
        } else {
            return; // No icon to show
        }

        // Icon color
        int iconColor;
        if (!isConfigured) {
            // Warning: orange
            iconColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.2f, 1.0f);
        } else {
            // Normal: white
            iconColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.95f);
        }

        // Select appropriate icon font based on tile size
        float targetSize = Math.min(tileWidth, tileHeight) * 0.7f;
        ImFont iconFont = EditorFonts.getIconFont(targetSize);
        int fontSize = (int) iconFont.getFontSize();

        // Center the icon
        float textX = centerX - fontSize / 2f;
        float textY = centerY - fontSize / 2f;

        // Draw shadow for better visibility
        int shadowColor = ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.6f);
        drawList.addText(iconFont, fontSize, textX + 1, textY + 1, shadowColor, icon);

        // Draw icon
        drawList.addText(iconFont, fontSize, textX, textY, iconColor, icon);

        // For unconfigured triggers, also draw the type icon in top-left corner
        if (!isConfigured && type.hasIcon() && tileWidth >= 28) {
            ImFont smallFont = EditorFonts.getIconFontTiny();
            int smallSize = (int) smallFont.getFontSize();
            float typeX = minX + 2;
            float typeY = minY + 2;
            int typeColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.7f);
            drawList.addText(smallFont, smallSize, typeX, typeY, typeColor, type.getIcon());
        }
    }

    /**
     * Renders animated selection highlight for selected trigger.
     * Corner markers are always visible, border lines pulse.
     */
    private void renderSelectionHighlight(ImDrawList drawList, EditorCamera camera) {
        int x = selectedTrigger.x();
        int y = selectedTrigger.y();

        Vector2f bottomLeft = camera.worldToScreen(x, y);
        Vector2f topRight = camera.worldToScreen(x + 1, y + 1);

        float minX = viewportX + Math.min(bottomLeft.x, topRight.x);
        float maxX = viewportX + Math.max(bottomLeft.x, topRight.x);
        float minY = viewportY + Math.min(bottomLeft.y, topRight.y);
        float maxY = viewportY + Math.max(bottomLeft.y, topRight.y);

        // Pulsing alpha for border (0.5 to 1.0)
        float pulse = 0.5f + 0.5f * (float) Math.sin(selectionPulse);

        // Yellow/gold selection color - border pulses
        int fillColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.9f, 0.0f, 0.15f * pulse);
        int borderColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.9f, 0.0f, 0.9f * pulse);

        // Draw filled highlight (pulses)
        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        // Draw thick border (pulses)
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 2.5f);

        // Draw corner markers - always fully visible (no pulse)
        float markerSize = Math.min(6, (maxX - minX) * 0.2f);
        int markerColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f);

        // Top-left corner
        drawList.addLine(minX, minY, minX + markerSize, minY, markerColor, 2);
        drawList.addLine(minX, minY, minX, minY + markerSize, markerColor, 2);

        // Top-right corner
        drawList.addLine(maxX, minY, maxX - markerSize, minY, markerColor, 2);
        drawList.addLine(maxX, minY, maxX, minY + markerSize, markerColor, 2);

        // Bottom-left corner
        drawList.addLine(minX, maxY, minX + markerSize, maxY, markerColor, 2);
        drawList.addLine(minX, maxY, minX, maxY - markerSize, markerColor, 2);

        // Bottom-right corner
        drawList.addLine(maxX, maxY, maxX - markerSize, maxY, markerColor, 2);
        drawList.addLine(maxX, maxY, maxX, maxY - markerSize, markerColor, 2);
    }

    /**
     * Sets viewport bounds for rendering.
     */
    public void setViewportBounds(float x, float y, float width, float height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }
}
