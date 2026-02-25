package com.pocket.rpg.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.shop.ShopInventory;
import com.pocket.rpg.shop.ShopRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for ShopRegistry files ({@code .shops.json}).
 * <p>
 * Loads shop definitions from a JSON array. Supports hot-reload
 * by mutating the existing ShopRegistry instance in place.
 */
public class ShopRegistryLoader extends JsonAssetLoader<ShopRegistry> {

    private static final String[] EXTENSIONS = {".shops.json"};

    // ========================================================================
    // JSON PARSING
    // ========================================================================

    @Override
    protected ShopRegistry fromJson(JsonObject json, String path) {
        ShopRegistry registry = new ShopRegistry();

        if (json.has("shops") && json.get("shops").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("shops")) {
                registry.addShop(parseShop(elem.getAsJsonObject()));
            }
        }

        return registry;
    }

    private ShopInventory parseShop(JsonObject json) {
        String shopId = json.get("shopId").getAsString();
        String shopName = json.has("shopName") ? json.get("shopName").getAsString() : shopId;

        List<ShopInventory.ShopEntry> entries = new ArrayList<>();
        if (json.has("items") && json.get("items").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("items")) {
                entries.add(parseEntry(elem.getAsJsonObject()));
            }
        }

        return new ShopInventory(shopId, shopName, entries);
    }

    private ShopInventory.ShopEntry parseEntry(JsonObject json) {
        String itemId = json.get("itemId").getAsString();
        int stock = json.has("stock") ? json.get("stock").getAsInt() : -1;
        return new ShopInventory.ShopEntry(itemId, stock);
    }

    // ========================================================================
    // JSON SERIALIZATION
    // ========================================================================

    @Override
    protected JsonObject toJson(ShopRegistry registry) {
        JsonObject root = new JsonObject();

        JsonArray shopsArray = new JsonArray();
        for (ShopInventory shop : registry.getAll()) {
            shopsArray.add(serializeShop(shop));
        }
        root.add("shops", shopsArray);

        return root;
    }

    private JsonObject serializeShop(ShopInventory shop) {
        JsonObject json = new JsonObject();
        json.addProperty("shopId", shop.getShopId());
        json.addProperty("shopName", shop.getShopName());

        JsonArray itemsArray = new JsonArray();
        for (ShopInventory.ShopEntry entry : shop.getItems()) {
            itemsArray.add(serializeEntry(entry));
        }
        json.add("items", itemsArray);

        return json;
    }

    private JsonObject serializeEntry(ShopInventory.ShopEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("itemId", entry.getItemId());
        json.addProperty("stock", entry.getStock());
        return json;
    }

    // ========================================================================
    // JsonAssetLoader CONFIGURATION
    // ========================================================================

    @Override
    protected ShopRegistry createPlaceholder() {
        return new ShopRegistry();
    }

    @Override
    protected String[] extensions() {
        return EXTENSIONS;
    }

    @Override
    protected String iconCodepoint() {
        return MaterialIcons.Store;
    }

    @Override
    protected void copyInto(ShopRegistry existing, ShopRegistry fresh) {
        existing.copyFrom(fresh);
    }
}
