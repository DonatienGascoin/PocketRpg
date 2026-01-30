package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import lombok.Getter;
import lombok.Setter;

/**
 * Simple interactable sign/panel that logs a message to the console.
 * <p>
 * Attach this to any GameObject to make it interactable. When the player
 * presses the interact key nearby, the configured message is printed
 * to the console.
 * <p>
 * A TriggerZone is automatically added if not already present
 * (via {@link InteractableComponent}'s {@code @RequiredComponent}).
 */
@ComponentMeta(category = "Interaction")
public class Sign extends InteractableComponent {

    /**
     * The message displayed when the player interacts with this sign.
     */
    @Getter
    @Setter
    private String message = "Hello, world!";

    /**
     * The prompt verb shown to the player (e.g. "Read", "Examine").
     */
    @Getter
    @Setter
    private String prompt = "Read";

    public Sign() {
        gizmoShape = GizmoShape.DIAMOND;
        gizmoColor = GizmoColors.fromRGBA(0.4f, 0.9f, 1.0f, 0.9f); // Cyan
    }

    @Override
    public void interact(GameObject player) {
        System.out.println("[Sign] " + gameObject.getName() + ": " + message);
    }

    @Override
    public String getInteractionPrompt() {
        return prompt;
    }
}
