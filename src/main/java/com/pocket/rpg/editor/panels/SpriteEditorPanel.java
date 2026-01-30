package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.spriteeditor.NineSliceEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.PivotEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.SlicingEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.TextureBrowserDialog;
import com.pocket.rpg.editor.panels.spriteeditor.TexturePreviewRenderer;
import com.pocket.rpg.rendering.resources.NineSliceData;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.GridSettings;
import com.pocket.rpg.resources.SpriteMetadata.PivotData;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sprite Editor - Full texture view with grid overlay and click-to-select.
 * <p>
 * Features:
 * <ul>
 *   <li>Full texture preview (not just individual sprites)</li>
 *   <li>Grid overlay for multiple mode spritesheets</li>
 *   <li>Click-to-select sprites in the preview</li>
 *   <li>Mode switching (Single ↔ Multiple)</li>
 *   <li>Tabs: Slicing (multiple only), Pivot, 9-Slice</li>
 * </ul>
 */
public class SpriteEditorPanel implements
        SlicingEditorTab.OnChangeListener,
        PivotEditorTab.Listener,
        NineSliceEditorTab.Listener {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    private static final String POPUP_ID = "Sprite Editor";
    private static final float SIDEBAR_WIDTH = 250f;

    // ========================================================================
    // ENUMS
    // ========================================================================

    public enum EditorTab {
        SLICING,
        PIVOT,
        NINE_SLICE
    }

    // ========================================================================
    // STATE
    // ========================================================================

    private boolean shouldOpen = false;
    private boolean isOpen = false;

    // Current asset
    private String texturePath = null;
    private Texture texture = null;
    private SpriteMetadata metadata = null;
    private SpriteMetadata originalMetadata = null;
    private boolean hasUnsavedChanges = false;

    // Mode state
    private boolean isMultipleMode = false;
    private SpriteGrid spriteGrid = null;
    private int selectedSpriteIndex = 0;

    // UI state
    private EditorTab activeTab = EditorTab.PIVOT;
    private EditorTab forceSelectTab = null; // Set to force-select a tab on next frame

    // Tab components
    private final TexturePreviewRenderer previewRenderer;
    private final SlicingEditorTab slicingTab;
    private final PivotEditorTab pivotTab;
    private final NineSliceEditorTab nineSliceTab;

    // Asset browser dialog
    private final TextureBrowserDialog textureBrowserDialog;

    // Confirmation dialogs
    private boolean showSingleModeConfirmation = false;

    // Undo/Redo state (local to editor session)
    private final java.util.Deque<SpriteMetadata> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<SpriteMetadata> redoStack = new java.util.ArrayDeque<>();
    private static final int MAX_UNDO_HISTORY = 50;
    private SpriteMetadata lastPushedState = null;
    private long lastUndoPushTime = 0;
    private static final long UNDO_DEBOUNCE_MS = 300; // Debounce rapid changes (drag operations)

    // Callbacks
    private Consumer<String> statusCallback;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public SpriteEditorPanel() {
        this.previewRenderer = new TexturePreviewRenderer();

        this.slicingTab = new SlicingEditorTab();
        this.slicingTab.setOnChangeListener(this);

        this.pivotTab = new PivotEditorTab();
        this.pivotTab.setListener(this);

        this.nineSliceTab = new NineSliceEditorTab();
        this.nineSliceTab.setListener(this);

        this.textureBrowserDialog = new TextureBrowserDialog();
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Opens the sprite editor for the specified texture path.
     */
    public void open(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        this.texturePath = path;
        this.shouldOpen = true;
        loadTexture(path);
    }

    /**
     * Opens the sprite editor without a pre-selected texture.
     */
    public void open() {
        this.shouldOpen = true;
        this.texturePath = null;
        this.texture = null;
        this.metadata = null;
        this.originalMetadata = null;
        this.spriteGrid = null;
        this.isMultipleMode = false;
        this.hasUnsavedChanges = false;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setActiveTab(EditorTab tab) {
        this.activeTab = tab;
    }

    public EditorTab getActiveTab() {
        return activeTab;
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    // ========================================================================
    // TAB CALLBACKS - SlicingEditorTab.OnChangeListener
    // ========================================================================

    @Override
    public void onGridSettingsChanged(GridSettings settings) {
        if (metadata == null || !isMultipleMode) return;

        pushUndoState();
        metadata.grid = settings;
        markDirty();
    }

    // ========================================================================
    // TAB CALLBACKS - PivotEditorTab.Listener
    // ========================================================================

    @Override
    public void onPivotChanged(float pivotX, float pivotY) {
        pushUndoState(); // Debounced for drag operations
        markDirty();
    }

    @Override
    public void onApplyToAllChanged(boolean applyToAll, float pivotX, float pivotY) {
        if (!applyToAll && metadata != null) {
            pushUndoState(true); // Force push for discrete action
            // Turning OFF "Apply to All" - save current value to all sprites
            PivotData pivotData = new PivotData(pivotX, pivotY);
            int totalCells = previewRenderer.getTotalCells();
            for (int i = 0; i < totalCells; i++) {
                setSpriteOverrideDirect(i, pivotData.copy(), null, true, false);
            }
            markDirty();
        }
    }

    @Override
    public void onCellSelected(int cellIndex) {
        selectSprite(cellIndex);
    }

    // ========================================================================
    // TAB CALLBACKS - NineSliceEditorTab.Listener
    // ========================================================================

    @Override
    public void onSliceChanged(int left, int right, int top, int bottom) {
        pushUndoState(); // Debounced for drag operations
        markDirty();
    }

    @Override
    public void onApplyToAllChanged(boolean applyToAll, NineSliceData sliceData) {
        if (!applyToAll && metadata != null) {
            pushUndoState(true); // Force push for discrete action
            // Turning OFF "Apply to All" - save current value to all sprites
            NineSliceData sliceToStore = sliceData.hasSlicing() ? sliceData : null;
            int totalCells = previewRenderer.getTotalCells();
            for (int i = 0; i < totalCells; i++) {
                setSpriteOverrideDirect(i, null, sliceToStore != null ? sliceToStore.copy() : null, false, true);
            }
            markDirty();
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
            isOpen = true;
        }

        ImGui.setNextWindowSize(1000, 750);

        ImBoolean pOpen = new ImBoolean(true);
        int flags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar;

        if (ImGui.beginPopupModal(POPUP_ID, pOpen, flags)) {
            handleKeyboardShortcuts();
            renderContent();
            ImGui.endPopup();
        }

        if (!pOpen.get() && isOpen) {
            isOpen = false;
        }
    }

    private void renderContent() {
        // Header: Asset selector and mode
        renderHeader();
        ImGui.separator();

        // Calculate layout dimensions
        float footerHeight = 40;
        float contentHeight = ImGui.getContentRegionAvailY() - footerHeight;

        if (texture == null) {
            // No asset - show placeholder in content area
            if (ImGui.beginChild("NoAssetContent", 0, contentHeight, false)) {
                renderNoAssetMessage();
            }
            ImGui.endChild();
        } else {
            // Tab bar
            renderTabBar();

            // Main content area (preview + sidebar)
            float remainingHeight = ImGui.getContentRegionAvailY() - footerHeight;

            if (ImGui.beginChild("MainContent", 0, remainingHeight, false)) {
                float availableWidth = ImGui.getContentRegionAvailX();
                float previewWidth = availableWidth - SIDEBAR_WIDTH - 10;

                // Preview area (left)
                if (ImGui.beginChild("PreviewArea", previewWidth, 0, true)) {
                    renderPreview();
                }
                ImGui.endChild();

                ImGui.sameLine();

                // Sidebar (right)
                if (ImGui.beginChild("Sidebar", SIDEBAR_WIDTH, 0, true)) {
                    renderSidebar();
                }
                ImGui.endChild();
            }
            ImGui.endChild();
        }

        // Footer (always at bottom)
        ImGui.separator();
        renderFooter();

        // Asset browser dialog (always rendered so it can open)
        textureBrowserDialog.render();

        // Confirmation dialogs
        renderSingleModeConfirmation();
    }

    private void renderSingleModeConfirmation() {
        if (showSingleModeConfirmation) {
            ImGui.openPopup("Convert to Single Mode?");
            showSingleModeConfirmation = false;
        }

        ImGui.setNextWindowSize(350, 0);
        if (ImGui.beginPopupModal("Convert to Single Mode?")) {
            ImGui.textWrapped("Converting to Single mode will discard per-sprite pivot and 9-slice data.");
            ImGui.spacing();
            ImGui.textWrapped("Sprite #0's values will be used for the single sprite.");
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            float buttonWidth = 80;
            float totalWidth = buttonWidth * 2 + 10;
            float startX = (ImGui.getContentRegionAvailX() - totalWidth) / 2;
            ImGui.setCursorPosX(ImGui.getCursorPosX() + startX);

            if (ImGui.button("Convert", buttonWidth, 0)) {
                switchToSingleMode();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", buttonWidth, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    // ========================================================================
    // HEADER
    // ========================================================================

    private void renderHeader() {
        // Row 1: Asset path and Browse button
        ImGui.text("Asset:");
        ImGui.sameLine();

        String displayPath = texturePath != null ? texturePath : "(None selected)";
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 100);
        ImGui.inputText("##AssetPath", new imgui.type.ImString(displayPath, 256),
                ImGuiInputTextFlags.ReadOnly);

        ImGui.sameLine();

        if (ImGui.button(MaterialIcons.FolderOpen + " Browse")) {
            textureBrowserDialog.open(this::onTextureSelected);
        }

        // Row 2: Mode selector
        if (texture != null) {
            ImGui.text("Mode:");
            ImGui.sameLine();

            if (ImGui.radioButton("Single", !isMultipleMode)) {
                if (isMultipleMode) {
                    showSingleModeConfirmation = true;
                }
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Multiple", isMultipleMode)) {
                if (!isMultipleMode) {
                    switchToMultipleMode();
                }
            }
        }
    }

    // ========================================================================
    // TAB BAR
    // ========================================================================

    private void renderTabBar() {
        if (ImGui.beginTabBar("SpriteEditorTabs")) {
            // Slicing tab (only visible in multiple mode)
            if (isMultipleMode) {
                int slicingFlags = (forceSelectTab == EditorTab.SLICING) ? ImGuiTabItemFlags.SetSelected : 0;
                if (ImGui.beginTabItem("Slicing", slicingFlags)) {
                    activeTab = EditorTab.SLICING;
                    ImGui.endTabItem();
                }
            }

            int pivotFlags = (forceSelectTab == EditorTab.PIVOT) ? ImGuiTabItemFlags.SetSelected : 0;
            if (ImGui.beginTabItem("Pivot", pivotFlags)) {
                activeTab = EditorTab.PIVOT;
                ImGui.endTabItem();
            }

            int nineSliceFlags = (forceSelectTab == EditorTab.NINE_SLICE) ? ImGuiTabItemFlags.SetSelected : 0;
            if (ImGui.beginTabItem("9-Slice", nineSliceFlags)) {
                activeTab = EditorTab.NINE_SLICE;
                ImGui.endTabItem();
            }

            ImGui.endTabBar();

            // Clear force-select after one frame
            forceSelectTab = null;
        }
    }

    // ========================================================================
    // PREVIEW
    // ========================================================================

    private void renderPreview() {
        float availWidth = ImGui.getContentRegionAvailX();
        float availHeight = ImGui.getContentRegionAvailY();

        if (previewRenderer.beginPreview(texture, metadata, availWidth, availHeight)) {
            // Draw grid overlay in multiple mode
            if (isMultipleMode) {
                previewRenderer.drawGridOverlay();

                if (activeTab == EditorTab.SLICING) {
                    previewRenderer.drawCellNumbers();
                }
            }

            // Draw tab-specific overlays
            switch (activeTab) {
                case SLICING -> {
                    // Grid overlay already drawn above
                }
                case PIVOT -> {
                    pivotTab.drawPreviewOverlay(previewRenderer, metadata);
                }
                case NINE_SLICE -> {
                    nineSliceTab.drawPreviewOverlay(previewRenderer, metadata);
                }
            }

            // Handle interaction
            handlePreviewInteraction();

            previewRenderer.endPreview();
        }
    }

    private void handlePreviewInteraction() {
        switch (activeTab) {
            case SLICING -> {
                // Slicing tab doesn't have special preview interaction
                handleCellSelection();
            }
            case PIVOT -> {
                if (!pivotTab.handleInteraction(previewRenderer)) {
                    // Pivot tab didn't handle it
                }
            }
            case NINE_SLICE -> {
                if (!nineSliceTab.handleInteraction(previewRenderer)) {
                    // Nine-slice tab didn't handle it
                }
            }
        }
    }

    private void handleCellSelection() {
        if (previewRenderer.isHovered() && !previewRenderer.isPanning() &&
                ImGui.isMouseClicked(imgui.flag.ImGuiMouseButton.Left)) {
            ImVec2 mousePos = ImGui.getMousePos();
            int hitCell = previewRenderer.hitTestCell(mousePos.x, mousePos.y);
            if (hitCell >= 0 && hitCell != selectedSpriteIndex) {
                selectSprite(hitCell);
            }
        }
    }

    // ========================================================================
    // SIDEBAR
    // ========================================================================

    private void renderSidebar() {
        switch (activeTab) {
            case SLICING -> slicingTab.renderSidebar(texture, metadata);
            case PIVOT -> pivotTab.renderSidebar(texture, metadata);
            case NINE_SLICE -> nineSliceTab.renderSidebar(texture, metadata);
        }
    }

    // ========================================================================
    // FOOTER
    // ========================================================================

    private void renderFooter() {
        // Zoom controls
        ImGui.text("Zoom:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(120);
        float[] zoomArr = {previewRenderer.getZoom()};
        if (ImGui.sliderFloat("##Zoom", zoomArr, TexturePreviewRenderer.MIN_ZOOM,
                TexturePreviewRenderer.MAX_ZOOM, "%.2fx")) {
            previewRenderer.setZoom(zoomArr[0]);
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Reset")) {
            previewRenderer.reset();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset zoom and pan to default");
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Fit")) {
            previewRenderer.fit(texture, previewRenderer.getAreaWidth(), previewRenderer.getAreaHeight());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Fit texture to preview area");
        }

        // Buttons on the right
        float buttonWidth = 80;
        float totalWidth = buttonWidth * 2 + 10;
        ImGui.sameLine(ImGui.getContentRegionAvailX() - totalWidth);

        if (ImGui.button("Cancel", buttonWidth, 0)) {
            revertChanges();
            ImGui.closeCurrentPopup();
            isOpen = false;
        }

        ImGui.sameLine();

        ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.4f, 0.7f, 0.4f, 1f);
        if (ImGui.button(MaterialIcons.Save + " Save", buttonWidth, 0)) {
            saveChanges();
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save changes to metadata file");
        }
    }

    // ========================================================================
    // NO ASSET MESSAGE
    // ========================================================================

    private void renderNoAssetMessage() {
        float centerX = ImGui.getContentRegionAvailX() / 2;
        float centerY = ImGui.getContentRegionAvailY() / 2 - 50;

        ImGui.dummy(0, centerY);

        String message = "Select an asset to edit";
        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, message);
        ImGui.setCursorPosX((ImGui.getContentRegionAvailX() - textSize.x) / 2);
        ImGui.textDisabled(message);

        ImGui.spacing();

        float buttonWidth = 100;
        ImGui.setCursorPosX((ImGui.getContentRegionAvailX() - buttonWidth) / 2);
        if (ImGui.button(MaterialIcons.FolderOpen + " Browse...", buttonWidth, 0)) {
            textureBrowserDialog.open(this::onTextureSelected);
        }
    }

    // ========================================================================
    // TEXTURE SELECTION CALLBACK
    // ========================================================================

    /**
     * Called when a texture is selected from the browser dialog.
     */
    private void onTextureSelected(String path) {
        if (path != null && !path.isEmpty()) {
            texturePath = path;
            loadTexture(path);
        }
    }

    // ========================================================================
    // ASSET LOADING
    // ========================================================================

    private void loadTexture(String path) {
        texture = null;
        metadata = null;
        originalMetadata = null;
        spriteGrid = null;
        isMultipleMode = false;
        hasUnsavedChanges = false;
        selectedSpriteIndex = 0;

        previewRenderer.reset();

        if (path == null || path.isEmpty()) return;

        try {
            // Load texture via sprite
            Sprite sprite = Assets.load(path, Sprite.class);
            if (sprite == null) {
                showStatus("Failed to load texture: " + path);
                return;
            }
            texture = sprite.getTexture();

            // Load metadata
            metadata = AssetMetadata.load(path, SpriteMetadata.class);
            if (metadata == null) {
                metadata = new SpriteMetadata();
            }

            // Store original for revert
            originalMetadata = copyMetadata(metadata);

            // Determine mode
            isMultipleMode = metadata.isMultiple();

            // Initialize tabs
            slicingTab.loadFromMetadata(metadata);
            pivotTab.setMultipleMode(isMultipleMode);
            pivotTab.setSelectedCellIndex(selectedSpriteIndex);
            pivotTab.loadFromMetadata(metadata, selectedSpriteIndex);
            nineSliceTab.setMultipleMode(isMultipleMode);
            nineSliceTab.setSelectedCellIndex(selectedSpriteIndex);
            nineSliceTab.loadFromMetadata(metadata, selectedSpriteIndex);

            // Update sprite dimensions for 9-slice
            updateSpriteDimensions();

            if (isMultipleMode && metadata.grid != null) {
                // Get sprite grid for reference
                spriteGrid = Assets.getSpriteGrid(sprite);
            }

            // Auto-fit to preview
            if (texture != null) {
                previewRenderer.fit(texture, 600, 500);
            }

            showStatus("Loaded: " + path);

        } catch (Exception e) {
            System.err.println("[SpriteEditorPanel] Failed to load: " + path + " - " + e.getMessage());
            showStatus("Failed to load: " + e.getMessage());
        }
    }

    private void updateSpriteDimensions() {
        int width, height;
        if (isMultipleMode && metadata != null && metadata.grid != null) {
            width = metadata.grid.spriteWidth;
            height = metadata.grid.spriteHeight;
        } else if (texture != null) {
            width = texture.getWidth();
            height = texture.getHeight();
        } else {
            width = 16;
            height = 16;
        }
        pivotTab.setSpriteDimensions(width, height);
        nineSliceTab.setSpriteDimensions(width, height);
    }

    private void selectSprite(int index) {
        // Save current edits to metadata before switching
        saveCurrentEditsToMetadata();

        selectedSpriteIndex = index;

        // Update tabs
        pivotTab.setSelectedCellIndex(index);
        pivotTab.loadFromMetadata(metadata, index);
        nineSliceTab.setSelectedCellIndex(index);
        nineSliceTab.loadFromMetadata(metadata, index);
    }

    /**
     * Writes current editing values to the working metadata copy.
     */
    private void saveCurrentEditsToMetadata() {
        if (metadata == null) return;

        if (isMultipleMode) {
            PivotData pivotData = new PivotData(pivotTab.getPivotX(), pivotTab.getPivotY());
            NineSliceData sliceData = nineSliceTab.getSliceData();
            NineSliceData sliceToStore = sliceData.hasSlicing() ? sliceData : null;

            int totalCells = previewRenderer.getTotalCells();
            if (totalCells == 0) totalCells = 1;

            if (pivotTab.isApplyToAll()) {
                for (int i = 0; i < totalCells; i++) {
                    setSpriteOverrideDirect(i, pivotData.copy(), null, true, false);
                }
            } else {
                setSpriteOverrideDirect(selectedSpriteIndex, pivotData, null, true, false);
            }

            if (nineSliceTab.isApplyToAll()) {
                for (int i = 0; i < totalCells; i++) {
                    setSpriteOverrideDirect(i, null, sliceToStore != null ? sliceToStore.copy() : null, false, true);
                }
            } else {
                setSpriteOverrideDirect(selectedSpriteIndex, null, sliceToStore, false, true);
            }
        } else {
            // Single mode
            metadata.pivotX = pivotTab.getPivotX();
            metadata.pivotY = pivotTab.getPivotY();

            NineSliceData sliceData = nineSliceTab.getSliceData();
            metadata.nineSlice = sliceData.hasSlicing() ? sliceData : null;
        }

        markDirty();
    }

    /**
     * Directly sets sprite override fields, allowing null values to clear.
     */
    private void setSpriteOverrideDirect(int index, PivotData pivot, NineSliceData nineSlice,
                                          boolean updatePivot, boolean updateNineSlice) {
        if (metadata.sprites == null) {
            metadata.sprites = new java.util.LinkedHashMap<>();
        }

        SpriteMetadata.SpriteOverride override = metadata.sprites.computeIfAbsent(index,
                k -> new SpriteMetadata.SpriteOverride());

        if (updatePivot) {
            override.pivot = pivot != null ? pivot.copy() : null;
        }
        if (updateNineSlice) {
            override.nineSlice = nineSlice != null ? nineSlice.copy() : null;
        }

        // Remove empty overrides to keep metadata clean
        if (override.isEmpty()) {
            metadata.sprites.remove(index);
        }
    }

    // ========================================================================
    // MODE SWITCHING
    // ========================================================================

    private void switchToSingleMode() {
        if (!isMultipleMode) return;

        isMultipleMode = false;
        selectedSpriteIndex = 0;

        if (metadata != null) {
            metadata.convertToSingle();
        }

        // Update tabs
        pivotTab.setMultipleMode(false);
        pivotTab.setSelectedCellIndex(0);
        pivotTab.loadFromMetadata(metadata, 0);
        nineSliceTab.setMultipleMode(false);
        nineSliceTab.setSelectedCellIndex(0);
        nineSliceTab.loadFromMetadata(metadata, 0);
        updateSpriteDimensions();

        markDirty();

        // Switch to Pivot tab (Slicing not available in single mode)
        if (activeTab == EditorTab.SLICING) {
            forceSelectTab = EditorTab.PIVOT;
        }
    }

    private void switchToMultipleMode() {
        if (isMultipleMode) return;

        isMultipleMode = true;
        selectedSpriteIndex = 0;

        if (metadata == null) {
            metadata = new SpriteMetadata();
        }

        // Create default grid settings if none exist
        GridSettings grid = slicingTab.toGridSettings();
        metadata.convertToMultiple(grid);

        // Update tabs
        slicingTab.loadFromMetadata(metadata);
        pivotTab.setMultipleMode(true);
        pivotTab.setSelectedCellIndex(0);
        pivotTab.loadFromMetadata(metadata, 0);
        nineSliceTab.setMultipleMode(true);
        nineSliceTab.setSelectedCellIndex(0);
        nineSliceTab.loadFromMetadata(metadata, 0);
        updateSpriteDimensions();

        markDirty();

        // Switch to Slicing tab to configure grid
        forceSelectTab = EditorTab.SLICING;
    }

    // ========================================================================
    // SAVE / REVERT
    // ========================================================================

    private void saveChanges() {
        if (texturePath == null || metadata == null) return;

        try {
            // Save current editing values to metadata before persisting
            saveCurrentEditsToMetadata();

            // Save to file
            AssetMetadata.save(texturePath, metadata);

            // Apply metadata to the cached in-memory Sprite so existing references
            // (e.g., UIImage components) pick up the changes immediately
            applySavedMetadataToSprite();

            // Update original for future revert
            originalMetadata = copyMetadata(metadata);
            hasUnsavedChanges = false;

            showStatus("Saved: " + texturePath);

            // Publish event to notify other components (e.g., asset browser)
            EditorEventBus.get().publish(new AssetChangedEvent(texturePath, AssetChangedEvent.ChangeType.MODIFIED));

        } catch (IOException e) {
            System.err.println("[SpriteEditorPanel] Failed to save: " + e.getMessage());
            showStatus("Failed to save: " + e.getMessage());
        }
    }

    /**
     * Applies saved metadata to the cached in-memory Sprite.
     * This ensures components referencing the sprite (e.g., UIImage) see updated
     * pivot and 9-slice data without requiring a full reload.
     */
    private void applySavedMetadataToSprite() {
        Sprite sprite = Assets.load(texturePath, Sprite.class);
        if (sprite == null) return;

        if (metadata.isSingle()) {
            // Apply pivot
            if (metadata.hasPivot()) {
                sprite.setPivot(metadata.pivotX, metadata.pivotY);
            }
            // Apply 9-slice data
            sprite.setNineSliceData(metadata.nineSlice != null ? metadata.nineSlice.copy() : null);
        } else {
            // For multiple mode, grid sprites are lazily created from metadata,
            // so we clear the grid cache to force re-generation with updated data
            SpriteGrid grid = Assets.getSpriteGrid(sprite);
            if (grid != null) {
                grid.clearCache();
            }
        }
    }

    private void revertChanges() {
        if (originalMetadata != null) {
            // Restore metadata from original
            metadata = copyMetadata(originalMetadata);

            // Reload tabs
            slicingTab.loadFromMetadata(metadata);
            pivotTab.loadFromMetadata(metadata, selectedSpriteIndex);
            nineSliceTab.loadFromMetadata(metadata, selectedSpriteIndex);
        }
        hasUnsavedChanges = false;
    }

    private void markDirty() {
        hasUnsavedChanges = true;
    }

    // ========================================================================
    // UNDO / REDO
    // ========================================================================

    /**
     * Pushes current state to undo stack before making changes.
     * Call this before modifying metadata.
     *
     * @param force If true, bypasses debounce (use for discrete actions like presets)
     */
    private void pushUndoState(boolean force) {
        if (metadata == null) return;

        long now = System.currentTimeMillis();

        // Debounce rapid changes (during drag operations)
        if (!force && (now - lastUndoPushTime) < UNDO_DEBOUNCE_MS) {
            return;
        }

        SpriteMetadata snapshot = copyMetadata(metadata);

        undoStack.push(snapshot);
        lastPushedState = snapshot;
        lastUndoPushTime = now;
        redoStack.clear(); // New change invalidates redo history

        // Enforce max history size
        while (undoStack.size() > MAX_UNDO_HISTORY) {
            undoStack.removeLast();
        }
    }

    /**
     * Pushes current state to undo stack (with debounce).
     */
    private void pushUndoState() {
        pushUndoState(false);
    }

    /**
     * Undoes the last change.
     */
    private void undo() {
        if (undoStack.isEmpty() || metadata == null) {
            showStatus("Nothing to undo");
            return;
        }

        // Push current state to redo stack
        redoStack.push(copyMetadata(metadata));

        // Restore previous state
        metadata = undoStack.pop();
        lastPushedState = null;

        // Reload all tabs
        reloadTabs();
        showStatus("Undo");
    }

    /**
     * Redoes the last undone change.
     */
    private void redo() {
        if (redoStack.isEmpty() || metadata == null) {
            showStatus("Nothing to redo");
            return;
        }

        // Push current state to undo stack
        undoStack.push(copyMetadata(metadata));

        // Restore redo state
        metadata = redoStack.pop();
        lastPushedState = null;

        // Reload all tabs
        reloadTabs();
        showStatus("Redo");
    }

    /**
     * Reloads all tabs from current metadata.
     */
    private void reloadTabs() {
        slicingTab.loadFromMetadata(metadata);
        pivotTab.loadFromMetadata(metadata, selectedSpriteIndex);
        nineSliceTab.loadFromMetadata(metadata, selectedSpriteIndex);
        updateSpriteDimensions();
        hasUnsavedChanges = true;
    }

    /**
     * Clears undo/redo history.
     */
    private void clearUndoHistory() {
        undoStack.clear();
        redoStack.clear();
        lastPushedState = null;
    }

    /**
     * Checks if two metadata objects are equal (for avoiding duplicate undo states).
     */
    private boolean metadataEquals(SpriteMetadata a, SpriteMetadata b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // Simple comparison - just check if they serialize to the same JSON
        // For now, always return false to allow all states
        return false;
    }

    /**
     * Handles keyboard shortcuts for the editor.
     */
    private void handleKeyboardShortcuts() {
        // Only handle when popup is focused
        if (!isOpen) return;

        boolean ctrl = ImGui.getIO().getKeyCtrl();
        boolean shift = ImGui.getIO().getKeyShift();

        // Ctrl+Z = Undo
        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.Z)) {
            undo();
        }
        // Ctrl+Shift+Z or Ctrl+Y = Redo
        if ((ctrl && shift && ImGui.isKeyPressed(ImGuiKey.Z)) ||
                (ctrl && ImGui.isKeyPressed(ImGuiKey.Y))) {
            redo();
        }
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    private SpriteMetadata copyMetadata(SpriteMetadata source) {
        if (source == null) return null;

        SpriteMetadata copy = new SpriteMetadata();
        copy.spriteMode = source.spriteMode;
        copy.pixelsPerUnitOverride = source.pixelsPerUnitOverride;
        copy.pivotX = source.pivotX;
        copy.pivotY = source.pivotY;
        copy.nineSlice = source.nineSlice != null ? source.nineSlice.copy() : null;

        if (source.grid != null) {
            copy.grid = source.grid.copy();
        }
        if (source.defaultPivot != null) {
            copy.defaultPivot = source.defaultPivot.copy();
        }
        if (source.defaultNineSlice != null) {
            copy.defaultNineSlice = source.defaultNineSlice.copy();
        }
        if (source.sprites != null) {
            copy.sprites = new java.util.LinkedHashMap<>();
            for (var entry : source.sprites.entrySet()) {
                copy.sprites.put(entry.getKey(), entry.getValue().copy());
            }
        }

        return copy;
    }

    private String getFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private void showStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
        System.out.println("[SpriteEditorPanel] " + message);
    }
}
