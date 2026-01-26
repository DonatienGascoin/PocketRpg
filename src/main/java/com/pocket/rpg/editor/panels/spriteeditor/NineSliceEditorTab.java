package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.rendering.resources.NineSliceData;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.SpriteMetadata;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImInt;

/**
 * 9-Slice editing tab with full texture view.
 * <p>
 * Features:
 * <ul>
 *   <li>Click-to-select sprite cells (multiple mode)</li>
 *   <li>9-slice borders visualization on selected sprite</li>
 *   <li>Interactive border dragging</li>
 *   <li>Preset buttons for common sizes</li>
 *   <li>"Apply to All" checkbox for batch editing</li>
 * </ul>
 */
public class NineSliceEditorTab {

    // ========================================================================
    // CALLBACK
    // ========================================================================

    /**
     * Callback for 9-slice changes and cell selection.
     */
    public interface Listener {
        void onSliceChanged(int left, int right, int top, int bottom);
        void onApplyToAllChanged(boolean applyToAll, NineSliceData sliceData);
        void onCellSelected(int cellIndex);
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    /**
     * Which border is being dragged.
     */
    public enum DragBorder {
        LEFT, RIGHT, TOP, BOTTOM
    }

    // ========================================================================
    // STATE
    // ========================================================================

    // 9-Slice editing state (using ImInt for ImGui binding)
    private final ImInt sliceLeft = new ImInt(0);
    private final ImInt sliceRight = new ImInt(0);
    private final ImInt sliceTop = new ImInt(0);
    private final ImInt sliceBottom = new ImInt(0);
    private boolean applyToAll = true;

    // Drag state
    private DragBorder draggingBorder = null;

    // Selection state
    private int selectedCellIndex = 0;
    private boolean isMultipleMode = false;

    // Sprite dimensions (needed for drag clamping)
    private int spriteWidth = 16;
    private int spriteHeight = 16;

    // Listener
    private Listener listener;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Sets the listener for 9-slice changes and cell selection.
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
     * Sets the current 9-slice values.
     */
    public void setSlice(int left, int right, int top, int bottom) {
        sliceLeft.set(left);
        sliceRight.set(right);
        sliceTop.set(top);
        sliceBottom.set(bottom);
    }

    /**
     * Sets the sprite dimensions (for drag clamping).
     */
    public void setSpriteDimensions(int width, int height) {
        this.spriteWidth = width;
        this.spriteHeight = height;
    }

    /**
     * Gets the current 9-slice data.
     */
    public NineSliceData getSliceData() {
        return new NineSliceData(sliceLeft.get(), sliceRight.get(), sliceTop.get(), sliceBottom.get());
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
     * Returns true if currently dragging a border.
     */
    public boolean isDragging() {
        return draggingBorder != null;
    }

    /**
     * Loads 9-slice data from metadata for the selected sprite.
     */
    public void loadFromMetadata(SpriteMetadata metadata, int spriteIndex) {
        NineSliceData data;

        if (metadata == null) {
            data = null;
        } else if (isMultipleMode) {
            data = metadata.getEffectiveNineSlice(spriteIndex);
        } else {
            data = metadata.nineSlice;
        }

        if (data != null) {
            sliceLeft.set(data.left);
            sliceRight.set(data.right);
            sliceTop.set(data.top);
            sliceBottom.set(data.bottom);
        } else {
            sliceLeft.set(0);
            sliceRight.set(0);
            sliceTop.set(0);
            sliceBottom.set(0);
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the 9-slice tab sidebar.
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

        // 9-Slice borders
        ImGui.text("9-Slice Borders");

        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("L##SliceL", sliceLeft)) {
            if (sliceLeft.get() < 0) sliceLeft.set(0);
            notifySliceChanged();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("R##SliceR", sliceRight)) {
            if (sliceRight.get() < 0) sliceRight.set(0);
            notifySliceChanged();
        }

        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("T##SliceT", sliceTop)) {
            if (sliceTop.get() < 0) sliceTop.set(0);
            notifySliceChanged();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("B##SliceB", sliceBottom)) {
            if (sliceBottom.get() < 0) sliceBottom.set(0);
            notifySliceChanged();
        }

        ImGui.spacing();

        // Preset buttons
        ImGui.text("Presets:");
        if (ImGui.button("4px")) {
            setSlice(4, 4, 4, 4);
            notifySliceChanged();
        }
        ImGui.sameLine();
        if (ImGui.button("8px")) {
            setSlice(8, 8, 8, 8);
            notifySliceChanged();
        }
        ImGui.sameLine();
        if (ImGui.button("16px")) {
            setSlice(16, 16, 16, 16);
            notifySliceChanged();
        }
        ImGui.sameLine();
        if (ImGui.button("Clear")) {
            setSlice(0, 0, 0, 0);
            notifySliceChanged();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Apply to all checkbox (multiple mode only)
        if (isMultipleMode) {
            if (ImGui.checkbox("Apply to All Sprites##Slice", applyToAll)) {
                boolean wasApplyToAll = applyToAll;
                applyToAll = !applyToAll;

                // Notify listener about the change (it handles propagation)
                if (listener != null) {
                    listener.onApplyToAllChanged(applyToAll, getSliceData());
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("When checked, 9-slice changes apply to all sprites at once");
            }
        }
    }

    // ========================================================================
    // PREVIEW OVERLAY
    // ========================================================================

    /**
     * Draws 9-slice borders on the preview.
     *
     * @param renderer The texture preview renderer
     * @param metadata The sprite metadata (for reading other sprites' slices)
     */
    public void drawPreviewOverlay(TexturePreviewRenderer renderer, SpriteMetadata metadata) {
        NineSliceData editSliceData = getSliceData();

        if (isMultipleMode) {
            // Draw 9-slice borders for all cells
            int totalCells = renderer.getTotalCells();
            for (int i = 0; i < totalCells; i++) {
                boolean isSelected = (i == selectedCellIndex);
                NineSliceData sliceData;

                if (isSelected) {
                    // Selected sprite uses current editing values
                    sliceData = editSliceData;
                } else if (applyToAll) {
                    // Apply to all: use current editing values
                    sliceData = editSliceData;
                } else if (metadata != null) {
                    // Read from metadata
                    sliceData = metadata.getEffectiveNineSlice(i);
                } else {
                    sliceData = null;
                }

                if (sliceData != null && sliceData.hasSlicing()) {
                    renderer.drawNineSliceBorders(i, sliceData);
                }
            }
        } else {
            // Single mode
            if (editSliceData.hasSlicing()) {
                renderer.drawNineSliceBorders(0, editSliceData);
            }
        }

        // Draw selection highlight
        renderer.drawSelectionHighlight(selectedCellIndex);
    }

    // ========================================================================
    // INTERACTION
    // ========================================================================

    /**
     * Handles mouse interaction for border dragging.
     *
     * @param renderer The texture preview renderer
     * @return true if interaction was handled (prevents other handlers)
     */
    public boolean handleInteraction(TexturePreviewRenderer renderer) {
        if (!renderer.isHovered() && draggingBorder == null) {
            return false;
        }

        int targetCell = isMultipleMode ? selectedCellIndex : 0;
        float[] cellRect = renderer.cellToScreenRect(targetCell);
        if (cellRect == null) return false;

        float cellX = cellRect[0];
        float cellY = cellRect[1];
        float cellW = cellRect[2];
        float cellH = cellRect[3];
        float zoom = renderer.getZoom();

        // Calculate current border positions in screen space
        float leftLineX = cellX + sliceLeft.get() * zoom;
        float rightLineX = cellX + cellW - sliceRight.get() * zoom;
        float topLineY = cellY + sliceTop.get() * zoom;
        float bottomLineY = cellY + cellH - sliceBottom.get() * zoom;

        float hitThreshold = 8f;
        ImVec2 mousePos = ImGui.getMousePos();

        // Start drag on mouse click
        if (renderer.isHovered() && !renderer.isPanning() &&
                ImGui.isMouseClicked(ImGuiMouseButton.Left)) {

            // Check which border line is closest to mouse
            DragBorder hitBorder = null;
            float minDist = hitThreshold;

            // Check vertical borders (left/right)
            if (mousePos.y >= cellY && mousePos.y <= cellY + cellH) {
                float distLeft = Math.abs(mousePos.x - leftLineX);
                float distRight = Math.abs(mousePos.x - rightLineX);

                if (distLeft < minDist) {
                    minDist = distLeft;
                    hitBorder = DragBorder.LEFT;
                }
                if (distRight < minDist) {
                    minDist = distRight;
                    hitBorder = DragBorder.RIGHT;
                }
            }

            // Check horizontal borders (top/bottom)
            if (mousePos.x >= cellX && mousePos.x <= cellX + cellW) {
                float distTop = Math.abs(mousePos.y - topLineY);
                float distBottom = Math.abs(mousePos.y - bottomLineY);

                if (distTop < minDist) {
                    minDist = distTop;
                    hitBorder = DragBorder.TOP;
                }
                if (distBottom < minDist) {
                    minDist = distBottom;
                    hitBorder = DragBorder.BOTTOM;
                }
            }

            if (hitBorder != null) {
                draggingBorder = hitBorder;
                return true;
            } else {
                // Not on a border - check for cell selection
                int hitCell = renderer.hitTestCell(mousePos.x, mousePos.y);
                if (hitCell >= 0 && hitCell != selectedCellIndex) {
                    if (listener != null) {
                        listener.onCellSelected(hitCell);
                    }
                    return true;
                }
            }
        }

        // Continue drag
        if (draggingBorder != null && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            updateBorderFromMouse(mousePos.x, mousePos.y, cellRect, zoom);

            // Set appropriate cursor
            if (draggingBorder == DragBorder.LEFT || draggingBorder == DragBorder.RIGHT) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            } else {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
            }
            return true;
        }

        // End drag
        if (draggingBorder != null && !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            draggingBorder = null;
        }

        // Show resize cursor when hovering over borders or edges
        if (draggingBorder == null && renderer.isHovered()) {
            boolean nearVertical = false;
            boolean nearHorizontal = false;

            if (mousePos.y >= cellY && mousePos.y <= cellY + cellH) {
                if (Math.abs(mousePos.x - leftLineX) < hitThreshold ||
                        Math.abs(mousePos.x - rightLineX) < hitThreshold) {
                    nearVertical = true;
                }
            }
            if (mousePos.x >= cellX && mousePos.x <= cellX + cellW) {
                if (Math.abs(mousePos.y - topLineY) < hitThreshold ||
                        Math.abs(mousePos.y - bottomLineY) < hitThreshold) {
                    nearHorizontal = true;
                }
            }

            if (nearVertical) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            } else if (nearHorizontal) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
            }
        }

        return false;
    }

    private void updateBorderFromMouse(float mouseX, float mouseY, float[] cellRect, float zoom) {
        if (draggingBorder == null) return;

        float cellX = cellRect[0];
        float cellY = cellRect[1];
        float cellW = cellRect[2];
        float cellH = cellRect[3];

        switch (draggingBorder) {
            case LEFT -> {
                float pixelX = (mouseX - cellX) / zoom;
                int value = Math.max(0, Math.min(spriteWidth / 2, Math.round(pixelX)));
                sliceLeft.set(value);
            }
            case RIGHT -> {
                float pixelX = (cellX + cellW - mouseX) / zoom;
                int value = Math.max(0, Math.min(spriteWidth / 2, Math.round(pixelX)));
                sliceRight.set(value);
            }
            case TOP -> {
                float pixelY = (mouseY - cellY) / zoom;
                int value = Math.max(0, Math.min(spriteHeight / 2, Math.round(pixelY)));
                sliceTop.set(value);
            }
            case BOTTOM -> {
                float pixelY = (cellY + cellH - mouseY) / zoom;
                int value = Math.max(0, Math.min(spriteHeight / 2, Math.round(pixelY)));
                sliceBottom.set(value);
            }
        }
        notifySliceChanged();
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    private void notifySliceChanged() {
        if (listener != null) {
            listener.onSliceChanged(sliceLeft.get(), sliceRight.get(), sliceTop.get(), sliceBottom.get());
        }
    }
}
