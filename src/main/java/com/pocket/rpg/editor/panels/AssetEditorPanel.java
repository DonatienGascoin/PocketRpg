package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.AssetFocusRequestEvent;
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
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
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
    private List<String> sidebarAllScannedPaths = new ArrayList<>(); // cached scanAll() result
    private List<Class<?>> sidebarTypeOptions = new ArrayList<>();
    private String[] sidebarTypeLabels = new String[]{"All"}; // cached combo labels
    private SidebarFolderNode sidebarRoot = new SidebarFolderNode("root");
    private boolean sidebarExpandAll = false;
    private boolean sidebarCollapseAll = false;
    private boolean sidebarScrollToSelected = false;
    private static final float SIDEBAR_WIDTH = 200f;

    // Unsaved changes guard
    private boolean showUnsavedChangesPopup = false;
    private String pendingSwitchPath = null;
    private String pendingSubItemId = null;
    private Runnable pendingAction = null;

    // Event handling
    private final Consumer<AssetChangedEvent> assetChangedHandler;

    // Status callback
    private Consumer<String> statusCallback;

    // Selection manager (set by EditorUIController after construction)
    private EditorSelectionManager selectionManager;

    // New asset dropdown
    private List<CreatableAssetType> creatableTypes;
    private boolean pendingOpenNewDropdown = false;

    // Popup viewers (floating asset windows)
    private final List<AssetPopupViewer> popupViewers = new ArrayList<>();
    private int popupViewerCounter = 0;

    // New asset creation popup (panel-level, for dropdown)
    private boolean showNewAssetPopup = false;
    private CreatableAssetType pendingNewAssetType = null;
    private final ImString newAssetName = new ImString(128);

    // Sidebar context menu
    private String pendingRenamePath = null;
    private final ImString renameBuffer = new ImString(128);
    private boolean showRenamePopup = false;
    private String pendingDeletePath = null;
    private boolean showDeleteConfirmPopup = false;

    // Navigation history
    private final List<String> navigationHistory = new ArrayList<>();
    private int navigationIndex = -1;
    private static final int MAX_HISTORY = 50;
    private boolean navigatingHistory = false;

    // Quick search popup
    private final AssetQuickSearchPopup quickSearchPopup = new AssetQuickSearchPopup();

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

        ShortcutBinding closeBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrl(ImGuiKey.Z)   // physical W on AZERTY
                : ShortcutBinding.ctrl(ImGuiKey.W);  // physical W on QWERTY

        List<ShortcutAction> shortcuts = new ArrayList<>(List.of(
                ShortcutAction.builder().panelVisible(getPanelId())
                        .id("editor.asset.new")
                        .displayName("New Asset")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.N))
                        .handler(() -> {
                            if (activeContent != null) {
                                activeContent.onNewRequested();
                            } else {
                                pendingOpenNewDropdown = true;
                            }
                        })
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
                        .build(),
                panelShortcut()
                        .id("editor.asset.back")
                        .displayName("Navigate Back")
                        .defaultBinding(ShortcutBinding.alt(ImGuiKey.LeftArrow))
                        .handler(() -> { if (canGoBack()) requestDirtyGuard(this::goBack); })
                        .build(),
                panelShortcut()
                        .id("editor.asset.forward")
                        .displayName("Navigate Forward")
                        .defaultBinding(ShortcutBinding.alt(ImGuiKey.RightArrow))
                        .handler(() -> { if (canGoForward()) requestDirtyGuard(this::goForward); })
                        .build(),
                panelShortcut()
                        .id("editor.asset.quickSearch")
                        .displayName("Quick Open Asset")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.P))
                        .handler(() -> {
                            EditorConfig cfg = getConfig();
                            List<String> recents = cfg != null ? cfg.getRecentAssets() : List.of();
                            List<String> favs = cfg != null ? cfg.getFavoriteAssets() : List.of();
                            quickSearchPopup.open(this::selectAssetByPath, recents, favs);
                        })
                        .build(),
                panelShortcut()
                        .id("editor.asset.close")
                        .displayName("Close Asset")
                        .defaultBinding(closeBinding)
                        .handler(() -> {
                            if (editingAsset != null) requestDirtyGuard(this::clearEditingAsset);
                        })
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
            renderShellToolbar();
            renderBreadcrumb();
            ImGui.separator();
            renderBody();
        }

        // Render popups (must be outside child windows)
        if (activeContent != null) {
            activeContent.renderPopups();
        }
        renderUnsavedChangesPopup();
        renderNewAssetPopup();
        renderRenamePopup();
        renderDeleteConfirmPopup();
        quickSearchPopup.render();

        ImGui.end();

        // Popup viewers render as independent top-level windows (outside main panel)
        for (AssetPopupViewer viewer : popupViewers) {
            viewer.render();
        }
        popupViewers.removeIf(v -> !v.isOpen());
    }

    // ========================================================================
    // SHELL TOOLBAR (always visible)
    // ========================================================================

    private void renderShellToolbar() {
        // Calculate right-aligned section width upfront
        float spacing = ImGui.getStyle().getItemSpacingX();
        float framePadX = ImGui.getStyle().getFramePaddingX();
        float newBtnW = ImGui.calcTextSize(MaterialIcons.Add + " New " + MaterialIcons.ArrowDropDown).x + framePadX * 2;
        float closeBtnW = ImGui.calcTextSize(MaterialIcons.Close).x + framePadX * 2;
        float rightWidth = newBtnW;
        if (editingAsset != null) rightWidth += spacing + closeBtnW;
        float rightEdge = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX();

        // Hamburger toggle
        String hamburgerIcon = sidebarOpen ? MaterialIcons.MenuOpen : MaterialIcons.Menu;
        if (ImGui.button(hamburgerIcon + "##hamburger")) {
            sidebarOpen = !sidebarOpen;
            if (sidebarOpen) sidebarRefreshRequested = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Toggle asset sidebar");
        }

        ImGui.sameLine();

        // Back / Forward navigation
        boolean canBack = canGoBack();
        if (!canBack) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.ArrowBack + "##back")) {
            requestDirtyGuard(this::goBack);
        }
        if (!canBack) ImGui.endDisabled();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Back (Alt+Left)");
        ImGui.sameLine();

        boolean canFwd = canGoForward();
        if (!canFwd) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.ArrowForward + "##forward")) {
            requestDirtyGuard(this::goForward);
        }
        if (!canFwd) ImGui.endDisabled();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Forward (Alt+Right)");

        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        // Asset name (or "No asset selected" when nothing loaded)
        if (editingAsset != null) {
            String name = editingPath != null ? extractFilename(editingPath) : "Unknown";
            if (dirty) {
                EditorColors.textColored(EditorColors.WARNING, name + " *");
            } else {
                ImGui.text(name);
            }

            // Favorite star toggle
            ImGui.sameLine();
            EditorConfig cfg = getConfig();
            boolean isFav = cfg != null && editingPath != null && cfg.isFavoriteAsset(editingPath);
            String starIcon = isFav ? MaterialIcons.Star : MaterialIcons.StarBorder;
            if (isFav) ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], EditorColors.WARNING[2], EditorColors.WARNING[3]);
            if (ImGui.smallButton(starIcon + "##favToggle")) {
                if (cfg != null && editingPath != null) {
                    cfg.toggleFavoriteAsset(editingPath);
                }
            }
            if (isFav) ImGui.popStyleColor();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(isFav ? "Remove from Favorites" : "Add to Favorites");
            }
        } else {
            ImGui.textDisabled("No asset selected");
        }

        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        // Save button (disabled when no asset or not dirty)
        boolean canSave = editingAsset != null && dirty;
        if (canSave) {
            EditorColors.pushWarningButton();
        } else {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Save + "##save")) {
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
        boolean canUndo = editingAsset != null && !panelUndoStack.isEmpty();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Undo + "##undo")) {
            undo();
        }
        if (!canUndo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ShortcutBinding undoBind = ShortcutRegistry.getInstance().getBinding("editor.asset.undo");
            String hint = undoBind != null ? " (" + undoBind.getDisplayString() + ")" : "";
            ImGui.setTooltip("Undo" + hint);
        }

        ImGui.sameLine();

        // Redo button
        boolean canRedo = editingAsset != null && !panelRedoStack.isEmpty();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Redo + "##redo")) {
            redo();
        }
        if (!canRedo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ShortcutBinding redoBind = ShortcutRegistry.getInstance().getBinding("editor.asset.redo");
            String hint = redoBind != null ? " (" + redoBind.getDisplayString() + ")" : "";
            ImGui.setTooltip("Redo" + hint);
        }

        // Right-aligned section: New dropdown + Close
        ImGui.sameLine(rightEdge - rightWidth);

        // New asset dropdown — always visible
        renderNewAssetDropdown();

        // Close current asset (only when asset loaded)
        if (editingAsset != null) {
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Close + "##closeAsset")) {
                requestDirtyGuard(this::clearEditingAsset);
            }
            if (ImGui.isItemHovered()) {
                ShortcutBinding closeBind = ShortcutRegistry.getInstance().getBinding("editor.asset.close");
                String closeHint = closeBind != null ? " (" + closeBind.getDisplayString() + ")" : "";
                ImGui.setTooltip("Close asset" + closeHint);
            }
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
                    renderEmptyState();
                } else {
                    renderContentArea();
                }
            }
            ImGui.endChild();
        } else {
            // Content area — takes full width
            if (editingAsset == null) {
                renderEmptyState();
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
            ImGui.setNextItemWidth(-1 - buttonsWidth - spacing);
            if (ImGui.combo("##typeFilter", sidebarTypeFilterIndex, sidebarTypeLabels)) {
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

        // Pinned favorites — rendered outside the scrolling child so they stay sticky
        String filter = sidebarSearchFilter.get().toLowerCase().trim();
        renderSidebarPinnedSection(filter);

        // Asset tree — scrollable, increase indent for clearer hierarchy
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
        // Children are already sorted (TreeMap)
        for (var entry : node.children.entrySet()) {
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
        // Files are pre-sorted at build time
        for (SidebarFileEntry file : node.files) {
            if (!filter.isEmpty() && !file.displayName().toLowerCase().contains(filter)) {
                continue;
            }

            String path = file.path();
            boolean isSelected = path.equals(editingPath);

            // Annotation from content (e.g., warning icon)
            String annotation = null;
            if (activeContent != null) {
                annotation = activeContent.getAssetAnnotation(path);
            }

            String label = annotation != null
                    ? file.label() + " " + annotation
                    : file.label();

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

            // Context menu
            renderSidebarFileContextMenu(path);
        }
    }

    private void renderSidebarFileContextMenu(String path) {
        if (ImGui.beginPopupContextItem("##ctx_" + path)) {
            if (ImGui.selectable("Open")) {
                selectAssetByPath(path);
            }
            if (ImGui.selectable("Rename...")) {
                pendingRenamePath = path;
                renameBuffer.set(stripExtension(extractFilename(path)));
                showRenamePopup = true;
            }
            if (ImGui.selectable("Delete")) {
                pendingDeletePath = path;
                showDeleteConfirmPopup = true;
            }
            if (ImGui.selectable("Duplicate")) {
                duplicateAsset(path);
            }
            ImGui.separator();
            EditorConfig cfg = getConfig();
            boolean isFav = cfg != null && cfg.isFavoriteAsset(path);
            if (ImGui.selectable(isFav ? "Remove from Favorites" : "Add to Favorites")) {
                if (cfg != null) cfg.toggleFavoriteAsset(path);
            }
            ImGui.separator();
            if (ImGui.selectable("Copy Path")) {
                ImGui.setClipboardText(path);
            }
            if (ImGui.selectable("Reveal in Browser")) {
                EditorEventBus.get().publish(new AssetFocusRequestEvent(path));
            }
            ImGui.endPopup();
        }
    }

    private boolean folderContainsPath(SidebarFolderNode node, String targetPath) {
        for (SidebarFileEntry file : node.files) {
            if (file.path().equals(targetPath)) return true;
        }
        for (SidebarFolderNode child : node.children.values()) {
            if (folderContainsPath(child, targetPath)) return true;
        }
        return false;
    }

    private boolean folderHasMatch(SidebarFolderNode node, String filter) {
        for (SidebarFileEntry file : node.files) {
            if (file.displayName().toLowerCase().contains(filter)) {
                return true;
            }
        }
        for (SidebarFolderNode child : node.children.values()) {
            if (folderHasMatch(child, filter)) return true;
        }
        return false;
    }

    private void refreshSidebarData() {
        // Single filesystem walk — reuse result for type discovery and asset list
        sidebarAllScannedPaths = Assets.scanAll();
        sidebarTypeOptions.clear();

        Set<Class<?>> discoveredTypes = new LinkedHashSet<>(contentRegistry.getRegisteredTypes());
        for (String path : sidebarAllScannedPaths) {
            Class<?> type = Assets.getTypeForPath(path);
            if (type != null && Assets.canSave(type)) {
                discoveredTypes.add(type);
            }
        }

        sidebarTypeOptions.addAll(discoveredTypes);
        sidebarTypeOptions.sort(Comparator.comparing(Class::getSimpleName));

        // Cache combo labels
        sidebarTypeLabels = new String[sidebarTypeOptions.size() + 1];
        sidebarTypeLabels[0] = "All";
        for (int i = 0; i < sidebarTypeOptions.size(); i++) {
            sidebarTypeLabels[i + 1] = sidebarTypeOptions.get(i).getSimpleName();
        }

        // Clamp filter index
        if (sidebarTypeFilterIndex.get() > sidebarTypeOptions.size()) {
            sidebarTypeFilterIndex.set(0);
        }

        refreshSidebarAssetList();
    }

    private void refreshSidebarAssetList() {
        // Filter the cached scan result in memory — no filesystem I/O
        Set<Class<?>> acceptedTypes;
        if (sidebarTypeFilterIndex.get() == 0 || sidebarTypeOptions.isEmpty()) {
            acceptedTypes = new HashSet<>(sidebarTypeOptions);
        } else {
            int typeIdx = sidebarTypeFilterIndex.get() - 1;
            if (typeIdx < sidebarTypeOptions.size()) {
                acceptedTypes = Set.of(sidebarTypeOptions.get(typeIdx));
            } else {
                acceptedTypes = Set.of();
            }
        }

        sidebarAssetPaths.clear();
        sidebarRoot = new SidebarFolderNode("root");

        for (String path : sidebarAllScannedPaths) {
            Class<?> type = Assets.getTypeForPath(path);
            if (type != null && acceptedTypes.contains(type)) {
                sidebarAssetPaths.add(path);

                // Pre-resolve display data at build time
                String filename = extractFilename(path);
                String displayName = stripExtension(filename);
                String icon = Assets.getIconCodepoint(type);
                if (icon == null) icon = MaterialIcons.InsertDriveFile;
                sidebarRoot.addFile(path, new SidebarFileEntry(path, displayName, icon, icon + " " + displayName));
            }
        }

        sidebarRoot.sortFiles();
    }

    // ========================================================================
    // UNSAVED CHANGES POPUP
    // ========================================================================

    private void renderUnsavedChangesPopup() {
        boolean hasPending = pendingSwitchPath != null || pendingAction != null;

        if (showUnsavedChangesPopup || hasPending) {
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
                clearPendingAction();
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        } else if (hasPending) {
            // Popup was closed externally (e.g. Escape key) — treat as Cancel
            clearPendingAction();
        }
    }

    private void clearPendingAction() {
        pendingSwitchPath = null;
        pendingSubItemId = null;
        pendingAction = null;
        navigatingHistory = false;
    }

    /**
     * Runs whichever pending action was deferred by the unsaved changes guard:
     * either a path switch or a custom action from {@link #requestDirtyGuard}.
     */
    private void runPendingAction() {
        if (pendingSwitchPath != null) {
            String path = pendingSwitchPath;
            String subItem = pendingSubItemId;
            pendingSwitchPath = null;
            pendingSubItemId = null;
            forceSelectAssetByPath(path);
            if (subItem != null && activeContent != null) {
                activeContent.selectSubItem(subItem);
            }
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
            // Content toolbar renders inside the content child window
            activeContent.renderToolbarExtras();
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
        selectAssetByPath(path, null);
    }

    /**
     * Selects an asset and optionally a sub-item within it.
     *
     * @param path      Asset path (relative to asset root)
     * @param subItemId Optional sub-item to select within the content (e.g. trainer ID)
     */
    public void selectAssetByPath(String path, String subItemId) {
        if (path == null) return;

        // Guard: unsaved changes on a different asset
        if (dirty && editingPath != null && !path.equals(editingPath)) {
            pendingSwitchPath = path;
            pendingSubItemId = subItemId;
            showUnsavedChangesPopup = true;
            requestFocus();
            return;
        }

        forceSelectAssetByPath(path);
        if (subItemId != null && activeContent != null) {
            activeContent.selectSubItem(subItemId);
        }
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

        // Track navigation history and recent assets
        pushHistory(path);
        navigatingHistory = false;
        EditorConfig cfg = getConfig();
        if (cfg != null) cfg.addRecentAsset(path);

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
    public void openPopupViewer(String assetPath, Class<?> assetType) {
        // Check if already open — focus existing
        for (AssetPopupViewer v : popupViewers) {
            if (assetPath.equals(v.getAssetPath())) {
                v.requestFocus();
                return;
            }
        }
        AssetPopupViewer viewer = new AssetPopupViewer(contentRegistry, String.valueOf(popupViewerCounter++));
        viewer.setStatusCallback(statusCallback);
        viewer.open(assetPath, assetType);
        popupViewers.add(viewer);
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
    // NAVIGATION HISTORY
    // ========================================================================

    private void pushHistory(String path) {
        if (navigatingHistory) return;
        if (path == null) return;

        // Don't push duplicate of current position
        if (navigationIndex >= 0 && navigationIndex < navigationHistory.size()
                && path.equals(navigationHistory.get(navigationIndex))) {
            return;
        }

        // Truncate forward history
        if (navigationIndex < navigationHistory.size() - 1) {
            navigationHistory.subList(navigationIndex + 1, navigationHistory.size()).clear();
        }

        navigationHistory.add(path);
        navigationIndex = navigationHistory.size() - 1;

        // Cap size
        if (navigationHistory.size() > MAX_HISTORY) {
            navigationHistory.remove(0);
            navigationIndex--;
        }
    }

    private boolean canGoBack() {
        return navigationIndex > 0;
    }

    private boolean canGoForward() {
        return navigationIndex < navigationHistory.size() - 1;
    }

    private void goBack() {
        if (!canGoBack()) return;
        navigatingHistory = true;
        navigationIndex--;
        selectAssetByPath(navigationHistory.get(navigationIndex));
    }

    private void goForward() {
        if (!canGoForward()) return;
        navigatingHistory = true;
        navigationIndex++;
        selectAssetByPath(navigationHistory.get(navigationIndex));
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
    // NEW ASSET DROPDOWN
    // ========================================================================

    private void initCreatableTypes() {
        creatableTypes = new ArrayList<>();
        for (Class<?> type : contentRegistry.getRegisteredTypes()) {
            AssetEditorContent temp = contentRegistry.createContent(type);
            if (temp != null) {
                AssetCreationInfo info = temp.getCreationInfo();
                if (info != null) {
                    String name = formatClassName(type.getSimpleName());
                    creatableTypes.add(new CreatableAssetType(name, type, info));
                }
                temp.destroy();
            }
        }
        creatableTypes.sort(Comparator.comparing(CreatableAssetType::displayName));
    }

    private void renderNewAssetDropdown() {
        if (creatableTypes == null) initCreatableTypes();

        if (pendingOpenNewDropdown) {
            ImGui.openPopup("##newAssetDropdown");
            pendingOpenNewDropdown = false;
        }

        if (ImGui.button(MaterialIcons.Add + " New " + MaterialIcons.ArrowDropDown)) {
            ImGui.openPopup("##newAssetDropdown");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create new asset (Ctrl+N)");
        }

        if (ImGui.beginPopup("##newAssetDropdown")) {
            ImGui.textDisabled("Create");
            ImGui.separator();
            for (CreatableAssetType ct : creatableTypes) {
                String icon = Assets.getIconCodepoint(ct.assetClass());
                if (ImGui.selectable(icon + " " + ct.displayName())) {
                    openNewAssetDialog(ct);
                }
            }
            ImGui.endPopup();
        }
    }

    private void openNewAssetDialog(CreatableAssetType type) {
        Runnable doCreate = () -> {
            // Switch content type if needed
            if (activeContent == null || activeContent.getAssetClass() != type.assetClass()) {
                switchContentForNewAsset(type.assetClass());
            }

            if (activeContent != null && activeContent.hasCreationDialog()) {
                // Content has its own creation dialog — set shell and delegate
                activeContent.setShell(this);
                activeContent.onNewRequested();
            } else {
                // Fall back to generic name popup
                pendingNewAssetType = type;
                newAssetName.set("");
                showNewAssetPopup = true;
            }
        };

        if (dirty && editingAsset != null) {
            requestDirtyGuard(doCreate);
        } else {
            doCreate.run();
        }
    }

    private void renderNewAssetPopup() {
        if (showNewAssetPopup) {
            ImGui.openPopup("New Asset##newAssetPopup");
            showNewAssetPopup = false;
        }

        if (ImGui.beginPopupModal("New Asset##newAssetPopup", new ImBoolean(true),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            if (pendingNewAssetType != null) {
                String icon = Assets.getIconCodepoint(pendingNewAssetType.assetClass());
                ImGui.text("Type: " + icon + " " + pendingNewAssetType.displayName());
                ImGui.spacing();
                ImGui.text("Name:");
                ImGui.sameLine();
                ImGui.setNextItemWidth(250);
                ImGui.inputText("##newAssetName", newAssetName);

                ImGui.spacing();

                if (ImGui.button("Create", 120, 0)) {
                    String name = newAssetName.get().trim();
                    if (!name.isEmpty()) {
                        createNewAssetFromType(pendingNewAssetType, name);
                        ImGui.closeCurrentPopup();
                    }
                }
                ImGui.sameLine();
                if (ImGui.button("Cancel", 120, 0)) {
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
    }

    private void createNewAssetFromType(CreatableAssetType type, String name) {
        // Switch content type if needed
        if (activeContent == null || activeContent.getAssetClass() != type.assetClass()) {
            switchContentForNewAsset(type.assetClass());
        }
        if (activeContent != null) {
            try {
                Object defaultAsset = type.assetClass().getDeclaredConstructor().newInstance();
                createAsset(name, defaultAsset);
            } catch (Exception e) {
                showStatus("Error creating asset: " + e.getMessage());
            }
        }
    }

    private void switchContentForNewAsset(Class<?> type) {
        if (activeContent != null) {
            activeContent.onAssetUnloaded();
            activeContent.destroy();
        }
        activeContent = contentRegistry.createContent(type);
        if (activeContent != null) {
            activeContent.initialize();
        }
        editingType = type;
    }

    // ========================================================================
    // BREADCRUMB
    // ========================================================================

    private void renderBreadcrumb() {
        if (editingPath == null) return;

        String[] segments = editingPath.split("/");
        for (int i = 0; i < segments.length; i++) {
            boolean isLast = (i == segments.length - 1);
            String segment = segments[i];

            if (isLast) {
                ImGui.text(segment);
            } else {
                ImGui.textDisabled(segment);
                ImGui.sameLine(0, 2);
                ImGui.textDisabled(">");
                ImGui.sameLine(0, 2);
            }
        }
    }

    // ========================================================================
    // EMPTY STATE
    // ========================================================================

    private void renderEmptyState() {
        if (creatableTypes == null) initCreatableTypes();

        ImGui.spacing();

        // Two-column layout with vertical separator
        if (ImGui.beginTable("##emptyStateLayout", 2, ImGuiTableFlags.BordersInnerV)) {
            ImGui.tableSetupColumn("left", ImGuiTableColumnFlags.WidthStretch, 0.4f);
            ImGui.tableSetupColumn("right", ImGuiTableColumnFlags.WidthStretch, 0.6f);

            ImGui.tableNextRow();

            // === LEFT COLUMN: Quick Actions ===
            ImGui.tableSetColumnIndex(0);
            renderEmptyStateLeftColumn();

            // === RIGHT COLUMN: Favorites + Recently Opened ===
            ImGui.tableSetColumnIndex(1);
            renderEmptyStateRightColumn();

            ImGui.endTable();
        }

        // Tip — anchored at bottom of available space
        float tipHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY() * 2;
        float remainingY = ImGui.getContentRegionAvailY() - tipHeight;
        if (remainingY > 0) {
            ImGui.setCursorPosY(ImGui.getCursorPosY() + remainingY);
        }
        ImGui.separator();
        ImGui.spacing();
        ImGui.textDisabled("Tip: Open the sidebar " + MaterialIcons.Menu
                + " to browse all assets, or press Ctrl+P to search");
    }

    private void renderEmptyStateLeftColumn() {
        ImGui.text("Quick Actions");
        ImGui.spacing();

        // Open Asset button
        if (ImGui.button(MaterialIcons.Search + " Open Asset##emptyOpen", -1, 32)) {
            EditorConfig cfg = getConfig();
            List<String> recents = cfg != null ? cfg.getRecentAssets() : List.of();
            List<String> favs = cfg != null ? cfg.getFavoriteAssets() : List.of();
            quickSearchPopup.open(this::selectAssetByPath, recents, favs);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Quick search assets (Ctrl+P)");
        }

        ImGui.spacing();
        ImGui.spacing();

        // Create New section — 2 buttons per row
        ImGui.text("Create New");
        ImGui.spacing();

        float cardSpacing = ImGui.getStyle().getItemSpacingX();
        float totalW = ImGui.getContentRegionAvailX();
        float cardWidth = (totalW - cardSpacing) / 2;

        for (int i = 0; i < creatableTypes.size(); i++) {
            CreatableAssetType ct = creatableTypes.get(i);
            String icon = Assets.getIconCodepoint(ct.assetClass());
            if (ImGui.button(icon + " " + ct.displayName() + "##create_" + ct.displayName(),
                    cardWidth, 28)) {
                openNewAssetDialog(ct);
            }
            // Same line for odd-indexed items (second column)
            if (i % 2 == 0 && i + 1 < creatableTypes.size()) {
                ImGui.sameLine();
            }
        }
    }

    private void renderEmptyStateRightColumn() {
        EditorConfig cfg = getConfig();

        // Favorites section — always show, even when empty
        ImGui.text(MaterialIcons.Star + " Favorites");
        ImGui.spacing();

        if (cfg != null && !cfg.getFavoriteAssets().isEmpty()) {
            int favFlags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg;
            if (ImGui.beginTable("##favoritesTable", 1, favFlags)) {
                ImGui.tableSetupColumn("Asset", ImGuiTableColumnFlags.WidthStretch);

                for (String path : List.copyOf(cfg.getFavoriteAssets())) {
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    Class<?> type = Assets.getTypeForPath(path);
                    String icon = type != null ? Assets.getIconCodepoint(type) : MaterialIcons.InsertDriveFile;
                    if (ImGui.selectable(icon + " " + stripExtension(extractFilename(path))
                            + "##fav_" + path, false, ImGuiSelectableFlags.SpanAllColumns)) {
                        selectAssetByPath(path);
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(path);
                    }
                }

                ImGui.endTable();
            }
        } else {
            ImGui.textDisabled("No favorites yet. Use " + MaterialIcons.StarBorder + " to pin assets.");
        }

        ImGui.spacing();
        ImGui.spacing();

        // Recently Opened section — table with borders
        ImGui.text(MaterialIcons.History + " Recently Opened");
        ImGui.spacing();

        if (cfg != null && !cfg.getRecentAssets().isEmpty()) {
            int tableFlags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg;
            if (ImGui.beginTable("##recentAssetsTable", 1, tableFlags)) {
                ImGui.tableSetupColumn("Asset", ImGuiTableColumnFlags.WidthStretch);

                for (String path : List.copyOf(cfg.getRecentAssets())) {
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    Class<?> type = Assets.getTypeForPath(path);
                    String icon = type != null ? Assets.getIconCodepoint(type) : MaterialIcons.InsertDriveFile;
                    if (ImGui.selectable(icon + " " + stripExtension(extractFilename(path))
                            + "##recent_" + path, false, ImGuiSelectableFlags.SpanAllColumns)) {
                        selectAssetByPath(path);
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(path);
                    }
                }

                ImGui.endTable();
            }
        } else {
            ImGui.textDisabled("No recently opened assets.");
        }
    }

    // ========================================================================
    // SIDEBAR PINNED SECTION
    // ========================================================================

    private void renderSidebarPinnedSection(String filter) {
        EditorConfig cfg = getConfig();
        if (cfg == null) return;
        List<String> favorites = List.copyOf(cfg.getFavoriteAssets());
        if (favorites.isEmpty()) return;

        // Apply search filter to pinned items too
        List<String> visible = favorites;
        if (!filter.isEmpty()) {
            visible = favorites.stream()
                    .filter(p -> extractFilename(p).toLowerCase().contains(filter))
                    .toList();
            if (visible.isEmpty()) return;
        }

        if (ImGui.collapsingHeader(MaterialIcons.Star + " Favorites (" + visible.size() + ")##sidebarPinned",
                ImGuiTreeNodeFlags.DefaultOpen)) {
            for (String path : visible) {
                Class<?> type = Assets.getTypeForPath(path);
                String icon = type != null ? Assets.getIconCodepoint(type) : MaterialIcons.InsertDriveFile;
                String display = icon + " " + stripExtension(extractFilename(path));

                boolean selected = path.equals(editingPath);
                if (ImGui.selectable(display + "##fav_" + path, selected)) {
                    selectAssetByPath(path);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(path);
                }

                // Context menu for pinned items
                renderSidebarFileContextMenu(path);
            }
        }
        ImGui.separator();
    }

    // ========================================================================
    // RENAME / DELETE / DUPLICATE
    // ========================================================================

    private void renderRenamePopup() {
        if (showRenamePopup) {
            ImGui.openPopup("Rename Asset##assetRename");
            showRenamePopup = false;
        }

        if (ImGui.beginPopupModal("Rename Asset##assetRename", new ImBoolean(true),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Name:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(250);
            ImGui.inputText("##renameName", renameBuffer);

            ImGui.spacing();

            // Warning about broken references
            ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], EditorColors.WARNING[2], EditorColors.WARNING[3]);
            ImGui.text(MaterialIcons.Warning + " Renaming an asset referenced by other assets");
            ImGui.text("  will break those references.");
            ImGui.popStyleColor();

            ImGui.spacing();

            if (ImGui.button("Rename", 120, 0)) {
                String newName = renameBuffer.get().trim();
                if (!newName.isEmpty() && pendingRenamePath != null) {
                    renameAsset(pendingRenamePath, newName);
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void renderDeleteConfirmPopup() {
        if (showDeleteConfirmPopup) {
            ImGui.openPopup("Delete Asset?##assetDelete");
            showDeleteConfirmPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Asset?##assetDelete", new ImBoolean(true),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = pendingDeletePath != null
                    ? stripExtension(extractFilename(pendingDeletePath)) : "this asset";
            ImGui.text("Are you sure you want to delete \"" + name + "\"?");
            ImGui.spacing();
            ImGui.textDisabled("This cannot be undone.");
            ImGui.spacing();

            EditorColors.pushDangerButton();
            if (ImGui.button("Delete", 120, 0)) {
                if (pendingDeletePath != null) {
                    deleteAsset(pendingDeletePath);
                }
                ImGui.closeCurrentPopup();
            }
            EditorColors.popButtonColors();
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void renameAsset(String oldPath, String newName) {
        try {
            String dir = oldPath.substring(0, oldPath.lastIndexOf('/') + 1);
            String filename = extractFilename(oldPath);
            String ext = filename.contains(".") ? filename.substring(filename.indexOf('.')) : "";
            String newPath = dir + newName + ext;

            Path oldFile = Paths.get(Assets.getAssetRoot(), oldPath);
            Path newFile = Paths.get(Assets.getAssetRoot(), newPath);

            if (Files.exists(newFile)) {
                showStatus("A file named \"" + newName + "\" already exists");
                return;
            }

            Files.move(oldFile, newFile);

            EditorEventBus.get().publish(
                    new AssetChangedEvent(oldPath, AssetChangedEvent.ChangeType.DELETED));
            EditorEventBus.get().publish(
                    new AssetChangedEvent(newPath, AssetChangedEvent.ChangeType.CREATED));

            // Update editing path
            if (oldPath.equals(editingPath)) {
                editingPath = newPath;
            }

            // Evict old cache entry so stale path isn't kept in memory
            Assets.unload(oldPath);

            // Update navigation history
            for (int i = 0; i < navigationHistory.size(); i++) {
                if (navigationHistory.get(i).equals(oldPath)) {
                    navigationHistory.set(i, newPath);
                }
            }

            // Update favorites and recents
            EditorConfig cfg = getConfig();
            if (cfg != null) {
                String normalizedOld = oldPath.replace('\\', '/');
                String normalizedNew = newPath.replace('\\', '/');
                List<String> recents = cfg.getRecentAssets();
                for (int i = 0; i < recents.size(); i++) {
                    if (recents.get(i).equals(normalizedOld)) {
                        recents.set(i, normalizedNew);
                    }
                }
                List<String> favs = cfg.getFavoriteAssets();
                for (int i = 0; i < favs.size(); i++) {
                    if (favs.get(i).equals(normalizedOld)) {
                        favs.set(i, normalizedNew);
                    }
                }
                // Update timestamps map
                Long ts = cfg.getRecentAssetTimestamps().remove(normalizedOld);
                if (ts != null) {
                    cfg.getRecentAssetTimestamps().put(normalizedNew, ts);
                }
                cfg.save();
            }

            sidebarRefreshRequested = true;
            showStatus("Renamed to: " + extractFilename(newPath));
        } catch (Exception e) {
            showStatus("Error renaming: " + e.getMessage());
        }
    }

    private void deleteAsset(String path) {
        try {
            Path file = Paths.get(Assets.getAssetRoot(), path);
            Files.deleteIfExists(file);

            EditorEventBus.get().publish(
                    new AssetChangedEvent(path, AssetChangedEvent.ChangeType.DELETED));

            // If we just deleted the currently open asset, clear it
            if (path.equals(editingPath)) {
                clearEditingAsset();
            }

            // Remove from favorites/recents if present
            EditorConfig cfg = getConfig();
            if (cfg != null) {
                if (cfg.isFavoriteAsset(path)) cfg.toggleFavoriteAsset(path);
                cfg.getRecentAssets().remove(path.replace('\\', '/'));
                cfg.save();
            }

            sidebarRefreshRequested = true;
            showStatus("Deleted: " + extractFilename(path));
        } catch (Exception e) {
            showStatus("Error deleting: " + e.getMessage());
        }
    }

    private void duplicateAsset(String originalPath) {
        try {
            Class<?> type = Assets.getTypeForPath(originalPath);
            Object asset = Assets.load(originalPath, type);
            if (asset == null) {
                showStatus("Failed to load asset for duplication");
                return;
            }

            String dir = originalPath.substring(0, originalPath.lastIndexOf('/') + 1);
            String origFilename = extractFilename(originalPath);
            String ext = origFilename.contains(".") ? origFilename.substring(origFilename.indexOf('.')) : "";
            String baseName = stripExtension(origFilename);
            String newName = baseName + "_copy";
            String newPath = dir + newName + ext;
            int counter = 2;
            while (Files.exists(Paths.get(Assets.getAssetRoot(), newPath))) {
                newName = baseName + "_copy_" + counter++;
                newPath = dir + newName + ext;
            }

            Assets.persist(asset, newPath);
            // Evict so the next load reads fresh from disk (avoids shared reference)
            Assets.unload(newPath);
            EditorEventBus.get().publish(
                    new AssetChangedEvent(newPath, AssetChangedEvent.ChangeType.CREATED));
            sidebarRefreshRequested = true;
            selectAssetByPath(newPath);
            showStatus("Duplicated: " + extractFilename(newPath));
        } catch (Exception e) {
            showStatus("Error duplicating: " + e.getMessage());
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

    /**
     * Formats a camelCase class name into a human-readable form.
     * E.g. "AnimatorController" → "Animator Controller", "ItemRegistry" → "Item Registry".
     */
    private static String formatClassName(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    @Override
    public void showStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }

    // ========================================================================
    // INNER TYPES
    // ========================================================================

    private record CreatableAssetType(String displayName, Class<?> assetClass, AssetCreationInfo info) {}

    /** Pre-resolved file entry to avoid per-frame type/icon lookups. */
    private record SidebarFileEntry(String path, String displayName, String icon, String label) {}

    private static class SidebarFolderNode {
        final String name;
        final Map<String, SidebarFolderNode> children = new TreeMap<>();
        final List<SidebarFileEntry> files = new ArrayList<>();

        SidebarFolderNode(String name) {
            this.name = name;
        }

        void addFile(String fullPath, SidebarFileEntry entry) {
            String[] segments = fullPath.split("/");
            SidebarFolderNode current = this;
            for (int i = 0; i < segments.length - 1; i++) {
                current = current.children.computeIfAbsent(segments[i], SidebarFolderNode::new);
            }
            current.files.add(entry);
        }

        /** Sort files in all nodes recursively. Call once after tree is built. */
        void sortFiles() {
            files.sort(Comparator.comparing(SidebarFileEntry::displayName));
            for (SidebarFolderNode child : children.values()) {
                child.sortFiles();
            }
        }
    }
}
