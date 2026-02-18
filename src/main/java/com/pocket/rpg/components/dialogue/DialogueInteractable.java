package com.pocket.rpg.components.dialogue;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.interaction.InteractableComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.dialogue.ConditionalDialogue;
import com.pocket.rpg.dialogue.Dialogue;
import com.pocket.rpg.dialogue.DialogueEventRef;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.serialization.Required;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NPC interactable that starts dialogue, with conditional dialogue selection.
 * <p>
 * Place on any NPC or object to make it start a dialogue when the player
 * interacts. Supports conditional dialogue selection: an ordered list of
 * {@link ConditionalDialogue} entries is evaluated top-to-bottom at
 * interaction time — first match wins. If none match, the default
 * {@link #dialogue} is used.
 * <p>
 * Directional interaction is inherited from {@link InteractableComponent}:
 * by default, the player must approach from below ({@code interactFrom = [DOWN]}).
 * Set {@code directionalInteraction = false} to allow interaction from any direction.
 * <p>
 * When {@link #hasConditionalDialogues} is false (the default), the
 * conditional dialogues list is hidden in the inspector, keeping the UI
 * clean for simple NPCs. Toggle it on to reveal the conditional section.
 * <p>
 * Design ref: §6 — DialogueComponent
 */
@ComponentMeta(category = "Dialogue")
public class DialogueInteractable extends InteractableComponent {

    private static final Logger LOG = Log.getLogger(DialogueInteractable.class);

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /** Whether conditional dialogue selection is enabled. When false, only the default dialogue is used. */
    @Getter @Setter
    private boolean hasConditionalDialogues = false;

    /** Ordered conditional dialogues — first match wins. Only evaluated when {@link #hasConditionalDialogues} is true. */
    @Getter @Setter
    private List<ConditionalDialogue> conditionalDialogues = new ArrayList<>();

    /** Default fallback dialogue when no conditions match. */
    @Getter @Setter @Required
    private Dialogue dialogue;

    /**
     * Variable values set in the inspector, shared across all dialogues
     * started by this component. These are the "static" variables in the
     * design's AUTO/STATIC/RUNTIME taxonomy — values known at edit time
     * (e.g. NPC_NAME, TRAINER_TYPE). Passed to
     * {@link PlayerDialogueManager#startDialogue} and merged with auto
     * and runtime variables.
     */
    @Getter @Setter
    private Map<String, String> variables = new HashMap<>();

    /**
     * Optional event fired when the entire conversation with this NPC ends
     * (after all chaining). This is where NPC-specific triggers live
     * (e.g. trainer sets onConversationEnd = START_BATTLE).
     */
    @Getter @Setter
    private DialogueEventRef onConversationEnd;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public DialogueInteractable() {
        gizmoShape = GizmoShape.CIRCLE;
        gizmoColor = GizmoColors.fromRGBA(0.8f, 0.4f, 1.0f, 0.9f); // Purple
    }

    // ========================================================================
    // INTERACTION
    // ========================================================================

    @Override
    public void interact(GameObject player) {
        PlayerDialogueManager manager = player.getComponent(PlayerDialogueManager.class);
        if (manager == null || manager.isActive()) {
            return;
        }

        Dialogue selected = selectDialogue();
        if (selected == null) {
            LOG.warn("DialogueInteractable on '%s' has no valid dialogue to start",
                    gameObject != null ? gameObject.getName() : "?");
            return;
        }

        manager.setSourceComponent(this);
        manager.startDialogue(selected, variables);
    }

    @Override
    public String getInteractionPrompt() {
        return "Talk";
    }

    // ========================================================================
    // CONDITIONAL SELECTION
    // ========================================================================

    /**
     * Evaluates conditional dialogues top-to-bottom, returns first match.
     * Falls back to the default {@link #dialogue} if none match.
     */
    Dialogue selectDialogue() {
        if (hasConditionalDialogues) {
            for (ConditionalDialogue cd : conditionalDialogues) {
                if (cd.allConditionsMet()) {
                    Dialogue d = cd.getDialogue();
                    if (d != null) {
                        return d;
                    }
                }
            }
        }
        return dialogue;
    }
}
