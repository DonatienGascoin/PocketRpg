package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.PivotData;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;

/**
 * Pivot editing tab with full texture view and visible pivots.
 * <p>
 * Features:
 * <ul>
 *   <li>Click-to-select sprite cells (multiple mode)</li>
 *   <li>Pivot markers on all sprites (dimmed for non-selected)</li>
 *   <li>Interactive pivot dragging</li>
 *   <li>Preset buttons (3x3 grid)</li>
 *   <li>Pixel snap option</li>
 *   <li>"Apply to All" checkbox for batch editing</li>
 * </ul>
 */
public class PivotEditorTab {

    // ========================================================================
    // CALLBACK
    // ========================================================================

    /**
     * Callback for pivot changes and cell selection.
     */
    public interface Listener {
        void onPivotChanged(float pivotX, float pivotY);
        void onApplyToAllChanged(boolean applyToAll, float pivotX, float pivotY);
        void onCellSelected(int cellIndex);
    }

    // ========================================================================
    // STATE
    // ========================================================================

    // Pivot editing state
    private float pivotX = 0.5f;
    private float pivotY = 0.5f;
    private boolean applyToAll = true;
    private boolean isDragging = false;
    private boolean pixelSnap = false;

    // Selection state
    private int selectedCellIndex = 0;
    private boolean isMultipleMode = false;

    // Sprite dimensions (for pixel snap)
    private int spriteWidth = 16;
    private int spriteHeight = 16;

    // Listener
    private Listener listener;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Sets the listener for pivot changes and cell selection.
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Sets whether in multiple mode (affects UI display).
     */
    public void setMultipleMode(boolean multipleMode) {
        this.isMultipleMode = multipleMode;
    }

    /**
     * Sets the selected cell index.
     */
    public void setSelectedCellIndex(int index) {
        this.selectedCellIndex = index;
    }

    /**
     * Gets the selected cell index.
     */
    public int getSelectedCellIndex() {
        return selectedCellIndex;
    }

    /**
     * Sets the current pivot values.
     */
    public void setPivot(float x, float y) {
        this.pivotX = x;
        this.pivotY = y;
    }

    /**
     * Gets the current pivot X value.
     */
    public float getPivotX() {
        return pivotX;
    }

    /**
     * Gets the current pivot Y value.
     */
    public float getPivotY() {
        return pivotY;
    }

    /**
     * Sets whether "Apply to All" is checked.
     */
    public void setApplyToAll(boolean applyToAll) {
        this.applyToAll = applyToAll;
    }

    /**
     * Gets whether "Apply to All" is checked.
     */
    public boolean isApplyToAll() {
        return applyToAll;
    }

    /**
     * Returns true if currently dragging the pivot.
     */
    public boolean isDragging() {
        return isDragging;
    }

    /**
     * Sets whether pixel snap is enabled.
     */
    public void setPixelSnap(boolean pixelSnap) {
        this.pixelSnap = pixelSnap;
    }

    /**
     * Gets whether pixel snap is enabled.
     */
    public boolean isPixelSnap() {
        return pixelSnap;
    }

    /**
     * Sets the sprite dimensions (for pixel snap calculations).
     */
    public void setSpriteDimensions(int width, int height) {
        this.spriteWidth = width;
        this.spriteHeight = height;
    }

    /**
     * Loads pivot from metadata for the selected sprite.
     */
    public void loadFromMetadata(SpriteMetadata metadata, int spriteIndex) {
        if (metadata == null) {
            pivotX = 0.5f;
            pivotY = 0.5f;
            return;
        }

        if (isMultipleMode) {
            PivotData pivot = metadata.getEffectivePivot(spriteIndex);
            pivotX = pivot.x;
            pivotY = pivot.y;
        } else {
            pivotX = metadata.getPivotXOrDefault();
            pivotY = metadata.getPivotYOrDefault();
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the pivot tab sidebar.
     *
     * @param texture  Current texture
     * @param metadata Current metadata
     */
    public void renderSidebar(Texture texture, SpriteMetadata metadata) {
        // Selected sprite info
        if (isMultipleMode) {
            ImGui.text("Selected: Sprite #" + selectedCellIndex);
        } else {
            ImGui.text("Sprite Info");
        }

        if (texture != null) {
            if (isMultipleMode && metadata != null && metadata.grid != null) {
                ImGui.text("Size: " + metadata.grid.spriteWidth + "x" + metadata.grid.spriteHeight + " px");
            } else {
                ImGui.text("Size: " + texture.getWidth() + "x" + texture.getHeight() + " px");
            }
        }

        ImGui.separator();
        ImGui.spacing();

        // Pivot controls
        ImGui.text("Pivot");

        ImGui.setNextItemWidth(100);
        float[] pivotXArr = {pivotX};
        if (ImGui.sliderFloat("X##PivotX", pivotXArr, 0f, 1f, "%.3f")) {
            pivotX = pivotXArr[0];
            if (pixelSnap) {
                pivotX = applyPixelSnap(pivotX, spriteWidth);
            }
            notifyPivotChanged();
        }

        ImGui.setNextItemWidth(100);
        float[] pivotYArr = {pivotY};
        if (ImGui.sliderFloat("Y##PivotY", pivotYArr, 0f, 1f, "%.3f")) {
            pivotY = pivotYArr[0];
            if (pixelSnap) {
                pivotY = applyPixelSnap(pivotY, spriteHeight);
            }
            notifyPivotChanged();
        }

        ImGui.spacing();

        // Pixel Snap toggle
        boolean wasPixelSnap = pixelSnap;
        if (wasPixelSnap) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button("Pixel Snap", 100, 0)) {
            pixelSnap = !pixelSnap;
            if (pixelSnap) {
                // Apply snap immediately when enabling
                pivotX = applyPixelSnap(pivotX, spriteWidth);
                pivotY = applyPixelSnap(pivotY, spriteHeight);
                notifyPivotChanged();
            }
        }
        if (wasPixelSnap) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Snap pivot to pixel boundaries (" + spriteWidth + "x" + spriteHeight + ")");
        }

        ImGui.spacing();

        // Preset buttons (3x3 grid)
        float buttonSize = 30;
        renderPresetButton("TL", 0f, 1f, buttonSize);
        ImGui.sameLine();
        renderPresetButton("TC", 0.5f, 1f, buttonSize);
        ImGui.sameLine();
        renderPresetButton("TR", 1f, 1f, buttonSize);

        renderPresetButton("ML", 0f, 0.5f, buttonSize);
        ImGui.sameLine();
        renderPresetButton("CC", 0.5f, 0.5f, buttonSize);
        ImGui.sameLine();
        renderPresetButton("MR", 1f, 0.5f, buttonSize);

        renderPresetButton("BL", 0f, 0f, buttonSize);
        ImGui.sameLine();
        renderPresetButton("BC", 0.5f, 0f, buttonSize);
        ImGui.sameLine();
        renderPresetButton("BR", 1f, 0f, buttonSize);

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Apply to all checkbox (multiple mode only)
        if (isMultipleMode) {
            if (ImGui.checkbox("Apply to All Sprites", applyToAll)) {
                boolean wasApplyToAll = applyToAll;
                applyToAll = !applyToAll;

                // Notify listener about the change (it handles propagation)
                if (listener != null) {
                    listener.onApplyToAllChanged(applyToAll, pivotX, pivotY);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("When checked, pivot changes apply to all sprites at once");
            }
        }
    }

    private void renderPresetButton(String label, float x, float y, float size) {
        boolean isSelected = Math.abs(pivotX - x) < 0.01f && Math.abs(pivotY - y) < 0.01f;
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.5f, 0.8f, 1f);
        }
        if (ImGui.button(label, size, size)) {
            pivotX = x;
            pivotY = y;
            notifyPivotChanged();
        }
        if (isSelected) {
            ImGui.popStyleColor();
        }
    }

    // ========================================================================
    // PREVIEW OVERLAY
    // ========================================================================

    /**
     * Draws pivot markers on the preview.
     *
     * @param renderer The texture preview renderer
     * @param metadata The sprite metadata (for reading other sprites' pivots)
     */
    public void drawPreviewOverlay(TexturePreviewRenderer renderer, SpriteMetadata metadata) {
        if (isMultipleMode) {
            // Draw pivot markers for all cells
            int totalCells = renderer.getTotalCells();
            for (int i = 0; i < totalCells; i++) {
                boolean isSelected = (i == selectedCellIndex);
                float px, py;

                if (isSelected) {
                    // Selected sprite uses current editing values
                    px = pivotX;
                    py = pivotY;
                } else if (applyToAll) {
                    // Apply to all: use current editing values
                    px = pivotX;
                    py = pivotY;
                } else if (metadata != null) {
                    // Read from metadata
                    var pivot = metadata.getEffectivePivot(i);
                    px = pivot.x;
                    py = pivot.y;
                } else {
                    px = 0.5f;
                    py = 0.5f;
                }
                renderer.drawPivotMarker(i, px, py, isSelected);
            }
        } else {
            // Single mode - just draw the editing pivot
            renderer.drawPivotMarker(0, pivotX, pivotY, true);
        }

        // Draw selection highlight
        renderer.drawSelectionHighlight(selectedCellIndex);
    }

    // ========================================================================
    // INTERACTION
    // ========================================================================

    /**
     * Handles mouse interaction for pivot dragging.
     *
     * @param renderer The texture preview renderer
     * @return true if interaction was handled (prevents other handlers)
     */
    public boolean handleInteraction(TexturePreviewRenderer renderer) {
        if (!renderer.isHovered() && !isDragging) {
            return false;
        }

        ImVec2 mousePos = ImGui.getMousePos();
        int targetCell = isMultipleMode ? selectedCellIndex : 0;

        // Start drag on mouse click
        if (renderer.isHovered() && !renderer.isPanning() &&
                ImGui.isMouseClicked(ImGuiMouseButton.Left)) {

            // Check if clicking on or near the pivot marker
            float[] pivotScreen = renderer.normalizedToScreen(targetCell, pivotX, pivotY);

            if (pivotScreen != null) {
                float dx = mousePos.x - pivotScreen[0];
                float dy = mousePos.y - pivotScreen[1];
                float distSq = dx * dx + dy * dy;

                // Start drag if within 15 pixels of pivot marker
                if (distSq < 15 * 15) {
                    isDragging = true;
                    return true;
                } else {
                    // Click not on pivot - check for cell selection
                    int hitCell = renderer.hitTestCell(mousePos.x, mousePos.y);
                    if (hitCell >= 0 && hitCell != selectedCellIndex) {
                        if (listener != null) {
                            listener.onCellSelected(hitCell);
                        }
                        return true;
                    } else if (hitCell >= 0) {
                        // Clicked inside the selected cell but not on pivot - start drag from click pos
                        isDragging = true;
                        updatePivotFromMouse(mousePos.x, mousePos.y, targetCell, renderer);
                        return true;
                    }
                }
            }
        }

        // Continue drag
        if (isDragging && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            updatePivotFromMouse(mousePos.x, mousePos.y, targetCell, renderer);
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            return true;
        }

        // End drag
        if (isDragging && !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            isDragging = false;
        }

        return false;
    }

    private void updatePivotFromMouse(float mouseX, float mouseY, int cellIndex, TexturePreviewRenderer renderer) {
        float[] normalized = renderer.screenToNormalized(mouseX, mouseY, cellIndex);
        if (normalized != null) {
            // Clamp to 0-1 range
            pivotX = Math.max(0f, Math.min(1f, normalized[0]));
            pivotY = Math.max(0f, Math.min(1f, normalized[1]));

            // Apply pixel snap if enabled
            if (pixelSnap) {
                pivotX = applyPixelSnap(pivotX, spriteWidth);
                pivotY = applyPixelSnap(pivotY, spriteHeight);
            }

            notifyPivotChanged();
        }
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    private void notifyPivotChanged() {
        if (listener != null) {
            listener.onPivotChanged(pivotX, pivotY);
        }
    }

    private float applyPixelSnap(float value, float size) {
        if (size <= 0) return value;
        float pixelValue = Math.round(value * size);
        return pixelValue / size;
    }
}
