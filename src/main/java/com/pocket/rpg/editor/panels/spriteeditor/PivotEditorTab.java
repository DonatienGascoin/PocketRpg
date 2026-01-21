package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;

/**
 * Pivot editing tab for the Sprite Editor.
 * <p>
 * Features:
 * <ul>
 *   <li>Interactive preview with draggable pivot point</li>
 *   <li>3x3 preset grid for common pivot positions</li>
 *   <li>Pixel snap option</li>
 *   <li>Crosshair and grid overlays</li>
 * </ul>
 */
public class PivotEditorTab {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    private static final float[][] PRESETS = {
            {0.0f, 1.0f},  // Top-Left
            {0.5f, 1.0f},  // Top-Center
            {1.0f, 1.0f},  // Top-Right
            {0.0f, 0.5f},  // Middle-Left
            {0.5f, 0.5f},  // Center
            {1.0f, 0.5f},  // Middle-Right
            {0.0f, 0.0f},  // Bottom-Left
            {0.5f, 0.0f},  // Bottom-Center (Recommended)
            {1.0f, 0.0f},  // Bottom-Right
    };

    private static final String[] PRESET_LABELS = {
            MaterialIcons.NorthWest, MaterialIcons.North, MaterialIcons.NorthEast,
            MaterialIcons.West, MaterialIcons.Adjust, MaterialIcons.East,
            MaterialIcons.SouthWest, MaterialIcons.South, MaterialIcons.SouthEast
    };

    private static final String[] PRESET_TOOLTIPS = {
            "Top-Left (0, 1)",
            "Top-Center (0.5, 1)",
            "Top-Right (1, 1)",
            "Middle-Left (0, 0.5)",
            "Center (0.5, 0.5)",
            "Middle-Right (1, 0.5)",
            "Bottom-Left (0, 0)",
            "Bottom-Center (0.5, 0) - Recommended for characters",
            "Bottom-Right (1, 0)"
    };

    // ========================================================================
    // STATE
    // ========================================================================

    // Pivot values
    private float pivotX = 0.5f;
    private float pivotY = 0.5f;

    // Original values (for revert)
    private float originalPivotX = 0.5f;
    private float originalPivotY = 0.5f;

    // Options
    private boolean showCrosshair = true;
    private boolean pixelSnap = false;

    // Drag state
    private boolean isDraggingPivot = false;
    private Float dragStartPivotX = null;
    private Float dragStartPivotY = null;

    // Undo tracking for input fields
    private Float undoStartPivotX = null;
    private Float undoStartPivotY = null;

    // Shared preview renderer
    private final SpritePreviewRenderer previewRenderer;

    // Current sprite reference
    private Sprite currentSprite;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public PivotEditorTab(SpritePreviewRenderer previewRenderer) {
        this.previewRenderer = previewRenderer;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    public float getPivotX() {
        return pivotX;
    }

    public float getPivotY() {
        return pivotY;
    }

    public void setPivot(float x, float y) {
        this.pivotX = x;
        this.pivotY = y;
    }

    public void setOriginalPivot(float x, float y) {
        this.originalPivotX = x;
        this.originalPivotY = y;
    }

    public void revertToOriginal() {
        pivotX = originalPivotX;
        pivotY = originalPivotY;
    }

    public void updateOriginal() {
        originalPivotX = pivotX;
        originalPivotY = pivotY;
    }

    public boolean isShowCrosshair() {
        return showCrosshair;
    }

    public boolean isPixelSnap() {
        return pixelSnap;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the pivot tab content.
     *
     * @param sprite The sprite being edited
     * @param availableWidth Available width for the tab
     * @param availableHeight Available height for the tab
     */
    public void render(Sprite sprite, float availableWidth, float availableHeight) {
        this.currentSprite = sprite;

        // 60% preview / 40% controls layout
        float previewWidth = availableWidth * 0.60f;
        float controlsWidth = availableWidth * 0.40f - 5;

        // Left: Preview area
        if (ImGui.beginChild("PivotPreviewArea", previewWidth, availableHeight, true)) {
            renderPreview(sprite);
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right: Controls panel
        if (ImGui.beginChild("PivotControlsPanel", controlsWidth, availableHeight, true)) {
            renderControls();
        }
        ImGui.endChild();
    }

    private void renderPreview(Sprite sprite) {
        float availWidth = ImGui.getContentRegionAvailX();
        float availHeight = ImGui.getContentRegionAvailY();

        if (!previewRenderer.beginPreview(sprite, availWidth, availHeight)) {
            return;
        }

        ImDrawList drawList = previewRenderer.getDrawList();
        float drawX = previewRenderer.getDrawX();
        float drawY = previewRenderer.getDrawY();
        float displayWidth = previewRenderer.getDisplayWidth();
        float displayHeight = previewRenderer.getDisplayHeight();

        // Calculate pivot screen position
        float pivotScreenX = previewRenderer.normalizedToScreenX(pivotX);
        float pivotScreenY = previewRenderer.normalizedToScreenY(pivotY);

        // Draw crosshair
        if (showCrosshair) {
            int crosshairColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 0f, 0.5f);
            drawList.addLine(pivotScreenX, drawY, pivotScreenX, drawY + displayHeight, crosshairColor);
            drawList.addLine(drawX, pivotScreenY, drawX + displayWidth, pivotScreenY, crosshairColor);
        }

        // Draw pivot marker
        int pivotOuterColor = ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f);
        int pivotInnerColor = ImGui.colorConvertFloat4ToU32(1f, 0.2f, 0.2f, 1f);
        drawList.addCircleFilled(pivotScreenX, pivotScreenY, 8, pivotOuterColor);
        drawList.addCircleFilled(pivotScreenX, pivotScreenY, 6, pivotInnerColor);
        drawList.addCircle(pivotScreenX, pivotScreenY, 8, pivotOuterColor, 0, 2);

        // Plus sign in pivot
        int plusColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
        drawList.addLine(pivotScreenX - 4, pivotScreenY, pivotScreenX + 4, pivotScreenY, plusColor, 2);
        drawList.addLine(pivotScreenX, pivotScreenY - 4, pivotScreenX, pivotScreenY + 4, plusColor, 2);

        previewRenderer.endPreview();

        // Handle pivot dragging (only if not panning)
        if (!previewRenderer.isPanning()) {
            handlePivotDragging();
        }
    }

    private void handlePivotDragging() {
        if (!previewRenderer.isHovered() && !isDraggingPivot) {
            return;
        }

        if (previewRenderer.isHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }

        // Start drag
        if (previewRenderer.isHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            isDraggingPivot = true;
            dragStartPivotX = pivotX;
            dragStartPivotY = pivotY;
        }

        // Continue drag
        if (isDraggingPivot) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                ImVec2 mousePos = ImGui.getMousePos();
                float newPivotX = previewRenderer.screenToNormalizedX(mousePos.x);
                float newPivotY = previewRenderer.screenToNormalizedY(mousePos.y);

                // Apply pixel snap if enabled
                if (pixelSnap && currentSprite != null) {
                    newPivotX = applyPixelSnap(newPivotX, currentSprite.getWidth());
                    newPivotY = applyPixelSnap(newPivotY, currentSprite.getHeight());
                }

                // Clamp to 0-1
                pivotX = Math.max(0, Math.min(1, newPivotX));
                pivotY = Math.max(0, Math.min(1, newPivotY));
            } else {
                // Drag ended - push undo command if values changed
                isDraggingPivot = false;
                if (dragStartPivotX != null && dragStartPivotY != null) {
                    float startX = dragStartPivotX;
                    float startY = dragStartPivotY;
                    float endX = pivotX;
                    float endY = pivotY;
                    dragStartPivotX = null;
                    dragStartPivotY = null;

                    if (startX != endX || startY != endY) {
                        UndoManager.getInstance().push(new SetterUndoCommand<>(
                                v -> { pivotX = v[0]; pivotY = v[1]; },
                                new float[]{startX, startY},
                                new float[]{endX, endY},
                                "Change Pivot"
                        ));
                    }
                }
            }
        }
    }

    private void renderControls() {
        float btnWidth = -1;
        float btnHeight = 28;
        float smallBtnSize = 38;

        // === PIVOT VALUES SECTION ===
        ImGui.text("Pivot");
        ImGui.separator();

        // X field
        float[] xArr = {pivotX};
        ImGui.text("X:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f - 12);
        if (ImGui.dragFloat("##PivotX", xArr, 0.01f, 0f, 1f, "%.3f")) {
            pivotX = Math.max(0, Math.min(1, xArr[0]));
            if (pixelSnap && currentSprite != null) {
                pivotX = applyPixelSnap(pivotX, currentSprite.getWidth());
            }
        }
        if (ImGui.isItemActivated()) {
            undoStartPivotX = pivotX;
        }
        if (ImGui.isItemDeactivatedAfterEdit() && undoStartPivotX != null) {
            float startVal = undoStartPivotX;
            float endVal = pivotX;
            undoStartPivotX = null;
            if (startVal != endVal) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        v -> pivotX = v, startVal, endVal, "Change Pivot X"
                ));
            }
        }

        // Y field
        float[] yArr = {pivotY};
        ImGui.sameLine();
        ImGui.text("Y:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 4);
        if (ImGui.dragFloat("##PivotY", yArr, 0.01f, 0f, 1f, "%.3f")) {
            pivotY = Math.max(0, Math.min(1, yArr[0]));
            if (pixelSnap && currentSprite != null) {
                pivotY = applyPixelSnap(pivotY, currentSprite.getHeight());
            }
        }
        if (ImGui.isItemActivated()) {
            undoStartPivotY = pivotY;
        }
        if (ImGui.isItemDeactivatedAfterEdit() && undoStartPivotY != null) {
            float startVal = undoStartPivotY;
            float endVal = pivotY;
            undoStartPivotY = null;
            if (startVal != endVal) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        v -> pivotY = v, startVal, endVal, "Change Pivot Y"
                ));
            }
        }

        ImGui.spacing();

        // === PRESETS SECTION ===
        ImGui.text("Presets");
        ImGui.separator();

        // 3x3 grid of preset buttons
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;

                if (col > 0) {
                    ImGui.sameLine();
                }

                boolean isSelected = Math.abs(pivotX - PRESETS[index][0]) < 0.01f &&
                        Math.abs(pivotY - PRESETS[index][1]) < 0.01f;

                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.5f, 0.7f, 1f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.6f, 0.8f, 1f);
                }

                if (ImGui.button(PRESET_LABELS[index] + "##preset" + index, smallBtnSize, smallBtnSize)) {
                    setPivotWithUndo(PRESETS[index][0], PRESETS[index][1]);
                }

                if (isSelected) {
                    ImGui.popStyleColor(2);
                }

                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PRESET_TOOLTIPS[index]);
                }
            }
        }

        ImGui.spacing();

        // === OPTIONS SECTION ===
        ImGui.text("Options");
        ImGui.separator();

        // Pixel Snap button
        // Store state before button - must use same value for push and pop
        boolean wasPixelSnap = pixelSnap;
        if (wasPixelSnap) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button(MaterialIcons.Grid4x4 + " Pixel Snap", btnWidth, btnHeight)) {
            pixelSnap = !pixelSnap;
            if (pixelSnap && currentSprite != null) {
                pivotX = applyPixelSnap(pivotX, currentSprite.getWidth());
                pivotY = applyPixelSnap(pivotY, currentSprite.getHeight());
            }
        }
        if (wasPixelSnap) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered() && currentSprite != null) {
            ImGui.setTooltip(String.format("Snap pivot to pixel boundaries (%dx%d)",
                    (int) currentSprite.getWidth(), (int) currentSprite.getHeight()));
        }

        // Grid button
        boolean showGrid = previewRenderer.isShowGrid();
        if (showGrid) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button(MaterialIcons.GridOn + " Grid", btnWidth, btnHeight)) {
            previewRenderer.setShowGrid(!showGrid);
        }
        if (showGrid) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Toggle grid overlay");

        // Crosshair button
        // Store state before button - must use same value for push and pop
        boolean wasCrosshair = showCrosshair;
        if (wasCrosshair) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button(MaterialIcons.CenterFocusWeak + " Crosshair", btnWidth, btnHeight)) {
            showCrosshair = !showCrosshair;
        }
        if (wasCrosshair) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Toggle crosshair lines");
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    private void setPivotWithUndo(float x, float y) {
        float oldX = pivotX;
        float oldY = pivotY;

        pivotX = x;
        pivotY = y;

        if (oldX != x || oldY != y) {
            UndoManager.getInstance().push(new SetterUndoCommand<>(
                    v -> { pivotX = v[0]; pivotY = v[1]; },
                    new float[]{oldX, oldY},
                    new float[]{x, y},
                    "Set Pivot Preset"
            ));
        }
    }

    private float applyPixelSnap(float value, float size) {
        if (size <= 0) return value;
        float pixelValue = Math.round(value * size);
        return pixelValue / size;
    }
}
