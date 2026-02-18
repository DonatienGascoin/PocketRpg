package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.assets.AssetPreviewRegistry;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.OpenSpriteEditorEvent;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.GridSettings;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImFloat;

/**
 * Inspector renderer for Sprite assets.
 * <p>
 * Features:
 * <ul>
 *   <li>Preview with grid overlay for multiple mode</li>
 *   <li>Size info (read-only)</li>
 *   <li>Mode indicator (Single/Multiple)</li>
 *   <li>PPU override (Single mode only)</li>
 *   <li>"Open Sprite Editor" button</li>
 * </ul>
 * <p>
 * Pivot editing is done in the Sprite Editor for more control.
 */
public class SpriteInspectorRenderer implements AssetInspectorRenderer<Sprite> {

    // Grid line color (ABGR format for ImGui) - green with 50% alpha
    private static final int COLOR_GRID = 0x8000FF00;

    // Cached metadata for editing
    private SpriteMetadata cachedMetadata;
    private String cachedPath;
    private Sprite cachedSprite;

    // Editable values
    private final ImFloat pixelsPerUnit = new ImFloat();

    // Change tracking
    private boolean hasChanges = false;

    @Override
    public boolean render(Sprite sprite, String assetPath, float maxPreviewSize) {
        // Load metadata if path changed
        loadMetadataIfNeeded(assetPath);
        cachedSprite = sprite;

        // Preview section
        ImGui.text("Preview");
        ImGui.separator();

        // Use custom preview with grid for multiple mode, otherwise use default
        if (cachedMetadata != null && cachedMetadata.isMultiple() && cachedMetadata.grid != null) {
            renderPreviewWithGrid(sprite, maxPreviewSize);
        } else {
            AssetPreviewRegistry.render(sprite, maxPreviewSize);
        }

        ImGui.separator();

        // Properties section
        ImGui.text("Properties");
        ImGui.separator();

        // Mode indicator
        renderModeIndicator();

        // Size (read-only)
        ImGui.textDisabled("Size:");
        ImGui.sameLine();
        ImGui.text(sprite.getWidth() + " x " + sprite.getHeight() + " px");

        // Mode-specific fields
        if (cachedMetadata == null || cachedMetadata.isSingle()) {
            renderSingleModeFields();
        } else {
            renderMultipleModeFields(sprite);
        }

        ImGui.separator();

        // Open Sprite Editor button
        renderOpenEditorButton(assetPath);

        return hasChanges;
    }

    /**
     * Renders the preview with grid overlay for multiple mode sprites.
     */
    private void renderPreviewWithGrid(Sprite sprite, float maxSize) {
        Texture texture = sprite.getTexture();
        if (texture == null) {
            ImGui.textDisabled("No texture");
            return;
        }

        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();

        // Calculate display size (fit to maxSize while maintaining aspect ratio)
        float scale = Math.min(maxSize / textureWidth, maxSize / textureHeight);
        if (scale > 1) scale = 1; // Don't upscale

        int displayWidth = (int) (textureWidth * scale);
        int displayHeight = (int) (textureHeight * scale);

        // Get cursor position before drawing
        ImVec2 cursorPos = ImGui.getCursorScreenPos();

        // Draw the texture (V flipped for OpenGL)
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

        // Draw vertical lines
        for (int col = 0; col <= gridColumns; col++) {
            float lineX = drawX + offsetX + col * (cellW + spacingX);
            if (col < gridColumns) {
                // Left edge of cell
                float lineEndY = drawY + offsetY + gridRows * (cellH + spacingY) - spacingY;
                drawList.addLine(lineX, drawY + offsetY, lineX, lineEndY, COLOR_GRID, 1.0f);
                // Right edge of cell
                drawList.addLine(lineX + cellW, drawY + offsetY, lineX + cellW, lineEndY, COLOR_GRID, 1.0f);
            }
        }

        // Draw horizontal lines
        for (int row = 0; row <= gridRows; row++) {
            float lineY = drawY + offsetY + row * (cellH + spacingY);
            if (row < gridRows) {
                // Top edge of cell
                float lineEndX = drawX + offsetX + gridColumns * (cellW + spacingX) - spacingX;
                drawList.addLine(drawX + offsetX, lineY, lineEndX, lineY, COLOR_GRID, 1.0f);
                // Bottom edge of cell
                drawList.addLine(drawX + offsetX, lineY + cellH, lineEndX, lineY + cellH, COLOR_GRID, 1.0f);
            }
        }

        // Show dimensions text
        ImGui.text(textureWidth + " x " + textureHeight + " px");
    }

    /**
     * Renders the mode indicator (Single/Multiple).
     */
    private void renderModeIndicator() {
        ImGui.textDisabled("Mode:");
        ImGui.sameLine();

        if (cachedMetadata == null || cachedMetadata.isSingle()) {
            ImGui.text("Single");
        } else {
            EditorColors.textColored(EditorColors.INFO, "Multiple");
        }
    }

    /**
     * Renders fields for single mode sprites.
     */
    private void renderSingleModeFields() {
        // Pixels per unit override
        ImGui.textDisabled("PPU Override:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputFloat("##ppu", pixelsPerUnit, 1, 8, "%.0f")) {
            pixelsPerUnit.set(Math.max(0, pixelsPerUnit.get()));
            hasChanges = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Clear##ppu")) {
            pixelsPerUnit.set(0);
            hasChanges = true;
        }
        if (pixelsPerUnit.get() == 0) {
            ImGui.sameLine();
            ImGui.textDisabled("(using default)");
        }
    }

    /**
     * Renders info for multiple mode sprites.
     */
    private void renderMultipleModeFields(Sprite sprite) {
        if (cachedMetadata == null || cachedMetadata.grid == null) {
            return;
        }

        var grid = cachedMetadata.grid;

        ImGui.textDisabled("Sprite Size:");
        ImGui.sameLine();
        ImGui.text(grid.spriteWidth + " x " + grid.spriteHeight + " px");

        // Calculate sprite count from parent texture
        int textureWidth = sprite.getTexture().getWidth();
        int textureHeight = sprite.getTexture().getHeight();
        int spriteCount = grid.calculateTotalSprites(textureWidth, textureHeight);

        ImGui.textDisabled("Sprites:");
        ImGui.sameLine();
        ImGui.text(String.valueOf(spriteCount));

        ImGui.spacing();
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f,
                "Use Sprite Editor for pivot and 9-slice editing.");
    }

    /**
     * Renders the "Open Sprite Editor" button.
     */
    private void renderOpenEditorButton(String assetPath) {
        float buttonWidth = ImGui.getContentRegionAvailX();

        if (ImGui.button(MaterialIcons.Edit + " Open Sprite Editor...", buttonWidth, 0)) {
            EditorEventBus.get().publish(new OpenSpriteEditorEvent(assetPath));
        }
    }

    /**
     * Loads metadata from disk if the path changed.
     */
    private void loadMetadataIfNeeded(String assetPath) {
        if (assetPath == null) {
            cachedMetadata = null;
            cachedPath = null;
            return;
        }

        if (!assetPath.equals(cachedPath)) {
            cachedPath = assetPath;
            cachedMetadata = AssetMetadata.load(assetPath, SpriteMetadata.class);
            hasChanges = false;

            // Initialize editable values from metadata
            if (cachedMetadata != null && cachedMetadata.isSingle()) {
                pixelsPerUnit.set(cachedMetadata.pixelsPerUnitOverride != null ?
                        cachedMetadata.pixelsPerUnitOverride : 0);
            } else {
                pixelsPerUnit.set(0);
            }
        }
    }

    @Override
    public boolean hasEditableProperties() {
        // Only single mode has editable PPU
        return cachedMetadata == null || cachedMetadata.isSingle();
    }

    @Override
    public void save(Sprite sprite, String assetPath) {
        if (assetPath == null) return;

        // Create metadata if it doesn't exist
        if (cachedMetadata == null) {
            cachedMetadata = new SpriteMetadata();
        }

        // Update single mode fields
        if (cachedMetadata.isSingle()) {
            // Only set PPU override if non-zero
            if (pixelsPerUnit.get() > 0) {
                cachedMetadata.pixelsPerUnitOverride = pixelsPerUnit.get();
            } else {
                cachedMetadata.pixelsPerUnitOverride = null;
            }
        }

        // Save to disk
        try {
            AssetMetadata.save(assetPath, cachedMetadata);
            hasChanges = false;
        } catch (java.io.IOException e) {
            System.err.println("Failed to save sprite metadata: " + e.getMessage());
        }
    }

    @Override
    public void onDeselect() {
        cachedMetadata = null;
        cachedPath = null;
        cachedSprite = null;
        hasChanges = false;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return hasChanges;
    }

    @Override
    public Class<Sprite> getAssetType() {
        return Sprite.class;
    }
}
