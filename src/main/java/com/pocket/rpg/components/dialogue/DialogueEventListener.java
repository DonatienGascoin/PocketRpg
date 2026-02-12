package com.pocket.rpg.components.dialogue;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.animations.AnimationComponent;
import com.pocket.rpg.dialogue.DialogueEventStore;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.serialization.Required;
import lombok.Getter;
import lombok.Setter;

/**
 * Reacts to a custom dialogue event with a predefined action.
 * <p>
 * Place on any GameObject that should respond when a dialogue event fires.
 * For example, a door that disappears when "OPEN_DOOR" is fired, or an
 * NPC that becomes visible after "RESCUED_VILLAGER".
 * <p>
 * On scene load, if the event was already fired ({@link DialogueEventStore#hasFired}),
 * the listener reacts immediately in {@link #onStart()} — this provides
 * cross-scene persistence without extra save logic.
 * <p>
 * The {@code eventName} field is set via dropdown from the DialogueEvents
 * asset in the custom inspector (Plan 3). At runtime it is a plain String.
 * <p>
 * Design ref: §5 — Component-Based Event Listener
 */
@ComponentMeta(category = "Dialogue")
public class DialogueEventListener extends Component {

    private static final Logger LOG = Log.getLogger(DialogueEventListener.class);

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /** The custom event name this listener reacts to. */
    @Getter @Setter @Required
    private String eventName;

    /** What to do when the event fires. */
    @Getter @Setter
    private DialogueReaction reaction = DialogueReaction.DISABLE_GAME_OBJECT;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        if (eventName == null || eventName.isBlank()) {
            LOG.warn("DialogueEventListener on '%s' has no event name — skipping",
                    gameObject != null ? gameObject.getName() : "?");
            return;
        }

        // Cross-scene support: if event was already fired, react immediately
        if (DialogueEventStore.hasFired(eventName)) {
            onDialogueEvent();
        }
    }

    // ========================================================================
    // EVENT HANDLING
    // ========================================================================

    /**
     * Called when the target event fires. Executes the configured reaction.
     * <p>
     * Uses a switch expression (not statement) so that adding a new
     * {@link DialogueReaction} value causes a compile error here.
     */
    public void onDialogueEvent() {
        if (reaction == null) return;

        // Switch expression — compile error if a DialogueReaction case is missing.
        var _ = switch (reaction) {
            case ENABLE_GAME_OBJECT -> {
                if (gameObject != null) {
                    gameObject.setEnabled(true);
                }
                yield true;
            }
            case DISABLE_GAME_OBJECT -> {
                if (gameObject != null) {
                    gameObject.setEnabled(false);
                }
                yield true;
            }
            case DESTROY_GAME_OBJECT -> {
                if (gameObject != null && gameObject.getScene() != null) {
                    gameObject.getScene().removeGameObject(gameObject);
                }
                yield true;
            }
            case RUN_ANIMATION -> {
                AnimationComponent anim = getComponent(AnimationComponent.class);
                if (anim != null) {
                    anim.play();
                    yield true;
                } else {
                    LOG.warn("RUN_ANIMATION reaction on '%s' but no AnimationComponent found — skipping",
                            gameObject != null ? gameObject.getName() : "?");
                    yield false;
                }
            }
        };
    }

    /**
     * Returns the event name this listener reacts to.
     * Used by {@link PlayerDialogueManager} for scene-level dispatch matching.
     */
    public String getEventName() {
        return eventName;
    }
}
