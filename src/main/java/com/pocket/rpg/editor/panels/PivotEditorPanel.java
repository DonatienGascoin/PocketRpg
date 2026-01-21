package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.assets.AssetPreviewRegistry;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteSheet;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.loaders.SpriteSheetLoader;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Modal panel for editing pivot points on sprites and sprite sheets.
 * <p>
 * Accessible from:
 * <ul>
 *   <li>Main menu: Edit &gt; Pivot Editor...</li>
 *   <li>Asset Browser: Right-click on sprite/spritesheet &gt; "Edit Pivot..."</li>
 * </ul>
 * <p>
 * Features:
 * <ul>
 *   <li>Interactive sprite preview with draggable pivot point</li>
 *   <li>X/Y sliders for precise pivot positioning</li>
 *   <li>9-grid quick preset buttons</li>
 *   <li>Zoom and grid overlay options</li>
 *   <li>Sprite sheet support with per-sprite or batch editing</li>
 * </ul>
 */
public class PivotEditorPanel {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    private static final String POPUP_ID = "Pivot Editor";
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 8.0f;
    private static final float DEFAULT_ZOOM = 2.0f;
    private static final int GRID_SUBDIVISIONS = 4;

    // Preset positions (pivotX, pivotY)
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

    private boolean shouldOpen = false;
    private boolean isOpen = false;

    // Current asset
    private String assetPath = null;
    private Sprite sprite = null;
    private SpriteSheet spriteSheet = null;
    private boolean isSpriteSheet = false;

    // Pivot values (working copy)
    private float pivotX = 0.5f;
    private float pivotY = 0.5f;

    // Original values (for cancel/revert)
    private float originalPivotX = 0.5f;
    private float originalPivotY = 0.5f;

    // Preview settings
    private float zoom = DEFAULT_ZOOM;
    private boolean showGrid = true;
    private boolean showCrosshair = true;
    private boolean pixelSnap = false;

    // Sprite sheet mode
    private int selectedSpriteIndex = 0;
    private boolean applyToAllSprites = true;

    // Drag state
    private boolean isDraggingPivot = false;

    // UI state
    private final ImFloat pivotXInput = new ImFloat(0.5f);
    private final ImFloat pivotYInput = new ImFloat(0.5f);

    // Undo tracking for pivot fields
    private Float undoStartPivotX = null;
    private Float undoStartPivotY = null;

    // Undo tracking for preview canvas dragging
    private Float dragStartPivotX = null;
    private Float dragStartPivotY = null;

    // Asset picker state
    private boolean showAssetPicker = false;
    private final imgui.type.ImString assetSearchBuffer = new imgui.type.ImString(128);
    private String previewAssetPath = null;
    private Object previewAsset = null;
    private int assetPickerTab = 0; // 0 = Sprites, 1 = Spritesheets

    // Status callback
    private Consumer<String> statusCallback;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Opens the pivot editor for the specified asset.
     *
     * @param path Asset path (sprite or spritesheet)
     */
    public void open(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        this.assetPath = path;
        this.shouldOpen = true;

        // Load asset
        loadAsset(path);
    }

    /**
     * Opens the pivot editor without a pre-selected asset.
     */
    public void open() {
        this.shouldOpen = true;
        this.assetPath = null;
        this.sprite = null;
        this.spriteSheet = null;
        this.isSpriteSheet = false;
    }

    /**
     * Checks if the panel is currently open.
     *
     * @return true if panel is visible
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Sets the status callback for showing messages.
     *
     * @param callback Status message consumer
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the pivot editor panel. Call every frame.
     */
    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
            isOpen = true;
        }

        // Set modal size (larger to accommodate sprite sheet mode without scroll)
        ImGui.setNextWindowSize(750, 680);

        ImBoolean pOpen = new ImBoolean(true);
        int flags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar;

        if (ImGui.beginPopupModal(POPUP_ID, pOpen, flags)) {
            renderContent();
            ImGui.endPopup();
        }

        // Check if closed
        if (!pOpen.get() && isOpen) {
            isOpen = false;
        }
    }

    private void renderContent() {
        // Asset selector row with padding
        renderAssetSelector();
        ImGui.separator();

        if (sprite == null && spriteSheet == null) {
            ImGui.textDisabled("Select an asset to edit its pivot point.");
            renderFooter();
            return;
        }

        // Calculate reserved height for footer and sprite sheet selector
        float footerHeight = 45; // Zoom controls + buttons
        float spriteSheetHeight = (isSpriteSheet && spriteSheet != null) ? 145 : 0; // Radio buttons + sprite grid
        float reservedHeight = footerHeight + spriteSheetHeight + 25; // separators/spacing

        // Main content: 70% preview / 30% buttons
        float availableWidth = ImGui.getContentRegionAvailX();
        float availableHeight = ImGui.getContentRegionAvailY() - reservedHeight;
        float previewWidth = availableWidth * 0.70f;
        float buttonsWidth = availableWidth * 0.30f - 5; // 5px gap

        // Left: Preview area (70%)
        if (ImGui.beginChild("PreviewArea", previewWidth, availableHeight, true)) {
            renderPreviewArea();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right: Buttons panel (30%)
        if (ImGui.beginChild("ButtonsPanel", buttonsWidth, availableHeight, true)) {
            renderButtonsPanel();
        }
        ImGui.endChild();

        // Sprite sheet selector (if applicable)
        if (isSpriteSheet && spriteSheet != null) {
            ImGui.separator();
            renderSpriteSheetSelector();
        }

        // Footer with buttons
        ImGui.separator();
        renderFooter();
    }

    // ========================================================================
    // ASSET SELECTOR
    // ========================================================================

    private void renderAssetSelector() {
        float padding = 10;

        ImGui.text("Asset:");
        ImGui.sameLine();

        // Display current path - account for padding on right
        String displayPath = assetPath != null ? assetPath : "(None selected)";
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 90 - padding);
        ImGui.inputText("##AssetPath", new imgui.type.ImString(displayPath, 256),
                ImGuiInputTextFlags.ReadOnly);

        ImGui.sameLine();

        // Browse button
        if (ImGui.button(MaterialIcons.FolderOpen + " Browse")) {
            showAssetPicker = true;
            assetSearchBuffer.set("");
        }

        // Right padding
        ImGui.sameLine();
        ImGui.dummy(padding, 0);

        // Render asset picker popup
        renderAssetPickerPopup();
    }

    private void renderAssetPickerPopup() {
        if (showAssetPicker) {
            ImGui.openPopup("Select Asset##PivotEditor");
            showAssetPicker = false;
            previewAssetPath = null;
            previewAsset = null;
        }

        ImGui.setNextWindowSize(600, 420);
        if (ImGui.beginPopupModal("Select Asset##PivotEditor", ImGuiWindowFlags.NoResize)) {
            // Header: search bar
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputTextWithHint("##search", "Search...", assetSearchBuffer);

            ImGui.separator();

            // Get all sprites and spritesheets
            java.util.List<String> spriteAssets = Assets.scanByType(Sprite.class);
            java.util.List<String> sheetAssets = Assets.scanByType(SpriteSheet.class);
            String filter = assetSearchBuffer.get().toLowerCase();

            // Two-column layout: tabs+list on left, preview on right
            float contentHeight = ImGui.getContentRegionAvailY() - 35;
            float leftWidth = 300;
            float rightWidth = ImGui.getContentRegionAvailX() - leftWidth - 10;

            // Left column: Tabs + Asset list
            if (ImGui.beginChild("LeftPanel", leftWidth, contentHeight, true)) {
                // Tabs
                if (ImGui.beginTabBar("AssetTypeTabs")) {
                    // Sprites tab
                    if (ImGui.beginTabItem(MaterialIcons.Image + " Sprites (" + spriteAssets.size() + ")")) {
                        assetPickerTab = 0;
                        renderAssetList(spriteAssets, filter, Sprite.class);
                        ImGui.endTabItem();
                    }

                    // Spritesheets tab
                    if (ImGui.beginTabItem(MaterialIcons.GridView + " Sheets (" + sheetAssets.size() + ")")) {
                        assetPickerTab = 1;
                        renderAssetList(sheetAssets, filter, SpriteSheet.class);
                        ImGui.endTabItem();
                    }

                    ImGui.endTabBar();
                }
            }
            ImGui.endChild();

            ImGui.sameLine();

            // Right column: Preview
            if (ImGui.beginChild("RightPanel", rightWidth, contentHeight, true)) {
                ImGui.text("Preview");
                ImGui.separator();

                if (previewAsset != null) {
                    // Show path
                    ImGui.textDisabled(previewAssetPath);
                    ImGui.spacing();

                    // Render preview centered
                    float previewMaxSize = Math.min(rightWidth - 20, contentHeight - 80);
                    AssetPreviewRegistry.render(previewAsset, previewMaxSize);
                } else {
                    ImGui.textDisabled("Select an asset to preview");
                }
            }
            ImGui.endChild();

            // Footer buttons
            ImGui.separator();
            float buttonWidth = 80;
            float totalButtonWidth = buttonWidth * 2 + 10;
            ImGui.setCursorPosX((ImGui.getContentRegionAvailX() - totalButtonWidth) / 2);

            // Load button
            boolean canLoad = previewAssetPath != null;
            if (!canLoad) {
                ImGui.beginDisabled();
            }
            if (ImGui.button("Load", buttonWidth, 0)) {
                loadAsset(previewAssetPath);
                assetPath = previewAssetPath;
                ImGui.closeCurrentPopup();
            }
            if (!canLoad) {
                ImGui.endDisabled();
            }

            ImGui.sameLine();

            // Cancel button
            if (ImGui.button("Cancel", buttonWidth, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void renderAssetList(java.util.List<String> assets, String filter, Class<?> assetType) {
        for (String path : assets) {
            if (!filter.isEmpty() && !path.toLowerCase().contains(filter)) {
                continue;
            }

            boolean isSelected = path.equals(previewAssetPath);
            if (ImGui.selectable(getFileName(path), isSelected)) {
                // Single click - load preview
                loadPreviewAsset(path, assetType);
            }

            // Double click - load to pivot editor
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                loadAsset(path);
                assetPath = path;
                ImGui.closeCurrentPopup();
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(path);
            }
        }
    }

    private void loadPreviewAsset(String path, Class<?> assetType) {
        if (path.equals(previewAssetPath)) {
            return; // Already loaded
        }

        previewAssetPath = path;
        previewAsset = null;

        try {
            previewAsset = Assets.load(path, assetType);
        } catch (Exception e) {
            System.err.println("[PivotEditorPanel] Failed to load preview: " + e.getMessage());
        }
    }

    private String getFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    // ========================================================================
    // PREVIEW AREA
    // ========================================================================

    private void renderPreviewArea() {
        Sprite previewSprite = getCurrentSprite();
        if (previewSprite == null || previewSprite.getTexture() == null) {
            ImGui.textDisabled("No sprite to preview");
            return;
        }

        // Calculate preview dimensions
        float spriteWidth = previewSprite.getWidth();
        float spriteHeight = previewSprite.getHeight();

        float availWidth = ImGui.getContentRegionAvailX();
        float availHeight = ImGui.getContentRegionAvailY() - 40; // Reserve space for zoom control

        float displayWidth = spriteWidth * zoom;
        float displayHeight = spriteHeight * zoom;

        // Center the preview
        float offsetX = Math.max(0, (availWidth - displayWidth) / 2);
        float offsetY = Math.max(0, (availHeight - displayHeight) / 2);

        // Get draw position
        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        float drawX = cursorPos.x + offsetX;
        float drawY = cursorPos.y + offsetY;

        ImDrawList drawList = ImGui.getWindowDrawList();

        // Draw checkerboard background for transparency
        drawCheckerboard(drawList, drawX, drawY, displayWidth, displayHeight);

        // Draw sprite
        int texId = previewSprite.getTexture().getTextureId();
        float u0 = previewSprite.getU0();
        float v0 = previewSprite.getV0();
        float u1 = previewSprite.getU1();
        float v1 = previewSprite.getV1();

        drawList.addImage(texId, drawX, drawY, drawX + displayWidth, drawY + displayHeight,
                u0, v1, u1, v0);  // V flipped for OpenGL

        // Draw grid overlay
        if (showGrid) {
            drawGrid(drawList, drawX, drawY, displayWidth, displayHeight);
        }

        // Draw pivot point
        float pivotScreenX = drawX + pivotX * displayWidth;
        float pivotScreenY = drawY + (1.0f - pivotY) * displayHeight; // Flip Y for screen coords

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

        // Handle pivot dragging
        ImGui.setCursorScreenPos(drawX, drawY);
        ImGui.invisibleButton("##PreviewInteraction", displayWidth, displayHeight);

        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand);

            // Start drag - capture start values for undo
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                isDraggingPivot = true;
                dragStartPivotX = pivotX;
                dragStartPivotY = pivotY;
            }
        }

        if (isDraggingPivot) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                ImVec2 mousePos = ImGui.getMousePos();
                float newPivotX = (mousePos.x - drawX) / displayWidth;
                float newPivotY = 1.0f - (mousePos.y - drawY) / displayHeight; // Flip Y

                // Apply pixel snap if enabled
                newPivotX = applyPixelSnap(newPivotX, true);
                newPivotY = applyPixelSnap(newPivotY, false);

                // Clamp to 0-1
                pivotX = Math.max(0, Math.min(1, newPivotX));
                pivotY = Math.max(0, Math.min(1, newPivotY));

                // Update input fields
                pivotXInput.set(pivotX);
                pivotYInput.set(pivotY);
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
                        // Combined undo for both X and Y
                        UndoManager.getInstance().push(new SetterUndoCommand<>(
                                v -> {
                                    pivotX = v[0];
                                    pivotY = v[1];
                                    pivotXInput.set(pivotX);
                                    pivotYInput.set(pivotY);
                                },
                                new float[]{startX, startY},
                                new float[]{endX, endY},
                                "Change Pivot"
                        ));
                    }
                }
            }
        }

        // Move cursor past preview area
        ImGui.setCursorScreenPos(cursorPos.x, cursorPos.y + availHeight);
    }

    private void drawCheckerboard(ImDrawList drawList, float x, float y, float width, float height) {
        int lightColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f);
        int darkColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
        float cellSize = 8;

        int cols = (int) Math.ceil(width / cellSize);
        int rows = (int) Math.ceil(height / cellSize);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                boolean isLight = (row + col) % 2 == 0;
                int color = isLight ? lightColor : darkColor;

                float cellX = x + col * cellSize;
                float cellY = y + row * cellSize;
                float cellW = Math.min(cellSize, x + width - cellX);
                float cellH = Math.min(cellSize, y + height - cellY);

                drawList.addRectFilled(cellX, cellY, cellX + cellW, cellY + cellH, color);
            }
        }
    }

    private void drawGrid(ImDrawList drawList, float x, float y, float width, float height) {
        int gridColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.2f);

        // Draw border
        drawList.addRect(x, y, x + width, y + height, gridColor);

        // Draw subdivisions
        for (int i = 1; i < GRID_SUBDIVISIONS; i++) {
            float t = (float) i / GRID_SUBDIVISIONS;
            // Vertical lines
            drawList.addLine(x + t * width, y, x + t * width, y + height, gridColor);
            // Horizontal lines
            drawList.addLine(x, y + t * height, x + width, y + t * height, gridColor);
        }
    }

    // ========================================================================
    // BUTTONS PANEL (Right side - 25%)
    // ========================================================================

    private void renderButtonsPanel() {
        float btnWidth = -1; // Full width
        float btnHeight = 28;
        float smallBtnSize = 38;
        float padding = 8;

        // === PIVOT VALUES SECTION ===
        ImGui.text("Pivot");
        ImGui.separator();

        // X field with label on left and undo support
        float[] xArr = {pivotX};
        ImGui.text("X:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f - 12);
        if (ImGui.dragFloat("##PivotX", xArr, 0.01f, 0f, 1f, "%.3f")) {
            pivotX = Math.max(0, Math.min(1, xArr[0]));
            if (pixelSnap) {
                pivotX = applyPixelSnap(pivotX, true);
            }
            pivotXInput.set(pivotX);
        }
        // Undo tracking for X
        if (ImGui.isItemActivated()) {
            undoStartPivotX = pivotX;
        }
        if (ImGui.isItemDeactivatedAfterEdit() && undoStartPivotX != null) {
            float startVal = undoStartPivotX;
            float endVal = pivotX;
            undoStartPivotX = null;
            if (startVal != endVal) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        v -> { pivotX = v; pivotXInput.set(v); },
                        startVal, endVal, "Change Pivot X"
                ));
            }
        }

        // Y field with label on left and undo support
        float[] yArr = {pivotY};
        ImGui.sameLine();
        ImGui.text("Y:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 4);
        if (ImGui.dragFloat("##PivotY", yArr, 0.01f, 0f, 1f, "%.3f")) {
            pivotY = Math.max(0, Math.min(1, yArr[0]));
            if (pixelSnap) {
                pivotY = applyPixelSnap(pivotY, false);
            }
            pivotYInput.set(pivotY);
        }
        // Undo tracking for Y
        if (ImGui.isItemActivated()) {
            undoStartPivotY = pivotY;
        }
        if (ImGui.isItemDeactivatedAfterEdit() && undoStartPivotY != null) {
            float startVal = undoStartPivotY;
            float endVal = pivotY;
            undoStartPivotY = null;
            if (startVal != endVal) {
                UndoManager.getInstance().push(new SetterUndoCommand<>(
                        v -> { pivotY = v; pivotYInput.set(v); },
                        startVal, endVal, "Change Pivot Y"
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
                    setPivot(PRESETS[index][0], PRESETS[index][1]);
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

        // Pixel Snap button - green when ON (capture state BEFORE button click)
        boolean wasPixelSnap = pixelSnap;
        if (wasPixelSnap) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button(MaterialIcons.Grid4x4 + " Pixel Snap", btnWidth, btnHeight)) {
            pixelSnap = !pixelSnap;
            if (pixelSnap) {
                pivotX = applyPixelSnap(pivotX, true);
                pivotY = applyPixelSnap(pivotY, false);
                pivotXInput.set(pivotX);
                pivotYInput.set(pivotY);
            }
        }
        if (wasPixelSnap) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) {
            Sprite s = getCurrentSprite();
            if (s != null) {
                ImGui.setTooltip(String.format("Snap pivot to pixel boundaries (%dx%d)", (int) s.getWidth(), (int) s.getHeight()));
            } else {
                ImGui.setTooltip("Snap pivot to pixel boundaries");
            }
        }

        // Grid button - green when ON (capture state BEFORE button click)
        boolean wasShowGrid = showGrid;
        if (wasShowGrid) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button(MaterialIcons.GridOn + " Grid", btnWidth, btnHeight)) {
            showGrid = !showGrid;
        }
        if (wasShowGrid) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Toggle grid overlay");

        // Crosshair button - green when ON (capture state BEFORE button click)
        boolean wasShowCrosshair = showCrosshair;
        if (wasShowCrosshair) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        }
        if (ImGui.button(MaterialIcons.CenterFocusWeak + " Crosshair", btnWidth, btnHeight)) {
            showCrosshair = !showCrosshair;
        }
        if (wasShowCrosshair) {
            ImGui.popStyleColor(2);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Toggle crosshair lines");
    }

    /**
     * Sets pivot and updates input fields with undo support.
     */
    private void setPivot(float x, float y) {
        float oldX = pivotX;
        float oldY = pivotY;

        pivotX = x;
        pivotY = y;
        pivotXInput.set(pivotX);
        pivotYInput.set(pivotY);

        // Push undo command if values changed
        if (oldX != x || oldY != y) {
            UndoManager.getInstance().push(new SetterUndoCommand<>(
                    v -> {
                        pivotX = v[0];
                        pivotY = v[1];
                        pivotXInput.set(pivotX);
                        pivotYInput.set(pivotY);
                    },
                    new float[]{oldX, oldY},
                    new float[]{x, y},
                    "Set Pivot Preset"
            ));
        }
    }

    // ========================================================================
    // SPRITE SHEET SELECTOR
    // ========================================================================

    private void renderSpriteSheetSelector() {
        if (spriteSheet == null) {
            return;
        }

        ImGui.text("Sprite Sheet Mode");
        ImGui.spacing();

        // Apply mode radio buttons
        if (ImGui.radioButton("Apply to All Sprites", applyToAllSprites)) {
            applyToAllSprites = true;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Apply to Selected Only", !applyToAllSprites)) {
            applyToAllSprites = false;
        }

        ImGui.spacing();

        // Sprite grid selector with previews
        ImGui.text("Select Sprite:");

        int totalSprites = spriteSheet.getTotalFrames();
        float previewSize = 48;
        float spacing = 4;
        float availWidth = ImGui.getContentRegionAvailX();
        int columns = Math.max(1, (int) ((availWidth + spacing) / (previewSize + spacing)));

        if (ImGui.beginChild("SpriteGrid", 0, 70, true)) {
            ImDrawList drawList = ImGui.getWindowDrawList();

            for (int i = 0; i < totalSprites; i++) {
                if (i > 0 && i % columns != 0) {
                    ImGui.sameLine(0, spacing);
                }

                Sprite frameSprite = spriteSheet.getSprite(i);
                if (frameSprite == null || frameSprite.getTexture() == null) {
                    continue;
                }

                boolean isSelected = i == selectedSpriteIndex;

                // Get button position
                ImVec2 cursorPos = ImGui.getCursorScreenPos();

                // Draw selection highlight
                if (isSelected) {
                    int highlightColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.6f, 1.0f, 0.5f);
                    drawList.addRectFilled(
                            cursorPos.x - 2, cursorPos.y - 2,
                            cursorPos.x + previewSize + 2, cursorPos.y + previewSize + 2,
                            highlightColor, 4);
                }

                // Create invisible button for interaction
                ImGui.pushID(i);
                if (ImGui.invisibleButton("##sprite", previewSize, previewSize)) {
                    selectedSpriteIndex = i;
                    // Load pivot for newly selected sprite
                    loadPivotForSelectedSprite();
                }
                ImGui.popID();

                // Draw sprite preview
                int texId = frameSprite.getTexture().getTextureId();
                float u0 = frameSprite.getU0();
                float v0 = frameSprite.getV0();
                float u1 = frameSprite.getU1();
                float v1 = frameSprite.getV1();

                drawList.addImage(texId,
                        cursorPos.x, cursorPos.y,
                        cursorPos.x + previewSize, cursorPos.y + previewSize,
                        u0, v1, u1, v0);  // V flipped for OpenGL

                // Draw border
                int borderColor = isSelected
                        ? ImGui.colorConvertFloat4ToU32(0.3f, 0.6f, 1.0f, 1.0f)
                        : ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.5f);
                drawList.addRect(cursorPos.x, cursorPos.y,
                        cursorPos.x + previewSize, cursorPos.y + previewSize,
                        borderColor);

                // Tooltip with index
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Sprite #" + i);
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // FOOTER
    // ========================================================================

    private void renderFooter() {
        // Zoom controls on the left
        ImGui.text("Zoom:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        float[] zoomArr = {zoom};
        if (ImGui.sliderFloat("##Zoom", zoomArr, MIN_ZOOM, MAX_ZOOM, "%.1fx")) {
            zoom = zoomArr[0];
        }
        ImGui.sameLine();
        if (ImGui.smallButton("1x")) {
            zoom = 1.0f;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("2x")) {
            zoom = 2.0f;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("4x")) {
            zoom = 4.0f;
        }

        // Buttons on the right
        float buttonWidth = 80;
        float totalWidth = buttonWidth * 2 + 10;
        ImGui.sameLine(ImGui.getContentRegionAvailX() - totalWidth);

        // Cancel button
        if (ImGui.button("Cancel", buttonWidth, 0)) {
            // Revert and close
            pivotX = originalPivotX;
            pivotY = originalPivotY;
            ImGui.closeCurrentPopup();
            isOpen = false;
        }

        ImGui.sameLine();

        // Save button
        ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.4f, 0.7f, 0.4f, 1f);
        if (ImGui.button(MaterialIcons.Save + " Save", buttonWidth, 0)) {
            applyPivot(true);
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save pivot to metadata file");
        }
    }

    // ========================================================================
    // ASSET LOADING
    // ========================================================================

    private void loadAsset(String path) {
        sprite = null;
        spriteSheet = null;
        isSpriteSheet = false;

        if (path == null || path.isEmpty()) {
            return;
        }

        try {
            // Determine asset type
            Class<?> type = Assets.getTypeForPath(path);

            if (type == SpriteSheet.class) {
                spriteSheet = Assets.load(path, SpriteSheet.class);
                isSpriteSheet = true;
                selectedSpriteIndex = 0;

                // Load pivot for selected sprite (default or per-sprite override)
                float[] pivot = spriteSheet.getEffectivePivot(selectedSpriteIndex);
                pivotX = pivot[0];
                pivotY = pivot[1];
            } else if (type == Sprite.class || isImageExtension(path)) {
                sprite = Assets.load(path, Sprite.class);

                // Load metadata if exists
                SpriteMetadata meta = AssetMetadata.load(path, SpriteMetadata.class);
                if (meta != null && meta.hasPivot()) {
                    pivotX = meta.pivotX;
                    pivotY = meta.pivotY;
                } else {
                    // Use sprite's current pivot
                    pivotX = sprite.getPivotX();
                    pivotY = sprite.getPivotY();
                }
            }

            // Store original values
            originalPivotX = pivotX;
            originalPivotY = pivotY;

            // Update input fields
            pivotXInput.set(pivotX);
            pivotYInput.set(pivotY);

        } catch (Exception e) {
            System.err.println("[PivotEditorPanel] Failed to load asset: " + path + " - " + e.getMessage());
            showStatus("Failed to load asset: " + e.getMessage());
        }
    }

    private boolean isImageExtension(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") || lower.endsWith(".bmp") ||
                lower.endsWith(".tga");
    }

    private Sprite getCurrentSprite() {
        if (isSpriteSheet && spriteSheet != null) {
            return spriteSheet.getSprite(selectedSpriteIndex);
        }
        return sprite;
    }

    // ========================================================================
    // PIVOT APPLICATION
    // ========================================================================

    /**
     * Loads the pivot for the currently selected sprite in a sprite sheet.
     * Called when switching between sprites in the grid selector.
     */
    private void loadPivotForSelectedSprite() {
        if (spriteSheet == null) {
            return;
        }

        float[] pivot = spriteSheet.getEffectivePivot(selectedSpriteIndex);
        pivotX = pivot[0];
        pivotY = pivot[1];
        pivotXInput.set(pivotX);
        pivotYInput.set(pivotY);

        // Update original values so Cancel reverts to this sprite's pivot
        originalPivotX = pivotX;
        originalPivotY = pivotY;
    }

    private void applyPivot(boolean saveToFile) {
        if (assetPath == null) {
            return;
        }

        try {
            if (isSpriteSheet && spriteSheet != null) {
                // Apply to spritesheet sprites
                if (applyToAllSprites) {
                    // Set as default pivot for all sprites
                    spriteSheet.setDefaultPivot(pivotX, pivotY);
                    // Clear per-sprite overrides and apply to all cached sprites
                    spriteSheet.clearSpritePivots();
                    for (int i = 0; i < spriteSheet.getTotalFrames(); i++) {
                        Sprite s = spriteSheet.getSprite(i);
                        s.setPivot(pivotX, pivotY);
                    }
                    showStatus("Applied pivot to all " + spriteSheet.getTotalFrames() + " sprites");
                } else {
                    // Set per-sprite pivot override
                    spriteSheet.setSpritePivot(selectedSpriteIndex, pivotX, pivotY);
                    Sprite s = spriteSheet.getSprite(selectedSpriteIndex);
                    s.setPivot(pivotX, pivotY);
                    showStatus("Applied pivot to sprite #" + selectedSpriteIndex);
                }

                if (saveToFile) {
                    // Save spritesheet JSON
                    String filePath = java.nio.file.Paths.get(Assets.getAssetRoot(), assetPath).toString();
                    SpriteSheetLoader loader = new SpriteSheetLoader();
                    loader.save(spriteSheet, filePath);
                    showStatus("Saved pivot to " + assetPath);
                }

            } else if (sprite != null) {
                // Apply to sprite
                sprite.setPivot(pivotX, pivotY);

                if (saveToFile) {
                    // Save to metadata file
                    SpriteMetadata meta = AssetMetadata.loadOrDefault(
                            assetPath, SpriteMetadata.class, SpriteMetadata::new);
                    meta.pivotX = pivotX;
                    meta.pivotY = pivotY;

                    AssetMetadata.saveOrDelete(assetPath, meta);
                    showStatus("Saved pivot for " + assetPath);
                } else {
                    showStatus("Applied pivot (not saved)");
                }
            }

            // Update original values after successful apply
            originalPivotX = pivotX;
            originalPivotY = pivotY;

        } catch (IOException e) {
            System.err.println("[PivotEditorPanel] Failed to save pivot: " + e.getMessage());
            showStatus("Failed to save: " + e.getMessage());
        }
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    private void showStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
        System.out.println("[PivotEditorPanel] " + message);
    }

    /**
     * Applies pixel snapping to a pivot value if enabled.
     *
     * @param value Raw pivot value (0-1)
     * @param isX   true for X axis, false for Y axis
     * @return Snapped value if pixel snap is enabled, otherwise original value
     */
    private float applyPixelSnap(float value, boolean isX) {
        if (!pixelSnap) {
            return value;
        }

        Sprite s = getCurrentSprite();
        if (s == null) {
            return value;
        }

        float size = isX ? s.getWidth() : s.getHeight();
        if (size <= 0) {
            return value;
        }

        // Snap to nearest pixel
        float pixelValue = Math.round(value * size);
        return pixelValue / size;
    }

}
