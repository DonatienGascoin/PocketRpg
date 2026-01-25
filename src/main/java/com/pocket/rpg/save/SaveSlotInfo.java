package com.pocket.rpg.save;

/**
 * Metadata about a save slot for UI display.
 * <p>
 * This is a lightweight record - actual save data is NOT loaded
 * until the player selects a slot.
 * <p>
 * Used by SaveManager.listSaves() to populate save/load menus.
 */
public record SaveSlotInfo(
        /**
         * Slot name (filename without .save extension).
         * Examples: "slot1", "autosave", "quicksave"
         */
        String slotName,

        /**
         * Display name set by player or auto-generated.
         * Examples: "Village - Level 5", "Auto Save", "Slot 1"
         */
        String displayName,

        /**
         * Unix timestamp when save was created.
         * Use to display "Saved: Jan 21, 2026 3:45 PM"
         */
        long timestamp,

        /**
         * Total play time in seconds.
         * Use to display "Play time: 12h 34m"
         */
        float playTime,

        /**
         * Scene name where player saved.
         * Use to display location or thumbnail.
         */
        String sceneName
) {
}
