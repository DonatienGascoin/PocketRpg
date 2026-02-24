package com.pocket.rpg.items;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemStackTest {

    @Test
    @DisplayName("add increases quantity")
    void addIncreasesQuantity() {
        ItemStack stack = new ItemStack("potion", 3);
        stack.add(2);
        assertEquals(5, stack.getQuantity());
    }

    @Test
    @DisplayName("add returns true on success")
    void addReturnsTrue() {
        ItemStack stack = new ItemStack("potion", 3);
        assertTrue(stack.add(2));
    }

    @Test
    @DisplayName("add with zero or negative returns false")
    void addNonPositiveReturnsFalse() {
        ItemStack stack = new ItemStack("potion", 3);
        assertFalse(stack.add(0));
        assertFalse(stack.add(-5));
        assertEquals(3, stack.getQuantity());
    }

    @Test
    @DisplayName("remove decreases quantity")
    void removeDecreasesQuantity() {
        ItemStack stack = new ItemStack("potion", 5);
        assertTrue(stack.remove(2));
        assertEquals(3, stack.getQuantity());
    }

    @Test
    @DisplayName("remove returns false when insufficient")
    void removeInsufficientReturnsFalse() {
        ItemStack stack = new ItemStack("potion", 2);
        assertFalse(stack.remove(5));
        assertEquals(2, stack.getQuantity());
    }

    @Test
    @DisplayName("remove with zero or negative returns false")
    void removeNegativeReturnsFalse() {
        ItemStack stack = new ItemStack("potion", 5);
        assertFalse(stack.remove(0));
        assertFalse(stack.remove(-3));
        assertEquals(5, stack.getQuantity());
    }

    @Test
    @DisplayName("isEmpty when quantity reaches 0")
    void isEmptyAtZero() {
        ItemStack stack = new ItemStack("potion", 1);
        stack.remove(1);
        assertTrue(stack.isEmpty());
    }

    @Test
    @DisplayName("serialization round-trip")
    void serializationRoundTrip() {
        ItemStack stack = new ItemStack("pokeball", 10);
        Map<String, Object> data = stack.toSaveData();
        ItemStack restored = ItemStack.fromSaveData(data);
        assertEquals("pokeball", restored.getItemId());
        assertEquals(10, restored.getQuantity());
    }

    @Test
    @DisplayName("fromSaveData with null map returns null")
    void fromNullMap() {
        assertNull(ItemStack.fromSaveData(null));
    }

    @Test
    @DisplayName("fromSaveData with missing quantity defaults to 0")
    void fromMissingQuantity() {
        Map<String, Object> data = new HashMap<>();
        data.put("itemId", "potion");
        ItemStack stack = ItemStack.fromSaveData(data);
        assertEquals("potion", stack.getItemId());
        assertEquals(0, stack.getQuantity());
    }

    @Test
    @DisplayName("fromSaveData handles Gson Double deserialization")
    void fromGsonDouble() {
        // Gson deserializes numbers in Map<String, Object> as Double
        Map<String, Object> data = new HashMap<>();
        data.put("itemId", "potion");
        data.put("quantity", 15.0);
        ItemStack stack = ItemStack.fromSaveData(data);
        assertEquals(15, stack.getQuantity());
    }

    @Test
    @DisplayName("fromSaveData clamps negative quantity to 0")
    void fromNegativeQuantity() {
        Map<String, Object> data = new HashMap<>();
        data.put("itemId", "potion");
        data.put("quantity", -5);
        ItemStack stack = ItemStack.fromSaveData(data);
        assertEquals(0, stack.getQuantity());
    }
}
