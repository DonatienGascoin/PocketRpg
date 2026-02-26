package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.items.ItemCategory;
import com.pocket.rpg.items.ItemDefinition;
import com.pocket.rpg.items.ItemRegistry;
import com.pocket.rpg.items.ItemStack;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom inspector for {@link PlayerInventoryComponent}.
 * Shows money, category tabs with item lists, and add/remove during play mode.
 * <p>
 * Uses raw ImGui widgets instead of FieldEditors/PrimitiveEditors because this inspector
 * operates on transient play-mode state persisted via PlayerData write-through, not on
 * serialized component fields. There is no undo support in play mode.
 */
@InspectorFor(PlayerInventoryComponent.class)
public class PlayerInventoryInspector extends CustomComponentInspector<PlayerInventoryComponent> {

    private static final String ITEM_REGISTRY_PATH = "data/items/items.items.json";

    private int addQuantity = 1;
    private int selectedItemIndex = 0;
    private String[] itemOptions;
    private int goldAmount = 1000;

    private static final String[] TAB_NAMES = {
            "Medicine", "Pokeball", "Battle", "TM/HM", "Berry", "Key", "Held"
    };
    private static final ItemCategory[] CATEGORIES = ItemCategory.values();

    @Override
    public boolean draw() {
        if (editorEntity() != null) {
            ImGui.textDisabled("Data available during play mode");
            return false;
        }

        // Money display
        ImGui.text("Money: " + component.getMoney());

        ImGui.spacing();

        // Category tabs
        if (ImGui.beginTabBar("InventoryTabs")) {
            for (int t = 0; t < CATEGORIES.length; t++) {
                ItemCategory category = CATEGORIES[t];
                String tabLabel = TAB_NAMES[t] + "##InvTab" + t;

                if (ImGui.beginTabItem(tabLabel)) {
                    drawCategoryContents(category);
                    ImGui.endTabItem();
                }
            }
            ImGui.endTabBar();
        }

        return false;
    }

    private void drawCategoryContents(ItemCategory category) {
        List<ItemStack> pocket = component.getInventory().getPocket(category);

        // --- Item list in bordered child ---
        float quickAddHeight = ImGui.getFrameHeightWithSpacing() * 5;
        float availHeight = ImGui.getContentRegionAvailY() - quickAddHeight;
        float childHeight = Math.max(availHeight, 120);
        if (ImGui.beginChild("##itemList", 0, childHeight, true)) {
            if (pocket.isEmpty()) {
                ImGui.textDisabled("Empty");
            } else {
                String removeItemId = null;
                for (int i = 0; i < pocket.size(); i++) {
                    ItemStack stack = pocket.get(i);
                    ImGui.pushID(i);

                    ImGui.text(stack.getItemId() + "  x" + stack.getQuantity());

                    ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                    if (ImGui.smallButton("x##rem")) {
                        removeItemId = stack.getItemId();
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Remove all");
                    }

                    ImGui.popID();
                }

                if (removeItemId != null) {
                    int qty = component.getItemCount(removeItemId);
                    if (qty > 0) {
                        component.removeItem(removeItemId, qty);
                    }
                }
            }
        }
        ImGui.endChild();

        // --- Quick Add ---
        ImGui.text("Quick Add");
        buildItemOptions();
        if (itemOptions != null && itemOptions.length > 0) {
            ImGui.text("Item:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(120);
            ImInt selected = new ImInt(selectedItemIndex);
            if (ImGui.combo("##addItem", selected, itemOptions)) {
                selectedItemIndex = selected.get();
            }
            ImGui.sameLine();
            ImGui.text("Qty:");
            ImGui.sameLine();
            int[] qtyBuf = {addQuantity};
            ImGui.setNextItemWidth(40);
            if (ImGui.dragInt("##addQty", qtyBuf, 0.5f, 1, 99)) {
                addQuantity = Math.max(1, Math.min(99, qtyBuf[0]));
            }
            ImGui.sameLine();
            if (ImGui.button("+ Add")) {
                String itemId = itemOptions[selectedItemIndex];
                component.addItem(itemId, addQuantity);
            }
        } else {
            ImGui.textDisabled("No item registry loaded");
        }

        // --- Gold ---
        ImGui.text("Gold:");
        ImGui.sameLine();
        int[] goldBuf = {goldAmount};
        ImGui.setNextItemWidth(80);
        if (ImGui.dragInt("##goldAmount", goldBuf, 10f,
                -PlayerInventoryComponent.MAX_MONEY, PlayerInventoryComponent.MAX_MONEY)) {
            goldAmount = goldBuf[0];
        }
        ImGui.sameLine();
        if (ImGui.button("Add##gold")) {
            if (goldAmount >= 0) {
                component.addMoney(goldAmount);
            } else {
                component.spendMoney(-goldAmount);
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Reset to 0##gold")) {
            component.spendMoney(component.getMoney());
        }
        ImGui.textDisabled("Supports negative amounts");
    }

    private void buildItemOptions() {
        if (itemOptions != null) return;
        ItemRegistry registry = Assets.load(ITEM_REGISTRY_PATH, ItemRegistry.class);
        if (registry == null) {
            itemOptions = new String[0];
            return;
        }
        List<ItemDefinition> all = new ArrayList<>(registry.getAll());
        all.sort(Comparator.comparing(ItemDefinition::getItemId));
        itemOptions = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            itemOptions[i] = all.get(i).getItemId();
        }
    }

    @Override
    public void unbind() {
        super.unbind();
        // Don't reset combo state here — unbind is called every frame when multiple
        // custom inspectors exist on the same entity (registry tracks only one at a time).
        itemOptions = null;
    }
}
