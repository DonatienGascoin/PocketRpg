package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.assets.AssetDragPayload;
import com.pocket.rpg.editor.assets.ThumbnailCache;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import lombok.Getter;

import java.util.*;

/**
 * Asset browser panel with folder tree and asset grid.
 * <p>
 * Features:
 * - Left panel: Folder tree navigation
 * - Right panel: Asset grid with thumbnails
 * - Breadcrumb navigation
 * - Search/filter
 * - SpriteSheet inline expansion
 * - Drag-drop source for scene placement
 */
public class AssetBrowserPanel {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    private static final float FOLDER_TREE_WIDTH = 180f;
    private static final float THUMBNAIL_SIZE = 64f;
    private static final float THUMBNAIL_PADDING = 8f;
    private static final float SPRITESHEET_CHILD_SIZE = 48f;

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
    private final Set<String> expandedSpritesheets = new HashSet<>();

    // Selection
    @Getter
    private AssetEntry selectedAsset = null;

    // Refresh tracking
    private boolean needsRefresh = true;
    private long lastRefreshTime = 0;
    private static final long REFRESH_COOLDOWN_MS = 1000;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initializes the asset browser.
     * Call after Assets system is initialized.
     */
    public void initialize() {
        refresh();
    }

    /**
     * Refreshes the asset list from disk.
     */
    public void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < REFRESH_COOLDOWN_MS) {
            return;
        }
        lastRefreshTime = now;

        // Scan all assets
        List<String> allPaths = Assets.scanAll();

        // Build folder tree
        rootFolder = new FolderNode("");
        for (String path : allPaths) {
            addPathToTree(path);
        }

        // Set current folder to root if not set
        if (currentFolder == null) {
            currentFolder = rootFolder;
            currentPath = "";
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
        AssetManager manager = (AssetManager) Assets.getContext();
        Class<?> type = getTypeFromPath(path);

        if (type == null) {
            return null;
        }

        // Get filename
        int lastSlash = path.lastIndexOf('/');
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        return new AssetEntry(path, filename, type);
    }

    private Class<?> getTypeFromPath(String path) {
        // Match extension to loader type
        String lowerPath = path.toLowerCase();

        if (lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") ||
                lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".bmp") ||
                lowerPath.endsWith(".tga")) {
            return Sprite.class; // Default to Sprite for images
        }
        if (lowerPath.endsWith(".spritesheet") || lowerPath.endsWith(".spritesheet.json") ||
                lowerPath.endsWith(".ss.json")) {
            return SpriteSheet.class;
        }
        if (lowerPath.endsWith(".prefab.json") || lowerPath.endsWith(".prefab")) {
            return com.pocket.rpg.prefab.JsonPrefab.class;
        }
        if (lowerPath.endsWith(".glsl") || lowerPath.endsWith(".shader") ||
                lowerPath.endsWith(".vs") || lowerPath.endsWith(".fs")) {
            return com.pocket.rpg.rendering.Shader.class;
        }
        if (lowerPath.endsWith(".scene")) {
            return com.pocket.rpg.serialization.SceneData.class;
        }

        return null;
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the asset browser panel.
     */
    public void render() {
        if (needsRefresh) {
            refresh();
        }

        if (ImGui.begin("Assets")) {
            // Top bar: search + refresh
            renderTopBar();

            ImGui.separator();

            // Breadcrumb
            renderBreadcrumb();

            ImGui.separator();

            // Main content: folder tree + asset grid
            float availableWidth = ImGui.getContentRegionAvailX();
            float availableHeight = ImGui.getContentRegionAvailY();

            // Left panel: folder tree
            if (ImGui.beginChild("FolderTree", FOLDER_TREE_WIDTH, availableHeight, true)) {
                renderFolderTree(rootFolder, "");
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
        if (ImGui.inputTextWithHint("##search", FontAwesomeIcons.Search + " Search assets...", searchFilter)) {
            // Filter changed - refresh display
        }

        ImGui.sameLine();

        // Refresh button
        if (ImGui.button(FontAwesomeIcons.Sync + " Refresh")) {
            needsRefresh = true;
            thumbnailCache.clear();
        }
    }

    private void renderBreadcrumb() {
        // Root
        if (ImGui.smallButton(FontAwesomeIcons.Home)) {
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
            String icon = hasChildren ? FontAwesomeIcons.Folder : FontAwesomeIcons.FolderOpen;
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
        float windowWidth = ImGui.getContentRegionAvailX();
        int columns = Math.max(1, (int) ((windowWidth) / (THUMBNAIL_SIZE + THUMBNAIL_PADDING)));

        int column = 0;
        for (AssetEntry entry : currentAssets) {
            // Apply search filter
            if (!filter.isEmpty() && !entry.filename.toLowerCase().contains(filter)) {
                continue;
            }

            if (column > 0) {
                ImGui.sameLine();
            }

            renderAssetItem(entry);

            // If this is an expanded spritesheet, render child sprites
            if (expandedSpritesheets.contains(entry.path)) {
                column = 0; // Reset column after expansion
                renderSpritesheetChildren(entry);
            } else {
                column = (column + 1) % columns;
            }
        }

        // Status bar
        ImGui.separator();
        ImGui.text(currentAssets.size() + " items");
        if (selectedAsset != null) {
            ImGui.sameLine();
            ImGui.text(" | Selected: " + selectedAsset.filename);
        }
    }

    private void renderAssetItem(AssetEntry entry) {
        boolean isSelected = entry == selectedAsset;

        ImGui.pushID(entry.path);
        ImGui.beginGroup();

        // Selection highlight
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
        }

        boolean clicked = false;

        // Try to get preview
        Sprite preview = getPreviewSprite(entry);
        if (preview != null && preview.getTexture() != null) {
            int texId = thumbnailCache.getTextureId(entry.path, preview);
            float[] uv = thumbnailCache.getUVCoords(entry.path);

            // Flip V for OpenGL
            clicked = ImGui.imageButton(entry.path, texId, THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                    uv[0], uv[3], uv[2], uv[1]);
        } else {
            // Fallback: icon button
            String icon = getIconForType(entry.type);
            clicked = ImGui.button(icon + "\n" + truncateFilename(entry.filename, 8),
                    THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        }

        if (isSelected) {
            ImGui.popStyleColor(2);
        }

        // Filename label below
        String displayName = truncateFilename(entry.filename, 10);
        float textWidth = ImGui.calcTextSize(displayName).x;
        float offset = (THUMBNAIL_SIZE - textWidth) / 2f;
        if (offset > 0) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        }
        ImGui.textWrapped(displayName);

        ImGui.endGroup();

        // Handle click
        if (clicked) {
            selectedAsset = entry;
        }

        // Double-click to expand spritesheets
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            if (entry.type == SpriteSheet.class) {
                toggleSpritesheetExpansion(entry.path);
            }
        }

        // Drag source
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
            AssetDragPayload payload = AssetDragPayload.of(entry.path, entry.type, null);
            ImGui.setDragDropPayload(AssetDragPayload.DRAG_TYPE, payload.serialize());

            // Drag preview
            ImGui.text(FontAwesomeIcons.ArrowsAlt + " " + entry.filename);

            ImGui.endDragDropSource();
        }

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(entry.filename);
            ImGui.textDisabled("Type: " + entry.type.getSimpleName());
            ImGui.textDisabled("Path: " + entry.path);

            if (entry.type == SpriteSheet.class) {
                ImGui.separator();
                ImGui.textDisabled("Double-click to expand");
            }

            // Show if instantiable
            if (canInstantiate(entry)) {
                ImGui.separator();
                ImGui.text(FontAwesomeIcons.MousePointer + " Drag to scene to place");
            }

            ImGui.endTooltip();
        }

        ImGui.popID();
    }

    private void renderSpritesheetChildren(AssetEntry entry) {
        // Load spritesheet if needed
        SpriteSheet sheet;
        try {
            sheet = Assets.load(entry.path, SpriteSheet.class);
        } catch (Exception e) {
            ImGui.textDisabled("Failed to load spritesheet");
            return;
        }

        if (sheet == null || sheet.getTotalFrames() == 0) {
            ImGui.textDisabled("Empty spritesheet");
            return;
        }

        // Render child sprites in indented grid
        ImGui.indent(20);

        float windowWidth = ImGui.getContentRegionAvailX();
        int columns = Math.max(1, (int) (windowWidth / (SPRITESHEET_CHILD_SIZE + THUMBNAIL_PADDING)));

        int totalFrames = sheet.getTotalFrames();
        for (int i = 0; i < totalFrames; i++) {
            if (i > 0 && i % columns != 0) {
                ImGui.sameLine();
            }

            renderSpritesheetSprite(entry, sheet, i);
        }

        ImGui.unindent(20);

        // Collapse button
        if (ImGui.smallButton("Collapse " + FontAwesomeIcons.ChevronUp)) {
            expandedSpritesheets.remove(entry.path);
        }

        ImGui.separator();
    }

    private void renderSpritesheetSprite(AssetEntry sheetEntry, SpriteSheet sheet, int index) {
        Sprite sprite = sheet.getSprite(index);
        if (sprite == null) return;

        String spriteId = sheetEntry.path + "#" + index;
        ImGui.pushID(spriteId);

        int texId = thumbnailCache.getTextureId(sheetEntry.path, index, sprite);
        float[] uv = thumbnailCache.getUVCoords(sheetEntry.path, index);

        // Flip V for OpenGL
        if (ImGui.imageButton(spriteId, texId, SPRITESHEET_CHILD_SIZE, SPRITESHEET_CHILD_SIZE,
                uv[0], uv[3], uv[2], uv[1])) {
            // Selected this sprite
            selectedAsset = sheetEntry; // Keep sheet selected
        }

        // Drag source for individual sprite
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceAllowNullID)) {
            AssetDragPayload payload = AssetDragPayload.ofSpriteSheet(sheetEntry.path, sheet, index);
            ImGui.setDragDropPayload(AssetDragPayload.DRAG_TYPE, payload.serialize());

            // Drag preview
            ImGui.text(FontAwesomeIcons.ArrowsAlt + " " + sheetEntry.filename + " [" + index + "]");

            ImGui.endDragDropSource();
        }

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text("Sprite #" + index);
            ImGui.textDisabled("Drag to scene to place");
            ImGui.endTooltip();
        }

        ImGui.popID();
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

    private void toggleSpritesheetExpansion(String path) {
        if (expandedSpritesheets.contains(path)) {
            expandedSpritesheets.remove(path);
        } else {
            expandedSpritesheets.add(path);
        }
    }

    private Sprite getPreviewSprite(AssetEntry entry) {
        try {
            Object asset = Assets.load(entry.path, entry.type);
            if (asset == null) return null;

            AssetManager manager = (AssetManager) Assets.getContext();
            // Get loader and call getPreviewSprite
            // For now, handle known types directly
            if (asset instanceof Sprite sprite) {
                return sprite;
            }
            if (asset instanceof SpriteSheet sheet) {
                return sheet.getSprite(0);
            }
            if (asset instanceof com.pocket.rpg.prefab.JsonPrefab prefab) {
                return prefab.getPreviewSprite();
            }
        } catch (Exception e) {
            // Ignore - will show icon instead
        }
        return null;
    }

    private String getIconForType(Class<?> type) {
        if (type == Sprite.class || type == com.pocket.rpg.rendering.Texture.class) {
            return FontAwesomeIcons.Image;
        }
        if (type == SpriteSheet.class) {
            return FontAwesomeIcons.ThLarge;
        }
        if (type == com.pocket.rpg.prefab.JsonPrefab.class) {
            return FontAwesomeIcons.Cubes;
        }
        if (type == com.pocket.rpg.rendering.Shader.class) {
            return FontAwesomeIcons.Code;
        }
        if (type == com.pocket.rpg.serialization.SceneData.class) {
            return FontAwesomeIcons.Map;
        }
        return FontAwesomeIcons.File;
    }

    private boolean canInstantiate(AssetEntry entry) {
        return entry.type == Sprite.class ||
                entry.type == SpriteSheet.class ||
                entry.type == com.pocket.rpg.prefab.JsonPrefab.class ||
                entry.type == com.pocket.rpg.rendering.Texture.class;
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
