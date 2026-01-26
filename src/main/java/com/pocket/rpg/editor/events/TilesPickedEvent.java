package com.pocket.rpg.editor.events;

import com.pocket.rpg.editor.tileset.TileSelection;

/**
 * Event published when tiles are picked from the canvas using the picker tool.
 *
 * @param selection The picked tile selection (single tile or multi-tile pattern)
 */
public record TilesPickedEvent(TileSelection selection) implements EditorEvent {
}
