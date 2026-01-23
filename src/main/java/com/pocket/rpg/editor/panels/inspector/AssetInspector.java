package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.assets.AssetPreviewRegistry;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import lombok.Setter;

/**
 * Inspector for assets selected in the asset browser.
 * Uses AssetPreviewRegistry for type-specific preview rendering.
 */
public class AssetInspector {

    private static final float PREVIEW_MAX_SIZE = 200f;

    @Setter
    private String assetPath;

    @Setter
    private Class<?> assetType;

    // Cached asset for preview
    private Object cachedAsset;
    private String cachedPath;

    public void render() {
        if (assetPath == null || assetType == null) {
            ImGui.textDisabled("No asset selected");
            return;
        }

        // Header with asset type icon and name
        String icon = Assets.getIconCodepoint(assetType);
        String fileName = extractFileName(assetPath);
        ImGui.text(icon + " " + fileName);
        ImGui.separator();

        // Asset type
        ImGui.textDisabled("Type:");
        ImGui.sameLine();
        ImGui.text(assetType.getSimpleName());

        // Path
        ImGui.textDisabled("Path:");
        ImGui.sameLine();
        ImGui.textWrapped(assetPath);

        ImGui.separator();

        // Preview section
        ImGui.text("Preview");
        ImGui.separator();

        // Load asset if needed
        loadAssetIfNeeded();

        if (cachedAsset != null) {
            // Use the registry for type-specific rendering (includes play button for audio)
            AssetPreviewRegistry.render(cachedAsset, PREVIEW_MAX_SIZE);
        } else {
            ImGui.textDisabled("Could not load asset for preview");
        }
    }

    private void loadAssetIfNeeded() {
        // Check if we need to reload
        if (cachedPath == null || !cachedPath.equals(assetPath)) {
            cachedAsset = null;
            cachedPath = assetPath;

            try {
                cachedAsset = Assets.load(assetPath, assetType);
            } catch (Exception e) {
                System.err.println("Failed to load asset for inspector: " + e.getMessage());
            }
        }
    }

    private String extractFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Clears cached asset (call when selection changes away from assets).
     */
    public void clearCache() {
        cachedAsset = null;
        cachedPath = null;
    }
}
