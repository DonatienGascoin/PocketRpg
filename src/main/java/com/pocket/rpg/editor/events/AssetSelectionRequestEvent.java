package com.pocket.rpg.editor.events;

/**
 * Event published when a panel requests selecting an asset.
 * <p>
 * Delegates to {@link com.pocket.rpg.editor.EditorSelectionManager#selectAsset(String, Class)},
 * which updates the global selection state. Any subscriber (inspector, asset browser, etc.)
 * can react to the resulting selection change.
 *
 * @param path The asset path (e.g., "dialogues/variables.dialogue-vars.json")
 * @param type The asset class
 */
public record AssetSelectionRequestEvent(String path, Class<?> type) implements EditorEvent {
}
