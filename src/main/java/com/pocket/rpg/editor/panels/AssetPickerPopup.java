package com.pocket.rpg.editor.panels;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Popup for selecting asset files (Sprites, Textures, SpriteSheets).
 * <p>
 * Usage:
 * assetPicker.open(Sprite.class, sprite -> {
 * myComponent.setSprite(sprite);
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

    // Preview
    private Object previewAsset = null;
    private String previewPath = null;

    /**
     * Opens the picker for a specific asset type.
     */
    public void open(Class<?> type, Consumer<Object> callback) {
        this.assetType = type;
        this.onSelected = callback;
        this.shouldOpen = true;
        this.searchBuffer.set("");
        this.selectedPath = null;
        this.previewAsset = null;
        this.previewPath = null;

        scanAssets();
    }

    /**
     * Opens the picker for a specific asset type with initial selection.
     */
    public void open(Class<?> type, String currentPath, Consumer<Object> callback) {
        open(type, callback);
        this.selectedPath = currentPath;
    }

    private void scanAssets() {
        availableAssets.clear();

        try {
            List<String> paths;

            if (assetType == Sprite.class || assetType == Texture.class) {
                // Scan for image files
                paths = Assets.getContext().scanByType(Texture.class);
            } else if (assetType == SpriteSheet.class) {
                paths = Assets.getContext().scanByType(SpriteSheet.class);
            } else {
                paths = Assets.getContext().scanAll();
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

        ImGui.setNextWindowSize(500, 450);

        if (ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoResize)) {
            String typeName = assetType != null ? assetType.getSimpleName() : "Asset";
            ImGui.text("Select " + typeName);
            ImGui.separator();

            // Search bar
            ImGui.text("Search:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(200);
            ImGui.inputText("##search", searchBuffer);

            ImGui.sameLine();
            if (ImGui.button("Clear")) {
                searchBuffer.set("");
            }

            ImGui.separator();

            // Two-column layout: list and preview
            ImGui.columns(2, "assetColumns", true);

            // Left column: asset list
            ImGui.setColumnWidth(0, 280);
            ImGui.beginChild("AssetList", 0, 300);

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

            // Right column: preview
            ImGui.nextColumn();

            ImGui.text("Preview");
            ImGui.separator();

            ImGui.beginChild("Preview", 0, 300);
            renderPreview();
            ImGui.endChild();

            ImGui.columns(1);

            ImGui.separator();

            // Selected path display
            ImGui.text("Selected: ");
            ImGui.sameLine();
            ImGui.textColored(0.7f, 0.9f, 0.7f, 1f,
                    selectedPath != null ? selectedPath : "(none)");

            ImGui.separator();

            // Buttons
            boolean canConfirm = selectedPath != null || true;  // Allow selecting "none"

            if (!canConfirm) {
                ImGui.beginDisabled();
            }
            if (ImGui.button("Select", 120, 0)) {
                confirmSelection();
            }
            if (!canConfirm) {
                ImGui.endDisabled();
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel", 120, 0)) {
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
            }
        } catch (Exception e) {
            System.err.println("Failed to load preview: " + e.getMessage());
        }
    }

    private void renderPreview() {
        if (previewAsset == null) {
            ImGui.textDisabled("No preview available");
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

    private void confirmSelection() {
        Object result = null;

        if (selectedPath != null) {
            try {
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