package com.pocket.rpg.components.dialogue;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.animations.AnimationComponent;
import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.components.pokemon.PlayerPartyComponent;
import com.pocket.rpg.components.pokemon.PokemonStorageComponent;
import com.pocket.rpg.dialogue.DialogueEventStore;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.pokemon.Pokedex;
import com.pocket.rpg.pokemon.PokemonFactory;
import com.pocket.rpg.pokemon.PokemonInstance;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.PlayerData;
import com.pocket.rpg.serialization.Required;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /** The custom event name this listener reacts to. */
    @Getter @Setter @Required
    private String eventName;

    /** What to do when the event fires. */
    @Getter @Setter
    private DialogueReaction reaction = DialogueReaction.DISABLE_GAME_OBJECT;

    // GIVE_ITEM fields (only used when reaction == GIVE_ITEM)
    @Getter @Setter private String itemId;
    @Getter @Setter private int quantity = 1;

    // GIVE_POKEMON fields (only used when reaction == GIVE_POKEMON)
    @Getter @Setter private String speciesId;
    @Getter @Setter private int level = 5;

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
            case GIVE_ITEM -> {
                // Rewards are persisted by PlayerData, not by event replay.
                // Skip if this is an auto-replay from onStart().
                if (DialogueEventStore.hasFired(eventName)) {
                    yield true;
                }
                PlayerInventoryComponent inv = findPlayerComponent(PlayerInventoryComponent.class);
                if (inv == null) {
                    LOG.warn("GIVE_ITEM on '%s' — no PlayerInventoryComponent in scene",
                            gameObject != null ? gameObject.getName() : "?");
                    yield false;
                }
                int added = inv.addItem(itemId, quantity);
                if (added == 0) {
                    LOG.warn("GIVE_ITEM failed — could not add item '%s' (bag full or invalid)",
                            itemId);
                }
                yield added > 0;
            }
            case GIVE_POKEMON -> {
                if (DialogueEventStore.hasFired(eventName)) {
                    yield true;
                }
                PlayerPartyComponent party = findPlayerComponent(PlayerPartyComponent.class);
                if (party == null) {
                    LOG.warn("GIVE_POKEMON on '%s' — no PlayerPartyComponent in scene",
                            gameObject != null ? gameObject.getName() : "?");
                    yield false;
                }
                Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
                if (pokedex == null) {
                    LOG.warn("GIVE_POKEMON on '%s' — could not load Pokedex",
                            gameObject != null ? gameObject.getName() : "?");
                    yield false;
                }
                PlayerData playerData = PlayerData.load();
                PokemonInstance pokemon = PokemonFactory.createStarter(
                        pokedex, speciesId, level, playerData.playerName);

                boolean added = party.addToParty(pokemon);
                if (!added) {
                    PokemonStorageComponent storage = findPlayerComponent(PokemonStorageComponent.class);
                    if (storage != null) {
                        added = storage.depositToFirstAvailable(pokemon);
                    }
                }
                if (!added) {
                    LOG.warn("GIVE_POKEMON failed — party full and no PC storage available for '%s'",
                            speciesId);
                }
                yield added;
            }
            case HEAL_PARTY -> {
                PlayerPartyComponent party = findPlayerComponent(PlayerPartyComponent.class);
                if (party == null) {
                    LOG.warn("HEAL_PARTY on '%s' — no PlayerPartyComponent in scene",
                            gameObject != null ? gameObject.getName() : "?");
                    yield false;
                }
                party.healAll();
                LOG.info("Healed %d Pokemon in party", party.getParty().size());
                yield true;
            }
        };
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Finds a component of the given type anywhere in the scene.
     * Used to locate player components (inventory, party, storage) for reward reactions.
     */
    private <T> T findPlayerComponent(Class<T> type) {
        if (gameObject == null || gameObject.getScene() == null) return null;
        List<T> found = gameObject.getScene().getComponentsImplementing(type);
        return found.isEmpty() ? null : found.getFirst();
    }

    /**
     * Returns the event name this listener reacts to.
     * Used by {@link PlayerDialogueManager} for scene-level dispatch matching.
     */
    public String getEventName() {
        return eventName;
    }
}
