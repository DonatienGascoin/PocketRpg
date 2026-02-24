package com.pocket.rpg.items;

import com.pocket.rpg.rendering.resources.Sprite;
import lombok.Getter;
import lombok.Setter;

/**
 * Template for an item. Loaded from JSON via the ItemRegistry.
 *
 * <p>Use {@link #builder(String, String, ItemCategory)} for programmatic construction.
 * The no-arg constructor and setters exist for Gson deserialization and editor use.
 */
@Getter
@Setter
public class ItemDefinition {
    private String itemId;
    private String name;
    private String description;
    private ItemCategory category;
    private int price;
    private int sellPrice;
    private boolean usableInBattle;
    private boolean usableOutside;
    private boolean consumable;
    private int stackLimit;
    private Sprite sprite;
    private ItemEffect effect;
    private int effectValue;
    private String teachesMove;

    /**
     * For {@link ItemEffect#HEAL_STATUS}: the name of the {@code StatusCondition}
     * to cure (e.g. {@code "POISON"}, {@code "BURN"}). Null or empty means cure all.
     * This replaces the fragile ordinal-based encoding via {@code effectValue}.
     */
    private String targetStatus;

    /** No-arg constructor for Gson deserialization. */
    public ItemDefinition() {}

    /** Creates a builder with the three required fields. */
    public static Builder builder(String itemId, String name, ItemCategory category) {
        return new Builder(itemId, name, category);
    }

    public static class Builder {
        private final ItemDefinition def = new ItemDefinition();

        private Builder(String itemId, String name, ItemCategory category) {
            def.itemId = itemId;
            def.name = name;
            def.category = category;
            def.stackLimit = 99; // sensible default
            def.effect = ItemEffect.NONE;
        }

        public Builder description(String description) { def.description = description; return this; }
        public Builder price(int price) { def.price = price; return this; }
        public Builder sellPrice(int sellPrice) { def.sellPrice = sellPrice; return this; }
        public Builder usableInBattle(boolean usableInBattle) { def.usableInBattle = usableInBattle; return this; }
        public Builder usableOutside(boolean usableOutside) { def.usableOutside = usableOutside; return this; }
        public Builder consumable(boolean consumable) { def.consumable = consumable; return this; }
        public Builder stackLimit(int stackLimit) { def.stackLimit = stackLimit; return this; }
        public Builder sprite(Sprite sprite) { def.sprite = sprite; return this; }
        public Builder effect(ItemEffect effect) { def.effect = effect; return this; }
        public Builder effectValue(int effectValue) { def.effectValue = effectValue; return this; }
        public Builder teachesMove(String teachesMove) { def.teachesMove = teachesMove; return this; }
        public Builder targetStatus(String targetStatus) { def.targetStatus = targetStatus; return this; }

        public ItemDefinition build() {
            return def;
        }
    }
}
