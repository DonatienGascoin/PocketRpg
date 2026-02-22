package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.content.ReflectionEditorContent;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.shortcut.ShortcutBinding;
import com.pocket.rpg.editor.shortcut.ShortcutRegistry;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * Unified asset editor panel with pluggable content implementations.
 * <p>
 * Features:
 * - Hamburger sidebar for quick asset navigation/switching
 * - Toolbar: asset name, dirty indicator, save/undo/redo, content-specific extras
 * - Content area delegated to {@link AssetEditorContent} implementations
 * - Undo/redo via UndoManager target redirection
 * - Save via Assets.persist() or content's custom save
 * <p>
 * The hamburger sidebar is only an asset list browser. Content implementations
 * keep their own internal layout (left panels, tabs, sub-columns).
 */
public class AssetEditorPanel extends EditorPanel implements AssetEditorShell {

    // ========================================================================
    // STATE
    // ========================================================================

    private Object editingAsset = null;
    private String editingPath = null;
    private Class<?> editingType = null;
    private boolean dirty = false;

    // Undo/Redo — panel-specific stacks via UndoManager target redirection
    private final Deque<EditorCommand> panelUndoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> panelRedoStack = new ArrayDeque<>();

    // Content system
    private final AssetEditorContentRegistry contentRegistry;
    private AssetEditorContent activeContent = null;

    // Hamburger sidebar
    private boolean sidebarOpen = false;
    private boolean sidebarRefreshRequested = true;
    private final ImString sidebarSearchFilter = new ImString(128);
    private final ImInt sidebarTypeFilterIndex = new ImInt(0); // 0 = All
    private List<String> sidebarAssetPaths = new ArrayList<>();
    private List<Class<?>> sidebarTypeOptions = new ArrayList<>();
    private SidebarFolderNode sidebarRoot = new SidebarFolderNode("root");
    private boolean sidebarExpandAll = false;
    private boolean sidebarCollapseAll = false;
    private boolean sidebarScrollToSelected = false;
    private static final float SIDEBAR_WIDTH = 200f;

    // Unsaved changes guard
    private boolean showUnsavedChangesPopup = false;
    private String pendingSwitchPath = null;
    private Runnable pendingAction = null;

    // Event handling
    private final Consumer<AssetChangedEvent> assetChangedHandler;

    // Status callback
    private Consumer<String> statusCallback;

    // Selection manager (set by EditorUIController after construction)
    private EditorSelectionManager selectionManager;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public AssetEditorPanel() {
        super(EditorShortcuts.PanelIds.ASSET_EDITOR, true);
        this.contentRegistry = new AssetEditorContentRegistry();
        this.contentRegistry.setDefaultFactory(ReflectionEditorContent::new);

        // Subscribe to asset change events to keep sidebar in sync
        this.assetChangedHandler = event -> sidebarRefreshRequested = true;
        EditorEventBus.get().subscribe(AssetChangedEvent.class, assetChangedHandler);
    }

    /**
     * Returns the content registry for external registration of content implementations.
     */
    public AssetEditorContentRegistry getContentRegistry() {
        return contentRegistry;
    }

    /**
     * Sets the editor selection manager. Called by EditorUIController during setup.
     */
    public void setSelectionManager(EditorSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    @Override
    public EditorSelectionManager getSelectionManager() {
        return selectionManager;
    }

    // ========================================================================
    // SHORTCUTS
    // ========================================================================

    @Override
    public List<ShortcutAction> provideShortcuts(KeyboardLayout layout) {
        ShortcutBinding undoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrl(ImGuiKey.W)
                : ShortcutBinding.ctrl(ImGuiKey.Z);
        ShortcutBinding redoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrlShift(ImGuiKey.W)
                : ShortcutBinding.ctrlShift(ImGuiKey.Z);

        List<ShortcutAction> shortcuts = new ArrayList<>(List.of(
                panelShortcut()
                        .id("editor.asset.new")
                        .displayName("New Asset")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.N))
                        .handler(() -> { if (activeContent != null) activeContent.onNewRequested(); })
                        .build(),
                panelShortcut()
                        .id("editor.asset.refresh")
                        .displayName("Refresh Asset List")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.F5))
                        .handler(() -> sidebarRefreshRequested = true)
                        .build(),
                panelShortcut()
                        .id("editor.asset.save")
                        .displayName("Save Asset")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.S))
                        .allowInInput(true)
                        .handler(this::save)
                        .build(),
                panelShortcut()
                        .id("editor.asset.undo")
                        .displayName("Asset Undo")
                        .defaultBinding(undoBinding)
                        .allowInInput(true)
                        .handler(this::undo)
                        .build(),
                panelShortcut()
                        .id("editor.asset.redo")
                        .displayName("Asset Redo")
                        .defaultBinding(redoBinding)
                        .allowInInput(true)
                        .handler(this::redo)
                        .build(),
                panelShortcut()
                        .id("editor.asset.redoAlt")
                        .displayName("Asset Redo (Alt)")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Y))
                        .allowInInput(true)
                        .handler(this::redo)
                        .build()
        ));

        // Add all content-specific shortcuts from all registered types (P1b).
        // Handlers delegate to active content at runtime and wrap with undo target (P1a).
        for (ShortcutAction extra : contentRegistry.collectAllContentShortcuts(layout)) {
            shortcuts.add(panelShortcut()
                    .id(extra.getId())
                    .displayName(extra.getDisplayName())
                    .defaultBinding(extra.getDefaultBinding())
                    .allowInInput(extra.isAllowInInput())
                    .handler(buildDelegatingHandler(extra.getId()))
                    .build());
        }

        return shortcuts;
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (!isOpen()) return;

        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        String title = "Asset Editor";
        boolean visible = ImGui.begin(title, flags);
        if (consumePendingFocus()) ImGui.setWindowFocus(title);
        setContentVisible(visible);
        setFocused(ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows));

        if (visible) {
            renderToolbar();
            ImGui.separator();
            renderBody();
        }

        // Render popups (must be outside child windows)
        if (activeContent != null) {
            activeContent.renderPopups();
        }
        renderUnsavedChangesPopup();

        ImGui.end();
    }

    // ========================================================================
    // TOOLBAR
    // ========================================================================

    private void renderToolbar() {
        // Hamburger toggle
        String hamburgerIcon = sidebarOpen ? MaterialIcons.MenuOpen : MaterialIcons.Menu;
        if (ImGui.button(hamburgerIcon + "##hamburger")) {
            sidebarOpen = !sidebarOpen;
            if (sidebarOpen) sidebarRefreshRequested = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle asset sidebar");
        }

        // Only show asset controls when an asset is loaded
        if (editingAsset == null) return;

        ImGui.sameLine();

        // Asset name + dirty indicator
        String name = editingPath != null ? extractFilename(editingPath) : "Unknown";
        if (dirty) {
            EditorColors.textColored(EditorColors.WARNING, name + " *");
        } else {
            ImGui.text(name);
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Save button
        boolean canSave = dirty;
        if (canSave) {
            EditorColors.pushWarningButton();
        } else {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Save + " Save")) {
            save();
        }
        if (canSave) {
            EditorColors.popButtonColors();
        } else {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save changes (Ctrl+S)");
        }

        ImGui.sameLine();

        // Undo button
        boolean canUndo = !panelUndoStack.isEmpty();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Undo)) {
            undo();
        }
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();

        // Redo button
        boolean canRedo = !panelRedoStack.isEmpty();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Redo)) {
            redo();
        }
        if (!canRedo) ImGui.endDisabled();

        // Content-specific toolbar extras
        if (activeContent != null) {
            activeContent.renderToolbarExtras();
        }
    }

    // ========================================================================
    // BODY (SIDEBAR + CONTENT)
    // ========================================================================

    private void renderBody() {
        if (sidebarOpen) {
            // Sidebar on the left
            if (ImGui.beginChild("##assetSidebar", SIDEBAR_WIDTH, -1, true)) {
                renderSidebar();
            }
            ImGui.endChild();

            ImGui.sameLine();

            // Content must be in its own child window when sidebar is open.
            // Otherwise, items after the first (table) start a new line at
            // max(table height, sidebar height) = bottom of window, clipping the timeline.
            if (ImGui.beginChild("##contentArea", 0, -1, false,
                    ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
                if (editingAsset == null) {
                    ImGui.textDisabled("No asset selected. Double-click an asset in the Asset Browser,");
                    ImGui.textDisabled("or select one from the sidebar.");
                } else {
                    renderContentArea();
                }
            }
            ImGui.endChild();
        } else {
            // Content area — takes full width
            if (editingAsset == null) {
                ImGui.textDisabled("No asset selected. Double-click an asset in the Asset Browser,");
                ImGui.textDisabled("or select one from the sidebar.");
            } else {
                renderContentArea();
            }
        }
    }

    // ========================================================================
    // HAMBURGER SIDEBAR
    // ========================================================================

    private void renderSidebar() {
        if (sidebarRefreshRequested) {
            refreshSidebarData();
            sidebarRefreshRequested = false;
        }

        // Type filter dropdown with expand/collapse buttons on the right
        float btnSize = ImGui.getFrameHeight();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float buttonsWidth = btnSize * 2 + spacing;
        if (!sidebarTypeOptions.isEmpty()) {
            String[] typeNames = buildTypeFilterLabels();
            ImGui.setNextItemWidth(-1 - buttonsWidth - spacing);
            if (ImGui.combo("##typeFilter", sidebarTypeFilterIndex, typeNames)) {
                refreshSidebarAssetList();
            }
        } else {
            ImGui.textDisabled("No types");
            ImGui.setNextItemWidth(-1 - buttonsWidth - spacing);
        }
        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.UnfoldMore + "##expandAll", btnSize, btnSize)) {
            sidebarExpandAll = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Expand all folders");
        }
        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.UnfoldLess + "##collapseAll", btnSize, btnSize)) {
            sidebarCollapseAll = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Collapse all folders");
        }

        // Search box with reset button
        float resetBtnWidth = ImGui.calcTextSize(MaterialIcons.Close).x + ImGui.getStyle().getFramePaddingX() * 2 + spacing;
        ImGui.setNextItemWidth(-1 - resetBtnWidth);
        if (ImGui.inputTextWithHint("##sidebarSearch", MaterialIcons.Search + " Search...", sidebarSearchFilter)) {
            // Filter updated — tree will re-filter below
        }
        ImGui.sameLine();
        boolean hasSearch = sidebarSearchFilter.get().length() > 0;
        if (!hasSearch) ImGui.beginDisabled();
        if (ImGui.smallButton(MaterialIcons.Close + "##clearSearch")) {
            sidebarSearchFilter.set("");
        }
        if (!hasSearch) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset search");
        }

        ImGui.separator();

        // Asset tree — increase indent for clearer hierarchy
        String filter = sidebarSearchFilter.get().toLowerCase().trim();
        if (ImGui.beginChild("##assetList", -1, -1, false, ImGuiWindowFlags.HorizontalScrollbar)) {
            ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 16f);
            renderSidebarTree(sidebarRoot, filter);
            ImGui.popStyleVar();
        }
        ImGui.endChild();

        // Clear one-frame flags
        sidebarExpandAll = false;
        sidebarCollapseAll = false;
        sidebarScrollToSelected = false;
    }

    private void renderSidebarTree(SidebarFolderNode node, String filter) {
        // Render child folders sorted alphabetically
        List<Map.Entry<String, SidebarFolderNode>> sortedChildren = new ArrayList<>(node.children.entrySet());
        sortedChildren.sort(Comparator.comparing(Map.Entry::getKey));

        for (var entry : sortedChildren) {
            SidebarFolderNode child = entry.getValue();

            // Skip folders with no matching descendants when filtering
            if (!filter.isEmpty() && !folderHasMatch(child, filter)) {
                continue;
            }

            // Apply expand/collapse flags, or force-open to reveal selected asset
            if (sidebarExpandAll) {
                ImGui.setNextItemOpen(true);
            } else if (sidebarCollapseAll) {
                ImGui.setNextItemOpen(false);
            } else if (sidebarScrollToSelected && editingPath != null
                    && folderContainsPath(child, editingPath)) {
                ImGui.setNextItemOpen(true);
            }

            int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth
                    | ImGuiTreeNodeFlags.DefaultOpen;
            boolean isOpen = ImGui.treeNodeEx(child.name + "##folder_" + child.name,
                    flags, MaterialIcons.Folder + " " + child.name);

            if (isOpen) {
                renderSidebarTree(child, filter);
                renderSidebarFiles(child, filter);
                ImGui.treePop();
            }
        }

        // Root-level files (not inside any folder)
        if (node == sidebarRoot) {
            renderSidebarFiles(node, filter);
        }
    }

    private void renderSidebarFiles(SidebarFolderNode node, String filter) {
        List<String> sortedFiles = new ArrayList<>(node.files);
        sortedFiles.sort(Comparator.naturalOrder());

        for (String path : sortedFiles) {
            String filename = extractFilename(path);
            if (!filter.isEmpty() && !filename.toLowerCase().contains(filter)) {
                continue;
            }

            boolean isSelected = path.equals(editingPath);

            // Get icon for asset type
            Class<?> type = Assets.getTypeForPath(path);
            String icon = type != null ? Assets.getIconCodepoint(type) : MaterialIcons.InsertDriveFile;

            // Annotation from content (e.g., warning icon)
            String annotation = null;
            if (activeContent != null) {
                annotation = activeContent.getAssetAnnotation(path);
            }

            String displayName = stripExtension(filename);
            String label = annotation != null
                    ? icon + " " + displayName + " " + annotation
                    : icon + " " + displayName;

            // Use leaf tree node so files get proper tree indentation
            int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen
                    | ImGuiTreeNodeFlags.SpanAvailWidth;
            if (isSelected) {
                flags |= ImGuiTreeNodeFlags.Selected;
            }
            ImGui.treeNodeEx(path, flags, label);
            if (ImGui.isItemClicked()) {
                selectAssetByPath(path);
            }
            if (isSelected && sidebarScrollToSelected) {
                ImGui.setScrollHereY(0.5f);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(path);
            }
        }
    }

    private boolean folderContainsPath(SidebarFolderNode node, String targetPath) {
        for (String path : node.files) {
            if (path.equals(targetPath)) return true;
        }
        for (SidebarFolderNode child : node.children.values()) {
            if (folderContainsPath(child, targetPath)) return true;
        }
        return false;
    }

    private boolean folderHasMatch(SidebarFolderNode node, String filter) {
        // Check direct files
        for (String path : node.files) {
            String filename = extractFilename(path);
            if (filename.toLowerCase().contains(filter)) {
                return true;
            }
        }
        // Check child folders recursively
        for (SidebarFolderNode child : node.children.values()) {
            if (folderHasMatch(child, filter)) {
                return true;
            }
        }
        return false;
    }

    private void refreshSidebarData() {
        // Discover saveable types by scanning all assets and checking canSave()
        sidebarTypeOptions.clear();
        Set<Class<?>> discoveredTypes = new LinkedHashSet<>();

        // Include types registered in the content registry
        discoveredTypes.addAll(contentRegistry.getRegisteredTypes());

        // Scan all assets to discover types with canSave()
        List<String> allPaths = Assets.scanAll();
        for (String path : allPaths) {
            Class<?> type = Assets.getTypeForPath(path);
            if (type != null && Assets.canSave(type) && !discoveredTypes.contains(type)) {
                discoveredTypes.add(type);
            }
        }

        sidebarTypeOptions.addAll(discoveredTypes);
        sidebarTypeOptions.sort(Comparator.comparing(Class::getSimpleName));

        // Clamp filter index
        if (sidebarTypeFilterIndex.get() > sidebarTypeOptions.size()) {
            sidebarTypeFilterIndex.set(0);
        }

        refreshSidebarAssetList();
    }

    private void refreshSidebarAssetList() {
        sidebarAssetPaths.clear();

        if (sidebarTypeFilterIndex.get() == 0 || sidebarTypeOptions.isEmpty()) {
            // "All" — scan all saveable types
            Set<String> paths = new TreeSet<>();
            for (Class<?> type : sidebarTypeOptions) {
                paths.addAll(Assets.scanByType(type));
            }
            // If no types registered yet, show nothing
            sidebarAssetPaths.addAll(paths);
        } else {
            // Specific type
            int typeIdx = sidebarTypeFilterIndex.get() - 1;
            if (typeIdx < sidebarTypeOptions.size()) {
                Class<?> type = sidebarTypeOptions.get(typeIdx);
                sidebarAssetPaths.addAll(Assets.scanByType(type));
                Collections.sort(sidebarAssetPaths);
            }
        }

        // Rebuild folder tree from flat path list
        sidebarRoot = new SidebarFolderNode("root");
        for (String path : sidebarAssetPaths) {
            sidebarRoot.addPath(path);
        }
    }

    private String[] buildTypeFilterLabels() {
        String[] labels = new String[sidebarTypeOptions.size() + 1];
        labels[0] = "All";
        for (int i = 0; i < sidebarTypeOptions.size(); i++) {
            labels[i + 1] = sidebarTypeOptions.get(i).getSimpleName();
        }
        return labels;
    }

    // ========================================================================
    // UNSAVED CHANGES POPUP
    // ========================================================================

    private void renderUnsavedChangesPopup() {
        // Re-open every frame while a pending action exists. This keeps the popup
        // alive even if ImGui.setWindowFocus() (from consumePendingFocus) closes
        // it on the frame after it first opened.
        if (showUnsavedChangesPopup || pendingSwitchPath != null || pendingAction != null) {
            ImGui.openPopup("Unsaved Changes##assetEditor");
            showUnsavedChangesPopup = false;
        }

        if (ImGui.beginPopupModal("Unsaved Changes##assetEditor", new ImBoolean(true),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = editingPath != null ? extractFilename(editingPath) : "current asset";
            ImGui.text("You have unsaved changes to \"" + stripExtension(name) + "\".");
            ImGui.text("What would you like to do?");
            ImGui.spacing();

            if (ImGui.button("Save & Continue", 130, 0)) {
                save();
                ImGui.closeCurrentPopup();
                if (!dirty) {
                    runPendingAction();
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Discard & Continue", 140, 0)) {
                // Reload the current asset to discard in-memory changes
                if (editingPath != null) {
                    Assets.reload(editingPath);
                }
                dirty = false;
                ImGui.closeCurrentPopup();
                runPendingAction();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 130, 0)) {
                pendingSwitchPath = null;
                pendingAction = null;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    /**
     * Runs whichever pending action was deferred by the unsaved changes guard:
     * either a path switch or a custom action from {@link #requestDirtyGuard}.
     */
    private void runPendingAction() {
        if (pendingSwitchPath != null) {
            String path = pendingSwitchPath;
            pendingSwitchPath = null;
            forceSelectAssetByPath(path);
        } else if (pendingAction != null) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
        }
    }

    // ========================================================================
    // CONTENT AREA
    // ========================================================================

    private void renderContentArea() {
        if (activeContent == null) return;

        UndoManager um = UndoManager.getInstance();
        um.pushTarget(panelUndoStack, panelRedoStack);
        try {
            activeContent.render();
        } finally {
            um.popTarget();
        }
    }

    // ========================================================================
    // UNDO/REDO
    // ========================================================================

    private void undo() {
        UndoManager um = UndoManager.getInstance();
        um.pushTarget(panelUndoStack, panelRedoStack);
        try {
            um.undo();
        } finally {
            um.popTarget();
        }
        if (activeContent != null) activeContent.onAfterUndoRedo();
    }

    private void redo() {
        UndoManager um = UndoManager.getInstance();
        um.pushTarget(panelUndoStack, panelRedoStack);
        try {
            um.redo();
        } finally {
            um.popTarget();
        }
        if (activeContent != null) activeContent.onAfterUndoRedo();
    }

    /**
     * Creates a handler that delegates to the active content's matching shortcut at runtime,
     * wrapped with pushTarget/popTarget so undo commands land on the panel's stacks.
     */
    private Runnable buildDelegatingHandler(String shortcutId) {
        return () -> {
            if (activeContent == null) return;
            KeyboardLayout layout = ShortcutRegistry.getInstance().getKeyboardLayout();
            for (ShortcutAction action : activeContent.provideExtraShortcuts(layout)) {
                if (action.getId().equals(shortcutId)) {
                    UndoManager um = UndoManager.getInstance();
                    um.pushTarget(panelUndoStack, panelRedoStack);
                    try {
                        action.getHandler().run();
                    } finally {
                        um.popTarget();
                    }
                    return;
                }
            }
        };
    }

    // ========================================================================
    // SAVE
    // ========================================================================

    private void save() {
        if (editingAsset == null || editingPath == null || !dirty) return;

        try {
            if (activeContent != null && activeContent.hasCustomSave()) {
                activeContent.customSave(editingPath);
            } else {
                Assets.persist(editingAsset, editingPath);
            }
            dirty = false;
            showStatus("Saved: " + extractFilename(editingPath));
        } catch (Exception e) {
            System.err.println("[AssetEditorPanel] Failed to save: " + e.getMessage());
            showStatus("Error saving: " + e.getMessage());
        }
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Opens the panel and selects the asset at the given path for editing.
     * If there are unsaved changes, shows a confirmation popup first.
     *
     * @param path The asset path (relative to asset root)
     */
    @Override
    public void selectAssetByPath(String path) {
        if (path == null) return;

        // Guard: unsaved changes on a different asset
        if (dirty && editingPath != null && !path.equals(editingPath)) {
            pendingSwitchPath = path;
            showUnsavedChangesPopup = true;
            requestFocus();
            return;
        }

        forceSelectAssetByPath(path);
    }

    /**
     * Unconditionally selects the asset at the given path, bypassing the unsaved changes guard.
     */
    private void forceSelectAssetByPath(String path) {
        if (path == null) return;

        requestFocus();

        Class<?> type = Assets.getTypeForPath(path);
        if (type == null) {
            showStatus("Unknown asset type: " + path);
            return;
        }

        Object asset = Assets.load(path, type);
        if (asset == null) {
            showStatus("Failed to load asset: " + path);
            return;
        }

        // Switch content if asset type changed; otherwise just unload current asset
        boolean typeChanged = editingType != type || activeContent == null;
        if (!typeChanged && activeContent != null && editingAsset != null) {
            activeContent.onAssetUnloaded();
        }
        if (typeChanged) {
            switchContent(type);
        }

        editingAsset = asset;
        editingPath = path;
        editingType = type;
        dirty = false;
        panelUndoStack.clear();
        panelRedoStack.clear();

        // Notify selection manager so the Inspector shows this asset
        if (selectionManager != null) {
            selectionManager.selectAsset(path, type);
        }

        // Scroll sidebar to show newly selected asset
        if (sidebarOpen) {
            sidebarScrollToSelected = true;
        }

        // Load new asset into content
        if (activeContent != null) {
            activeContent.onAssetLoaded(path, asset, this);
        }
    }

    private void switchContent(Class<?> newType) {
        // Destroy old content
        if (activeContent != null) {
            activeContent.onAssetUnloaded();
            activeContent.destroy();
        }

        // Create new content for the asset type
        activeContent = contentRegistry.createContent(newType);
        if (activeContent != null) {
            activeContent.initialize();
        }
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    // ========================================================================
    // AssetEditorShell IMPLEMENTATION
    // ========================================================================

    @Override
    public void markDirty() {
        this.dirty = true;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void requestDirtyGuard(Runnable afterResolved) {
        if (!dirty) {
            afterResolved.run();
            return;
        }
        pendingAction = afterResolved;
        showUnsavedChangesPopup = true;
    }

    @Override
    public String getEditingPath() {
        return editingPath;
    }

    @Override
    public Deque<EditorCommand> getUndoStack() {
        return panelUndoStack;
    }

    @Override
    public Deque<EditorCommand> getRedoStack() {
        return panelRedoStack;
    }

    @Override
    public void requestSidebarRefresh() {
        sidebarRefreshRequested = true;
    }

    @Override
    public void clearEditingAsset() {
        if (activeContent != null) {
            activeContent.onAssetUnloaded();
        }
        editingAsset = null;
        editingPath = null;
        editingType = null;
        dirty = false;
        panelUndoStack.clear();
        panelRedoStack.clear();
        sidebarRefreshRequested = true;
    }

    @Override
    public String createAsset(String name, Object defaultAsset) {
        if (activeContent == null) return null;
        AssetCreationInfo info = activeContent.getCreationInfo();
        if (info == null) return null;

        String sanitized = (name == null || name.isBlank()) ? "unnamed"
                : name.trim().replaceAll("[^a-zA-Z0-9_-]", "_");

        String filename = sanitized + info.extension();
        String path = info.subdirectory() + filename;
        Path filePath = Paths.get(Assets.getAssetRoot(), path);

        int counter = 1;
        String baseName = sanitized;
        while (Files.exists(filePath)) {
            sanitized = baseName + "_" + counter;
            filename = sanitized + info.extension();
            path = info.subdirectory() + filename;
            filePath = Paths.get(Assets.getAssetRoot(), path);
            counter++;
        }

        try {
            Files.createDirectories(filePath.getParent());
            Assets.persist(defaultAsset, path);

            EditorEventBus.get().publish(
                    new AssetChangedEvent(path, AssetChangedEvent.ChangeType.CREATED));

            forceSelectAssetByPath(path);
            showStatus("Created: " + filename);
            return path;
        } catch (Exception e) {
            System.err.println("[AssetEditorPanel] Failed to create asset: " + e.getMessage());
            showStatus("Error creating asset: " + e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    /**
     * Destroys the active content and cleans up resources.
     * Called when the editor shuts down.
     */
    public void destroy() {
        EditorEventBus.get().unsubscribe(AssetChangedEvent.class, assetChangedHandler);
        if (activeContent != null) {
            activeContent.onAssetUnloaded();
            activeContent.destroy();
            activeContent = null;
        }
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String stripExtension(String filename) {
        int dot = filename.indexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    @Override
    public void showStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }

    // ========================================================================
    // SIDEBAR TREE NODE
    // ========================================================================

    private static class SidebarFolderNode {
        final String name;
        final Map<String, SidebarFolderNode> children = new LinkedHashMap<>();
        final List<String> files = new ArrayList<>();

        SidebarFolderNode(String name) {
            this.name = name;
        }

        void addPath(String fullPath) {
            String[] segments = fullPath.split("/");
            SidebarFolderNode current = this;
            for (int i = 0; i < segments.length - 1; i++) {
                current = current.children.computeIfAbsent(segments[i], SidebarFolderNode::new);
            }
            current.files.add(fullPath);
        }
    }
}
