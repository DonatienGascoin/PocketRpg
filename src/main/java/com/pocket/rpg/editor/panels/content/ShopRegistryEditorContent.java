package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.AssetCreationInfo;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import com.pocket.rpg.items.ItemDefinition;
import com.pocket.rpg.items.ItemRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.ShopRegistryLoader;
import com.pocket.rpg.shop.ShopInventory;
import com.pocket.rpg.shop.ShopRegistry;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Content implementation for editing .shops.json assets in the unified AssetEditorPanel.
 * <p>
 * Two-column layout: left (searchable shop list) and right (identity fields + items table).
 */
@EditorContentFor(com.pocket.rpg.shop.ShopRegistry.class)
public class ShopRegistryEditorContent implements AssetEditorContent {

    private static final String ITEM_REGISTRY_PATH = "data/items/items.items.json";

    // State
    private ShopRegistry editingRegistry;
    private String editingPath;
    private AssetEditorShell shell;

    // Selection state
    private ShopInventory selectedShop = null;
    private String selectedShopId = null;
    private int selectedShopIdx = -1;

    // Search
    private final ImString searchFilter = new ImString();

    // Shop ID editing (stable key + deferred rename)
    private final ImString shopIdBuffer = new ImString(256);
    private boolean shopIdActive = false;

    // Popups
    private boolean showDeleteShopPopup = false;

    // Event subscription
    private Consumer<AssetChangedEvent> assetChangedHandler;

    // Cached item names for combo dropdown
    private String[] itemComboLabels = new String[0];
    private String[] itemComboIds = new String[0];

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void initialize() {
        assetChangedHandler = event -> {
            if (event.path().endsWith(".shops.json")) {
                if (shell != null) shell.requestSidebarRefresh();
            }
        };
        EditorEventBus.get().subscribe(AssetChangedEvent.class, assetChangedHandler);
    }

    @Override
    public void destroy() {
        if (assetChangedHandler != null) {
            EditorEventBus.get().unsubscribe(AssetChangedEvent.class, assetChangedHandler);
            assetChangedHandler = null;
        }
    }

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.editingPath = path;
        this.editingRegistry = (ShopRegistry) asset;
        this.shell = shell;
        this.selectedShop = null;
        this.selectedShopId = null;
        this.searchFilter.set("");
        rebuildItemCombo();
    }

    @Override
    public void onAssetUnloaded() {
        editingRegistry = null;
        editingPath = null;
        selectedShop = null;
        selectedShopId = null;
    }

    @Override
    public void onAfterUndoRedo() {
        if (selectedShopId != null && editingRegistry != null) {
            selectedShop = editingRegistry.getShop(selectedShopId);
            if (selectedShop == null) {
                // ID changed (e.g., rename undo) — fall back to index
                var all = getSortedShops();
                if (!all.isEmpty() && selectedShopIdx >= 0) {
                    selectedShop = all.get(Math.min(selectedShopIdx, all.size() - 1));
                    selectedShopId = selectedShop != null ? selectedShop.getShopId() : null;
                } else {
                    selectedShopId = null;
                }
            }
        }
    }

    @Override
    public Class<?> getAssetClass() {
        return ShopRegistry.class;
    }

    @Override
    public AssetCreationInfo getCreationInfo() {
        return new AssetCreationInfo("data/shops/", ".shops.json");
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (editingRegistry == null) return;

        float totalWidth = ImGui.getContentRegionAvailX();
        float leftColumnWidth = Math.max(200, totalWidth * 0.25f);

        // Left column: shop list
        if (ImGui.beginChild("##shopList", leftColumnWidth, -1, true)) {
            renderShopList();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right column: shop editor
        if (ImGui.beginChild("##shopEditor", 0, -1, true)) {
            renderShopEditor();
        }
        ImGui.endChild();
    }

    // ========================================================================
    // LEFT COLUMN — SHOP LIST
    // ========================================================================

    private void renderShopList() {
        // Search box
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);

        ImGui.spacing();

        // Add / Delete buttons
        if (ImGui.button(MaterialIcons.Add + " New Shop")) {
            addNewShop();
        }

        if (selectedShop != null) {
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Delete + " Delete")) {
                showDeleteShopPopup = true;
            }
        }

        ImGui.separator();

        // Filtered & sorted shop list
        String filter = searchFilter.get().toLowerCase();

        if (ImGui.beginChild("##shopListScroll")) {
            List<ShopInventory> sorted = getSortedShops();

            for (int i = 0; i < sorted.size(); i++) {
                ShopInventory shop = sorted.get(i);

                // Text search filter
                if (!filter.isEmpty()) {
                    String id = shop.getShopId() != null ? shop.getShopId().toLowerCase() : "";
                    String name = shop.getShopName() != null ? shop.getShopName().toLowerCase() : "";
                    if (!id.contains(filter) && !name.contains(filter)) continue;
                }

                boolean isSelected = shop == selectedShop;
                String displayName = shop.getShopName() != null && !shop.getShopName().isEmpty()
                        ? shop.getShopName()
                        : shop.getShopId();

                if (ImGui.selectable(displayName + "##" + shop.getShopId(), isSelected)) {
                    selectedShop = shop;
                    selectedShopId = shop.getShopId();
                    selectedShopIdx = i;
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // RIGHT COLUMN — SHOP EDITOR
    // ========================================================================

    private void renderShopEditor() {
        if (selectedShop == null) {
            ImGui.textDisabled("Select a shop to edit");
            return;
        }

        ShopInventory shop = selectedShop;
        String sid = shop.getShopId();

        // --- Identity section ---
        if (ImGui.collapsingHeader(MaterialIcons.Store + " Identity", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderIdentitySection(shop, sid);
        }

        ImGui.separator();

        // --- Items section ---
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 0);
        if (ImGui.beginChild("##shopFieldsScroll")) {
            if (ImGui.collapsingHeader(MaterialIcons.ShoppingCart + " Items", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderItemsSection(shop, sid);
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(2);
    }

    // ========================================================================
    // IDENTITY SECTION
    // ========================================================================

    private void renderIdentitySection(ShopInventory shop, String sid) {
        // Shop ID — stable key, rename only on deactivation
        if (!shopIdActive) {
            shopIdBuffer.set(shop.getShopId() != null ? shop.getShopId() : "");
        }
        FieldEditorUtils.inspectorRow("Shop ID", () -> {
            ImGui.inputText("##shopId_edit", shopIdBuffer);
        });
        shopIdActive = ImGui.isItemActive();
        if (ImGui.isItemDeactivatedAfterEdit()) {
            String newId = shopIdBuffer.get().trim();
            if (!newId.isEmpty() && !newId.equals(shop.getShopId())
                    && editingRegistry.getShop(newId) == null) {
                captureStructuralUndo("Rename Shop", () -> {
                    editingRegistry.removeShop(shop.getShopId());
                    shop.setShopId(newId);
                    editingRegistry.addShop(shop);
                    selectedShopId = newId;
                });
            }
        }

        PrimitiveEditors.drawString("Shop Name", "shop." + sid + ".shopName",
                () -> shop.getShopName() != null ? shop.getShopName() : "",
                val -> { shop.setShopName(val); shell.markDirty(); });
    }

    // ========================================================================
    // ITEMS SECTION
    // ========================================================================

    private void renderItemsSection(ShopInventory shop, String sid) {
        // Add Item button
        if (ImGui.button(MaterialIcons.Add + " Add Item")) {
            captureStructuralUndo("Add Shop Entry", () ->
                    shop.addEntry(new ShopInventory.ShopEntry("", -1)));
        }

        ImGui.spacing();

        List<ShopInventory.ShopEntry> entries = shop.getItems();
        if (entries.isEmpty()) {
            ImGui.textDisabled("No items. Click \"Add Item\" to add one.");
            return;
        }

        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp;
        if (ImGui.beginTable("##shopEntries_" + sid, 4, flags)) {
            ImGui.tableSetupColumn("Item", ImGuiTableColumnFlags.WidthStretch, 2.0f);
            ImGui.tableSetupColumn("Stock", ImGuiTableColumnFlags.WidthFixed, 100);
            ImGui.tableSetupColumn("##unlimited", ImGuiTableColumnFlags.WidthFixed, 80);
            ImGui.tableSetupColumn("##remove", ImGuiTableColumnFlags.WidthFixed, 30);
            ImGui.tableHeadersRow();

            int removeIdx = -1;
            for (int i = 0; i < entries.size(); i++) {
                ImGui.tableNextRow();
                removeIdx = renderItemRow(shop, i, entries.get(i), sid, removeIdx);
            }

            ImGui.endTable();

            // Deferred removal (can't remove during iteration)
            if (removeIdx >= 0) {
                final int idx = removeIdx;
                captureStructuralUndo("Remove Shop Entry", () -> shop.removeEntry(idx));
            }
        }
    }

    /**
     * Renders a single item row. Returns the updated removeIdx (set to this row's index if remove was clicked).
     */
    private int renderItemRow(ShopInventory shop, int rowIdx, ShopInventory.ShopEntry entry,
                              String sid, int removeIdx) {
        ImGui.pushID("entry_" + rowIdx);

        // --- Item column: combo dropdown from ItemRegistry ---
        ImGui.tableNextColumn();
        int currentItemIdx = findItemComboIndex(entry.getItemId());
        ImInt comboIdx = new ImInt(currentItemIdx);
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##itemId", comboIdx, itemComboLabels)) {
            int newIdx = comboIdx.get();
            if (newIdx >= 0 && newIdx < itemComboIds.length) {
                String newItemId = itemComboIds[newIdx];
                captureStructuralUndo("Change Entry Item", () -> entry.setItemId(newItemId));
            }
        }

        // --- Stock column ---
        ImGui.tableNextColumn();
        boolean isUnlimited = entry.isUnlimitedStock();
        if (isUnlimited) {
            ImGui.textDisabled("--");
        } else {
            ImInt stockBuf = new ImInt(entry.getStock());
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputInt("##stock", stockBuf, 0, 0, ImGuiInputTextFlags.EnterReturnsTrue)) {
                int newStock = Math.max(0, stockBuf.get());
                captureStructuralUndo("Change Entry Stock", () -> entry.setStock(newStock));
            }
        }

        // --- Unlimited toggle column ---
        ImGui.tableNextColumn();
        boolean wasUnlimited = isUnlimited;
        if (ImGui.checkbox("##unlim", isUnlimited)) {
            if (wasUnlimited) {
                // Switching from unlimited to limited
                captureStructuralUndo("Set Limited Stock", () -> entry.setStock(10));
            } else {
                // Switching from limited to unlimited
                captureStructuralUndo("Set Unlimited Stock", () -> entry.setStock(-1));
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Unlimited stock");
        }

        // --- Remove button column ---
        ImGui.tableNextColumn();
        if (ImGui.smallButton(MaterialIcons.Close + "##remove")) {
            removeIdx = rowIdx;
        }

        ImGui.popID();
        return removeIdx;
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    @Override
    public void renderPopups() {
        if (showDeleteShopPopup) {
            ImGui.openPopup("Delete Confirmation##shopReg");
            showDeleteShopPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Confirmation##shopReg", ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = selectedShop != null
                    ? selectedShop.getShopName() + " (" + selectedShop.getShopId() + ")"
                    : "?";
            ImGui.text("Are you sure you want to delete this shop?");
            ImGui.text(name);
            ImGui.spacing();

            if (ImGui.button("Delete", 120, 0)) {
                performDeleteShop();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void performDeleteShop() {
        if (selectedShop == null || editingRegistry == null) return;

        String id = selectedShop.getShopId();
        captureStructuralUndo("Delete Shop", () -> editingRegistry.removeShop(id));
        selectedShop = null;
        selectedShopId = null;
    }

    // ========================================================================
    // SAVE
    // ========================================================================

    @Override
    public boolean hasCustomSave() {
        return true;
    }

    @Override
    public void customSave(String path) {
        if (editingRegistry == null) return;

        try {
            String fullPath = Assets.getAssetRoot() + "/" + path;
            ShopRegistryLoader loader = new ShopRegistryLoader();
            loader.save(editingRegistry, fullPath);
            Assets.reload(path);
            shell.showStatus("Saved: " + path);
        } catch (IOException e) {
            shell.showStatus("Save failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // UNDO SUPPORT
    // ========================================================================

    /** Deep snapshot of a ShopRegistry for undo/redo. */
    private static class ShopRegistrySnapshot {
        private final List<ShopInventory> shops;

        static ShopRegistrySnapshot capture(ShopRegistry registry) {
            ShopRegistrySnapshot snap = new ShopRegistrySnapshot();
            for (ShopInventory shop : registry.getAll()) {
                snap.shops.add(copyShop(shop));
            }
            return snap;
        }

        private ShopRegistrySnapshot() {
            this.shops = new ArrayList<>();
        }

        void restore(ShopRegistry registry) {
            ShopRegistry temp = new ShopRegistry();
            for (ShopInventory shop : shops) {
                temp.addShop(copyShop(shop));
            }
            registry.copyFrom(temp);
        }

        private static ShopInventory copyShop(ShopInventory src) {
            List<ShopInventory.ShopEntry> copiedEntries = new ArrayList<>();
            for (ShopInventory.ShopEntry entry : src.getItems()) {
                copiedEntries.add(new ShopInventory.ShopEntry(entry.getItemId(), entry.getStock()));
            }
            return new ShopInventory(src.getShopId(), src.getShopName(), copiedEntries);
        }
    }

    private void captureStructuralUndo(String description, Runnable mutation) {
        if (editingRegistry == null) return;
        UndoManager um = UndoManager.getInstance();
        um.push(SnapshotCommand.capture(
                editingRegistry,
                ShopRegistrySnapshot::capture,
                (target, snapshot) -> ((ShopRegistrySnapshot) snapshot).restore(target),
                mutation,
                description
        ));
        shell.markDirty();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void addNewShop() {
        String id = generateUniqueShopId();
        ShopInventory shop = new ShopInventory(id, "New Shop", new ArrayList<>());
        captureStructuralUndo("Add Shop", () -> editingRegistry.addShop(shop));
        selectedShop = shop;
        selectedShopId = id;
    }

    private String generateUniqueShopId() {
        int counter = 1;
        while (editingRegistry.getShop("new_shop_" + counter) != null) {
            counter++;
        }
        return "new_shop_" + counter;
    }

    private List<ShopInventory> getSortedShops() {
        List<ShopInventory> sorted = new ArrayList<>(editingRegistry.getAll());
        sorted.sort(Comparator.comparing(s ->
                s.getShopName() != null ? s.getShopName() : s.getShopId()));
        return sorted;
    }

    /**
     * Rebuilds the item combo dropdown labels and IDs from the ItemRegistry.
     */
    private void rebuildItemCombo() {
        ItemRegistry itemReg = loadItemRegistry();
        if (itemReg == null) {
            itemComboLabels = new String[0];
            itemComboIds = new String[0];
            return;
        }

        List<ItemDefinition> allItems = new ArrayList<>(itemReg.getAll());
        allItems.sort(Comparator.comparing(d -> d.getName() != null ? d.getName() : d.getItemId()));

        itemComboLabels = new String[allItems.size()];
        itemComboIds = new String[allItems.size()];
        for (int i = 0; i < allItems.size(); i++) {
            ItemDefinition def = allItems.get(i);
            itemComboLabels[i] = def.getName() + " (" + def.getItemId() + ")";
            itemComboIds[i] = def.getItemId();
        }
    }

    private int findItemComboIndex(String itemId) {
        if (itemId == null || itemId.isEmpty()) return -1;
        for (int i = 0; i < itemComboIds.length; i++) {
            if (itemComboIds[i].equals(itemId)) return i;
        }
        return -1;
    }

    private ItemRegistry loadItemRegistry() {
        try {
            return Assets.load(ITEM_REGISTRY_PATH, ItemRegistry.class);
        } catch (Exception e) {
            return null;
        }
    }
}
