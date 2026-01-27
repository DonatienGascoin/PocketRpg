package com.pocket.rpg.editor.events;

import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorState;

/**
 * Event published when an animator state is selected in the Animator Editor.
 *
 * @param state The selected state
 * @param controller The controller containing the state
 * @param onModified Callback to invoke when the state is modified (for undo capture)
 */
public record AnimatorStateSelectedEvent(
    AnimatorState state,
    AnimatorController controller,
    Runnable onModified
) implements EditorEvent {
}
