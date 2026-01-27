package com.pocket.rpg.editor.events;

/**
 * Event published when the animator selection is cleared.
 * This occurs when:
 * - Switching to a different controller
 * - Closing the Animator Editor
 * - Clicking on empty canvas
 */
public record AnimatorSelectionClearedEvent() implements EditorEvent {
}
