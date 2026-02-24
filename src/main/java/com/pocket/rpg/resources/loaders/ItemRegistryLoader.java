package com.pocket.rpg.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.items.*;
import com.pocket.rpg.resources.SpriteReference;

/**
 * Asset loader for ItemRegistry files ({@code .items.json}).
 * <p>
 * Loads item definitions from a JSON array. Supports hot-reload
 * by mutating the existing ItemRegistry instance in place.
 */
public class ItemRegistryLoader extends JsonAssetLoader<ItemRegistry> {

    private static final String[] EXTENSIONS = {".items.json"};

    // ========================================================================
    // JSON PARSING
    // ========================================================================

    @Override
    protected ItemRegistry fromJson(JsonObject json, String path) {
        ItemRegistry registry = new ItemRegistry();

        if (json.has("items") && json.get("items").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("items")) {
                registry.addItem(parseItem(elem.getAsJsonObject()));
            }
        }

        return registry;
    }

    private ItemDefinition parseItem(JsonObject json) {
        ItemDefinition.Builder b = ItemDefinition.builder(
                json.get("itemId").getAsString(),
                json.get("name").getAsString(),
                ItemCategory.valueOf(json.get("category").getAsString())
        );

        if (json.has("description") && !json.get("description").isJsonNull())
            b.description(json.get("description").getAsString());
        if (json.has("price")) b.price(json.get("price").getAsInt());
        if (json.has("sellPrice")) b.sellPrice(json.get("sellPrice").getAsInt());
        if (json.has("usableInBattle")) b.usableInBattle(json.get("usableInBattle").getAsBoolean());
        if (json.has("usableOutside")) b.usableOutside(json.get("usableOutside").getAsBoolean());
        if (json.has("consumable")) b.consumable(json.get("consumable").getAsBoolean());
        if (json.has("stackLimit")) b.stackLimit(json.get("stackLimit").getAsInt());
        if (json.has("spriteId") && !json.get("spriteId").isJsonNull()) {
            try {
                b.sprite(SpriteReference.fromPath(json.get("spriteId").getAsString()));
            } catch (Exception e) {
                System.err.println("ItemRegistryLoader: Failed to load sprite '"
                        + json.get("spriteId").getAsString() + "': " + e.getMessage());
            }
        }
        if (json.has("effect") && !json.get("effect").isJsonNull())
            b.effect(ItemEffect.valueOf(json.get("effect").getAsString()));
        if (json.has("effectValue")) b.effectValue(json.get("effectValue").getAsInt());
        if (json.has("teachesMove") && !json.get("teachesMove").isJsonNull())
            b.teachesMove(json.get("teachesMove").getAsString());
        if (json.has("targetStatus") && !json.get("targetStatus").isJsonNull())
            b.targetStatus(json.get("targetStatus").getAsString());

        return b.build();
    }

    // ========================================================================
    // JSON SERIALIZATION
    // ========================================================================

    @Override
    protected JsonObject toJson(ItemRegistry registry) {
        JsonObject root = new JsonObject();

        JsonArray itemsArray = new JsonArray();
        for (ItemDefinition def : registry.getAll()) {
            itemsArray.add(serializeItem(def));
        }
        root.add("items", itemsArray);

        return root;
    }

    private JsonObject serializeItem(ItemDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("itemId", def.getItemId());
        json.addProperty("name", def.getName());
        json.addProperty("description", def.getDescription());
        json.addProperty("category", def.getCategory().name());
        json.addProperty("price", def.getPrice());
        json.addProperty("sellPrice", def.getSellPrice());
        json.addProperty("usableInBattle", def.isUsableInBattle());
        json.addProperty("usableOutside", def.isUsableOutside());
        json.addProperty("consumable", def.isConsumable());
        json.addProperty("stackLimit", def.getStackLimit());
        json.addProperty("spriteId", SpriteReference.toPath(def.getSprite()));
        json.addProperty("effect", def.getEffect() != null ? def.getEffect().name() : null);
        json.addProperty("effectValue", def.getEffectValue());
        json.addProperty("teachesMove", def.getTeachesMove());
        if (def.getTargetStatus() != null) {
            json.addProperty("targetStatus", def.getTargetStatus());
        }
        return json;
    }

    // ========================================================================
    // JsonAssetLoader CONFIGURATION
    // ========================================================================

    @Override
    protected ItemRegistry createPlaceholder() {
        return new ItemRegistry();
    }

    @Override
    protected String[] extensions() {
        return EXTENSIONS;
    }

    @Override
    protected String iconCodepoint() {
        return MaterialIcons.LocalPharmacy;
    }

    @Override
    protected void copyInto(ItemRegistry existing, ItemRegistry fresh) {
        existing.copyFrom(fresh);
    }
}
