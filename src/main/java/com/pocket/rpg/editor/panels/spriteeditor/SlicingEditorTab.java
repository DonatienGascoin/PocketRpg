package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.GridSettings;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;

/**
 * Slicing tab for configuring grid parameters in multiple mode.
 * <p>
 * Provides UI for:
 * <ul>
 *   <li>Sprite size (width, height)</li>
 *   <li>Spacing between sprites</li>
 *   <li>Offset from texture edge</li>
 *   <li>Preset buttons for common sizes</li>
 * </ul>
 */
public class SlicingEditorTab {

    // ========================================================================
    // CALLBACK
    // ========================================================================

    /**
     * Callback for when grid settings change.
     */
    @FunctionalInterface
    public interface OnChangeListener {
        void onGridSettingsChanged(GridSettings settings);
    }

    // ========================================================================
    // STATE
    // ========================================================================

    // Grid parameters (using ImInt for ImGui binding)
    private final ImInt spriteWidth = new ImInt(16);
    private final ImInt spriteHeight = new ImInt(16);
    private final ImInt spacingX = new ImInt(0);
    private final ImInt spacingY = new ImInt(0);
    private final ImInt offsetX = new ImInt(0);
    private final ImInt offsetY = new ImInt(0);

    // Change listener
    private OnChangeListener onChangeListener;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Sets the change listener called when grid settings are modified.
     */
    public void setOnChangeListener(OnChangeListener listener) {
        this.onChangeListener = listener;
    }

    /**
     * Loads grid settings from metadata into the editor.
     */
    public void loadFromMetadata(SpriteMetadata metadata) {
        if (metadata != null && metadata.grid != null) {
            spriteWidth.set(metadata.grid.spriteWidth);
            spriteHeight.set(metadata.grid.spriteHeight);
            spacingX.set(metadata.grid.spacingX);
            spacingY.set(metadata.grid.spacingY);
            offsetX.set(metadata.grid.offsetX);
            offsetY.set(metadata.grid.offsetY);
        } else {
            // Default values
            spriteWidth.set(16);
            spriteHeight.set(16);
            spacingX.set(0);
            spacingY.set(0);
            offsetX.set(0);
            offsetY.set(0);
        }
    }

    /**
     * Creates a GridSettings object from current editor values.
     */
    public GridSettings toGridSettings() {
        return new GridSettings(
                spriteWidth.get(),
                spriteHeight.get(),
                spacingX.get(),
                spacingY.get(),
                offsetX.get(),
                offsetY.get()
        );
    }

    /**
     * Applies current settings to metadata.
     */
    public void applyToMetadata(SpriteMetadata metadata) {
        if (metadata == null) return;

        if (metadata.grid == null) {
            metadata.grid = new GridSettings();
        }

        metadata.grid.spriteWidth = spriteWidth.get();
        metadata.grid.spriteHeight = spriteHeight.get();
        metadata.grid.spacingX = spacingX.get();
        metadata.grid.spacingY = spacingY.get();
        metadata.grid.offsetX = offsetX.get();
        metadata.grid.offsetY = offsetY.get();
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the slicing tab sidebar.
     *
     * @param texture  Current texture (for calculating grid info)
     * @param metadata Current metadata
     */
    public void renderSidebar(Texture texture, SpriteMetadata metadata) {
        ImGui.text("Grid Settings");
        ImGui.separator();
        ImGui.spacing();

        // Sprite size
        ImGui.text("Sprite Size:");
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("W##GridW", spriteWidth)) {
            if (spriteWidth.get() < 1) spriteWidth.set(1);
            notifyChange();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("H##GridH", spriteHeight)) {
            if (spriteHeight.get() < 1) spriteHeight.set(1);
            notifyChange();
        }

        // Presets
        ImGui.spacing();
        ImGui.text("Presets:");
        if (ImGui.button("8x8")) {
            spriteWidth.set(8);
            spriteHeight.set(8);
            notifyChange();
        }
        ImGui.sameLine();
        if (ImGui.button("16x16")) {
            spriteWidth.set(16);
            spriteHeight.set(16);
            notifyChange();
        }
        ImGui.sameLine();
        if (ImGui.button("32x32")) {
            spriteWidth.set(32);
            spriteHeight.set(32);
            notifyChange();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Spacing
        ImGui.text("Spacing:");
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("X##SpacingX", spacingX)) {
            if (spacingX.get() < 0) spacingX.set(0);
            notifyChange();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Y##SpacingY", spacingY)) {
            if (spacingY.get() < 0) spacingY.set(0);
            notifyChange();
        }

        // Offset
        ImGui.text("Offset:");
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("X##OffsetX", offsetX)) {
            if (offsetX.get() < 0) offsetX.set(0);
            notifyChange();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Y##OffsetY", offsetY)) {
            if (offsetY.get() < 0) offsetY.set(0);
            notifyChange();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Grid info
        renderGridInfo(texture);
    }

    /**
     * Renders grid information (columns, rows, total sprites).
     */
    private void renderGridInfo(Texture texture) {
        if (texture == null) return;

        GridSettings grid = toGridSettings();
        int cols = grid.calculateColumns(texture.getWidth());
        int rows = grid.calculateRows(texture.getHeight());
        int total = cols * rows;

        ImGui.text("Grid: " + cols + " x " + rows + " = " + total + " sprites");

        if (total == 0) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f);
            ImGui.text("Warning: No sprites fit in texture!");
            ImGui.popStyleColor();
        }
    }

    // ========================================================================
    // PREVIEW OVERLAY
    // ========================================================================

    /**
     * Draws the grid overlay on the preview.
     *
     * @param renderer The texture preview renderer
     */
    public void drawPreviewOverlay(TexturePreviewRenderer renderer) {
        renderer.drawGridOverlay(toGridSettings());
        renderer.drawCellNumbers(toGridSettings());
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    private void notifyChange() {
        if (onChangeListener != null) {
            onChangeListener.onGridSettingsChanged(toGridSettings());
        }
    }

    // ========================================================================
    // ACCESSORS
    // ========================================================================

    public int getSpriteWidth() {
        return spriteWidth.get();
    }

    public int getSpriteHeight() {
        return spriteHeight.get();
    }
}
