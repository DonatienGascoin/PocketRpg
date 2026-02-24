package com.pocket.rpg.items;

import com.pocket.rpg.components.interaction.ItemPickup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ItemPickup}'s ISaveable contract.
 * Verifies that pickup state (quantity, pickedUp) survives save/load cycles.
 */
class ItemPickupSaveableTest {

    private ItemPickup createPickup(String itemId, int quantity) {
        ItemPickup pickup = new ItemPickup();
        pickup.setItemId(itemId);
        pickup.setQuantity(quantity);
        return pickup;
    }

    @Test
    @DisplayName("hasSaveableState returns false for default state")
    void defaultStateNotSaveable() {
        ItemPickup pickup = createPickup("potion", 1);
        assertFalse(pickup.hasSaveableState());
    }

    @Test
    @DisplayName("hasSaveableState returns true when quantity differs from default")
    void modifiedQuantityIsSaveable() {
        ItemPickup pickup = createPickup("potion", 5);
        assertTrue(pickup.hasSaveableState());
    }

    @Test
    @DisplayName("getSaveState captures quantity")
    void getSaveStateCapturesQuantity() {
        ItemPickup pickup = createPickup("potion", 3);
        Map<String, Object> state = pickup.getSaveState();
        assertEquals(3, state.get("quantity"));
        assertEquals(false, state.get("pickedUp"));
    }

    @Test
    @DisplayName("loadSaveState restores quantity")
    void loadSaveStateRestoresQuantity() {
        ItemPickup pickup = createPickup("potion", 1);
        pickup.loadSaveState(Map.of("quantity", 3, "pickedUp", false));
        assertEquals(3, pickup.getQuantity());
    }

    @Test
    @DisplayName("loadSaveState restores pickedUp flag")
    void loadSaveStateRestoresPickedUp() {
        ItemPickup pickup = createPickup("potion", 1);
        pickup.loadSaveState(Map.of("quantity", 0, "pickedUp", true));
        assertTrue(pickup.hasSaveableState());
        Map<String, Object> state = pickup.getSaveState();
        assertEquals(true, state.get("pickedUp"));
    }

    @Test
    @DisplayName("loadSaveState handles null gracefully")
    void loadSaveStateHandlesNull() {
        ItemPickup pickup = createPickup("potion", 5);
        assertDoesNotThrow(() -> pickup.loadSaveState(null));
        assertEquals(5, pickup.getQuantity());
    }

    @Test
    @DisplayName("loadSaveState handles missing keys gracefully")
    void loadSaveStateHandlesMissingKeys() {
        ItemPickup pickup = createPickup("potion", 5);
        pickup.loadSaveState(Map.of());
        assertEquals(5, pickup.getQuantity());
    }

    @Test
    @DisplayName("round-trip preserves partial pickup state")
    void roundTrip() {
        ItemPickup pickup = createPickup("potion", 10);
        // Simulate partial pickup: quantity reduced to 3
        pickup.setQuantity(3);

        Map<String, Object> saved = pickup.getSaveState();

        ItemPickup restored = createPickup("potion", 10); // scene default
        restored.loadSaveState(saved);

        assertEquals(3, restored.getQuantity());
    }
}
