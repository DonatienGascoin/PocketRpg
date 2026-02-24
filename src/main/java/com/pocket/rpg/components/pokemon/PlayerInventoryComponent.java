package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.items.Inventory;
import com.pocket.rpg.items.ItemCategory;
import com.pocket.rpg.items.ItemRegistry;
import com.pocket.rpg.items.SortMode;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.PlayerData;
import lombok.Getter;

/**
 * Player's bag and money. Uses write-through persistence to {@link PlayerData}.
 *
 * <p>Every mutation immediately flushes to {@code PlayerData.save()}, which writes
 * to {@code SaveManager.globalState} in memory (cheap). This guarantees
 * {@code SaveManager.save()} always captures the latest state.
 */
@ComponentMeta(category = "Player")
public class PlayerInventoryComponent extends Component {

    public static final int MAX_MONEY = 999_999;
    private static final String ITEM_REGISTRY_PATH = "data/items/items.items.json";

    @Getter
    private transient Inventory inventory = new Inventory();
    @Getter
    private transient int money = 0;

    @Override
    protected void onStart() {
        PlayerData data = PlayerData.load();
        if (data.inventory != null) {
            inventory = Inventory.fromSaveData(data.inventory);
        }
        money = Math.max(0, Math.min(data.money, MAX_MONEY));
    }

    // --- Inventory delegation ---

    /**
     * Adds items to the player's inventory.
     *
     * @return the quantity actually added (0 if nothing could be added)
     */
    public int addItem(String itemId, int quantity) {
        ItemRegistry registry = getRegistry();
        if (registry == null) return 0;
        int added = inventory.addItem(itemId, quantity, registry);
        if (added > 0) flushToPlayerData();
        return added;
    }

    public boolean removeItem(String itemId, int quantity) {
        boolean result = inventory.removeItem(itemId, quantity);
        if (result) flushToPlayerData();
        return result;
    }

    public boolean hasItem(String itemId) {
        return inventory.hasItem(itemId);
    }

    public boolean hasItem(String itemId, int minQuantity) {
        return inventory.hasItem(itemId, minQuantity);
    }

    public int getItemCount(String itemId) {
        return inventory.getCount(itemId);
    }

    public void sortPocket(ItemCategory category, SortMode mode) {
        ItemRegistry registry = getRegistry();
        if (registry == null) return;
        inventory.sortPocket(category, mode, registry);
        flushToPlayerData();
    }

    // --- Money ---

    public void addMoney(int amount) {
        if (amount <= 0) return;
        money = (int) Math.min((long) money + amount, MAX_MONEY);
        flushToPlayerData();
    }

    public boolean spendMoney(int amount) {
        if (amount <= 0) return false;
        if (money < amount) return false;
        money -= amount;
        flushToPlayerData();
        return true;
    }

    // --- Persistence ---

    private void flushToPlayerData() {
        PlayerData data = PlayerData.load();
        data.inventory = inventory.toSaveData();
        data.money = money;
        data.save();
    }

    private ItemRegistry getRegistry() {
        return Assets.load(ITEM_REGISTRY_PATH, ItemRegistry.class);
    }
}
