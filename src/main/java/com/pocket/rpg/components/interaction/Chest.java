package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.Tooltip;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import lombok.Getter;
import lombok.Setter;

/**
 * Interactive chest that can be opened by the player.
 * <p>
 * When the player interacts, the chest opens and logs its contents
 * to the console. Once opened, the chest cannot be interacted with again
 * unless {@link #reopenable} is set to true.
 * <p>
 * A TriggerZone is automatically added if not already present
 * (via {@link InteractableComponent}'s {@code @RequiredComponent}).
 */
@ComponentMeta(category = "Interaction")
public class Chest extends InteractableComponent {

    /**
     * The item or loot description inside the chest.
     */
    @Getter
    @Setter
    private String contents = "Gold Coins";

    /**
     * Whether the chest is currently open.
     */
    @Getter
    @Setter
    private boolean open = false;

    /**
     * Whether the chest can be opened again after being opened.
     */
    @Getter
    @Setter
    @Tooltip("If true, the chest can be opened multiple times")
    private boolean reopenable = false;

    public Chest() {
        gizmoShape = GizmoShape.SQUARE;
        gizmoColor = GizmoColors.fromRGBA(1.0f, 0.8f, 0.2f, 0.9f); // Gold
    }

    @Override
    public boolean canInteract(GameObject player) {
        if (!super.canInteract(player)) {
            return false;
        }
        return !open || reopenable;
    }

    @Override
    public void interact(GameObject player) {
        if (open && !reopenable) {
            return;
        }
        open = true;
        System.out.println("[Chest] " + gameObject.getName() + " opened! Contains: " + contents);
    }

    @Override
    public String getInteractionPrompt() {
        return "Open";
    }

    @Override
    public int getInteractionPriority() {
        return 5;
    }
}
