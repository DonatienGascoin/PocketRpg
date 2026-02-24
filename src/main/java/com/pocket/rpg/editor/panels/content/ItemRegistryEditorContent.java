package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.AssetCreationInfo;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.ui.fields.AssetEditor;
import com.pocket.rpg.editor.ui.fields.EnumEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import com.pocket.rpg.items.*;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.ItemRegistryLoader;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Content implementation for editing .items.json assets in the unified AssetEditorPanel.
 * <p>
 * Two-column layout: left (searchable item list) and right (field editors).
 * The hamburger sidebar handles item registry file selection.
 */
@EditorContentFor(com.pocket.rpg.items.ItemRegistry.class)
public class ItemRegistryEditorContent implements AssetEditorContent {

    // State
    private ItemRegistry editingRegistry;
    private String editingPath;
    private AssetEditorShell shell;

    // Selection state
    private ItemDefinition selectedItem = null;
    private String selectedItemId = null;
    private int selectedItemIdx = -1;

    // Search & filter
    private final ImString searchFilter = new ImString();
    private int categoryFilterIdx = 0; // 0 = All

    // ID editing (stable key + deferred rename)
    private final ImString itemIdBuffer = new ImString(256);
    private boolean itemIdActive = false;

    // Popups
    private boolean showDeleteConfirmPopup = false;

    // Event subscription
    private Consumer<AssetChangedEvent> assetChangedHandler;

    // targetStatus combo options
    private static final String[] STATUS_OPTIONS = {"(Cure All)", "BURN", "FREEZE", "PARALYZE", "POISON", "SLEEP"};
    private static final String[] STATUS_VALUES = {null, "BURN", "FREEZE", "PARALYZE", "POISON", "SLEEP"};

    // Category filter options (built once)
    private static final ItemCategory[] CATEGORIES = ItemCategory.values();
    private static final String[] CATEGORY_FILTER_OPTIONS;
    static {
        CATEGORY_FILTER_OPTIONS = new String[CATEGORIES.length + 1];
        CATEGORY_FILTER_OPTIONS[0] = "All Categories";
        for (int i = 0; i < CATEGORIES.length; i++) {
            CATEGORY_FILTER_OPTIONS[i + 1] = CATEGORIES[i].name();
        }
    }

    // Effects that use a numeric effectValue
    private static final Set<ItemEffect> NUMERIC_EFFECTS = EnumSet.of(
            ItemEffect.HEAL_HP, ItemEffect.REVIVE, ItemEffect.CAPTURE, ItemEffect.REPEL,
            ItemEffect.BOOST_ATK, ItemEffect.BOOST_DEF, ItemEffect.BOOST_SP_ATK,
            ItemEffect.BOOST_SP_DEF, ItemEffect.BOOST_SPD, ItemEffect.BOOST_ACCURACY,
            ItemEffect.BOOST_CRIT
    );

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void initialize() {
        assetChangedHandler = event -> {
            if (event.path().endsWith(".items.json")) {
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
        this.editingRegistry = (ItemRegistry) asset;
        this.shell = shell;
        this.selectedItem = null;
        this.selectedItemId = null;
        this.searchFilter.set("");
        this.categoryFilterIdx = 0;
    }

    @Override
    public void onAssetUnloaded() {
        editingRegistry = null;
        editingPath = null;
        selectedItem = null;
        selectedItemId = null;
    }

    @Override
    public void onAfterUndoRedo() {
        if (selectedItemId != null && editingRegistry != null) {
            selectedItem = editingRegistry.get(selectedItemId);
            if (selectedItem == null) {
                // ID changed (e.g., rename undo) — fall back to index
                var all = getSortedItems();
                if (!all.isEmpty() && selectedItemIdx >= 0) {
                    selectedItem = all.get(Math.min(selectedItemIdx, all.size() - 1));
                    selectedItemId = selectedItem != null ? selectedItem.getItemId() : null;
                } else {
                    selectedItemId = null;
                }
            }
        }
    }

    @Override
    public Class<?> getAssetClass() {
        return ItemRegistry.class;
    }

    @Override
    public AssetCreationInfo getCreationInfo() {
        return new AssetCreationInfo("data/items/", ".items.json");
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (editingRegistry == null) return;

        float totalWidth = ImGui.getContentRegionAvailX();
        float leftColumnWidth = Math.max(200, totalWidth * 0.25f);

        // Left column: item list
        if (ImGui.beginChild("##itemList", leftColumnWidth, -1, true)) {
            renderItemList();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right column: item editor
        if (ImGui.beginChild("##itemEditor", 0, -1, true)) {
            renderItemEditor();
        }
        ImGui.endChild();
    }

    // ========================================================================
    // LEFT COLUMN — ITEM LIST
    // ========================================================================

    private void renderItemList() {
        // Search box
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);

        // Category filter
        ImInt catIdx = new ImInt(categoryFilterIdx);
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##categoryFilter", catIdx, CATEGORY_FILTER_OPTIONS)) {
            categoryFilterIdx = catIdx.get();
        }

        ImGui.spacing();

        // Add / Delete buttons
        if (ImGui.button(MaterialIcons.Add + " New Item")) {
            addNewItem();
        }

        if (selectedItem != null) {
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Delete + " Delete")) {
                showDeleteConfirmPopup = true;
            }
        }

        ImGui.separator();

        // Filtered & sorted item list
        String filter = searchFilter.get().toLowerCase();
        ItemCategory catFilter = categoryFilterIdx > 0 ? CATEGORIES[categoryFilterIdx - 1] : null;

        if (ImGui.beginChild("##itemListScroll")) {
            List<ItemDefinition> sorted = getSortedItems();

            for (int i = 0; i < sorted.size(); i++) {
                ItemDefinition item = sorted.get(i);

                // Category filter
                if (catFilter != null && item.getCategory() != catFilter) continue;

                // Text search filter
                if (!filter.isEmpty()) {
                    String id = item.getItemId() != null ? item.getItemId().toLowerCase() : "";
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if (!id.contains(filter) && !name.contains(filter)) continue;
                }

                boolean isSelected = item == selectedItem;
                String displayName = (item.getName() != null ? item.getName() : "?")
                        + " (" + item.getItemId() + ")";

                if (ImGui.selectable(displayName, isSelected)) {
                    selectedItem = item;
                    selectedItemId = item.getItemId();
                    selectedItemIdx = i;
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // RIGHT COLUMN — ITEM EDITOR
    // ========================================================================

    private void renderItemEditor() {
        if (selectedItem == null) {
            ImGui.textDisabled("Select an item to edit");
            return;
        }

        ItemDefinition item = selectedItem;
        String iid = item.getItemId();

        // --- Identity: sprite button + fields (pinned at top) ---
        if (ImGui.collapsingHeader(MaterialIcons.Badge + " Identity", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderIdentitySection(item, iid);
        }

        ImGui.separator();

        // --- Scrollable area for remaining sections ---
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 0);
        if (ImGui.beginChild("##itemFieldsScroll")) {
            if (ImGui.collapsingHeader(MaterialIcons.Category + " Category & Pricing", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderCategorySection(item, iid);
            }

            if (ImGui.collapsingHeader(MaterialIcons.Settings + " Behavior", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderBehaviorSection(item, iid);
            }

            if (ImGui.collapsingHeader(MaterialIcons.AutoFixHigh + " Effect", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderEffectSection(item, iid);
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(2);
    }

    // ========================================================================
    // IDENTITY SECTION (sprite button left, fields right)
    // ========================================================================

    private void renderIdentitySection(ItemDefinition item, String iid) {
        Sprite sprite = item.getSprite();
        float spriteSize = 64;

        if (ImGui.beginTable("##identity_layout", 2, ImGuiTableFlags.None)) {
            ImGui.tableSetupColumn("sprite", ImGuiTableColumnFlags.WidthFixed, spriteSize + 16);
            ImGui.tableSetupColumn("fields", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableNextRow();

            // --- Left: Sprite button ---
            ImGui.tableNextColumn();
            renderSpriteButton(item, sprite, spriteSize);

            // --- Right: ID, Name, Description ---
            ImGui.tableNextColumn();
            // Item ID — stable key, rename only on deactivation
            if (!itemIdActive) {
                itemIdBuffer.set(item.getItemId() != null ? item.getItemId() : "");
            }
            FieldEditorUtils.inspectorRow("Item ID", () -> {
                ImGui.inputText("##itemId_edit", itemIdBuffer);
            });
            itemIdActive = ImGui.isItemActive();
            if (ImGui.isItemDeactivatedAfterEdit()) {
                String newId = itemIdBuffer.get().trim();
                if (!newId.isEmpty() && !newId.equals(item.getItemId())
                        && editingRegistry.get(newId) == null) {
                    captureStructuralUndo("Rename Item", () -> {
                        editingRegistry.removeItem(item.getItemId());
                        item.setItemId(newId);
                        editingRegistry.addItem(item);
                        selectedItemId = newId;
                    });
                }
            }

            PrimitiveEditors.drawString("Name", "item." + iid + ".name",
                    () -> item.getName() != null ? item.getName() : "",
                    val -> { item.setName(val); shell.markDirty(); });

            PrimitiveEditors.drawString("Description", "item." + iid + ".description",
                    () -> item.getDescription() != null ? item.getDescription() : "",
                    val -> { item.setDescription(val); shell.markDirty(); });

            ImGui.endTable();
        }
    }

    private void renderSpriteButton(ItemDefinition item, Sprite sprite, float size) {
        ImGui.pushID("##spritePicker");

        boolean clicked;
        if (sprite != null && sprite.getTexture() != null) {
            int texId = sprite.getTexture().getTextureId();
            clicked = ImGui.imageButton("##sprBtn", texId, size, size,
                    sprite.getU0(), sprite.getV1(),
                    sprite.getU1(), sprite.getV0());
        } else {
            clicked = ImGui.button(MaterialIcons.Image, size, size);
        }

        if (clicked) {
            String currentPath = sprite != null ? Assets.getPathForResource(sprite) : null;
            AssetEditor.openPicker(Sprite.class, currentPath, selectedAsset -> {
                Sprite picked = (Sprite) selectedAsset;
                captureStructuralUndo("Change Sprite", () -> item.setSprite(picked));
            });
        }

        if (ImGui.isItemHovered()) {
            if (sprite != null) {
                String path = Assets.getPathForResource(sprite);
                ImGui.setTooltip(path != null ? path : "Sprite");
            } else {
                ImGui.setTooltip("Click to set sprite");
            }
        }

        ImGui.popID();
    }

    // ========================================================================
    // CATEGORY & PRICING SECTION
    // ========================================================================

    private void renderCategorySection(ItemDefinition item, String iid) {
        EnumEditor.drawEnum("Category", "item." + iid + ".category",
                item::getCategory, val -> { item.setCategory(val); shell.markDirty(); },
                ItemCategory.class);

        PrimitiveEditors.drawInt("Price", "item." + iid + ".price",
                item::getPrice, val -> { item.setPrice(Math.max(0, val)); shell.markDirty(); });

        PrimitiveEditors.drawInt("Sell Price", "item." + iid + ".sellPrice",
                item::getSellPrice, val -> { item.setSellPrice(Math.max(0, val)); shell.markDirty(); });
    }

    // ========================================================================
    // BEHAVIOR SECTION
    // ========================================================================

    private void renderBehaviorSection(ItemDefinition item, String iid) {
        PrimitiveEditors.drawBoolean("Usable In Battle", "item." + iid + ".usableInBattle",
                item::isUsableInBattle, val -> { item.setUsableInBattle(val); shell.markDirty(); });

        PrimitiveEditors.drawBoolean("Usable Outside", "item." + iid + ".usableOutside",
                item::isUsableOutside, val -> { item.setUsableOutside(val); shell.markDirty(); });

        PrimitiveEditors.drawBoolean("Consumable", "item." + iid + ".consumable",
                item::isConsumable, val -> { item.setConsumable(val); shell.markDirty(); });

        PrimitiveEditors.drawInt("Stack Limit", "item." + iid + ".stackLimit",
                item::getStackLimit, val -> { item.setStackLimit(Math.max(1, Math.min(999, val))); shell.markDirty(); });
    }

    // ========================================================================
    // EFFECT SECTION (with conditional fields)
    // ========================================================================

    private void renderEffectSection(ItemDefinition item, String iid) {
        EnumEditor.drawEnum("Effect", "item." + iid + ".effect",
                () -> item.getEffect() != null ? item.getEffect() : ItemEffect.NONE,
                val -> { item.setEffect(val); shell.markDirty(); },
                ItemEffect.class);

        ItemEffect effect = item.getEffect() != null ? item.getEffect() : ItemEffect.NONE;

        // effectValue — shown for effects that use a numeric parameter
        if (NUMERIC_EFFECTS.contains(effect)) {
            PrimitiveEditors.drawInt("Effect Value", "item." + iid + ".effectValue",
                    item::getEffectValue, val -> { item.setEffectValue(val); shell.markDirty(); });
        }

        // teachesMove — shown only for TEACH_MOVE
        if (effect == ItemEffect.TEACH_MOVE) {
            PrimitiveEditors.drawString("Teaches Move", "item." + iid + ".teachesMove",
                    () -> item.getTeachesMove() != null ? item.getTeachesMove() : "",
                    val -> { item.setTeachesMove(val.isEmpty() ? null : val); shell.markDirty(); });
        }

        // targetStatus — shown only for HEAL_STATUS
        if (effect == ItemEffect.HEAL_STATUS) {
            renderTargetStatusCombo(item, iid);
        }
    }

    private void renderTargetStatusCombo(ItemDefinition item, String iid) {
        int currentIdx = 0;
        String current = item.getTargetStatus();
        if (current != null && !current.isEmpty()) {
            for (int i = 1; i < STATUS_VALUES.length; i++) {
                if (current.equals(STATUS_VALUES[i])) {
                    currentIdx = i;
                    break;
                }
            }
        }

        ImInt selected = new ImInt(currentIdx);
        FieldEditorUtils.inspectorRow("Target Status", () -> {
            if (ImGui.combo("##targetStatus_" + iid, selected, STATUS_OPTIONS)) {
                int newIdx = selected.get();
                String newValue = STATUS_VALUES[newIdx];
                captureStructuralUndo("Edit Target Status", () -> item.setTargetStatus(newValue));
            }
        });
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    @Override
    public void renderPopups() {
        if (showDeleteConfirmPopup) {
            ImGui.openPopup("Delete Confirmation##itemReg");
            showDeleteConfirmPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Confirmation##itemReg", ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = selectedItem != null ? selectedItem.getName() + " (" + selectedItem.getItemId() + ")" : "?";
            ImGui.text("Are you sure you want to delete this item?");
            ImGui.text(name);
            ImGui.spacing();

            if (ImGui.button("Delete", 120, 0)) {
                performDelete();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void performDelete() {
        if (selectedItem == null || editingRegistry == null) return;

        String id = selectedItem.getItemId();
        captureStructuralUndo("Delete Item", () -> editingRegistry.removeItem(id));
        selectedItem = null;
        selectedItemId = null;
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
            ItemRegistryLoader loader = new ItemRegistryLoader();
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

    /** Deep snapshot of an ItemRegistry for undo/redo. */
    private static class ItemRegistrySnapshot {
        private final List<ItemDefinition> items;

        static ItemRegistrySnapshot capture(ItemRegistry registry) {
            ItemRegistrySnapshot snap = new ItemRegistrySnapshot();
            for (ItemDefinition def : registry.getAll()) {
                snap.items.add(copyItem(def));
            }
            return snap;
        }

        private ItemRegistrySnapshot() {
            this.items = new ArrayList<>();
        }

        void restore(ItemRegistry registry) {
            // Clear via copyFrom with empty, then re-add
            ItemRegistry temp = new ItemRegistry();
            for (ItemDefinition def : items) {
                temp.addItem(copyItem(def));
            }
            registry.copyFrom(temp);
        }

        private static ItemDefinition copyItem(ItemDefinition src) {
            ItemDefinition copy = new ItemDefinition();
            copy.setItemId(src.getItemId());
            copy.setName(src.getName());
            copy.setDescription(src.getDescription());
            copy.setCategory(src.getCategory());
            copy.setPrice(src.getPrice());
            copy.setSellPrice(src.getSellPrice());
            copy.setUsableInBattle(src.isUsableInBattle());
            copy.setUsableOutside(src.isUsableOutside());
            copy.setConsumable(src.isConsumable());
            copy.setStackLimit(src.getStackLimit());
            copy.setSprite(src.getSprite()); // Asset reference — shared, not cloned
            copy.setEffect(src.getEffect());
            copy.setEffectValue(src.getEffectValue());
            copy.setTeachesMove(src.getTeachesMove());
            copy.setTargetStatus(src.getTargetStatus());
            return copy;
        }
    }

    private void captureStructuralUndo(String description, Runnable mutation) {
        if (editingRegistry == null) return;
        UndoManager um = UndoManager.getInstance();
        um.push(SnapshotCommand.capture(
                editingRegistry,
                ItemRegistrySnapshot::capture,
                (target, snapshot) -> ((ItemRegistrySnapshot) snapshot).restore(target),
                mutation,
                description
        ));
        shell.markDirty();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void addNewItem() {
        String id = generateUniqueId();
        ItemDefinition item = ItemDefinition.builder(id, "New Item", ItemCategory.MEDICINE)
                .description("")
                .stackLimit(99)
                .effect(ItemEffect.NONE)
                .build();
        captureStructuralUndo("Add Item", () -> editingRegistry.addItem(item));
        selectedItem = item;
        selectedItemId = id;
    }

    private String generateUniqueId() {
        int counter = 1;
        while (editingRegistry.get("new_item_" + counter) != null) {
            counter++;
        }
        return "new_item_" + counter;
    }

    private List<ItemDefinition> getSortedItems() {
        List<ItemDefinition> sorted = new ArrayList<>(editingRegistry.getAll());
        sorted.sort(Comparator.comparing(d -> d.getName() != null ? d.getName() : d.getItemId()));
        return sorted;
    }
}
