package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.assets.AssetPreviewRegistry;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;

import java.util.function.Consumer;

/**
 * Preview-only inspector for assets selected in the asset browser.
 * <p>
 * Uses {@link AssetPreviewRegistry} for type-specific preview/inspector rendering.
 * Provides an "Open in Editor" button to open the asset in the appropriate
 * editor panel (generic Asset Editor or a custom panel like Dialogue Editor).
 */
public class AssetInspector {

    private static final float PREVIEW_MAX_SIZE = 200f;

    // Current asset
    private String assetPath;
    private Class<?> assetType;

    // Cached asset for preview
    private Object cachedAsset;
    private String cachedPath;

    // Callback for "Open in Editor" button
    private Consumer<String> openInEditorCallback;

    /**
     * Sets the asset to inspect. Switches immediately (no unsaved-changes guard).
     */
    public void setAsset(String path, Class<?> type) {
        // Same asset - no action needed
        if (path != null && path.equals(assetPath) && type == assetType) {
            return;
        }

        assetPath = path;
        assetType = type;
        cachedAsset = null;
        cachedPath = null;
    }

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

        // Load asset if needed
        loadAssetIfNeeded();

        if (cachedAsset != null) {
            // Use the preview registry for type-specific inspector rendering
            AssetPreviewRegistry.renderInspector(cachedAsset, assetPath, PREVIEW_MAX_SIZE);
        } else {
            ImGui.textDisabled("Could not load asset for preview");
        }

        // "Open in Editor" button
        renderOpenInEditorButton();
    }

    /**
     * Renders the "Open in Editor" button if an editor is available.
     */
    private void renderOpenInEditorButton() {
        EditorPanelType panelType = Assets.getEditorPanelType(assetType);
        boolean canEdit = panelType != null || Assets.canSave(assetType);

        if (canEdit) {
            ImGui.separator();
            float buttonWidth = ImGui.getContentRegionAvailX();

            String buttonLabel;
            if (panelType != null && panelType != EditorPanelType.ASSET_EDITOR) {
                buttonLabel = MaterialIcons.OpenInNew + " Open in " + panelType.getWindowName();
            } else {
                buttonLabel = MaterialIcons.Edit + " Open in Asset Editor";
            }

            if (ImGui.button(buttonLabel, buttonWidth, 0)) {
                if (openInEditorCallback != null) {
                    openInEditorCallback.accept(assetPath);
                }
            }
        }
    }

    public void setOpenInEditorCallback(Consumer<String> callback) {
        this.openInEditorCallback = callback;
    }

    private void loadAssetIfNeeded() {
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
        assetPath = null;
        assetType = null;
    }
}
