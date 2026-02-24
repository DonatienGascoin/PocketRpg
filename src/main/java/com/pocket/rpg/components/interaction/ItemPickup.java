package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.Tooltip;
import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.save.ISaveable;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * World-placed item pickup. When the player interacts, the item is added to their
 * inventory and the pickup is destroyed or disabled.
 *
 * <p>Requires a {@code PersistentId} component on the same GameObject so the save
 * system tracks that the item was picked up. Without it, the item reappears on scene reload.
 */
@ComponentMeta(category = "Interaction")
public class ItemPickup extends InteractableComponent implements ISaveable {

    private static final Logger LOG = Log.getLogger(ItemPickup.class);

    @Getter @Setter
    @Tooltip("Item ID from the ItemRegistry (e.g., \"potion\", \"pokeball\")")
    private String itemId;

    @Getter @Setter
    @Tooltip("How many items to give on pickup")
    private int quantity = 1;

    @Getter @Setter
    @Tooltip("If true, destroy the GameObject on pickup. If false, disable it.")
    private boolean destroyOnPickup = true;

    /** Tracks whether this pickup was fully collected (persisted via ISaveable). */
    private transient boolean pickedUp = false;

    public ItemPickup() {
        gizmoShape = GizmoShape.CIRCLE;
        gizmoColor = GizmoColors.fromRGBA(0.2f, 1.0f, 0.4f, 0.9f); // Green
    }

    @Override
    public boolean canInteract(GameObject player) {
        if (pickedUp || quantity <= 0) return false;
        return super.canInteract(player);
    }

    @Override
    public void interact(GameObject player) {
        if (itemId == null || itemId.isEmpty()) {
            LOG.warn("ItemPickup on {} has no itemId set", gameObject.getName());
            return;
        }

        if (quantity <= 0) {
            LOG.warn("ItemPickup on {} has invalid quantity: {}", gameObject.getName(), quantity);
            return;
        }

        PlayerInventoryComponent inventory = player.getComponent(PlayerInventoryComponent.class);
        if (inventory == null) {
            LOG.warn("Player has no PlayerInventoryComponent");
            return;
        }

        int added = inventory.addItem(itemId, quantity);
        if (added == 0) {
            LOG.info("Bag is full — cannot pick up {}", itemId);
            return;
        }

        if (added < quantity) {
            // Partial add: keep the remainder in the pickup
            quantity -= added;
            LOG.info("Picked up {} x{} ({} remaining)", itemId, added, quantity);
            return;
        }

        LOG.info("Picked up {} x{}", itemId, quantity);
        pickedUp = true;

        if (destroyOnPickup) {
            gameObject.getScene().removeGameObject(gameObject);
        } else {
            gameObject.setEnabled(false);
        }
    }

    @Override
    protected void onInteractableStart() {
        if (pickedUp) {
            if (destroyOnPickup) {
                gameObject.getScene().removeGameObject(gameObject);
            } else {
                gameObject.setEnabled(false);
            }
        }
    }

    // --- ISaveable ---

    @Override
    public Map<String, Object> getSaveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("quantity", quantity);
        state.put("pickedUp", pickedUp);
        return state;
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;
        if (state.containsKey("quantity")) {
            quantity = ((Number) state.get("quantity")).intValue();
        }
        if (state.containsKey("pickedUp")) {
            pickedUp = (Boolean) state.get("pickedUp");
        }
    }

    @Override
    public boolean hasSaveableState() {
        return pickedUp || quantity != 1;
    }

    @Override
    public String getInteractionPrompt() {
        return "Pick up";
    }
}
