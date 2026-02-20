package com.pocket.rpg.pokemon;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * An individual move known by a Pokemon instance, tracking current PP.
 */
@Getter
public class MoveSlot {
    private String moveId;
    private int maxPp;
    private int currentPp;

    public MoveSlot() {}

    public MoveSlot(String moveId, int maxPp) {
        this.moveId = moveId;
        this.maxPp = maxPp;
        this.currentPp = maxPp;
    }

    public boolean hasPp() {
        return currentPp > 0;
    }

    public void usePp() {
        if (currentPp > 0) {
            currentPp--;
        }
    }

    public void restorePp(int amount) {
        currentPp = Math.min(currentPp + amount, maxPp);
    }

    public void restoreAllPp() {
        currentPp = maxPp;
    }

    public Map<String, Object> toSaveData() {
        Map<String, Object> data = new HashMap<>();
        data.put("moveId", moveId);
        data.put("maxPp", maxPp);
        data.put("currentPp", currentPp);
        return data;
    }

    @SuppressWarnings("unchecked")
    public static MoveSlot fromSaveData(Map<String, Object> data) {
        MoveSlot slot = new MoveSlot();
        slot.moveId = (String) data.get("moveId");
        slot.maxPp = ((Number) data.get("maxPp")).intValue();
        slot.currentPp = ((Number) data.get("currentPp")).intValue();
        return slot;
    }
}
