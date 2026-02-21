package com.pocket.rpg.editor.assets;

import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.GridSettings;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * Preview and inspector renderer for Sprite assets.
 * <p>
 * {@link #renderPreview} shows a basic sprite thumbnail.
 * {@link #renderInspector} shows the full preview with grid overlay,
 * mode indicator, size info, and sprite count.
 */
public class SpritePreviewRenderer implements AssetPreviewRenderer<Sprite> {

    // Grid line color (ABGR format for ImGui) - green with 50% alpha
    private static final int COLOR_GRID = 0x8000FF00;

    // Cached metadata for inspector
    private SpriteMetadata cachedMetadata;
    private String cachedPath;

    @Override
    public void renderPreview(Sprite sprite, float maxSize) {
        if (sprite == null) {
            ImGui.textDisabled("No sprite");
            return;
        }

        Texture texture = sprite.getTexture();
        if (texture == null) {
            ImGui.textDisabled("No preview available");
            return;
        }

        float width = sprite.getWidth();
        float height = sprite.getHeight();

        float scale = Math.min(maxSize / width, maxSize / height);
        if (scale > 1) scale = 1;

        int displayWidth = (int) (width * scale);
        int displayHeight = (int) (height * scale);

        ImGui.image(texture.getTextureId(), displayWidth, displayHeight,
                sprite.getU0(), sprite.getV1(), sprite.getU1(), sprite.getV0());
        ImGui.text((int) width + " x " + (int) height + " px");
    }

    @Override
    public void renderInspector(Sprite sprite, String assetPath, float maxSize) {
        loadMetadataIfNeeded(assetPath);

        // Preview section
        ImGui.text("Preview");
        ImGui.separator();

        if (cachedMetadata != null && cachedMetadata.isMultiple() && cachedMetadata.grid != null) {
            renderPreviewWithGrid(sprite, maxSize);
        } else {
            renderPreview(sprite, maxSize);
        }

        ImGui.separator();

        // Properties section (read-only)
        ImGui.text("Properties");
        ImGui.separator();

        // Mode indicator
        renderModeIndicator();

        // Size (read-only)
        ImGui.textDisabled("Size:");
        ImGui.sameLine();
        ImGui.text(sprite.getWidth() + " x " + sprite.getHeight() + " px");

        // Multiple mode info
        if (cachedMetadata != null && cachedMetadata.isMultiple()) {
            renderMultipleModeInfo(sprite);
        }
    }

    private void renderPreviewWithGrid(Sprite sprite, float maxSize) {
        Texture texture = sprite.getTexture();
        if (texture == null) {
            ImGui.textDisabled("No texture");
            return;
        }

        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();

        float scale = Math.min(maxSize / textureWidth, maxSize / textureHeight);
        if (scale > 1) scale = 1;

        int displayWidth = (int) (textureWidth * scale);
        int displayHeight = (int) (textureHeight * scale);

        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        ImGui.image(texture.getTextureId(), displayWidth, displayHeight, 0, 1, 1, 0);

        // Draw grid overlay
        GridSettings grid = cachedMetadata.grid;
        ImDrawList drawList = ImGui.getWindowDrawList();

        float cellW = grid.spriteWidth * scale;
        float cellH = grid.spriteHeight * scale;
        float spacingX = grid.spacingX * scale;
        float spacingY = grid.spacingY * scale;
        float offsetX = grid.offsetX * scale;
        float offsetY = grid.offsetY * scale;

        int gridColumns = grid.calculateColumns(textureWidth);
        int gridRows = grid.calculateRows(textureHeight);

        float drawX = cursorPos.x;
        float drawY = cursorPos.y;

        for (int col = 0; col <= gridColumns; col++) {
            float lineX = drawX + offsetX + col * (cellW + spacingX);
            if (col < gridColumns) {
                float lineEndY = drawY + offsetY + gridRows * (cellH + spacingY) - spacingY;
                drawList.addLine(lineX, drawY + offsetY, lineX, lineEndY, COLOR_GRID, 1.0f);
                drawList.addLine(lineX + cellW, drawY + offsetY, lineX + cellW, lineEndY, COLOR_GRID, 1.0f);
            }
        }

        for (int row = 0; row <= gridRows; row++) {
            float lineY = drawY + offsetY + row * (cellH + spacingY);
            if (row < gridRows) {
                float lineEndX = drawX + offsetX + gridColumns * (cellW + spacingX) - spacingX;
                drawList.addLine(drawX + offsetX, lineY, lineEndX, lineY, COLOR_GRID, 1.0f);
                drawList.addLine(drawX + offsetX, lineY + cellH, lineEndX, lineY + cellH, COLOR_GRID, 1.0f);
            }
        }

        ImGui.text(textureWidth + " x " + textureHeight + " px");
    }

    private void renderModeIndicator() {
        ImGui.textDisabled("Mode:");
        ImGui.sameLine();

        if (cachedMetadata == null || cachedMetadata.isSingle()) {
            ImGui.text("Single");
        } else {
            EditorColors.textColored(EditorColors.INFO, "Multiple");
        }
    }

    private void renderMultipleModeInfo(Sprite sprite) {
        if (cachedMetadata.grid == null) return;

        var grid = cachedMetadata.grid;

        ImGui.textDisabled("Sprite Size:");
        ImGui.sameLine();
        ImGui.text(grid.spriteWidth + " x " + grid.spriteHeight + " px");

        int textureWidth = sprite.getTexture().getWidth();
        int textureHeight = sprite.getTexture().getHeight();
        int spriteCount = grid.calculateTotalSprites(textureWidth, textureHeight);

        ImGui.textDisabled("Sprites:");
        ImGui.sameLine();
        ImGui.text(String.valueOf(spriteCount));

        if (cachedMetadata.isUsableAsTileset()) {
            ImGui.textDisabled("Tileset:");
            ImGui.sameLine();
            ImGui.text("Yes");
        }
    }

    private void loadMetadataIfNeeded(String assetPath) {
        if (assetPath == null) {
            cachedMetadata = null;
            cachedPath = null;
            return;
        }

        if (!assetPath.equals(cachedPath)) {
            cachedPath = assetPath;
            cachedMetadata = AssetMetadata.load(assetPath, SpriteMetadata.class);
        }
    }

    @Override
    public Class<Sprite> getAssetType() {
        return Sprite.class;
    }
}
