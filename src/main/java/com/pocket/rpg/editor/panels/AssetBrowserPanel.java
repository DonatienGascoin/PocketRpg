package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.SelectionGuard;
import com.pocket.rpg.editor.assets.AssetDragPayload;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.AssetFocusRequestEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.assets.ThumbnailCache;
import com.pocket.rpg.editor.core.EditorFonts;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.EditorCapability;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.function.Consumer;

import static imgui.flag.ImGuiKey.Escape;

/**
 * Asset browser panel with folder tree and asset grid.
 * <p>
 * Features:
 * - Left panel: Folder tree navigation
 * - Right panel: Asset grid with thumbnails
 * - Breadcrumb navigation
 * - Search/filter
 * - Multiple-mode sprite inline expansion
 * - Drag-drop source for scene placement
 * <p>
 * Drag payloads use unified path format (e.g., "sheets/player.spritesheet#3")
 * that can be used directly with {@code Assets.load(path, type)}.
 */
public class AssetBrowserPanel extends EditorPanel {

    private static final String PANEL_ID = "assetBrowser";

    public AssetBrowserPanel() {
        super(PANEL_ID, true); // Default open - core panel
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    private static final float FOLDER_TREE_WIDTH = 180f;
    private static final float BASE_THUMBNAIL_SIZE = 48f;
    private static final float THUMBNAIL_PADDING = 8f;
    private static final float SPRITESHEET_CHILD_SIZE = 40f;

    // Zoom settings
    private static final float MIN_ZOOM = 0.0f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float DEFAULT_ZOOM = 1f;

    // ========================================================================
    // STATE
    // ========================================================================

    private final ThumbnailCache thumbnailCache = new ThumbnailCache();
    private final ImString searchFilter = new ImString(128);

    // Folder structure
    private FolderNode rootFolder;
    private FolderNode currentFolder;
    private String currentPath = "";

    // Asset data
    private List<AssetEntry> currentAssets = new ArrayList<>();
    private List<AssetEntry> allAssets = new ArrayList<>(); // For global search
    private final Set<String> expandedSpritesheets = new HashSet<>();

    // Selection
    @Getter
    private AssetEntry selectedAsset = null;

    // Zoom level (affects thumbnail size)
    private float zoomLevel = DEFAULT_ZOOM;

    // Refresh tracking
    private boolean needsRefresh = true;
    private long lastRefreshTime = 0;
    private boolean isRefreshing = false;
    private static final long REFRESH_COOLDOWN_MS = 1000;

    // Highlight state for focusOnAsset
    private String highlightedAssetPath = null;
    private long highlightStartTime = 0;
    private boolean scrollToHighlight = false;
    private static final long HIGHLIGHT_DURATION_MS = 2000;

    // Panel handlers for double-click (keyed by EditorPanelType)
    private final Map<EditorPanelType, Consumer<String>> panelHandlers = new EnumMap<>(EditorPanelType.class);

    // Sprite editor panel reference for context menu and double-click
    @Setter
    private SpriteEditorPanel spriteEditorPanel;

    // Selection guard for mode-aware asset selection
    @Setter
    private SelectionGuard selectionManager;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initializes the asset browser.
     * Call after Assets system is initialized.
     */
    public void initialize() {
        refresh();

        // Subscribe to asset change events
        EditorEventBus.get().subscribe(AssetChangedEvent.class, this::onAssetChanged);

        // Subscribe to asset focus requests from inspector
        EditorEventBus.get().subscribe(AssetFocusRequestEvent.class, e -> focusOnAsset(e.path()));
    }

    private void onAssetChanged(AssetChangedEvent event) {
        if (isRefreshing) return; // Ignore events from our own refresh
        // Force refresh (bypass cooldown) when assets change
        lastRefreshTime = 0;
        refresh();
    }

    /**
     * Refreshes the asset list from disk.
     * Also hot-reloads any cached assets that have been modified.
     */
    public void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < REFRESH_COOLDOWN_MS) {
            return;
        }
        lastRefreshTime = now;

        // Hot-reload modified assets from disk
        isRefreshing = true;
        try {
            int reloaded = Assets.reloadAll();
            if (reloaded > 0) {
                Log.info("AssetBrowser", "Reloaded " + reloaded + " assets from disk");
                // Notify other panels (e.g. GameViewPanel) that assets were reloaded
                EditorEventBus.get().publish(new AssetChangedEvent("*", AssetChangedEvent.ChangeType.MODIFIED));
            }
        } finally {
            isRefreshing = false;
        }

        // Clear caches (thumbnails will regenerate with new data)
        multipleModeCache.clear();
        thumbnailCache.clear();

        // Scan all assets
        List<String> allPaths = Assets.scanAll();

        // Build folder tree and all assets list
        rootFolder = new FolderNode("");
        allAssets.clear();

        for (String path : allPaths) {
            addPathToTree(path);

            // Add to allAssets for global search
            AssetEntry entry = createAssetEntry(path);
            if (entry != null) {
                allAssets.add(entry);
            }
        }

        // Sort all assets by filename
        allAssets.sort(Comparator.comparing(e -> e.filename.toLowerCase()));

        // Re-navigate to current folder in the new tree
        if (currentPath == null || currentPath.isEmpty()) {
            currentFolder = rootFolder;
            currentPath = "";
        } else {
            // Navigate to the same path in new tree
            FolderNode folder = rootFolder;
            String[] parts = currentPath.split("/");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    FolderNode child = folder.getChild(part);
                    if (child != null) {
                        folder = child;
                    } else {
                        // Path no longer exists, go to root
                        folder = rootFolder;
                        currentPath = "";
                        break;
                    }
                }
            }
            currentFolder = folder;
        }

        // Refresh current folder contents
        refreshCurrentFolder();
        needsRefresh = false;
    }

    private void addPathToTree(String path) {
        String[] parts = path.split("/");
        FolderNode current = rootFolder;

        // Navigate/create folders (all but last part which is the file)
        for (int i = 0; i < parts.length - 1; i++) {
            String folderName = parts[i];
            FolderNode child = current.getChild(folderName);
            if (child == null) {
                child = new FolderNode(folderName);
                current.addChild(child);
            }
            current = child;
        }

        // Add file to folder
        if (parts.length > 0) {
            current.addFile(path);
        }
    }

    private void refreshCurrentFolder() {
        currentAssets.clear();

        if (currentFolder == null) {
            return;
        }

        // Get files in current folder
        for (String path : currentFolder.getFiles()) {
            AssetEntry entry = createAssetEntry(path);
            if (entry != null) {
                currentAssets.add(entry);
            }
        }

        // Sort by name
        currentAssets.sort(Comparator.comparing(e -> e.filename.toLowerCase()));
    }

    private AssetEntry createAssetEntry(String path) {
        // Determine asset type from extension
        Class<?> type = Assets.getTypeForPath(path);

        if (type == null) {
            return null;
        }

        // Get filename
        int lastSlash = path.lastIndexOf('/');
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        return new AssetEntry(path, filename, type);
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the asset browser panel.
     */
    @Override
    public void render() {
        if (!isOpen()) return;

        if (needsRefresh) {
            refresh();
        }

        // Check for Escape to cancel drag operation.
        // Workaround: imgui-java doesn't expose ImGui::ClearDragDrop(), so we use a flag-based
        // approach. The drag tooltip is hidden but the user must release the mouse to fully end it.
        // See: https://github.com/SpaiR/imgui-java/issues/365
        // TODO: Once the binding is added, call ImGui.clearDragDrop() here to instantly end the drag.
        if (ImGui.getDragDropPayload() == null) {
            AssetDragPayload.clearCancellation();
        } else if (ImGui.isKeyPressed(Escape, false)) {
            AssetDragPayload.cancelDrag();
        }

        if (ImGui.begin("Assets")) {
            // Top bar: search + refresh
            renderTopBar();

            ImGui.separator();

            // Breadcrumb + item count on same line
            renderBreadcrumbWithInfo();

            ImGui.separator();

            // Main content: folder tree + asset grid
            float availableWidth = ImGui.getContentRegionAvailX();
            float availableHeight = ImGui.getContentRegionAvailY();

            // Left panel: folder tree with zoom slider
            if (ImGui.beginChild("FolderTreeContainer", FOLDER_TREE_WIDTH, availableHeight, true, ImGuiWindowFlags.NoScrollbar)) {
                // Folder tree (scrollable area)
                float treeHeight = availableHeight - 50; // Reserve space for zoom slider
                if (ImGui.beginChild("FolderTree", -1, treeHeight, false, ImGuiWindowFlags.HorizontalScrollbar)) {
                    renderFolderTree(rootFolder, "");
                }
                ImGui.endChild();

                // Zoom slider at bottom (always visible)
                ImGui.separator();
                ImGui.setNextItemWidth(-1);
                float[] zoom = {zoomLevel};
                if (ImGui.sliderFloat("##zoom", zoom, MIN_ZOOM, MAX_ZOOM, "%.1fx")) {
                    zoomLevel = zoom[0];
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Thumbnail zoom");
                }
            }
            ImGui.endChild();

            ImGui.sameLine();

            // Right panel: asset grid
            if (ImGui.beginChild("AssetGrid", availableWidth - FOLDER_TREE_WIDTH - 10, availableHeight, true)) {
                renderAssetGrid();
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private void renderTopBar() {
        // Search input
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 80);
        if (ImGui.inputTextWithHint("##search", MaterialIcons.Search + " Search assets...", searchFilter)) {
            // Filter changed - refresh display
        }

        ImGui.sameLine();

        // Refresh button
        if (ImGui.button(MaterialIcons.Sync + " Refresh")) {
            needsRefresh = true;
            thumbnailCache.clear();
        }
    }

    private void renderBreadcrumbWithInfo() {
        // Left side: breadcrumb navigation
        if (ImGui.smallButton(MaterialIcons.Home)) {
            navigateTo(rootFolder, "");
        }

        if (!currentPath.isEmpty()) {
            String[] parts = currentPath.split("/");
            StringBuilder pathBuilder = new StringBuilder();
            FolderNode node = rootFolder;

            for (String part : parts) {
                if (part.isEmpty()) continue;

                ImGui.sameLine();
                ImGui.text(">");
                ImGui.sameLine();

                pathBuilder.append(part);
                String fullPath = pathBuilder.toString();

                node = node.getChild(part);
                if (node != null) {
                    if (ImGui.smallButton(part)) {
                        navigateTo(node, fullPath);
                    }
                }

                pathBuilder.append("/");
            }
        }

        // Right side: item count and selection info
        String filter = searchFilter.get().trim();
        boolean isSearching = !filter.isEmpty();

        int folderCount = (!isSearching && currentFolder != null) ? currentFolder.getChildren().size() : 0;
        int assetCount = isSearching ? countFilteredAssets(filter) : currentAssets.size();
        int itemCount = folderCount + assetCount;
        String infoText = itemCount + " item" + (itemCount != 1 ? "s" : "");
        if (isSearching) {
            infoText += " (searching all)";
        }
        if (selectedAsset != null) {
            infoText += " | " + selectedAsset.filename;
        }

        float infoWidth = ImGui.calcTextSize(infoText).x;
        float availableWidth = ImGui.getContentRegionAvailX();

        ImGui.sameLine(ImGui.getCursorPosX() + availableWidth - infoWidth - 5);
        ImGui.textDisabled(infoText);
    }

    private int countFilteredAssets(String filter) {
        String lowerFilter = filter.toLowerCase();
        int count = 0;
        for (AssetEntry entry : allAssets) {
            if (entry.filename.toLowerCase().contains(lowerFilter)) {
                count++;
            }
        }
        return count;
    }

    private void renderFolderTree(FolderNode node, String path) {
        if (node == null) return;

        // Sort children alphabetically
        List<FolderNode> children = new ArrayList<>(node.getChildren());
        children.sort(Comparator.comparing(FolderNode::getName));

        for (FolderNode child : children) {
            String childPath = path.isEmpty() ? child.getName() : path + "/" + child.getName();
            boolean isSelected = childPath.equals(currentPath);
            boolean hasChildren = !child.getChildren().isEmpty();

            int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
            if (isSelected) {
                flags |= ImGuiTreeNodeFlags.Selected;
            }
            if (!hasChildren) {
                flags |= ImGuiTreeNodeFlags.Leaf;
            }

            // Folder icon
            String icon = hasChildren ? MaterialIcons.Folder : MaterialIcons.FolderOpen;
            boolean isOpen = ImGui.treeNodeEx(child.getName(), flags, icon + " " + child.getName());

            // Click to navigate
            if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
                navigateTo(child, childPath);
            }

            if (isOpen) {
                if (hasChildren) {
                    renderFolderTree(child, childPath);
                }
                ImGui.treePop();
            }
        }
    }

    private void renderAssetGrid() {
        String filter = searchFilter.get().toLowerCase().trim();
        boolean isSearching = !filter.isEmpty();

        // Determine which assets to show
        List<AssetEntry> assetsToShow = isSearching ? allAssets : currentAssets;

        // Collect subfolders (only when not searching)
        List<FolderNode> subfolders = List.of();
        if (!isSearching && currentFolder != null && !currentFolder.getChildren().isEmpty()) {
            subfolders = new ArrayList<>(currentFolder.getChildren());
            subfolders.sort(Comparator.comparing(FolderNode::getName));
        }

        // List view when zoom is 0
        if (zoomLevel == 0) {
            renderAssetList(subfolders, assetsToShow, filter, isSearching);
            return;
        }

        // Grid view - folders and assets in one flow
        float thumbnailSize = BASE_THUMBNAIL_SIZE * zoomLevel;
        boolean firstInRow = true;

        // Subfolders first
        for (FolderNode child : subfolders) {
            if (!firstInRow) {
                ImGui.sameLine();
                if (ImGui.getContentRegionAvailX() < thumbnailSize) {
                    ImGui.newLine();
                }
            }
            renderFolderGridItem(child, thumbnailSize);
            firstInRow = false;
        }

        // Then assets
        for (AssetEntry entry : assetsToShow) {
            if (isSearching && !entry.filename.toLowerCase().contains(filter)) {
                continue;
            }

            if (!firstInRow) {
                ImGui.sameLine();
                if (ImGui.getContentRegionAvailX() < thumbnailSize) {
                    ImGui.newLine();
                }
            }

            renderAssetItem(entry, thumbnailSize);
            firstInRow = false;

            if (expandedSpritesheets.contains(entry.path)) {
                firstInRow = true;
                renderSpritesheetChildren(entry, thumbnailSize);
            }
        }
    }

    private void renderAssetList(List<FolderNode> subfolders, List<AssetEntry> assets, String filter, boolean isSearching) {
        // Subfolders first
        for (FolderNode child : subfolders) {
            renderFolderListItem(child);
        }

        // Then assets
        for (AssetEntry entry : assets) {
            if (isSearching && !entry.filename.toLowerCase().contains(filter)) {
                continue;
            }

            renderAssetListItem(entry);

            if (expandedSpritesheets.contains(entry.path)) {
                renderSpritesheetListChildren(entry);
            }
        }
    }

    private void renderFolderGridItem(FolderNode child, float thumbnailSize) {
        String childPath = currentPath.isEmpty() ? child.getName() : currentPath + "/" + child.getName();
        ImGui.pushID("folder_" + child.getName());
        ImGui.beginGroup();

        // Folder icon button
        ImGui.pushFont(EditorFonts.getIconFont(thumbnailSize));
        if (ImGui.button(MaterialIcons.Folder, thumbnailSize, thumbnailSize)) {
            navigateTo(child, childPath);
        }
        ImGui.popFont();

        // Folder name below
        int maxLabelChars = Math.max(6, (int)(thumbnailSize / 6));
        String displayName = truncateFilename(child.getName(), maxLabelChars);
        float textWidth = ImGui.calcTextSize(displayName).x;
        float offset = (thumbnailSize - textWidth) / 2f;
        if (offset > 0) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        }
        ImGui.text(displayName);

        ImGui.endGroup();

        // Double-click to navigate
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            navigateTo(child, childPath);
        }

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(child.getName());
            ImGui.textDisabled("Folder");
            ImGui.endTooltip();
        }

        ImGui.popID();
    }

    private void renderFolderListItem(FolderNode child) {
        String childPath = currentPath.isEmpty() ? child.getName() : currentPath + "/" + child.getName();

        ImGui.pushID("folder_" + child.getName());
        if (ImGui.selectable(MaterialIcons.Folder + "  " + child.getName(), false, ImGuiSelectableFlags.SpanAllColumns)) {
            navigateTo(child, childPath);
        }

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(child.getName());
            ImGui.textDisabled("Folder");
            ImGui.endTooltip();
        }
        ImGui.popID();
    }

    private void renderAssetListItem(AssetEntry entry) {
        boolean isSelected = entry == selectedAsset;
        boolean isMultipleMode = isMultipleModeSprite(entry);
        boolean isExpanded = expandedSpritesheets.contains(entry.path);

        // Highlight state for focus-on-asset
        boolean highlighted = isHighlighted(entry.path);
        float highlightAlpha = highlighted ? getHighlightAlpha() : 0f;
        highlighted = highlightAlpha > 0f;

        ImGui.pushID(entry.path);

        // Selection highlight for entire row
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Header, 0.3f, 0.6f, 1.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.5f, 0.7f, 1.0f, 1.0f);
        } else if (highlighted) {
            ImGui.pushStyleColor(ImGuiCol.Header, 1.0f, 0.8f, 0.2f, highlightAlpha * 0.5f);
            ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 1.0f, 0.85f, 0.3f, highlightAlpha * 0.6f);
        }

        // Selectable row with icon and filename
        String icon = getIconForType(entry.type);
        String label = icon + "  " + entry.filename;

        int flags = ImGuiSelectableFlags.SpanAllColumns;
        if (ImGui.selectable(label, isSelected || highlighted, flags)) {
            selectedAsset = entry;
            if (selectionManager != null) {
                selectionManager.selectAsset(entry.path, entry.type);
            }
        }

        // Scroll to highlighted asset
        if (scrollToHighlight && highlighted) {
            ImGui.setScrollHereY();
            scrollToHighlight = false;
        }

        if (isSelected || highlighted) {
            ImGui.popStyleColor(2);
        }

        // Multiple-mode sprite expand button on same line
        if (isMultipleMode) {
            ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
            String expandIcon = isExpanded ? MaterialIcons.ExpandLess : MaterialIcons.ExpandMore;
            if (ImGui.smallButton(expandIcon + "##expand_" + entry.path)) {
                toggleSpritesheetExpansion(entry.path);
            }
        }

        // Double-click handler
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            EditorPanelType panel = Assets.getEditorPanelType(entry.type);
            if (panel != null) {
                Consumer<String> handler = panelHandlers.get(panel);
                if (handler != null) {
                    handler.accept(entry.path);
                }
            }
        }

        // Drag source
        if (canInstantiate(entry) && ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
            if (!AssetDragPayload.isDragCancelled()) {
                AssetDragPayload payload = AssetDragPayload.of(entry.path, entry.type);
                ImGui.setDragDropPayload(AssetDragPayload.DRAG_TYPE, payload.serialize());
                ImGui.text(entry.filename);
            }
            ImGui.endDragDropSource();
        }

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.textDisabled("Type: " + entry.type.getSimpleName());
            ImGui.textDisabled("Path: " + entry.path);
            if (canInstantiate(entry)) {
                ImGui.text(MaterialIcons.Mouse + " Drag to scene");
            }
            ImGui.endTooltip();
        }

        // Context menu
        if (ImGui.beginPopupContextItem("asset_ctx_" + entry.path)) {
            Set<EditorCapability> caps = Assets.getEditorCapabilities(entry.type);
            if (caps.contains(EditorCapability.PIVOT_EDITING)) {
                if (ImGui.menuItem(MaterialIcons.Edit + " Sprite Editor...")) {
                    if (spriteEditorPanel != null) {
                        spriteEditorPanel.open(entry.path);
                    }
                }
            }
            if (!caps.isEmpty()) {
                ImGui.separator();
            }
            if (ImGui.menuItem(MaterialIcons.ContentCopy + " Copy Path")) {
                ImGui.setClipboardText(entry.path);
            }
            ImGui.endPopup();
        }

        ImGui.popID();
    }

    private void renderSpritesheetListChildren(AssetEntry entry) {
        SpriteGrid grid;
        try {
            Sprite parent = Assets.load(entry.path, Sprite.class);
            grid = Assets.getSpriteGrid(parent);
        } catch (Exception e) {
            ImGui.indent(20);
            ImGui.textDisabled("Failed to load");
            ImGui.unindent(20);
            return;
        }

        if (grid == null || grid.getTotalSprites() == 0) {
            ImGui.indent(20);
            ImGui.textDisabled("Empty");
            ImGui.unindent(20);
            return;
        }

        ImGui.indent(20);
        for (int i = 0; i < grid.getTotalSprites(); i++) {
            String spritePath = entry.path + "#" + i;
            boolean childHighlighted = isHighlighted(spritePath);
            float childAlpha = childHighlighted ? getHighlightAlpha() : 0f;
            childHighlighted = childAlpha > 0f;

            ImGui.pushID(spritePath);

            if (childHighlighted) {
                ImGui.pushStyleColor(ImGuiCol.Header, 1.0f, 0.8f, 0.2f, childAlpha * 0.5f);
                ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 1.0f, 0.85f, 0.3f, childAlpha * 0.6f);
            }

            if (ImGui.selectable(MaterialIcons.Image + "  #" + i, childHighlighted)) {
                selectedAsset = entry;
            }

            if (childHighlighted) {
                if (scrollToHighlight) {
                    ImGui.setScrollHereY();
                    scrollToHighlight = false;
                }
                ImGui.popStyleColor(2);
            }

            // Drag source for individual sprite
            if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
                if (!AssetDragPayload.isDragCancelled()) {
                    AssetDragPayload payload = AssetDragPayload.of(spritePath, Sprite.class);
                    ImGui.setDragDropPayload(AssetDragPayload.DRAG_TYPE, payload.serialize());
                    ImGui.text(entry.filename + "#" + i);
                }
                ImGui.endDragDropSource();
            }

            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.textDisabled("Path: " + spritePath);
                ImGui.textDisabled("Drag to scene");
                ImGui.endTooltip();
            }

            ImGui.popID();
        }
        ImGui.unindent(20);
    }

    private void renderAssetItem(AssetEntry entry, float thumbnailSize) {
        boolean isSelected = entry == selectedAsset;
        boolean isMultipleMode = isMultipleModeSprite(entry);
        boolean isExpanded = expandedSpritesheets.contains(entry.path);

        // Highlight state for focus-on-asset
        boolean highlighted = isHighlighted(entry.path);
        float highlightAlpha = highlighted ? getHighlightAlpha() : 0f;
        highlighted = highlightAlpha > 0f;

        ImGui.pushID(entry.path);
        ImGui.beginGroup();

        // Selection highlight
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.5f, 0.7f, 1.0f, 1.0f);
        }

        boolean clicked = false;

        // Try to get preview
        Sprite preview = getPreviewSprite(entry);
        if (preview != null && preview.getTexture() != null) {
            int texId = thumbnailCache.getTextureId(entry.path, preview);
            float[] uv = thumbnailCache.getUVCoords(entry.path);

            // Remove frame padding so imageButton matches button total size
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
            clicked = ImGui.imageButton(entry.path, texId, thumbnailSize, thumbnailSize,
                    uv[0], uv[3], uv[2], uv[1]);
            ImGui.popStyleVar();
        } else {
            // Fallback: icon-only button with appropriately sized font
            String icon = getIconForType(entry.type);
            ImGui.pushFont(EditorFonts.getIconFont(thumbnailSize));
            clicked = ImGui.button(icon, thumbnailSize, thumbnailSize);
            ImGui.popFont();
        }

        if (isSelected) {
            ImGui.popStyleColor(2);
        }

        // Multiple-mode sprite expand/collapse button (Unity-style arrow)
        if (isMultipleMode) {
            ImGui.sameLine(0, 2);
            String expandIcon = isExpanded ? MaterialIcons.ExpandLess : MaterialIcons.ExpandMore;
            if (ImGui.smallButton(expandIcon + "##expand_" + entry.path)) {
                toggleSpritesheetExpansion(entry.path);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(isExpanded ? "Collapse" : "Expand sprites");
            }
        }

        // Filename label below
        int maxLabelChars = Math.max(6, (int)(thumbnailSize / 6));
        String displayName = truncateFilename(entry.filename, maxLabelChars);
        float textWidth = ImGui.calcTextSize(displayName).x;
        float offset = (thumbnailSize - textWidth) / 2f;
        if (offset > 0) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        }
        ImGui.text(displayName);

        ImGui.endGroup();

        // Draw highlight overlay on top of the item (visible over images)
        if (highlighted) {
            var drawList = ImGui.getWindowDrawList();
            var min = ImGui.getItemRectMin();
            var max = ImGui.getItemRectMax();
            drawList.addRectFilled(min.x, min.y, max.x, max.y,
                    ImGui.getColorU32(1.0f, 0.8f, 0.2f, highlightAlpha * 0.25f), 4f);
            drawList.addRect(min.x, min.y, max.x, max.y,
                    ImGui.getColorU32(1.0f, 0.8f, 0.2f, highlightAlpha * 0.8f), 4f, 0, 2f);
        }

        // Scroll to highlighted asset
        if (scrollToHighlight && highlighted) {
            ImGui.setScrollHereY();
            scrollToHighlight = false;
        }

        // Handle click
        if (clicked) {
            selectedAsset = entry;
            // Notify selection manager for inspector display
            if (selectionManager != null) {
                selectionManager.selectAsset(entry.path, entry.type);
            }
        }

        // Double-click to open asset-specific editor
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            EditorPanelType panel = Assets.getEditorPanelType(entry.type);
            if (panel != null) {
                Consumer<String> handler = panelHandlers.get(panel);
                if (handler != null) {
                    handler.accept(entry.path);
                }
            }
        }

        // Drag source - use unified path format
        if (canInstantiate(entry) && ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
            if (!AssetDragPayload.isDragCancelled()) {
                AssetDragPayload payload = AssetDragPayload.of(entry.path, entry.type);
                ImGui.setDragDropPayload(AssetDragPayload.DRAG_TYPE, payload.serialize());

                // Drag preview with sprite thumbnail
                if (preview != null && preview.getTexture() != null) {
                    int texId = thumbnailCache.getTextureId(entry.path, preview);
                    float[] uv = thumbnailCache.getUVCoords(entry.path);
                    ImGui.image(texId, 32, 32, uv[0], uv[3], uv[2], uv[1]);
                    ImGui.sameLine();
                }
                ImGui.text(entry.filename);
            }
            // When cancelled, empty tooltip (small box remains due to imgui-java limitation)

            ImGui.endDragDropSource();
        }

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(entry.filename);
            ImGui.textDisabled("Type: " + entry.type.getSimpleName());
            ImGui.textDisabled("Path: " + entry.path);

            if (isMultipleMode) {
                ImGui.separator();
                ImGui.text(MaterialIcons.ExpandMore + " Click arrow to expand");
            }

            // Show if instantiable
            if (canInstantiate(entry)) {
                ImGui.separator();
                ImGui.text(MaterialIcons.Mouse + " Drag to scene to place");
            }

            ImGui.endTooltip();
        }

        // Context menu
        if (ImGui.beginPopupContextItem("asset_ctx_" + entry.path)) {
            Set<EditorCapability> caps = Assets.getEditorCapabilities(entry.type);

            // Sprite Editor (pivot and 9-slice editing)
            if (caps.contains(EditorCapability.PIVOT_EDITING)) {
                if (ImGui.menuItem(MaterialIcons.Edit + " Sprite Editor...")) {
                    if (spriteEditorPanel != null) {
                        spriteEditorPanel.open(entry.path);
                    }
                }
            }

            // Common actions
            if (!caps.isEmpty()) {
                ImGui.separator();
            }

            if (ImGui.menuItem(MaterialIcons.ContentCopy + " Copy Path")) {
                ImGui.setClipboardText(entry.path);
            }

            ImGui.endPopup();
        }

        ImGui.popID();
    }

    private void renderSpritesheetChildren(AssetEntry entry, float parentThumbnailSize) {
        // Load sprite grid
        SpriteGrid grid;
        try {
            Sprite parent = Assets.load(entry.path, Sprite.class);
            grid = Assets.getSpriteGrid(parent);
        } catch (Exception e) {
            ImGui.textDisabled("Failed to load sprite grid");
            return;
        }

        if (grid == null || grid.getTotalSprites() == 0) {
            ImGui.textDisabled("Empty sprite grid");
            return;
        }

        // Child sprite size scales with zoom but stays smaller than parent
        float childSize = Math.min(SPRITESHEET_CHILD_SIZE * zoomLevel, parentThumbnailSize * 0.75f);

        // Render child sprites in indented grid
        ImGui.indent(20);

        int totalSprites = grid.getTotalSprites();
        for (int i = 0; i < totalSprites; i++) {
            if (i > 0) {
                ImGui.sameLine();
                // If placing on the same line would overflow, wrap to next row
                if (ImGui.getContentRegionAvailX() < childSize) {
                    ImGui.newLine();
                }
            }

            renderSpritesheetSprite(entry, grid, i, childSize);
        }

        ImGui.unindent(20);
        ImGui.separator();
    }

    private void renderSpritesheetSprite(AssetEntry sheetEntry, SpriteGrid grid, int index, float size) {
        Sprite sprite = grid.getSprite(index);
        if (sprite == null) return;

        // Full path for this specific sprite
        String spritePath = sheetEntry.path + "#" + index;
        String spriteId = spritePath;

        // Highlight state for focus-on-asset
        boolean highlighted = isHighlighted(spritePath);
        float highlightAlpha = highlighted ? getHighlightAlpha() : 0f;
        highlighted = highlightAlpha > 0f;

        ImGui.pushID(spriteId);

        int texId = thumbnailCache.getTextureId(sheetEntry.path, index, sprite);
        float[] uv = thumbnailCache.getUVCoords(sheetEntry.path, index);

        // Remove frame padding so imageButton matches button total size
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
        if (ImGui.imageButton(spriteId, texId, size, size,
                uv[0], uv[3], uv[2], uv[1])) {
            // Selected this sprite
            selectedAsset = sheetEntry; // Keep sheet selected
        }
        ImGui.popStyleVar();

        // Draw highlight overlay on top of the sprite
        if (highlighted) {
            var drawList = ImGui.getWindowDrawList();
            var min = ImGui.getItemRectMin();
            var max = ImGui.getItemRectMax();
            drawList.addRectFilled(min.x, min.y, max.x, max.y,
                    ImGui.getColorU32(1.0f, 0.8f, 0.2f, highlightAlpha * 0.25f), 4f);
            drawList.addRect(min.x, min.y, max.x, max.y,
                    ImGui.getColorU32(1.0f, 0.8f, 0.2f, highlightAlpha * 0.8f), 4f, 0, 2f);
            if (scrollToHighlight) {
                ImGui.setScrollHereY();
                scrollToHighlight = false;
            }
        }

        // Drag source - use unified path format with #index
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
            if (!AssetDragPayload.isDragCancelled()) {
                AssetDragPayload payload = AssetDragPayload.of(spritePath, Sprite.class);
                ImGui.setDragDropPayload(AssetDragPayload.DRAG_TYPE, payload.serialize());

                // Drag preview with sprite thumbnail
                ImGui.image(texId, 32, 32, uv[0], uv[3], uv[2], uv[1]);
                ImGui.sameLine();
                ImGui.text(sheetEntry.filename + "#" + index);
            }
            // When cancelled, empty tooltip (small box remains due to imgui-java limitation)

            ImGui.endDragDropSource();
        }

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text("Sprite #" + index);
            ImGui.textDisabled("Path: " + spritePath);
            ImGui.textDisabled("Drag to scene to place");
            ImGui.endTooltip();
        }

        ImGui.popID();
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Registers a handler for opening a specific editor panel.
     * When an asset is double-clicked and its loader returns the given panel,
     * the handler is called with the asset path.
     *
     * @param panel   the editor panel this handler opens
     * @param handler receives the asset path when triggered
     */
    public void registerPanelHandler(EditorPanelType panel, Consumer<String> handler) {
        panelHandlers.put(panel, handler);
    }

    /**
     * Navigates to the folder containing the given asset and highlights it.
     * Opens the panel if it is not already visible.
     *
     * @param path asset path, e.g. "sprites/player.png" or "sheets/player.spritesheet#3"
     */
    public void focusOnAsset(String path) {
        if (path == null || path.isEmpty()) return;

        setOpen(true);

        // Handle spritesheet child paths (e.g., "sheets/player.spritesheet#3")
        String basePath = path;
        int hashIndex = path.indexOf('#');
        if (hashIndex >= 0) {
            basePath = path.substring(0, hashIndex);
            // Expand the parent spritesheet so the child is visible
            expandedSpritesheets.add(basePath);
        }

        // Extract folder path (everything before last '/')
        int lastSlash = basePath.lastIndexOf('/');
        String folderPath = lastSlash >= 0 ? basePath.substring(0, lastSlash) : "";

        // Walk the rootFolder tree to find the target folder
        FolderNode targetFolder = rootFolder;
        if (!folderPath.isEmpty()) {
            String[] parts = folderPath.split("/");
            for (String part : parts) {
                FolderNode child = targetFolder.getChild(part);
                if (child == null) {
                    // Folder not found — asset might not exist
                    return;
                }
                targetFolder = child;
            }
        }

        // Navigate to the folder
        navigateTo(targetFolder, folderPath);

        // Clear search filter so the asset is visible
        searchFilter.set("");

        // Set highlight state — keep full path (with #index) for sub-asset highlighting
        highlightedAssetPath = path;
        highlightStartTime = System.currentTimeMillis();
        scrollToHighlight = true;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void navigateTo(FolderNode folder, String path) {
        currentFolder = folder;
        currentPath = path;
        refreshCurrentFolder();
        selectedAsset = null;
    }

    /**
     * Returns the highlight alpha for the focused asset (0.0 when expired, fades over last 500ms).
     */
    private float getHighlightAlpha() {
        if (highlightedAssetPath == null) return 0f;
        long elapsed = System.currentTimeMillis() - highlightStartTime;
        if (elapsed >= HIGHLIGHT_DURATION_MS) {
            highlightedAssetPath = null;
            return 0f;
        }
        long fadeStart = HIGHLIGHT_DURATION_MS - 500;
        if (elapsed < fadeStart) return 1f;
        return 1f - (float)(elapsed - fadeStart) / 500f;
    }

    private boolean isHighlighted(String entryPath) {
        return highlightedAssetPath != null && entryPath.equals(highlightedAssetPath);
    }

    private void toggleSpritesheetExpansion(String path) {
        if (expandedSpritesheets.contains(path)) {
            expandedSpritesheets.remove(path);
        } else {
            expandedSpritesheets.add(path);
        }
    }

    /** Cache for multiple-mode sprite checks */
    private final Map<String, Boolean> multipleModeCache = new HashMap<>();

    /**
     * Checks if an asset entry is a MULTIPLE-mode sprite (tileset/spritesheet).
     */
    private boolean isMultipleModeSprite(AssetEntry entry) {
        if (entry.type != Sprite.class) {
            return false;
        }

        // Check cache first
        Boolean cached = multipleModeCache.get(entry.path);
        if (cached != null) {
            return cached;
        }

        // Check metadata
        try {
            SpriteMetadata meta = AssetMetadata.load(entry.path, SpriteMetadata.class);
            boolean isMultiple = meta != null && meta.isMultiple();
            multipleModeCache.put(entry.path, isMultiple);
            return isMultiple;
        } catch (Exception e) {
            multipleModeCache.put(entry.path, false);
            return false;
        }
    }

    private Sprite getPreviewSprite(AssetEntry entry) {
        return Assets.getPreviewSprite(entry.path, entry.type);
    }

    private String getIconForType(Class<?> type) {
        return Assets.getIconCodepoint(type);
    }

    private boolean canInstantiate(AssetEntry entry) {
        return Assets.canInstantiate(entry.type);
    }

    private String truncateFilename(String filename, int maxLength) {
        if (filename.length() <= maxLength) {
            return filename;
        }
        return filename.substring(0, maxLength - 2) + "..";
    }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * Represents a folder in the asset tree.
     */
    private static class FolderNode {
        @Getter
        private final String name;
        private final Map<String, FolderNode> children = new LinkedHashMap<>();
        private final List<String> files = new ArrayList<>();

        FolderNode(String name) {
            this.name = name;
        }

        void addChild(FolderNode child) {
            children.put(child.getName(), child);
        }

        FolderNode getChild(String name) {
            return children.get(name);
        }

        Collection<FolderNode> getChildren() {
            return children.values();
        }

        void addFile(String path) {
            files.add(path);
        }

        List<String> getFiles() {
            return files;
        }
    }

    /**
     * Represents an asset entry in the grid.
     */
    public static class AssetEntry {
        public final String path;
        public final String filename;
        public final Class<?> type;

        AssetEntry(String path, String filename, Class<?> type) {
            this.path = path;
            this.filename = filename;
            this.type = type;
        }
    }
}
