package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.interaction.InteractableComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;

/**
 * Pokemon Center healing interaction.
 * <p>
 * When the player interacts, all party Pokemon are fully healed
 * (HP restored, status cured, all move PP restored).
 */
@ComponentMeta(category = "Interaction")
public class HealZoneComponent extends InteractableComponent {

    public HealZoneComponent() {
        gizmoShape = GizmoShape.CROSS;
        gizmoColor = GizmoColors.fromRGBA(1.0f, 0.4f, 0.6f, 0.9f);
    }

    @Override
    public void interact(GameObject player) {
        PlayerPartyComponent party = player.getComponent(PlayerPartyComponent.class);
        if (party != null) {
            party.healAll();
        }
    }

    @Override
    public String getInteractionPrompt() {
        return "Heal";
    }
}
