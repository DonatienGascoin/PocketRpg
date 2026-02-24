package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.pokemon.PlayerPartyComponent;
import com.pocket.rpg.components.pokemon.TrainerComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.pokemon.PokemonInstance;
import com.pocket.rpg.scenes.transitions.SceneTransition;

import java.util.List;

/**
 * Debug interactable that simulates entering a trainer battle.
 * <p>
 * Place on the same GameObject as a {@link TrainerComponent}. On interact:
 * <ol>
 *   <li>Logs the player's party and the trainer's party to the console</li>
 *   <li>Transitions to the Battle scene (where BattlePlayer handles the round-trip)</li>
 * </ol>
 * If the trainer is already defeated, logs that and does nothing.
 */
@ComponentMeta(category = "Debug")
public class DebugBattleTrigger extends InteractableComponent {

    private static final Logger LOG = Log.getLogger(DebugBattleTrigger.class);

    public DebugBattleTrigger() {
        gizmoShape = GizmoShape.CROSS;
        gizmoColor = GizmoColors.fromRGBA(1.0f, 0.3f, 0.3f, 0.9f);
    }

    @Override
    public boolean canInteract(GameObject player) {
        if (!super.canInteract(player)) return false;
        TrainerComponent trainer = getComponent(TrainerComponent.class);
        return trainer != null && !trainer.isDefeated();
    }

    @Override
    public void interact(GameObject player) {
        TrainerComponent trainer = getComponent(TrainerComponent.class);
        if (trainer == null) {
            LOG.warn("DebugBattleTrigger on %s has no TrainerComponent", gameObject.getName());
            return;
        }

        if (trainer.isDefeated()) {
            LOG.info("[Battle] %s is already defeated", trainer.getTrainerName());
            return;
        }

        // Log player party
        PlayerPartyComponent playerParty = player.getComponent(PlayerPartyComponent.class);
        LOG.info("=== TRAINER BATTLE: vs %s ===", trainer.getTrainerName());

        if (playerParty != null && playerParty.partySize() > 0) {
            LOG.info("  Player party (%d):", playerParty.partySize());
            for (PokemonInstance p : playerParty.getParty()) {
                LOG.info("    - %s Lv.%d  HP: %d/%s",
                        p.getSpecies(), p.getLevel(), p.getCurrentHp(),
                        p.getSpeciesData() != null ? String.valueOf(p.calcMaxHp()) : "?");
            }
        } else {
            LOG.info("  Player party: (empty)");
        }

        // Log trainer party
        List<PokemonInstance> trainerParty = trainer.getParty();
        LOG.info("  %s's party (%d):", trainer.getTrainerName(), trainerParty.size());
        for (PokemonInstance p : trainerParty) {
            LOG.info("    - %s Lv.%d  HP: %d/%d",
                    p.getSpecies(), p.getLevel(), p.getCurrentHp(), p.calcMaxHp());
        }
        LOG.info("  Prize money: %d", trainer.getDefeatMoney());

        // Mark defeated (simulated instant win) and award money
        trainer.markDefeated();
        if (playerParty != null) {
            var inv = player.getComponent(
                    com.pocket.rpg.components.pokemon.PlayerInventoryComponent.class);
            if (inv != null) {
                inv.addMoney(trainer.getDefeatMoney());
                LOG.info("  Awarded %d money to player", trainer.getDefeatMoney());
            }
        }
        LOG.info("  %s defeated!", trainer.getTrainerName());

        // Transition to battle scene for the visual round-trip
        LOG.info("  Transitioning to Battle scene...");
        SceneTransition.loadScene("Battle");
    }

    @Override
    public String getInteractionPrompt() {
        return "Battle";
    }
}
