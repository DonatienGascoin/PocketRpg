package com.pocket.rpg.editor.events;

/**
 * Event requesting to open the Animation Editor for a specific animation.
 *
 * @param animationPath The path to the animation to edit, or null to just open the panel
 */
public record OpenAnimationEditorEvent(String animationPath) implements EditorEvent {
}
