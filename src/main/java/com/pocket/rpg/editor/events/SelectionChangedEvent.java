package com.pocket.rpg.editor.events;

import com.pocket.rpg.editor.EditorSelectionManager.SelectionType;

/**
 * Event published when the editor selection changes.
 * <p>
 * Consumers can query {@link com.pocket.rpg.editor.EditorSelectionManager} for
 * detailed selection information (selected entities, layer index, asset path, etc.).
 *
 * @param selectionType The new selection type
 * @param previousType The previous selection type (can be same if selection changed within same type)
 */
public record SelectionChangedEvent(SelectionType selectionType, SelectionType previousType) implements EditorEvent {
}
