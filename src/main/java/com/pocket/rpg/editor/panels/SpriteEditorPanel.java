package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.assets.AssetPreviewRegistry;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.spriteeditor.NineSliceEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.PivotEditorTab;
import com.pocket.rpg.editor.panels.spriteeditor.SpritePreviewRenderer;
import com.pocket.rpg.rendering.resources.NineSliceData;
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
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Modal panel for editing sprite metadata (pivot points and 9-slice borders).
 * <p>
 * Accessible from:
 * <ul>
 *   <li>Main menu: Edit &gt; Sprite Editor...</li>
 *   <li>Asset Browser: Right-click on sprite/spritesheet &gt; "Sprite Editor..."</li>
 *   <li>Asset Browser: Double-click on sprite/spritesheet</li>
 * </ul>
 */
public class SpriteEditorPanel {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    private static final String POPUP_ID = "Sprite Editor";

    // ========================================================================
    // TAB STATE
    // ========================================================================

    public enum EditorTab {
        PIVOT,
        NINE_SLICE
    }

    private EditorTab activeTab = EditorTab.PIVOT;

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

    // Sprite sheet mode
    private int selectedSpriteIndex = 0;
    private boolean applyToAllSprites = true;

    // Asset picker state
    private boolean showAssetPicker = false;
    private final imgui.type.ImString assetSearchBuffer = new imgui.type.ImString(128);
    private String previewAssetPath = null;
    private Object previewAsset = null;
    private int assetPickerTab = 0;

    // Components
    private final SpritePreviewRenderer previewRenderer;
    private final PivotEditorTab pivotTab;
    private final NineSliceEditorTab nineSliceTab;

    // Status callback
    private Consumer<String> statusCallback;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public SpriteEditorPanel() {
        this.previewRenderer = new SpritePreviewRenderer();
        this.pivotTab = new PivotEditorTab(previewRenderer);
        this.nineSliceTab = new NineSliceEditorTab(previewRenderer);
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Opens the sprite editor for the specified asset.
     */
    public void open(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        this.assetPath = path;
        this.shouldOpen = true;
        loadAsset(path);
    }

    /**
     * Opens the sprite editor without a pre-selected asset.
     */
    public void open() {
        this.shouldOpen = true;
        this.assetPath = null;
        this.sprite = null;
        this.spriteSheet = null;
        this.isSpriteSheet = false;
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
    // RENDERING
    // ========================================================================

    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
            isOpen = true;
        }

        ImGui.setNextWindowSize(800, 750);

        ImBoolean pOpen = new ImBoolean(true);
        int flags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar;

        if (ImGui.beginPopupModal(POPUP_ID, pOpen, flags)) {
            renderContent();
            ImGui.endPopup();
        }

        if (!pOpen.get() && isOpen) {
            isOpen = false;
        }
    }

    private void renderContent() {
        renderAssetSelector();
        ImGui.separator();

        Sprite currentSprite = getCurrentSprite();

        if (currentSprite == null) {
            ImGui.textDisabled("Select an asset to edit.");
            renderFooter(null);
            return;
        }

        // Tab bar
        if (ImGui.beginTabBar("SpriteEditorTabs")) {
            if (ImGui.beginTabItem("Pivot")) {
                activeTab = EditorTab.PIVOT;
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("9-Slice")) {
                activeTab = EditorTab.NINE_SLICE;
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }

        // Calculate available height
        float footerHeight = 45;
        float spriteSheetHeight = (isSpriteSheet && spriteSheet != null) ? 165 : 0;
        float reservedHeight = footerHeight + spriteSheetHeight + 25;

        float availableWidth = ImGui.getContentRegionAvailX();
        float availableHeight = ImGui.getContentRegionAvailY() - reservedHeight;

        // Render active tab
        if (activeTab == EditorTab.PIVOT) {
            pivotTab.render(currentSprite, availableWidth, availableHeight);
        } else {
            nineSliceTab.render(currentSprite, availableWidth, availableHeight);
        }

        // Sprite sheet selector
        if (isSpriteSheet && spriteSheet != null) {
            ImGui.separator();
            renderSpriteSheetSelector();
        }

        // Footer
        ImGui.separator();
        renderFooter(currentSprite);
    }

    // ========================================================================
    // ASSET SELECTOR
    // ========================================================================

    private void renderAssetSelector() {
        float padding = 10;

        ImGui.text("Asset:");
        ImGui.sameLine();

        String displayPath = assetPath != null ? assetPath : "(None selected)";
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 90 - padding);
        ImGui.inputText("##AssetPath", new imgui.type.ImString(displayPath, 256),
                ImGuiInputTextFlags.ReadOnly);

        ImGui.sameLine();

        if (ImGui.button(MaterialIcons.FolderOpen + " Browse")) {
            showAssetPicker = true;
            assetSearchBuffer.set("");
        }

        ImGui.sameLine();
        ImGui.dummy(padding, 0);

        renderAssetPickerPopup();
    }

    private void renderAssetPickerPopup() {
        if (showAssetPicker) {
            ImGui.openPopup("Select Asset##SpriteEditor");
            showAssetPicker = false;
            previewAssetPath = null;
            previewAsset = null;
        }

        ImGui.setNextWindowSize(600, 420);
        if (ImGui.beginPopupModal("Select Asset##SpriteEditor", ImGuiWindowFlags.NoResize)) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputTextWithHint("##search", "Search...", assetSearchBuffer);

            ImGui.separator();

            java.util.List<String> spriteAssets = Assets.scanByType(Sprite.class);
            java.util.List<String> sheetAssets = Assets.scanByType(SpriteSheet.class);
            String filter = assetSearchBuffer.get().toLowerCase();

            float contentHeight = ImGui.getContentRegionAvailY() - 35;
            float leftWidth = 300;
            float rightWidth = ImGui.getContentRegionAvailX() - leftWidth - 10;

            if (ImGui.beginChild("LeftPanel", leftWidth, contentHeight, true)) {
                if (ImGui.beginTabBar("AssetTypeTabs")) {
                    if (ImGui.beginTabItem(MaterialIcons.Image + " Sprites (" + spriteAssets.size() + ")")) {
                        assetPickerTab = 0;
                        renderAssetList(spriteAssets, filter, Sprite.class);
                        ImGui.endTabItem();
                    }
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

            if (ImGui.beginChild("RightPanel", rightWidth, contentHeight, true)) {
                ImGui.text("Preview");
                ImGui.separator();

                if (previewAsset != null) {
                    ImGui.textDisabled(previewAssetPath);
                    ImGui.spacing();
                    float previewMaxSize = Math.min(rightWidth - 20, contentHeight - 80);
                    AssetPreviewRegistry.render(previewAsset, previewMaxSize);
                } else {
                    ImGui.textDisabled("Select an asset to preview");
                }
            }
            ImGui.endChild();

            ImGui.separator();
            float buttonWidth = 80;
            float totalButtonWidth = buttonWidth * 2 + 10;
            ImGui.setCursorPosX((ImGui.getContentRegionAvailX() - totalButtonWidth) / 2);

            boolean canLoad = previewAssetPath != null;
            if (!canLoad) ImGui.beginDisabled();
            if (ImGui.button("Load", buttonWidth, 0)) {
                loadAsset(previewAssetPath);
                assetPath = previewAssetPath;
                ImGui.closeCurrentPopup();
            }
            if (!canLoad) ImGui.endDisabled();

            ImGui.sameLine();
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
                loadPreviewAsset(path, assetType);
            }

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
        if (path.equals(previewAssetPath)) return;

        previewAssetPath = path;
        previewAsset = null;

        try {
            previewAsset = Assets.load(path, assetType);
        } catch (Exception e) {
            System.err.println("[SpriteEditorPanel] Failed to load preview: " + e.getMessage());
        }
    }

    // ========================================================================
    // SPRITE SHEET SELECTOR
    // ========================================================================

    private void renderSpriteSheetSelector() {
        if (spriteSheet == null) return;

        ImGui.text("Sprite Sheet Mode");
        ImGui.spacing();

        if (ImGui.radioButton("Apply to All Sprites", applyToAllSprites)) {
            applyToAllSprites = true;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Apply to Selected Only", !applyToAllSprites)) {
            applyToAllSprites = false;
        }

        ImGui.spacing();
        ImGui.text("Select Sprite:");

        int totalSprites = spriteSheet.getTotalFrames();
        float previewSize = 48;
        float spacing = 4;
        float availWidth = ImGui.getContentRegionAvailX();
        int columns = Math.max(1, (int) ((availWidth + spacing) / (previewSize + spacing)));

        if (ImGui.beginChild("SpriteGrid", 0, 90, true)) {
            ImDrawList drawList = ImGui.getWindowDrawList();

            for (int i = 0; i < totalSprites; i++) {
                if (i > 0 && i % columns != 0) {
                    ImGui.sameLine(0, spacing);
                }

                Sprite frameSprite = spriteSheet.getSprite(i);
                if (frameSprite == null || frameSprite.getTexture() == null) continue;

                boolean isSelected = i == selectedSpriteIndex;
                ImVec2 cursorPos = ImGui.getCursorScreenPos();

                if (isSelected) {
                    int highlightColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.6f, 1.0f, 0.5f);
                    drawList.addRectFilled(
                            cursorPos.x - 2, cursorPos.y - 2,
                            cursorPos.x + previewSize + 2, cursorPos.y + previewSize + 2,
                            highlightColor, 4);
                }

                ImGui.pushID(i);
                if (ImGui.invisibleButton("##sprite", previewSize, previewSize)) {
                    selectedSpriteIndex = i;
                    loadPivotForSelectedSprite();
                }
                ImGui.popID();

                int texId = frameSprite.getTexture().getTextureId();
                drawList.addImage(texId,
                        cursorPos.x, cursorPos.y,
                        cursorPos.x + previewSize, cursorPos.y + previewSize,
                        frameSprite.getU0(), frameSprite.getV1(), frameSprite.getU1(), frameSprite.getV0());

                int borderColor = isSelected
                        ? ImGui.colorConvertFloat4ToU32(0.3f, 0.6f, 1.0f, 1.0f)
                        : ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.5f);
                drawList.addRect(cursorPos.x, cursorPos.y,
                        cursorPos.x + previewSize, cursorPos.y + previewSize, borderColor);

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

    private void renderFooter(Sprite currentSprite) {
        // Zoom controls
        ImGui.text("Zoom:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(100);
        float[] zoomArr = {previewRenderer.getZoom()};
        if (ImGui.sliderFloat("##Zoom", zoomArr, SpritePreviewRenderer.MIN_ZOOM,
                SpritePreviewRenderer.MAX_ZOOM, "%.1fx")) {
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
            previewRenderer.fit(currentSprite, previewRenderer.getAreaWidth(), previewRenderer.getAreaHeight());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Fit sprite to preview area");
        }

        // Buttons on the right
        float buttonWidth = 80;
        float totalWidth = buttonWidth * 2 + 10;
        ImGui.sameLine(ImGui.getContentRegionAvailX() - totalWidth);

        if (ImGui.button("Cancel", buttonWidth, 0)) {
            pivotTab.revertToOriginal();
            nineSliceTab.revertToOriginal();
            ImGui.closeCurrentPopup();
            isOpen = false;
        }

        ImGui.sameLine();

        ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.2f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.3f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.4f, 0.7f, 0.4f, 1f);
        if (ImGui.button(MaterialIcons.Save + " Save", buttonWidth, 0)) {
            if (activeTab == EditorTab.PIVOT) {
                applyPivot(true);
            } else {
                applyNineSlice(true);
            }
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(activeTab == EditorTab.PIVOT
                    ? "Save pivot to metadata file"
                    : "Save 9-slice to metadata file");
        }
    }

    // ========================================================================
    // ASSET LOADING
    // ========================================================================

    private void loadAsset(String path) {
        sprite = null;
        spriteSheet = null;
        isSpriteSheet = false;

        previewRenderer.reset();

        if (path == null || path.isEmpty()) return;

        try {
            Class<?> type = Assets.getTypeForPath(path);

            if (type == SpriteSheet.class) {
                spriteSheet = Assets.load(path, SpriteSheet.class);
                isSpriteSheet = true;
                selectedSpriteIndex = 0;

                float[] pivot = spriteSheet.getEffectivePivot(selectedSpriteIndex);
                pivotTab.setPivot(pivot[0], pivot[1]);
                pivotTab.setOriginalPivot(pivot[0], pivot[1]);

                // Load 9-slice data from sprite sheet
                NineSliceData nineSlice = spriteSheet.getEffectiveNineSlice(selectedSpriteIndex);
                if (nineSlice != null) {
                    nineSliceTab.setSlices(nineSlice.left, nineSlice.right, nineSlice.top, nineSlice.bottom);
                } else {
                    nineSliceTab.setSlices(0, 0, 0, 0);
                }
                nineSliceTab.setOriginalSlices(nineSliceTab.getSliceLeft(), nineSliceTab.getSliceRight(),
                        nineSliceTab.getSliceTop(), nineSliceTab.getSliceBottom());

            } else if (type == Sprite.class || isImageExtension(path)) {
                sprite = Assets.load(path, Sprite.class);

                SpriteMetadata meta = AssetMetadata.load(path, SpriteMetadata.class);
                if (meta != null && meta.hasPivot()) {
                    pivotTab.setPivot(meta.pivotX, meta.pivotY);
                } else {
                    pivotTab.setPivot(sprite.getPivotX(), sprite.getPivotY());
                }
                pivotTab.setOriginalPivot(pivotTab.getPivotX(), pivotTab.getPivotY());

                if (meta != null && meta.hasNineSlice()) {
                    nineSliceTab.setSlices(meta.nineSlice.left, meta.nineSlice.right,
                            meta.nineSlice.top, meta.nineSlice.bottom);
                } else {
                    nineSliceTab.setSlices(0, 0, 0, 0);
                }
                nineSliceTab.setOriginalSlices(nineSliceTab.getSliceLeft(), nineSliceTab.getSliceRight(),
                        nineSliceTab.getSliceTop(), nineSliceTab.getSliceBottom());
            }

        } catch (Exception e) {
            System.err.println("[SpriteEditorPanel] Failed to load asset: " + path + " - " + e.getMessage());
            showStatus("Failed to load asset: " + e.getMessage());
        }
    }

    private void loadPivotForSelectedSprite() {
        if (spriteSheet == null) return;

        // Load pivot
        float[] pivot = spriteSheet.getEffectivePivot(selectedSpriteIndex);
        pivotTab.setPivot(pivot[0], pivot[1]);
        pivotTab.setOriginalPivot(pivot[0], pivot[1]);

        // Load 9-slice
        NineSliceData nineSlice = spriteSheet.getEffectiveNineSlice(selectedSpriteIndex);
        if (nineSlice != null) {
            nineSliceTab.setSlices(nineSlice.left, nineSlice.right, nineSlice.top, nineSlice.bottom);
        } else {
            nineSliceTab.setSlices(0, 0, 0, 0);
        }
        nineSliceTab.setOriginalSlices(nineSliceTab.getSliceLeft(), nineSliceTab.getSliceRight(),
                nineSliceTab.getSliceTop(), nineSliceTab.getSliceBottom());
    }

    private Sprite getCurrentSprite() {
        if (isSpriteSheet && spriteSheet != null) {
            return spriteSheet.getSprite(selectedSpriteIndex);
        }
        return sprite;
    }

    private boolean isImageExtension(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") || lower.endsWith(".bmp") ||
                lower.endsWith(".tga");
    }

    private String getFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    // ========================================================================
    // SAVE/APPLY
    // ========================================================================

    private void applyPivot(boolean saveToFile) {
        if (assetPath == null) return;

        float pivotX = pivotTab.getPivotX();
        float pivotY = pivotTab.getPivotY();

        try {
            if (isSpriteSheet && spriteSheet != null) {
                if (applyToAllSprites) {
                    spriteSheet.setDefaultPivot(pivotX, pivotY);
                    spriteSheet.clearSpritePivots();
                    for (int i = 0; i < spriteSheet.getTotalFrames(); i++) {
                        spriteSheet.getSprite(i).setPivot(pivotX, pivotY);
                    }
                    showStatus("Applied pivot to all " + spriteSheet.getTotalFrames() + " sprites");
                } else {
                    spriteSheet.setSpritePivot(selectedSpriteIndex, pivotX, pivotY);
                    spriteSheet.getSprite(selectedSpriteIndex).setPivot(pivotX, pivotY);
                    showStatus("Applied pivot to sprite #" + selectedSpriteIndex);
                }

                if (saveToFile) {
                    String filePath = java.nio.file.Paths.get(Assets.getAssetRoot(), assetPath).toString();
                    new SpriteSheetLoader().save(spriteSheet, filePath);
                    showStatus("Saved pivot to " + assetPath);
                }

            } else if (sprite != null) {
                sprite.setPivot(pivotX, pivotY);

                if (saveToFile) {
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

            pivotTab.updateOriginal();

        } catch (IOException e) {
            System.err.println("[SpriteEditorPanel] Failed to save pivot: " + e.getMessage());
            showStatus("Failed to save: " + e.getMessage());
        }
    }

    private void applyNineSlice(boolean saveToFile) {
        if (assetPath == null) return;

        try {
            boolean hasSlicing = nineSliceTab.hasSlicing();
            NineSliceData data = hasSlicing ? new NineSliceData(
                    nineSliceTab.getSliceLeft(), nineSliceTab.getSliceRight(),
                    nineSliceTab.getSliceTop(), nineSliceTab.getSliceBottom()) : null;

            if (isSpriteSheet && spriteSheet != null) {
                if (applyToAllSprites) {
                    spriteSheet.setDefaultNineSlice(data);
                    spriteSheet.clearSpriteNineSlices();
                    for (int i = 0; i < spriteSheet.getTotalFrames(); i++) {
                        Sprite s = spriteSheet.getSprite(i);
                        s.setNineSliceData(data != null ? data.copy() : null);
                    }
                    showStatus("Applied 9-slice to all " + spriteSheet.getTotalFrames() + " sprites");
                } else {
                    spriteSheet.setSpriteNineSlice(selectedSpriteIndex, data);
                    Sprite s = spriteSheet.getSprite(selectedSpriteIndex);
                    s.setNineSliceData(data != null ? data.copy() : null);
                    showStatus("Applied 9-slice to sprite #" + selectedSpriteIndex);
                }

                if (saveToFile) {
                    String filePath = java.nio.file.Paths.get(Assets.getAssetRoot(), assetPath).toString();
                    new SpriteSheetLoader().save(spriteSheet, filePath);
                    showStatus("Saved 9-slice to " + assetPath);
                }

            } else if (sprite != null) {
                sprite.setNineSliceData(data);

                if (saveToFile) {
                    SpriteMetadata meta = AssetMetadata.loadOrDefault(
                            assetPath, SpriteMetadata.class, SpriteMetadata::new);
                    meta.nineSlice = data;
                    AssetMetadata.saveOrDelete(assetPath, meta);
                    showStatus("Saved 9-slice for " + assetPath);
                } else {
                    showStatus("Applied 9-slice (not saved)");
                }
            }

            nineSliceTab.updateOriginal();

        } catch (IOException e) {
            System.err.println("[SpriteEditorPanel] Failed to save 9-slice: " + e.getMessage());
            showStatus("Failed to save: " + e.getMessage());
        }
    }

    private void showStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
        System.out.println("[SpriteEditorPanel] " + message);
    }
}
