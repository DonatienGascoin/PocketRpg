package com.pocket.rpg.items;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * An item and its quantity within an inventory pocket.
 */
@Getter
public class ItemStack {
    private String itemId;
    private int quantity;

    public ItemStack() {}

    public ItemStack(String itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public boolean add(int amount) {
        if (amount <= 0) return false;
        quantity += amount;
        return true;
    }

    public boolean remove(int amount) {
        if (amount <= 0) return false;
        if (quantity < amount) return false;
        quantity -= amount;
        return true;
    }

    public boolean isEmpty() {
        return quantity <= 0;
    }

    public Map<String, Object> toSaveData() {
        Map<String, Object> data = new HashMap<>();
        data.put("itemId", itemId);
        data.put("quantity", quantity);
        return data;
    }

    public static ItemStack fromSaveData(Map<String, Object> data) {
        if (data == null) return null;
        ItemStack stack = new ItemStack();
        stack.itemId = (String) data.get("itemId");
        Number qty = (Number) data.get("quantity");
        stack.quantity = qty != null ? Math.max(0, qty.intValue()) : 0;
        return stack;
    }
}
