package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * Inspector for assets selected in the asset browser.
 * <p>
 * Uses {@link AssetInspectorRegistry} for type-specific inspector rendering.
 * Assets with editable properties show a Save button.
 * <p>
 * When switching assets with unsaved changes, a confirmation popup is shown.
 */
public class AssetInspector {

    private static final float PREVIEW_MAX_SIZE = 200f;
    private static final String UNSAVED_CHANGES_POPUP = "Unsaved Changes##AssetInspector";

    // Current asset
    private String assetPath;
    private Class<?> assetType;

    // Cached asset for preview
    private Object cachedAsset;
    private String cachedPath;

    // Unsaved changes tracking
    private boolean hasUnsavedChanges = false;

    // Pending switch (when user tries to switch with unsaved changes)
    private String pendingAssetPath;
    private Class<?> pendingAssetType;
    private boolean pendingSwitchAway = false; // true if switching to non-asset (entity, nothing, etc.)
    private boolean showUnsavedChangesPopup = false;
    private boolean popupIsOpen = false;

    /**
     * Call this every frame BEFORE deciding which inspector to show.
     * Returns true if the unsaved changes popup is blocking and we should
     * continue showing the asset inspector.
     */
    public boolean hasPendingPopup() {
        return popupIsOpen || showUnsavedChangesPopup;
    }

    /**
     * Call this when selection is about to change away from assets.
     * Returns true if there are unsaved changes and we need to show a popup.
     */
    public boolean checkUnsavedChangesBeforeLeaving() {
        if (AssetInspectorRegistry.hasUnsavedChanges()) {
            pendingAssetPath = null;
            pendingAssetType = null;
            pendingSwitchAway = true;
            showUnsavedChangesPopup = true;
            return true;
        }
        return false;
    }

    /**
     * Sets the asset to inspect. If there are unsaved changes, shows a confirmation popup.
     */
    public void setAsset(String path, Class<?> type) {
        // Don't process new selections while popup is showing
        if (popupIsOpen) {
            return;
        }

        // Same asset - no action needed
        if (path != null && path.equals(assetPath) && type == assetType) {
            return;
        }

        // Check for unsaved changes
        if (AssetInspectorRegistry.hasUnsavedChanges()) {
            // Store pending switch and show popup
            pendingAssetPath = path;
            pendingAssetType = type;
            pendingSwitchAway = false;
            showUnsavedChangesPopup = true;
        } else {
            // No unsaved changes - switch immediately
            performSwitch(path, type);
        }
    }

    public void render() {
        // Always render the popup first (it's modal, blocks everything else)
        renderUnsavedChangesPopup();

        // Don't render inspector content while popup is open
        if (popupIsOpen) {
            return;
        }

        if (assetPath == null || assetType == null) {
            ImGui.textDisabled("No asset selected");
            return;
        }

        // Header with asset type icon and name
        String icon = Assets.getIconCodepoint(assetType);
        String fileName = extractFileName(assetPath);

        // Show modified indicator if has unsaved changes
        if (hasUnsavedChanges) {
            ImGui.text(icon + " " + fileName + " *");
        } else {
            ImGui.text(icon + " " + fileName);
        }
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
            // Use the inspector registry for type-specific rendering
            hasUnsavedChanges = AssetInspectorRegistry.render(cachedAsset, assetPath, PREVIEW_MAX_SIZE);

            // Save button if asset has editable properties
            if (AssetInspectorRegistry.hasEditableProperties(assetType)) {
                ImGui.separator();
                renderSaveButton();
            }
        } else {
            ImGui.textDisabled("Could not load asset for preview");
        }
    }

    /**
     * Renders the unsaved changes confirmation popup.
     */
    private void renderUnsavedChangesPopup() {
        if (showUnsavedChangesPopup) {
            ImGui.openPopup(UNSAVED_CHANGES_POPUP);
            showUnsavedChangesPopup = false;
        }

        // Track if popup is open
        popupIsOpen = ImGui.isPopupOpen(UNSAVED_CHANGES_POPUP);

        ImGui.setNextWindowSize(300, 0);
        if (ImGui.beginPopupModal(UNSAVED_CHANGES_POPUP, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoMove)) {
            ImGui.text("You have unsaved changes.");
            ImGui.spacing();
            ImGui.text("Do you want to save before switching?");
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            float buttonWidth = 80;
            float spacing = ImGui.getStyle().getItemSpacingX();
            float totalWidth = buttonWidth * 2 + spacing;
            float startX = (ImGui.getContentRegionAvailX() - totalWidth) / 2;
            ImGui.setCursorPosX(ImGui.getCursorPosX() + startX);

            // Save button
            EditorColors.pushWarningButtonWithText();
            if (ImGui.button("Save", buttonWidth, 0)) {
                // Save current, then switch
                if (cachedAsset != null) {
                    AssetInspectorRegistry.save(cachedAsset, assetPath);
                }
                completePendingSwitch();
                ImGui.closeCurrentPopup();
            }
            EditorColors.popWarningButtonWithText();

            ImGui.sameLine();

            // Discard button
            EditorColors.pushDangerButton();
            if (ImGui.button("Discard", buttonWidth, 0)) {
                completePendingSwitch();
                ImGui.closeCurrentPopup();
            }
            EditorColors.popButtonColors();

            ImGui.endPopup();
        }
    }

    /**
     * Completes the pending switch after user confirms.
     */
    private void completePendingSwitch() {
        if (pendingSwitchAway) {
            // Switching away from assets entirely - clear everything
            AssetInspectorRegistry.notifyDeselect();
            assetPath = null;
            assetType = null;
            cachedAsset = null;
            cachedPath = null;
            hasUnsavedChanges = false;
        } else {
            // Switching to another asset
            performSwitch(pendingAssetPath, pendingAssetType);
        }
        clearPendingSwitch();
    }

    /**
     * Actually performs the asset switch.
     */
    private void performSwitch(String path, Class<?> type) {
        // Notify current inspector of deselect
        AssetInspectorRegistry.notifyDeselect();

        // Switch to new asset
        assetPath = path;
        assetType = type;
        cachedAsset = null;
        cachedPath = null;
        hasUnsavedChanges = false;
    }

    private void clearPendingSwitch() {
        pendingAssetPath = null;
        pendingAssetType = null;
        pendingSwitchAway = false;
    }

    /**
     * Renders the save button with visual feedback for unsaved changes.
     */
    private void renderSaveButton() {
        float buttonWidth = ImGui.getContentRegionAvailX();

        if (hasUnsavedChanges) {
            // Highlight save button when there are changes (amber + dark text for readability)
            EditorColors.pushWarningButtonWithText();
        }

        if (ImGui.button(MaterialIcons.Save + " Save", buttonWidth, 0)) {
            AssetInspectorRegistry.save(cachedAsset, assetPath);
            hasUnsavedChanges = false;
        }

        if (hasUnsavedChanges) {
            EditorColors.popWarningButtonWithText();
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
        AssetInspectorRegistry.notifyDeselect();
        cachedAsset = null;
        cachedPath = null;
        assetPath = null;
        assetType = null;
        hasUnsavedChanges = false;
    }
}
