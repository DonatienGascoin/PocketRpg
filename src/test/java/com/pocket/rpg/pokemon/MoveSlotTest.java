package com.pocket.rpg.pokemon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MoveSlotTest {

    @Test
    @DisplayName("new slot starts at max PP")
    void startsAtMaxPp() {
        MoveSlot slot = new MoveSlot("tackle", 35);
        assertEquals(35, slot.getCurrentPp());
        assertEquals(35, slot.getMaxPp());
        assertTrue(slot.hasPp());
    }

    @Test
    @DisplayName("usePp decrements by 1")
    void usePpDecrements() {
        MoveSlot slot = new MoveSlot("tackle", 35);
        slot.usePp();
        assertEquals(34, slot.getCurrentPp());
    }

    @Test
    @DisplayName("hasPp returns false at 0")
    void hasPpFalseAtZero() {
        MoveSlot slot = new MoveSlot("tackle", 1);
        slot.usePp();
        assertFalse(slot.hasPp());
        assertEquals(0, slot.getCurrentPp());
    }

    @Test
    @DisplayName("usePp does not go below 0")
    void usePpFloorAtZero() {
        MoveSlot slot = new MoveSlot("tackle", 1);
        slot.usePp();
        slot.usePp(); // already at 0
        assertEquals(0, slot.getCurrentPp());
    }

    @Test
    @DisplayName("restorePp adds PP capped at max")
    void restorePpCapped() {
        MoveSlot slot = new MoveSlot("tackle", 35);
        slot.usePp();
        slot.usePp();
        slot.restorePp(1);
        assertEquals(34, slot.getCurrentPp());

        slot.restorePp(100); // over max
        assertEquals(35, slot.getCurrentPp());
    }

    @Test
    @DisplayName("restoreAllPp resets to max")
    void restoreAllPp() {
        MoveSlot slot = new MoveSlot("tackle", 35);
        for (int i = 0; i < 10; i++) slot.usePp();
        slot.restoreAllPp();
        assertEquals(35, slot.getCurrentPp());
    }

    @Test
    @DisplayName("serialization round-trip preserves all fields")
    void serializationRoundTrip() {
        MoveSlot slot = new MoveSlot("thunderbolt", 15);
        slot.usePp();
        slot.usePp();

        Map<String, Object> data = slot.toSaveData();
        MoveSlot restored = MoveSlot.fromSaveData(data);

        assertEquals("thunderbolt", restored.getMoveId());
        assertEquals(15, restored.getMaxPp());
        assertEquals(13, restored.getCurrentPp());
    }
}
