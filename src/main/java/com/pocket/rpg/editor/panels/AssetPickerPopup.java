package com.pocket.rpg.editor.panels;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.editor.assets.AssetPreviewRegistry;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteReference;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Popup for selecting asset files.
 * <p>
 * Works with any asset type registered in Assets. For Sprite selection,
 * also displays individual sprites from MULTIPLE-mode sprites in an indented list format:
 * <pre>
 * player.png (tileset)
 *   - player.png#0
 *   - player.png#1
 *   - player.png#2
 * enemy.png
 * </pre>
 * <p>
 * Selection returns the full path (including #index for grid sprites),
 * which can be used directly with {@code Assets.load(path, type)}.
 * <p>
 * Usage:
 * <pre>
 * assetPicker.open(Sprite.class, currentPath, sprite -> {
 *     myComponent.setSprite(sprite);
 * });
 * </pre>
 */
public class AssetPickerPopup {

    private static final String POPUP_ID = "Select Asset";
    private static final float PREVIEW_MAX_SIZE = 180f;

    private boolean shouldOpen = false;
    private boolean focusSearchNextFrame = false;
    private boolean focusNoneNextFrame = false;
    private boolean focusFirstItemNextFrame = false;
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

    /**
     * Scans for assets of the requested type.
     * <p>
     * Special case: For Sprite, also expands MULTIPLE-mode sprites with individual sub-sprites.
     */
    private void scanAssets() {
        availableAssets.clear();

        try {
            List<String> paths = Assets.scanByType(assetType);

            // Special case: Sprite can come from MULTIPLE-mode sprite grids
            if (assetType == Sprite.class) {
                paths = new ArrayList<>(paths);  // Make mutable

                // Sort all paths
                paths.sort(String::compareToIgnoreCase);

                // Add entries with MULTIPLE-mode expansion
                for (String path : paths) {
                    String fileName = getFileName(path);

                    // Check if this is a MULTIPLE-mode sprite
                    if (isMultipleModeSprite(path)) {
                        // Add the sprite grid header
                        availableAssets.add(new AssetEntry(path, MaterialIcons.PhotoLibrary + " " + fileName, false, false));

                        // Expand individual sprites
                        try {
                            Sprite parent = Assets.load(path, Sprite.class);
                            SpriteGrid grid = Assets.getSpriteGrid(parent);
                            if (grid != null) {
                                int totalSprites = grid.getTotalSprites();
                                for (int i = 0; i < totalSprites; i++) {
                                    String spritePath = SpriteReference.buildPath(path, i);
                                    String spriteName = "  " + MaterialIcons.Image + " " + fileName + "#" + i;
                                    availableAssets.add(new AssetEntry(spritePath, spriteName, true, true));
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to expand sprite grid: " + path + " - " + e.getMessage());
                        }
                    } else {
                        // Regular image file
                        availableAssets.add(new AssetEntry(path, MaterialIcons.Image + " " + fileName, false, true));
                    }
                }
            } else {
                // Standard case: just list the assets
                for (String path : paths) {
                    availableAssets.add(new AssetEntry(path, getFileName(path), false, true));
                }

                // Sort alphabetically
                availableAssets.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
            }

        } catch (Exception e) {
            System.err.println("Failed to scan assets: " + e.getMessage());
        }
    }

    /**
     * Checks if a sprite path points to a MULTIPLE-mode sprite.
     */
    private boolean isMultipleModeSprite(String path) {
        try {
            SpriteMetadata meta = AssetMetadata.load(path, SpriteMetadata.class);
            return meta != null && meta.isMultiple();
        } catch (Exception e) {
            return false;
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
            focusSearchNextFrame = true;
        }

        ImGui.setNextWindowSize(520, 480);

        if (ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoResize)) {
            renderPopupContent();
            ImGui.endPopup();
        }
    }

    /**
     * Renders the popup content. Separated for exception safety.
     */
    private void renderPopupContent() {
        String typeName = assetType != null ? assetType.getSimpleName() : "Asset";

            // Header row: Title + Search on same line
            ImGui.text("Select " + typeName);
            ImGui.sameLine(ImGui.getContentRegionAvailX() - 220);
            ImGui.setNextItemWidth(220);
            if (focusSearchNextFrame) {
                ImGui.setKeyboardFocusHere();
                focusSearchNextFrame = false;
            }
            ImGui.inputTextWithHint("##search", "Search...", searchBuffer);
            // Down arrow from search bar -> focus None option
            if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
                focusNoneNextFrame = true;
            }

            ImGui.separator();

            // Calculate available height for content (excluding footer)
            float footerHeight = 60;  // Space for selected + buttons
            float contentHeight = ImGui.getContentRegionAvail().y - footerHeight;

            // Two-column layout: list and preview
            ImGui.columns(2, "assetColumns", true);
            ImGui.setColumnWidth(0, 280);

            String filter = searchBuffer.get().toLowerCase();

            // Left column: container with fixed height (matches original layout)
            ImGui.beginChild("AssetListContainer", 0, contentHeight, true);

            // "None" option - always visible at top (sticky)
            if (focusNoneNextFrame) {
                ImGui.setKeyboardFocusHere();
                focusNoneNextFrame = false;
            }
            if (ImGui.selectable("(None)", selectedPath == null)) {
                stopAudioPreview();
                selectedPath = null;
                previewAsset = null;
                previewPath = null;
            }
            // Keyboard navigation: update selection when focused
            if (ImGui.isItemFocused() && selectedPath != null) {
                stopAudioPreview();
                selectedPath = null;
                previewAsset = null;
                previewPath = null;
            }
            // Enter to confirm when focused
            if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.Enter)) {
                confirmSelection();
            }
            // Down arrow from None -> focus first item in list
            if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
                focusFirstItemNextFrame = true;
            }
            // Up arrow from None -> focus search bar
            if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
                focusSearchNextFrame = true;
            }
            ImGui.separator();

            // Scrollable asset list (fills remaining space)
            ImGui.beginChild("AssetList", 0, 0, false);

            boolean isFirstSelectableItem = true;
            for (AssetEntry entry : availableAssets) {
                // Apply filter
                if (!filter.isEmpty() &&
                        !entry.displayName.toLowerCase().contains(filter) &&
                        !entry.path.toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = entry.path.equals(selectedPath);

                // Only selectable if it's a valid selection for the requested type
                if (entry.isSelectable) {
                    // Focus first item when navigating from None
                    if (isFirstSelectableItem && focusFirstItemNextFrame) {
                        ImGui.setKeyboardFocusHere();
                        focusFirstItemNextFrame = false;
                    }

                    if (ImGui.selectable(entry.displayName, isSelected)) {
                        selectedPath = entry.path;
                        loadPreview(entry.path);
                    }

                    // Keyboard navigation: update preview when focused
                    if (ImGui.isItemFocused() && !entry.path.equals(selectedPath)) {
                        selectedPath = entry.path;
                        loadPreview(entry.path);
                    }

                    // Enter to confirm when focused
                    if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.Enter)) {
                        confirmSelection();
                    }

                    // Up arrow from first item -> focus None
                    if (isFirstSelectableItem && ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
                        focusNoneNextFrame = true;
                    }

                    // Double-click to confirm
                    if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                        confirmSelection();
                    }

                    // Tooltip with full path
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(entry.path);
                    }

                    isFirstSelectableItem = false;
                } else {
                    // Non-selectable header (sprite grid when selecting Sprite)
                    ImGui.textDisabled(entry.displayName);
                }
            }

            ImGui.endChild();  // AssetList
            ImGui.endChild();  // AssetListContainer

            // Right column: preview
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
            EditorColors.textColored(EditorColors.SUCCESS, displayPath);

            ImGui.spacing();

            // Buttons
            float buttonWidth = 100;
            float totalButtonWidth = buttonWidth * 2 + 10;
            float startX = (ImGui.getContentRegionAvailX() - totalButtonWidth) / 2;
            ImGui.setCursorPosX(ImGui.getCursorPosX() + startX);

            if (ImGui.button("Select", buttonWidth, 0)) {
                confirmSelection();
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel", buttonWidth, 0)) {
                cancelSelection();
            }
    }

    /**
     * Loads preview for the selected path.
     * Uses type inference from Assets.load().
     */
    private void loadPreview(String path) {
        if (path.equals(previewPath)) {
            return;  // Already loaded
        }

        // Stop any audio preview when selection changes
        stopAudioPreview();

        previewPath = path;
        previewAsset = null;

        try {
            // Type inference handles everything - including #index format
            previewAsset = Assets.load(path, assetType);
        } catch (Exception e) {
            System.err.println("Failed to load preview: " + e.getMessage());
        }
    }

    /**
     * Stops any currently playing audio preview.
     */
    private void stopAudioPreview() {
        if (previewAsset instanceof AudioClip clip) {
            EditorAudio.stopPreview(clip);
        }
    }

    /**
     * Renders preview using the centralized AssetPreviewRegistry.
     */
    private void renderPreview() {
        if (previewAsset == null) {
            if (selectedPath != null) {
                ImGui.textDisabled("Loading preview...");
            } else {
                ImGui.textDisabled("No asset selected");
            }
            return;
        }

        // Delegate to registry - handles all types generically (including AudioClip)
        AssetPreviewRegistry.render(previewAsset, PREVIEW_MAX_SIZE);
    }

    private void confirmSelection() {
        // Stop any audio preview
        stopAudioPreview();

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

    private void cancelSelection() {
        // Stop any audio preview
        stopAudioPreview();
        ImGui.closeCurrentPopup();
    }

    /**
     * Asset entry for the list.
     *
     * @param path        Full path (including #index for sub-assets)
     * @param displayName Display name (with indentation prefix for sub-assets)
     * @param isIndented  Whether this is an indented sub-asset entry
     * @param isSelectable Whether this entry can be selected
     */
    private record AssetEntry(String path, String displayName, boolean isIndented, boolean isSelectable) {
    }
}
