package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.Tooltip;
import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.components.pokemon.PlayerPartyComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.pokemon.Pokedex;
import com.pocket.rpg.pokemon.PokemonFactory;
import com.pocket.rpg.pokemon.PokemonInstance;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.PlayerData;
import lombok.Getter;
import lombok.Setter;

/**
 * Debug interactable that gives the player a starter Pokemon, some items, and money.
 * <p>
 * Place in a scene on an NPC or object. On interact, gives:
 * <ul>
 *   <li>A starter Pokemon (configurable species + level)</li>
 *   <li>5 Potions + 5 Poke Balls</li>
 *   <li>3000 money</li>
 * </ul>
 * Only gives once per interaction (re-interacting gives another Pokemon if party isn't full).
 */
@ComponentMeta(category = "Debug")
public class DebugStarterGiver extends InteractableComponent {

    private static final Logger LOG = Log.getLogger(DebugStarterGiver.class);
    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    @Getter @Setter
    @Tooltip("Species ID to give (e.g. pikachu, bulbasaur, charmander, squirtle)")
    private String speciesId = "pikachu";

    @Getter @Setter
    @Tooltip("Level of the starter Pokemon")
    private int level = 5;

    @Getter @Setter
    @Tooltip("Give items and money on first interact")
    private boolean giveStarterKit = true;

    private transient boolean gaveKit = false;

    public DebugStarterGiver() {
        gizmoShape = GizmoShape.DIAMOND;
        gizmoColor = GizmoColors.fromRGBA(0.3f, 1.0f, 0.3f, 0.9f);
    }

    @Override
    public void interact(GameObject player) {
        Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
        if (pokedex == null) {
            LOG.warn("Pokedex not found at {}", POKEDEX_PATH);
            return;
        }

        // Set player name if not set
        PlayerData data = PlayerData.load();
        if (data.playerName == null || data.playerName.isEmpty()) {
            data.playerName = "Red";
            data.save();
        }

        // Give Pokemon
        PlayerPartyComponent party = player.getComponent(PlayerPartyComponent.class);
        if (party != null) {
            String trainerName = PlayerData.load().playerName;
            PokemonInstance starter = PokemonFactory.createStarter(pokedex, speciesId, level, trainerName);
            if (party.addToParty(starter)) {
                LOG.info("[Debug] Gave {} Lv.{} to player (party size: {})",
                        speciesId, level, party.partySize());
            } else {
                LOG.info("[Debug] Party full! Cannot give {}", speciesId);
            }
        }

        // Give starter kit (items + money) once
        if (giveStarterKit && !gaveKit) {
            PlayerInventoryComponent inv = player.getComponent(PlayerInventoryComponent.class);
            if (inv != null) {
                inv.addItem("potion", 5);
                inv.addItem("pokeball", 5);
                inv.addMoney(3000);
                LOG.info("[Debug] Gave starter kit: 5 Potions, 5 Poke Balls, 3000 money");
            }
            gaveKit = true;
        }
    }

    @Override
    public String getInteractionPrompt() {
        return "Get Pokemon";
    }
}
