package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Asset browser dialog for selecting textures.
 * <p>
 * Features:
 * <ul>
 *   <li>Lists all texture assets with mode badges</li>
 *   <li>Shows sprite count for multiple mode</li>
 *   <li>Shows 9S indicator if has 9-slice data</li>
 *   <li>Search/filter functionality</li>
 *   <li>Preview panel with texture info</li>
 * </ul>
 */
public class TextureBrowserDialog {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    private static final String POPUP_ID = "Select Texture##TextureBrowser";

    // ========================================================================
    // STATE
    // ========================================================================

    private boolean shouldOpen = false;
    private final ImString searchBuffer = new ImString(128);

    // Selection state
    private String selectedPath = null;
    private Texture previewTexture = null;
    private TextureInfo previewInfo = null;

    // Cached asset list
    private List<TextureInfo> cachedAssets = null;
    private long lastScanTime = 0;
    private static final long CACHE_DURATION_MS = 5000; // Refresh cache every 5 seconds

    // Callback
    private Consumer<String> onSelect;

    // ========================================================================
    // TEXTURE INFO
    // ========================================================================

    /**
     * Cached information about a texture asset.
     */
    private static class TextureInfo {
        final String path;
        final String fileName;
        String modeLabel;
        int spriteCount;
        boolean hasNineSlice;

        TextureInfo(String path) {
            this.path = path;
            int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            this.fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            loadMetadata();
        }

        private void loadMetadata() {
            try {
                SpriteMetadata meta = AssetMetadata.load(path, SpriteMetadata.class);
                if (meta == null) {
                    modeLabel = "No meta";
                    spriteCount = 1;
                    hasNineSlice = false;
                } else if (meta.isMultiple()) {
                    modeLabel = "Multiple";
                    // Calculate sprite count
                    if (meta.grid != null) {
                        Sprite sprite = Assets.load(path, Sprite.class);
                        if (sprite != null && sprite.getTexture() != null) {
                            int cols = meta.grid.calculateColumns(sprite.getTexture().getWidth());
                            int rows = meta.grid.calculateRows(sprite.getTexture().getHeight());
                            spriteCount = cols * rows;
                        } else {
                            spriteCount = 0;
                        }
                    }
                    hasNineSlice = meta.defaultNineSlice != null ||
                            (meta.sprites != null && meta.sprites.values().stream()
                                    .anyMatch(s -> s.nineSlice != null));
                } else {
                    modeLabel = "Single";
                    spriteCount = 1;
                    hasNineSlice = meta.nineSlice != null;
                }
            } catch (Exception e) {
                modeLabel = "?";
                spriteCount = 0;
                hasNineSlice = false;
            }
        }

        String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            sb.append(fileName);
            sb.append("  [").append(modeLabel).append("]");
            if ("Multiple".equals(modeLabel) && spriteCount > 0) {
                sb.append(" ").append(spriteCount);
            }
            if (hasNineSlice) {
                sb.append(" 9S");
            }
            return sb.toString();
        }
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Opens the texture browser dialog.
     *
     * @param onSelect Callback when a texture is selected (receives the path)
     */
    public void open(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        this.shouldOpen = true;
        this.searchBuffer.set("");
        this.selectedPath = null;
        this.previewTexture = null;
        this.previewInfo = null;
        refreshAssetCache();
    }

    /**
     * Renders the dialog. Call this every frame.
     */
    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        ImGui.setNextWindowSize(700, 500);
        if (ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoResize)) {
            renderContent();
            ImGui.endPopup();
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    private void renderContent() {
        // Search bar
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint("##search", "Search textures...", searchBuffer);

        ImGui.separator();

        // Main content area
        float contentHeight = ImGui.getContentRegionAvailY() - 40;
        float leftWidth = 400;
        float rightWidth = ImGui.getContentRegionAvailX() - leftWidth - 10;

        // Left panel - asset list
        if (ImGui.beginChild("AssetList", leftWidth, contentHeight, true)) {
            renderAssetList();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right panel - preview
        if (ImGui.beginChild("PreviewPanel", rightWidth, contentHeight, true)) {
            renderPreviewPanel();
        }
        ImGui.endChild();

        ImGui.separator();

        // Footer buttons
        renderFooter();
    }

    private void renderAssetList() {
        // Refresh cache if needed
        if (System.currentTimeMillis() - lastScanTime > CACHE_DURATION_MS) {
            refreshAssetCache();
        }

        if (cachedAssets == null || cachedAssets.isEmpty()) {
            ImGui.textDisabled("No textures found");
            return;
        }

        String filter = searchBuffer.get().toLowerCase();

        for (TextureInfo info : cachedAssets) {
            // Apply filter
            if (!filter.isEmpty() &&
                    !info.path.toLowerCase().contains(filter) &&
                    !info.fileName.toLowerCase().contains(filter)) {
                continue;
            }

            boolean isSelected = info.path.equals(selectedPath);
            if (ImGui.selectable(info.getDisplayName(), isSelected)) {
                selectAsset(info);
            }

            // Double-click to confirm
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                confirmSelection();
            }

            // Tooltip with full path
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(info.path);
            }
        }
    }

    private void renderPreviewPanel() {
        ImGui.text("Preview");
        ImGui.separator();

        if (previewInfo == null) {
            ImGui.textDisabled("Select a texture to preview");
            return;
        }

        // File name
        ImGui.textDisabled(previewInfo.path);
        ImGui.spacing();

        // Texture preview
        if (previewTexture != null) {
            float availWidth = ImGui.getContentRegionAvailX() - 10;
            float availHeight = ImGui.getContentRegionAvailY() - 100;
            float maxSize = Math.min(availWidth, availHeight);

            float scale = Math.min(maxSize / previewTexture.getWidth(),
                    maxSize / previewTexture.getHeight());
            scale = Math.min(scale, 4f); // Max 4x zoom

            float displayW = previewTexture.getWidth() * scale;
            float displayH = previewTexture.getHeight() * scale;

            // Center the image
            float offsetX = (availWidth - displayW) / 2;
            if (offsetX > 0) {
                ImGui.setCursorPosX(ImGui.getCursorPosX() + offsetX);
            }

            ImGui.image(previewTexture.getTextureId(), displayW, displayH, 0, 1, 1, 0);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Metadata info
        ImGui.text("Size: " + (previewTexture != null ?
                previewTexture.getWidth() + "x" + previewTexture.getHeight() + " px" : "?"));
        ImGui.text("Mode: " + previewInfo.modeLabel);

        if ("Multiple".equals(previewInfo.modeLabel)) {
            ImGui.text("Sprites: " + previewInfo.spriteCount);
        }

        if (previewInfo.hasNineSlice) {
            ImGui.text("9-Slice: Yes");
        }
    }

    private void renderFooter() {
        float buttonWidth = 80;
        float totalWidth = buttonWidth * 2 + 10;
        float startX = (ImGui.getContentRegionAvailX() - totalWidth) / 2;
        ImGui.setCursorPosX(ImGui.getCursorPosX() + startX);

        boolean canSelect = selectedPath != null;
        if (!canSelect) ImGui.beginDisabled();
        if (ImGui.button("Select", buttonWidth, 0)) {
            confirmSelection();
        }
        if (!canSelect) ImGui.endDisabled();

        ImGui.sameLine();

        if (ImGui.button("Cancel", buttonWidth, 0)) {
            ImGui.closeCurrentPopup();
        }
    }

    // ========================================================================
    // SELECTION
    // ========================================================================

    private void selectAsset(TextureInfo info) {
        selectedPath = info.path;
        previewInfo = info;

        // Load preview texture
        try {
            Sprite sprite = Assets.load(info.path, Sprite.class);
            if (sprite != null) {
                previewTexture = sprite.getTexture();
            } else {
                previewTexture = null;
            }
        } catch (Exception e) {
            previewTexture = null;
        }
    }

    private void confirmSelection() {
        if (selectedPath != null && onSelect != null) {
            onSelect.accept(selectedPath);
        }
        ImGui.closeCurrentPopup();
    }

    // ========================================================================
    // CACHING
    // ========================================================================

    private void refreshAssetCache() {
        lastScanTime = System.currentTimeMillis();
        cachedAssets = new ArrayList<>();

        List<String> paths = Assets.scanByType(Sprite.class);
        for (String path : paths) {
            cachedAssets.add(new TextureInfo(path));
        }

        // Sort by path
        cachedAssets.sort((a, b) -> a.path.compareToIgnoreCase(b.path));
    }
}
