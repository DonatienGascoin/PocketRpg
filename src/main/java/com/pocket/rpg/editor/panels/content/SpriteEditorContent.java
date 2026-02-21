package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.panels.spriteeditor.NineSliceEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.PivotEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.SlicingEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.TexturePreviewRenderer;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
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
import imgui.flag.ImGuiTabItemFlags;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Content implementation for editing sprite metadata (.png assets) in the unified AssetEditorPanel.
 * <p>
 * Provides:
 * <ul>
 *   <li>Mode toggle (Single / Multiple) with tilemap checkbox</li>
 *   <li>Tab bar: Slicing (multiple only), Pivot, 9-Slice</li>
 *   <li>Texture preview with grid overlay, pivot/9-slice overlays</li>
 *   <li>Tab-specific sidebar controls</li>
 *   <li>Zoom/pan controls</li>
 * </ul>
 * <p>
 * The shell handles save/undo/redo toolbar, dirty tracking, and asset selection via hamburger sidebar.
 */
@EditorContentFor(com.pocket.rpg.rendering.resources.Sprite.class)
public class SpriteEditorContent implements AssetEditorContent,
        SlicingEditorTab.OnChangeListener,
        PivotEditorTab.Listener,
        NineSliceEditorTab.Listener {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    private static final float SIDEBAR_WIDTH = 250f;
    private static final long UNDO_DEBOUNCE_MS = 300;

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

    private AssetEditorShell shell;
    private String texturePath;
    private Texture texture;
    private SpriteMetadata metadata;

    // Mode state
    private boolean isMultipleMode = false;
    private SpriteGrid spriteGrid = null;
    private int selectedSpriteIndex = 0;

    // UI state
    private EditorTab activeTab = EditorTab.PIVOT;
    private EditorTab forceSelectTab = null;

    // Tab components
    private final TexturePreviewRenderer previewRenderer;
    private final SlicingEditorTab slicingTab;
    private final PivotEditorTab pivotTab;
    private final NineSliceEditorTab nineSliceTab;

    // Confirmation dialogs
    private boolean showSingleModeConfirmation = false;

    // Undo — deferred snapshot pattern with debounce
    private SpriteMetadata pendingBeforeSnapshot = null;
    private long lastUndoCaptureTime = 0;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public SpriteEditorContent() {
        this.previewRenderer = new TexturePreviewRenderer();

        this.slicingTab = new SlicingEditorTab();
        this.slicingTab.setOnChangeListener(this);

        this.pivotTab = new PivotEditorTab();
        this.pivotTab.setListener(this);

        this.nineSliceTab = new NineSliceEditorTab();
        this.nineSliceTab.setListener(this);
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.shell = shell;
        this.texturePath = path;

        Sprite sprite = (Sprite) asset;
        this.texture = sprite.getTexture();

        // Load metadata
        metadata = AssetMetadata.load(path, SpriteMetadata.class);
        if (metadata == null) {
            metadata = new SpriteMetadata();
        }

        // Reset state
        isMultipleMode = metadata.isMultiple();
        selectedSpriteIndex = 0;
        pendingBeforeSnapshot = null;
        lastUndoCaptureTime = 0;

        // Reset preview
        previewRenderer.reset();

        // Initialize tabs
        slicingTab.loadFromMetadata(metadata);
        pivotTab.setMultipleMode(isMultipleMode);
        pivotTab.setSelectedCellIndex(selectedSpriteIndex);
        pivotTab.loadFromMetadata(metadata, selectedSpriteIndex);
        nineSliceTab.setMultipleMode(isMultipleMode);
        nineSliceTab.setSelectedCellIndex(selectedSpriteIndex);
        nineSliceTab.loadFromMetadata(metadata, selectedSpriteIndex);

        updateSpriteDimensions();

        if (isMultipleMode && metadata.grid != null) {
            spriteGrid = Assets.getSpriteGrid(sprite);
        } else {
            spriteGrid = null;
        }

        // Auto-fit to preview
        if (texture != null) {
            previewRenderer.fit(texture, 600, 500);
        }

        // Default to Pivot tab, or Slicing if multiple mode
        if (isMultipleMode) {
            activeTab = EditorTab.SLICING;
        } else {
            activeTab = EditorTab.PIVOT;
        }
    }

    @Override
    public void onAssetUnloaded() {
        texture = null;
        metadata = null;
        texturePath = null;
        spriteGrid = null;
        isMultipleMode = false;
        selectedSpriteIndex = 0;
        pendingBeforeSnapshot = null;
        previewRenderer.reset();
    }

    @Override
    public Class<?> getAssetClass() {
        return Sprite.class;
    }

    // ========================================================================
    // TAB CALLBACKS - SlicingEditorTab.OnChangeListener
    // ========================================================================

    @Override
    public void onGridSettingsChanged(GridSettings settings) {
        if (metadata == null || !isMultipleMode) return;

        captureUndoState(false);
        metadata.grid = settings;
        shell.markDirty();
    }

    // ========================================================================
    // TAB CALLBACKS - PivotEditorTab.Listener
    // ========================================================================

    @Override
    public void onPivotChanged(float pivotX, float pivotY) {
        captureUndoState(false); // Debounced for drag operations
        shell.markDirty();
    }

    @Override
    public void onApplyToAllChanged(boolean applyToAll, float pivotX, float pivotY) {
        if (!applyToAll && metadata != null) {
            captureUndoState(true); // Force push for discrete action
            PivotData pivotData = new PivotData(pivotX, pivotY);
            int totalCells = previewRenderer.getTotalCells();
            for (int i = 0; i < totalCells; i++) {
                setSpriteOverrideDirect(i, pivotData.copy(), null, true, false);
            }
            shell.markDirty();
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
        captureUndoState(false); // Debounced for drag operations
        shell.markDirty();
    }

    @Override
    public void onApplyToAllChanged(boolean applyToAll, NineSliceData sliceData) {
        if (!applyToAll && metadata != null) {
            captureUndoState(true); // Force push for discrete action
            NineSliceData sliceToStore = sliceData.hasSlicing() ? sliceData : null;
            int totalCells = previewRenderer.getTotalCells();
            for (int i = 0; i < totalCells; i++) {
                setSpriteOverrideDirect(i, null, sliceToStore != null ? sliceToStore.copy() : null, false, true);
            }
            shell.markDirty();
        }
    }

    // ========================================================================
    // CONTENT INTERFACE
    // ========================================================================

    @Override
    public void render() {
        if (texture == null || metadata == null) return;

        flushPendingUndo();

        // Tab bar
        renderTabBar();

        // Main content area (preview + sidebar)
        float availableHeight = ImGui.getContentRegionAvailY();

        if (ImGui.beginChild("SpriteMainContent", 0, availableHeight, false)) {
            float availableWidth = ImGui.getContentRegionAvailX();
            float previewWidth = availableWidth - SIDEBAR_WIDTH - 10;

            // Preview area (left)
            if (ImGui.beginChild("PreviewArea", previewWidth, 0, true)) {
                renderPreview();

                // Zoom controls at bottom of preview
                ImGui.separator();
                renderZoomControls();
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

        // Confirmation dialogs
        renderSingleModeConfirmation();
    }

    @Override
    public void renderToolbarExtras() {
        if (metadata == null) return;

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Mode radio buttons
        ImGui.text("Mode:");
        ImGui.sameLine();

        boolean wasMultiple = isMultipleMode;
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

        // Tilemap checkbox (only visible in Multiple mode)
        if (isMultipleMode) {
            ImGui.sameLine();
            ImGui.text(" ");
            ImGui.sameLine();
            boolean tilemap = metadata.usableAsTileset != null && metadata.usableAsTileset;
            if (ImGui.checkbox("Tilemap", tilemap)) {
                captureUndoState(true);
                metadata.usableAsTileset = !tilemap ? true : null;
                shell.markDirty();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Mark this spritesheet as usable in the tile palette");
            }
        }
    }

    @Override
    public void renderPopups() {
        // Single mode confirmation is handled in render() since it's modal
    }

    @Override
    public boolean hasCustomSave() {
        return true;
    }

    @Override
    public void customSave(String path) {
        if (metadata == null) return;

        try {
            // Save current editing values to metadata before persisting
            saveCurrentEditsToMetadata();

            // Save to file
            AssetMetadata.save(path, metadata);

            // Apply metadata to the cached in-memory Sprite
            applySavedMetadataToSprite();

            shell.showStatus("Saved: " + path);

            // Publish event to notify other components
            EditorEventBus.get().publish(new AssetChangedEvent(path, AssetChangedEvent.ChangeType.MODIFIED));

        } catch (IOException e) {
            System.err.println("[SpriteEditorContent] Failed to save: " + e.getMessage());
            shell.showStatus("Failed to save: " + e.getMessage());
        }
    }

    @Override
    public List<ShortcutAction> provideExtraShortcuts(KeyboardLayout layout) {
        return List.of();
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
        // Reserve space for zoom controls + separator
        float availHeight = ImGui.getContentRegionAvailY() - 30;

        if (availHeight < 50) availHeight = 50;

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
            case SLICING -> handleCellSelection();
            case PIVOT -> pivotTab.handleInteraction(previewRenderer);
            case NINE_SLICE -> nineSliceTab.handleInteraction(previewRenderer);
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
    // ZOOM CONTROLS
    // ========================================================================

    private void renderZoomControls() {
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
            if (texture != null) {
                previewRenderer.fit(texture, previewRenderer.getAreaWidth(), previewRenderer.getAreaHeight());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Fit texture to preview area");
        }
    }

    // ========================================================================
    // CONFIRMATION DIALOG
    // ========================================================================

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
    // SPRITE SELECTION
    // ========================================================================

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
    }

    private void setSpriteOverrideDirect(int index, PivotData pivot, NineSliceData nineSlice,
                                          boolean updatePivot, boolean updateNineSlice) {
        if (metadata.sprites == null) {
            metadata.sprites = new LinkedHashMap<>();
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

        captureUndoState(true);

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

        shell.markDirty();

        // Switch to Pivot tab (Slicing not available in single mode)
        if (activeTab == EditorTab.SLICING) {
            forceSelectTab = EditorTab.PIVOT;
        }
    }

    private void switchToMultipleMode() {
        if (isMultipleMode) return;

        captureUndoState(true);

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

        shell.markDirty();

        // Switch to Slicing tab to configure grid
        forceSelectTab = EditorTab.SLICING;
    }

    // ========================================================================
    // SAVE HELPERS
    // ========================================================================

    private void applySavedMetadataToSprite() {
        Sprite sprite = Assets.load(texturePath, Sprite.class);
        if (sprite == null) return;

        boolean spriteIsMultiple = Assets.isMultipleMode(sprite);

        if (!spriteIsMultiple) {
            if (metadata.isSingle()) {
                if (metadata.hasPivot()) {
                    sprite.setPivot(metadata.pivotX, metadata.pivotY);
                }
                sprite.setNineSliceData(metadata.nineSlice != null ? metadata.nineSlice.copy() : null);
            }
        } else {
            if (metadata.isMultiple()) {
                SpriteGrid grid = Assets.getSpriteGrid(sprite);
                if (grid != null) {
                    grid.clearCache();
                }
            }
        }
    }

    // ========================================================================
    // UNDO/REDO (deferred snapshot pattern with debounce)
    // ========================================================================

    /**
     * Captures the current metadata state for undo. Uses debounce for drag operations.
     *
     * @param force If true, bypasses debounce (use for discrete actions like mode switch, presets)
     */
    private void captureUndoState(boolean force) {
        if (metadata == null) return;

        long now = System.currentTimeMillis();

        // Debounce rapid changes (during drag operations)
        if (!force && pendingBeforeSnapshot != null && (now - lastUndoCaptureTime) < UNDO_DEBOUNCE_MS) {
            return;
        }

        // Flush any existing pending undo before starting a new one
        flushPendingUndo();

        pendingBeforeSnapshot = copyMetadata(metadata);
        lastUndoCaptureTime = now;
    }

    private void flushPendingUndo() {
        if (pendingBeforeSnapshot == null || metadata == null) return;
        SpriteMetadata beforeSnapshot = pendingBeforeSnapshot;
        SpriteMetadata afterSnapshot = copyMetadata(metadata);
        pendingBeforeSnapshot = null;

        UndoManager um = UndoManager.getInstance();
        um.push(new SnapshotCommand<>(metadata, beforeSnapshot, afterSnapshot,
                (target, snapshot) -> {
                    SpriteMetadata restored = (SpriteMetadata) snapshot;
                    restoreMetadata(restored);
                },
                "Edit sprite metadata"));
    }

    /**
     * Restores metadata from a snapshot and reloads all tabs.
     */
    private void restoreMetadata(SpriteMetadata source) {
        // Copy all fields from source into current metadata
        metadata.spriteMode = source.spriteMode;
        metadata.pixelsPerUnitOverride = source.pixelsPerUnitOverride;
        metadata.pivotX = source.pivotX;
        metadata.pivotY = source.pivotY;
        metadata.nineSlice = source.nineSlice != null ? source.nineSlice.copy() : null;
        metadata.usableAsTileset = source.usableAsTileset;

        if (source.grid != null) {
            metadata.grid = source.grid.copy();
        } else {
            metadata.grid = null;
        }
        if (source.defaultPivot != null) {
            metadata.defaultPivot = source.defaultPivot.copy();
        } else {
            metadata.defaultPivot = null;
        }
        if (source.defaultNineSlice != null) {
            metadata.defaultNineSlice = source.defaultNineSlice.copy();
        } else {
            metadata.defaultNineSlice = null;
        }
        if (source.sprites != null) {
            metadata.sprites = new LinkedHashMap<>();
            for (var entry : source.sprites.entrySet()) {
                metadata.sprites.put(entry.getKey(), entry.getValue().copy());
            }
        } else {
            metadata.sprites = null;
        }

        // Update mode state
        isMultipleMode = metadata.isMultiple();

        // Reload all tabs
        reloadTabs();
        shell.markDirty();
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

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

    private void reloadTabs() {
        slicingTab.loadFromMetadata(metadata);
        pivotTab.setMultipleMode(isMultipleMode);
        pivotTab.setSelectedCellIndex(selectedSpriteIndex);
        pivotTab.loadFromMetadata(metadata, selectedSpriteIndex);
        nineSliceTab.setMultipleMode(isMultipleMode);
        nineSliceTab.setSelectedCellIndex(selectedSpriteIndex);
        nineSliceTab.loadFromMetadata(metadata, selectedSpriteIndex);
        updateSpriteDimensions();
    }

    /**
     * Deep-copies SpriteMetadata. Includes usableAsTileset (bug fix from old SpriteEditorPanel).
     */
    private SpriteMetadata copyMetadata(SpriteMetadata source) {
        if (source == null) return null;

        SpriteMetadata copy = new SpriteMetadata();
        copy.spriteMode = source.spriteMode;
        copy.pixelsPerUnitOverride = source.pixelsPerUnitOverride;
        copy.pivotX = source.pivotX;
        copy.pivotY = source.pivotY;
        copy.nineSlice = source.nineSlice != null ? source.nineSlice.copy() : null;
        copy.usableAsTileset = source.usableAsTileset;

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
            copy.sprites = new LinkedHashMap<>();
            for (var entry : source.sprites.entrySet()) {
                copy.sprites.put(entry.getKey(), entry.getValue().copy());
            }
        }

        return copy;
    }
}
