package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;

/**
 * 9-slice editing tab for the Sprite Editor.
 * <p>
 * Features:
 * <ul>
 *   <li>Interactive preview with draggable border lines</li>
 *   <li>Preset buttons (4px, 8px, 16px, clear)</li>
 *   <li>Scaled preview showing how the 9-slice looks at different sizes</li>
 * </ul>
 */
public class NineSliceEditorTab {

    // ========================================================================
    // STATE
    // ========================================================================

    // Border values
    private int sliceLeft = 0;
    private int sliceRight = 0;
    private int sliceTop = 0;
    private int sliceBottom = 0;

    // Original values (for revert)
    private int originalSliceLeft = 0;
    private int originalSliceRight = 0;
    private int originalSliceTop = 0;
    private int originalSliceBottom = 0;

    // Drag state
    private enum DragBorder { NONE, LEFT, RIGHT, TOP, BOTTOM }
    private DragBorder draggingBorder = DragBorder.NONE;
    private int dragStartValue = 0;

    // Scaled preview settings
    private int previewWidth = 128;
    private int previewHeight = 128;

    // Shared preview renderer
    private final SpritePreviewRenderer previewRenderer;

    // Current sprite reference
    private Sprite currentSprite;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public NineSliceEditorTab(SpritePreviewRenderer previewRenderer) {
        this.previewRenderer = previewRenderer;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    public int getSliceLeft() {
        return sliceLeft;
    }

    public int getSliceRight() {
        return sliceRight;
    }

    public int getSliceTop() {
        return sliceTop;
    }

    public int getSliceBottom() {
        return sliceBottom;
    }

    public void setSlices(int left, int right, int top, int bottom) {
        this.sliceLeft = left;
        this.sliceRight = right;
        this.sliceTop = top;
        this.sliceBottom = bottom;
    }

    public void setOriginalSlices(int left, int right, int top, int bottom) {
        this.originalSliceLeft = left;
        this.originalSliceRight = right;
        this.originalSliceTop = top;
        this.originalSliceBottom = bottom;
    }

    public void revertToOriginal() {
        sliceLeft = originalSliceLeft;
        sliceRight = originalSliceRight;
        sliceTop = originalSliceTop;
        sliceBottom = originalSliceBottom;
    }

    public void updateOriginal() {
        originalSliceLeft = sliceLeft;
        originalSliceRight = sliceRight;
        originalSliceTop = sliceTop;
        originalSliceBottom = sliceBottom;
    }

    public boolean hasSlicing() {
        return sliceLeft > 0 || sliceRight > 0 || sliceTop > 0 || sliceBottom > 0;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the 9-slice tab content.
     *
     * @param sprite The sprite being edited
     * @param availableWidth Available width for the tab
     * @param availableHeight Available height for the tab
     */
    public void render(Sprite sprite, float availableWidth, float availableHeight) {
        this.currentSprite = sprite;

        // 60% preview / 40% controls layout
        float previewPanelWidth = availableWidth * 0.60f;
        float controlsWidth = availableWidth * 0.40f - 5;

        // Left: Preview area with border lines
        if (ImGui.beginChild("NineSlicePreviewArea", previewPanelWidth, availableHeight, true)) {
            renderPreview(sprite);
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right: Controls panel
        if (ImGui.beginChild("NineSliceControlsPanel", controlsWidth, availableHeight, true, ImGuiWindowFlags.NoScrollbar)) {
            renderControls(sprite);
        }
        ImGui.endChild();
    }

    private void renderPreview(Sprite sprite) {
        float availWidth = ImGui.getContentRegionAvailX();
        float availHeight = ImGui.getContentRegionAvailY();

        if (!previewRenderer.beginPreview(sprite, availWidth, availHeight)) {
            return;
        }

        // Draw 9-slice border lines
        drawBorderLines();

        previewRenderer.endPreview();

        // Handle border dragging (only if not panning)
        if (!previewRenderer.isPanning()) {
            handleBorderDragging();
        }
    }

    private void drawBorderLines() {
        ImDrawList drawList = previewRenderer.getDrawList();
        float drawX = previewRenderer.getDrawX();
        float drawY = previewRenderer.getDrawY();
        float displayWidth = previewRenderer.getDisplayWidth();
        float displayHeight = previewRenderer.getDisplayHeight();
        float spriteWidth = previewRenderer.getSpriteWidth();
        float spriteHeight = previewRenderer.getSpriteHeight();

        // Border line colors
        int leftRightColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1.0f, 0.9f); // Blue
        int topBottomColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.4f, 0.4f, 0.9f); // Red

        // Calculate line positions
        float leftLineX = drawX + (sliceLeft / spriteWidth) * displayWidth;
        float rightLineX = drawX + displayWidth - (sliceRight / spriteWidth) * displayWidth;
        float topLineY = drawY + (sliceTop / spriteHeight) * displayHeight;
        float bottomLineY = drawY + displayHeight - (sliceBottom / spriteHeight) * displayHeight;

        // Draw vertical lines
        if (sliceLeft > 0) {
            drawList.addLine(leftLineX, drawY, leftLineX, drawY + displayHeight, leftRightColor, 2);
        }
        if (sliceRight > 0) {
            drawList.addLine(rightLineX, drawY, rightLineX, drawY + displayHeight, leftRightColor, 2);
        }

        // Draw horizontal lines
        if (sliceTop > 0) {
            drawList.addLine(drawX, topLineY, drawX + displayWidth, topLineY, topBottomColor, 2);
        }
        if (sliceBottom > 0) {
            drawList.addLine(drawX, bottomLineY, drawX + displayWidth, bottomLineY, topBottomColor, 2);
        }

        // Draw corner region overlays
        int cornerColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 0.0f, 0.15f);

        if (sliceLeft > 0 && sliceTop > 0) {
            drawList.addRectFilled(drawX, drawY, leftLineX, topLineY, cornerColor);
        }
        if (sliceRight > 0 && sliceTop > 0) {
            drawList.addRectFilled(rightLineX, drawY, drawX + displayWidth, topLineY, cornerColor);
        }
        if (sliceLeft > 0 && sliceBottom > 0) {
            drawList.addRectFilled(drawX, bottomLineY, leftLineX, drawY + displayHeight, cornerColor);
        }
        if (sliceRight > 0 && sliceBottom > 0) {
            drawList.addRectFilled(rightLineX, bottomLineY, drawX + displayWidth, drawY + displayHeight, cornerColor);
        }

        // Draw sprite boundary
        previewRenderer.drawBoundary();
    }

    private void handleBorderDragging() {
        boolean isHovered = previewRenderer.isHovered();

        if (!isHovered && draggingBorder == DragBorder.NONE) {
            return;
        }

        ImVec2 mousePos = ImGui.getMousePos();
        float mouseX = mousePos.x;
        float mouseY = mousePos.y;

        float drawX = previewRenderer.getDrawX();
        float drawY = previewRenderer.getDrawY();
        float displayWidth = previewRenderer.getDisplayWidth();
        float displayHeight = previewRenderer.getDisplayHeight();
        float spriteWidth = previewRenderer.getSpriteWidth();
        float spriteHeight = previewRenderer.getSpriteHeight();

        // Calculate line positions
        float leftLineX = drawX + (sliceLeft / spriteWidth) * displayWidth;
        float rightLineX = drawX + displayWidth - (sliceRight / spriteWidth) * displayWidth;
        float topLineY = drawY + (sliceTop / spriteHeight) * displayHeight;
        float bottomLineY = drawY + displayHeight - (sliceBottom / spriteHeight) * displayHeight;

        float hitTolerance = 8;

        // Determine which border we're near
        DragBorder nearBorder = DragBorder.NONE;
        if (Math.abs(mouseX - leftLineX) < hitTolerance && mouseY >= drawY && mouseY <= drawY + displayHeight) {
            nearBorder = DragBorder.LEFT;
        } else if (Math.abs(mouseX - rightLineX) < hitTolerance && mouseY >= drawY && mouseY <= drawY + displayHeight) {
            nearBorder = DragBorder.RIGHT;
        } else if (Math.abs(mouseY - topLineY) < hitTolerance && mouseX >= drawX && mouseX <= drawX + displayWidth) {
            nearBorder = DragBorder.TOP;
        } else if (Math.abs(mouseY - bottomLineY) < hitTolerance && mouseX >= drawX && mouseX <= drawX + displayWidth) {
            nearBorder = DragBorder.BOTTOM;
        }

        // Set cursor
        if (draggingBorder == DragBorder.LEFT || draggingBorder == DragBorder.RIGHT ||
                nearBorder == DragBorder.LEFT || nearBorder == DragBorder.RIGHT) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        } else if (draggingBorder == DragBorder.TOP || draggingBorder == DragBorder.BOTTOM ||
                nearBorder == DragBorder.TOP || nearBorder == DragBorder.BOTTOM) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
        }

        // Start drag
        if (isHovered && ImGui.isMouseClicked(ImGuiMouseButton.Left) && nearBorder != DragBorder.NONE) {
            draggingBorder = nearBorder;
            dragStartValue = switch (nearBorder) {
                case LEFT -> sliceLeft;
                case RIGHT -> sliceRight;
                case TOP -> sliceTop;
                case BOTTOM -> sliceBottom;
                default -> 0;
            };
        }

        // Continue drag
        if (draggingBorder != DragBorder.NONE && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            int maxLeft = (int) spriteWidth - sliceRight - 1;
            int maxRight = (int) spriteWidth - sliceLeft - 1;
            int maxTop = (int) spriteHeight - sliceBottom - 1;
            int maxBottom = (int) spriteHeight - sliceTop - 1;

            switch (draggingBorder) {
                case LEFT -> {
                    float pixelX = previewRenderer.screenToPixelX(mouseX);
                    sliceLeft = Math.max(0, Math.min(maxLeft, Math.round(pixelX)));
                }
                case RIGHT -> {
                    float pixelX = spriteWidth - previewRenderer.screenToPixelX(mouseX);
                    sliceRight = Math.max(0, Math.min(maxRight, Math.round(pixelX)));
                }
                case TOP -> {
                    float pixelY = previewRenderer.screenToPixelY(mouseY);
                    sliceTop = Math.max(0, Math.min(maxTop, Math.round(pixelY)));
                }
                case BOTTOM -> {
                    float pixelY = spriteHeight - previewRenderer.screenToPixelY(mouseY);
                    sliceBottom = Math.max(0, Math.min(maxBottom, Math.round(pixelY)));
                }
            }
        }

        // End drag
        if (draggingBorder != DragBorder.NONE && !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            int endValue = switch (draggingBorder) {
                case LEFT -> sliceLeft;
                case RIGHT -> sliceRight;
                case TOP -> sliceTop;
                case BOTTOM -> sliceBottom;
                default -> 0;
            };

            if (dragStartValue != endValue) {
                final DragBorder border = draggingBorder;
                final int start = dragStartValue;
                final int end = endValue;

                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        v -> {
                            switch (border) {
                                case LEFT -> sliceLeft = v;
                                case RIGHT -> sliceRight = v;
                                case TOP -> sliceTop = v;
                                case BOTTOM -> sliceBottom = v;
                            }
                        },
                        start, end, "Change 9-Slice Border"
                ));
            }

            draggingBorder = DragBorder.NONE;
        }
    }

    private void renderControls(Sprite sprite) {
        int spriteWidth = sprite != null ? (int) sprite.getWidth() : 64;
        int spriteHeight = sprite != null ? (int) sprite.getHeight() : 64;

        // === BORDERS SECTION ===
        ImGui.text("Borders (pixels)");
        ImGui.separator();

        float availWidth = ImGui.getContentRegionAvailX();
        float fieldColWidth = availWidth * 0.55f;
        float presetColWidth = availWidth * 0.45f - 5;

        // Left column: border fields
        if (ImGui.beginChild("BorderFields", fieldColWidth, 110, false, ImGuiWindowFlags.NoScrollbar)) {
            float fieldWidth = 50;
            float labelWidth = 55;

            ImGui.text("Left:");
            ImGui.sameLine(labelWidth);
            ImGui.setNextItemWidth(fieldWidth);
            int[] leftArr = {sliceLeft};
            if (ImGui.dragInt("##SliceLeft", leftArr, 1, 0, spriteWidth - sliceRight - 1)) {
                sliceLeft = leftArr[0];
            }

            ImGui.text("Right:");
            ImGui.sameLine(labelWidth);
            ImGui.setNextItemWidth(fieldWidth);
            int[] rightArr = {sliceRight};
            if (ImGui.dragInt("##SliceRight", rightArr, 1, 0, spriteWidth - sliceLeft - 1)) {
                sliceRight = rightArr[0];
            }

            ImGui.text("Top:");
            ImGui.sameLine(labelWidth);
            ImGui.setNextItemWidth(fieldWidth);
            int[] topArr = {sliceTop};
            if (ImGui.dragInt("##SliceTop", topArr, 1, 0, spriteHeight - sliceBottom - 1)) {
                sliceTop = topArr[0];
            }

            ImGui.text("Bottom:");
            ImGui.sameLine(labelWidth);
            ImGui.setNextItemWidth(fieldWidth);
            int[] bottomArr = {sliceBottom};
            if (ImGui.dragInt("##SliceBottom", bottomArr, 1, 0, spriteHeight - sliceTop - 1)) {
                sliceBottom = bottomArr[0];
            }
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right column: presets
        if (ImGui.beginChild("BorderPresets", presetColWidth, 110, false, ImGuiWindowFlags.NoScrollbar)) {
            float btnWidth = -1;
            float btnHeight = 20;

            if (ImGui.button("4px", btnWidth, btnHeight)) {
                setBordersWithUndo(4, 4, 4, 4);
            }
            if (ImGui.button("8px", btnWidth, btnHeight)) {
                setBordersWithUndo(8, 8, 8, 8);
            }
            if (ImGui.button("16px", btnWidth, btnHeight)) {
                setBordersWithUndo(16, 16, 16, 16);
            }
            if (ImGui.button("Clear", btnWidth, btnHeight)) {
                setBordersWithUndo(0, 0, 0, 0);
            }
        }
        ImGui.endChild();

        ImGui.spacing();

        // === SCALED PREVIEW SECTION ===
        ImGui.text("Scaled Preview");
        ImGui.separator();

        // Size controls
        ImGui.text("Size:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(40);
        int[] pwArr = {previewWidth};
        if (ImGui.dragInt("##PreviewW", pwArr, 1, 16, 512)) {
            previewWidth = pwArr[0];
        }
        ImGui.sameLine();
        ImGui.text("x");
        ImGui.sameLine();
        ImGui.setNextItemWidth(40);
        int[] phArr = {previewHeight};
        if (ImGui.dragInt("##PreviewH", phArr, 1, 16, 512)) {
            previewHeight = phArr[0];
        }
        ImGui.sameLine();
        if (ImGui.smallButton("1x")) {
            previewWidth = spriteWidth;
            previewHeight = spriteHeight;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("2x")) {
            previewWidth = spriteWidth * 2;
            previewHeight = spriteHeight * 2;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("3x")) {
            previewWidth = spriteWidth * 3;
            previewHeight = spriteHeight * 3;
        }

        ImGui.spacing();

        // Scaled preview in scrollable child
        float previewAreaHeight = ImGui.getContentRegionAvailY() - 5;
        if (ImGui.beginChild("ScaledPreviewScroll", -1, previewAreaHeight, true)) {
            renderScaledPreview(sprite);
        }
        ImGui.endChild();
    }

    private void renderScaledPreview(Sprite sprite) {
        if (sprite == null || sprite.getTexture() == null) {
            ImGui.textDisabled("No preview available");
            return;
        }

        // Ensure minimum size
        int minWidth = sliceLeft + sliceRight + 1;
        int minHeight = sliceTop + sliceBottom + 1;
        int pw = Math.max(minWidth, previewWidth);
        int ph = Math.max(minHeight, previewHeight);

        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        float drawX = cursorPos.x;
        float drawY = cursorPos.y;

        ImDrawList drawList = ImGui.getWindowDrawList();

        // Background
        int bgColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
        drawList.addRectFilled(drawX, drawY, drawX + pw, drawY + ph, bgColor);

        // Get texture info
        int texId = sprite.getTexture().getTextureId();
        float spriteW = sprite.getWidth();
        float spriteH = sprite.getHeight();
        float baseU0 = sprite.getU0();
        float baseV0 = sprite.getV0();
        float baseU1 = sprite.getU1();
        float baseV1 = sprite.getV1();

        float uSpan = baseU1 - baseU0;
        float vSpan = baseV1 - baseV0;

        // UV coordinates for borders
        float leftU = baseU0 + (sliceLeft / spriteW) * uSpan;
        float rightU = baseU1 - (sliceRight / spriteW) * uSpan;
        float topV = baseV0 + (sliceTop / spriteH) * vSpan;
        float bottomV = baseV1 - (sliceBottom / spriteH) * vSpan;

        int centerW = pw - sliceLeft - sliceRight;
        int centerH = ph - sliceTop - sliceBottom;

        // Draw 9 regions (V flipped for OpenGL)
        // Top-left
        if (sliceLeft > 0 && sliceTop > 0) {
            drawList.addImage(texId, drawX, drawY, drawX + sliceLeft, drawY + sliceTop,
                    baseU0, topV, leftU, baseV0);
        }
        // Top-center
        if (centerW > 0 && sliceTop > 0) {
            drawList.addImage(texId, drawX + sliceLeft, drawY, drawX + sliceLeft + centerW, drawY + sliceTop,
                    leftU, topV, rightU, baseV0);
        }
        // Top-right
        if (sliceRight > 0 && sliceTop > 0) {
            drawList.addImage(texId, drawX + pw - sliceRight, drawY, drawX + pw, drawY + sliceTop,
                    rightU, topV, baseU1, baseV0);
        }

        // Middle-left
        if (sliceLeft > 0 && centerH > 0) {
            drawList.addImage(texId, drawX, drawY + sliceTop, drawX + sliceLeft, drawY + sliceTop + centerH,
                    baseU0, bottomV, leftU, topV);
        }
        // Middle-center
        if (centerW > 0 && centerH > 0) {
            drawList.addImage(texId, drawX + sliceLeft, drawY + sliceTop,
                    drawX + sliceLeft + centerW, drawY + sliceTop + centerH,
                    leftU, bottomV, rightU, topV);
        }
        // Middle-right
        if (sliceRight > 0 && centerH > 0) {
            drawList.addImage(texId, drawX + pw - sliceRight, drawY + sliceTop,
                    drawX + pw, drawY + sliceTop + centerH,
                    rightU, bottomV, baseU1, topV);
        }

        // Bottom-left
        if (sliceLeft > 0 && sliceBottom > 0) {
            drawList.addImage(texId, drawX, drawY + ph - sliceBottom,
                    drawX + sliceLeft, drawY + ph,
                    baseU0, baseV1, leftU, bottomV);
        }
        // Bottom-center
        if (centerW > 0 && sliceBottom > 0) {
            drawList.addImage(texId, drawX + sliceLeft, drawY + ph - sliceBottom,
                    drawX + sliceLeft + centerW, drawY + ph,
                    leftU, baseV1, rightU, bottomV);
        }
        // Bottom-right
        if (sliceRight > 0 && sliceBottom > 0) {
            drawList.addImage(texId, drawX + pw - sliceRight, drawY + ph - sliceBottom,
                    drawX + pw, drawY + ph,
                    rightU, baseV1, baseU1, bottomV);
        }

        // Border
        int borderColor = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1f);
        drawList.addRect(drawX, drawY, drawX + pw, drawY + ph, borderColor);

        ImGui.dummy(pw, ph);
    }

    private void setBordersWithUndo(int left, int right, int top, int bottom) {
        int oldLeft = sliceLeft, oldRight = sliceRight, oldTop = sliceTop, oldBottom = sliceBottom;
        sliceLeft = left;
        sliceRight = right;
        sliceTop = top;
        sliceBottom = bottom;

        if (oldLeft != left || oldRight != right || oldTop != top || oldBottom != bottom) {
            UndoManager.getInstance().push(new SetterUndoCommand<>(
                    v -> {
                        sliceLeft = v[0];
                        sliceRight = v[1];
                        sliceTop = v[2];
                        sliceBottom = v[3];
                    },
                    new int[]{oldLeft, oldRight, oldTop, oldBottom},
                    new int[]{left, right, top, bottom},
                    "Set 9-Slice Borders"
            ));
        }
    }
}
