package com.pocket.rpg.editor.panels;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.ui.text.Font;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Popup for selecting asset files (Sprites, Textures, SpriteSheets, Fonts).
 * <p>
 * Usage:
 * assetPicker.open(Sprite.class, sprite -> {
 *     myComponent.setSprite(sprite);
 * });
 */
public class AssetPickerPopup {

    private static final String POPUP_ID = "Select Asset";

    private boolean shouldOpen = false;
    private Class<?> assetType;
    private Consumer<Object> onSelected;

    private final ImString searchBuffer = new ImString(128);
    private List<AssetEntry> availableAssets = new ArrayList<>();

    @Getter
    private String selectedPath = null;
    private String initialPath = null;

    // Preview
    private Object previewAsset = null;
    private String previewPath = null;

    /**
     * Opens the picker for a specific asset type.
     */
    public void open(Class<?> type, Consumer<Object> callback) {
        open(type, null, callback);
    }

    /**
     * Opens the picker for a specific asset type with initial selection.
     */
    public void open(Class<?> type, String currentPath, Consumer<Object> callback) {
        this.assetType = type;
        this.onSelected = callback;
        this.shouldOpen = true;
        this.searchBuffer.set("");
        this.selectedPath = currentPath;
        this.initialPath = currentPath;
        this.previewAsset = null;
        this.previewPath = null;

        scanAssets();

        // Load preview for initial selection
        if (currentPath != null && !currentPath.isEmpty()) {
            loadPreview(currentPath);
        }
    }

    private void scanAssets() {
        availableAssets.clear();

        try {
            List<String> paths;

            if (assetType == Sprite.class || assetType == Texture.class) {
                // Scan for image files
                paths = Assets.scanByType(Texture.class);
            } else if (assetType == SpriteSheet.class) {
                paths = Assets.scanByType(SpriteSheet.class);
            } else if (assetType == Font.class) {
                // Try scanByType first (uses registered loader extensions)
                paths = Assets.scanByType(Font.class);
                // If empty, fallback to manual scan for font files
                if (paths.isEmpty()) {
                    paths = scanForFonts();
                }
            } else {
                paths = Assets.scanAll();
            }

            for (String path : paths) {
                availableAssets.add(new AssetEntry(path, getFileName(path)));
            }

            // Sort alphabetically
            availableAssets.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        } catch (Exception e) {
            System.err.println("Failed to scan assets: " + e.getMessage());
        }
    }

    private List<String> scanForFonts() {
        List<String> all = Assets.scanAll();
        List<String> fonts = new ArrayList<>();
        for (String path : all) {
            String lower = path.toLowerCase();
            // Include both raw font files and font definition files
            if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".fnt") ||
                    lower.endsWith(".font") || lower.endsWith(".font.json")) {
                fonts.add(path);
            }
        }
        return fonts;
    }

    private String getFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Renders the popup. Call every frame.
     */
    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        ImGui.setNextWindowSize(520, 480);

        if (ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoResize)) {
            String typeName = assetType != null ? assetType.getSimpleName() : "Asset";

            // Header row: Title + Search on same line
            ImGui.text("Select " + typeName);
            ImGui.sameLine(ImGui.getContentRegionAvailX() - 220);
            ImGui.setNextItemWidth(220);
            ImGui.inputTextWithHint("##search", "Search...", searchBuffer);

            ImGui.separator();

            // Calculate available height for content (excluding footer)
            float footerHeight = 60;  // Space for selected + buttons
            float contentHeight = ImGui.getContentRegionAvail().y - footerHeight;

            // Two-column layout: list and preview
            ImGui.columns(2, "assetColumns", true);
            ImGui.setColumnWidth(0, 280);

            // Left column: asset list (scrollable)
            ImGui.beginChild("AssetList", 0, contentHeight, true);

            String filter = searchBuffer.get().toLowerCase();

            // "None" option
            if (ImGui.selectable("(None)", selectedPath == null)) {
                selectedPath = null;
                previewAsset = null;
                previewPath = null;
            }

            ImGui.separator();

            for (AssetEntry entry : availableAssets) {
                // Apply filter
                if (!filter.isEmpty() &&
                        !entry.name.toLowerCase().contains(filter) &&
                        !entry.path.toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = entry.path.equals(selectedPath);

                if (ImGui.selectable(entry.name, isSelected)) {
                    selectedPath = entry.path;
                    loadPreview(entry.path);
                }

                // Tooltip with full path
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(entry.path);
                }

                // Double-click to confirm
                if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                    confirmSelection();
                }
            }

            ImGui.endChild();

            // Right column: preview (also scrollable for consistency)
            ImGui.nextColumn();

            ImGui.text("Preview");
            ImGui.separator();

            ImGui.beginChild("Preview", 0, contentHeight - 25, true);
            renderPreview();
            ImGui.endChild();

            ImGui.columns(1);

            // Footer: always visible
            ImGui.separator();

            // Selected path display
            ImGui.text("Selected:");
            ImGui.sameLine();
            String displayPath = selectedPath != null ? selectedPath : "(none)";
            ImGui.textColored(0.7f, 0.9f, 0.7f, 1f, displayPath);

            ImGui.spacing();

            // Buttons
            float buttonWidth = 100;
            float totalButtonWidth = buttonWidth * 2 + 10;  // 2 buttons + spacing
            float startX = (ImGui.getContentRegionAvailX() - totalButtonWidth) / 2;
            ImGui.setCursorPosX(ImGui.getCursorPosX() + startX);

            if (ImGui.button("Select", buttonWidth, 0)) {
                confirmSelection();
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel", buttonWidth, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void loadPreview(String path) {
        if (path.equals(previewPath)) {
            return;  // Already loaded
        }

        previewPath = path;
        previewAsset = null;

        try {
            if (assetType == Sprite.class) {
                previewAsset = Assets.load(path, Sprite.class);
            } else if (assetType == Texture.class) {
                previewAsset = Assets.load(path, Texture.class);
            } else if (assetType == SpriteSheet.class) {
                previewAsset = Assets.load(path, SpriteSheet.class);
            } else if (assetType == Font.class) {
                // FontLoader handles both .font.json and raw TTF/OTF files
                previewAsset = Assets.load(path, Font.class);
            }
        } catch (Exception e) {
            System.err.println("Failed to load preview: " + e.getMessage());
        }
    }

    private void renderPreview() {
        if (previewAsset == null) {
            if (selectedPath != null) {
                ImGui.textDisabled("Loading preview...");
            } else {
                ImGui.textDisabled("No asset selected");
            }
            return;
        }

        Texture texture = null;
        int width = 0;
        int height = 0;

        if (previewAsset instanceof Sprite sprite) {
            texture = sprite.getTexture();
            width = (int) sprite.getWidth();
            height = (int) sprite.getHeight();
        } else if (previewAsset instanceof Texture tex) {
            texture = tex;
            width = tex.getWidth();
            height = tex.getHeight();
        } else if (previewAsset instanceof SpriteSheet sheet) {
            texture = sheet.getTexture();
            width = texture.getWidth();
            height = texture.getHeight();
        } else if (previewAsset instanceof Font font) {
            // Font preview - show info and sample text
            renderFontPreview(font);
            return;
        }

        if (texture != null) {
            // Scale to fit preview area
            float maxSize = 180;
            float scale = Math.min(maxSize / width, maxSize / height);
            if (scale > 1) scale = 1;  // Don't upscale

            int displayWidth = (int) (width * scale);
            int displayHeight = (int) (height * scale);

            // ImGui.image() needs texture ID
            ImGui.image(texture.getTextureId(), displayWidth, displayHeight);

            ImGui.text(width + " x " + height + " px");
        }
    }

    private void renderFontPreview(Font font) {
        // Font info - use path from selectedPath since Font doesn't expose name
        String fontName = selectedPath != null ? getFileName(selectedPath) : "Unknown";
        ImGui.text("Font: " + fontName);
        ImGui.text("Size: " + font.getSize() + " px");
        ImGui.text("Line Height: " + font.getLineHeight() + " px");
        ImGui.text("Ascent: " + font.getAscent() + " / Descent: " + font.getDescent());

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Atlas preview
        ImGui.text("Atlas:");

        int atlasId = font.getAtlasTextureId();
        int atlasWidth = font.getAtlasWidth();
        int atlasHeight = font.getAtlasHeight();

        if (atlasId != 0) {
            // Show atlas preview (scaled down)
            float maxSize = 150;
            float scale = Math.min(maxSize / atlasWidth, maxSize / atlasHeight);
            if (scale > 1) scale = 1;

            int displayWidth = (int) (atlasWidth * scale);
            int displayHeight = (int) (atlasHeight * scale);

            ImGui.image(atlasId, displayWidth, displayHeight);
            ImGui.textDisabled(atlasWidth + "x" + atlasHeight);
        } else {
            ImGui.textDisabled("(No atlas available)");
        }
    }

    private void confirmSelection() {
        Object result = null;

        if (selectedPath != null) {
            try {
                // FontLoader handles both .font.json and raw TTF/OTF files
                result = Assets.load(selectedPath, assetType);
            } catch (Exception e) {
                System.err.println("Failed to load selected asset: " + e.getMessage());
            }
        }

        if (onSelected != null) {
            onSelected.accept(result);
        }

        ImGui.closeCurrentPopup();
    }

    /**
     * Simple asset entry for the list.
     */
    private record AssetEntry(String path, String name) {
    }
}