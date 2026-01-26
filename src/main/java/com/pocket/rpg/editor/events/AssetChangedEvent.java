package com.pocket.rpg.editor.events;

/**
 * Event published when an asset's metadata is changed.
 * <p>
 * This includes:
 * <ul>
 *   <li>Sprite metadata (pivot, 9-slice, grid settings)</li>
 *   <li>Animation definitions</li>
 *   <li>Other asset metadata</li>
 * </ul>
 *
 * @param path The asset path that changed (e.g., "sprites/player.png")
 * @param changeType The type of change
 */
public record AssetChangedEvent(String path, ChangeType changeType) implements EditorEvent {

    /**
     * The type of change that occurred.
     */
    public enum ChangeType {
        /** Asset was created (new file or new metadata) */
        CREATED,
        /** Asset metadata was modified */
        MODIFIED,
        /** Asset was deleted */
        DELETED
    }
}
