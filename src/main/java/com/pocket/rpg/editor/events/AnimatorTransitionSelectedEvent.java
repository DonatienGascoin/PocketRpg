package com.pocket.rpg.editor.events;

import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorTransition;

/**
 * Event published when an animator transition is selected in the Animator Editor.
 *
 * @param transition The selected transition
 * @param controller The controller containing the transition
 * @param onModified Callback to invoke when the transition is modified (for undo capture)
 */
public record AnimatorTransitionSelectedEvent(
    AnimatorTransition transition,
    AnimatorController controller,
    Runnable onModified
) implements EditorEvent {
}
