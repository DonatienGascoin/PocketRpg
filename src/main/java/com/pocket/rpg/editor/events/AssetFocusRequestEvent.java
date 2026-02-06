package com.pocket.rpg.editor.events;

/**
 * Event published when the Asset Browser should navigate to and highlight an asset.
 * <p>
 * Typically fired when clicking an asset name in the inspector.
 *
 * @param path The asset path to focus on (e.g., "sprites/player.png" or "sheets/player.spritesheet#3")
 */
public record AssetFocusRequestEvent(String path) implements EditorEvent {
}
