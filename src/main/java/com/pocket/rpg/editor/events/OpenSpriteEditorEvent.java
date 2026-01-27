package com.pocket.rpg.editor.events;

/**
 * Event requesting to open the Sprite Editor for a specific texture.
 *
 * @param texturePath The path to the texture to edit, or null to open without a specific texture
 */
public record OpenSpriteEditorEvent(String texturePath) implements EditorEvent {
}
