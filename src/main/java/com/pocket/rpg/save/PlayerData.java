package com.pocket.rpg.save;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.serialization.Serializer;

import java.util.List;

/**
 * Single source of truth for all cross-scene player state.
 *
 * <p>Stored as a JSON string in {@code SaveManager.globalState} under the "player" namespace,
 * serialized via Gson. {@code SaveManager.save()} writes globalState to disk — so as long as
 * this object is up-to-date in globalState, the save file will be correct.</p>
 *
 * <h3>Persistence patterns</h3>
 * <ul>
 *   <li><b>Write-through</b>: Components that own game state (party, inventory, storage)
 *       call {@link #save()} immediately on every mutation. This guarantees
 *       {@code SaveManager.save()} always captures the latest state.</li>
 *   <li><b>onBeforeSceneUnload</b>: Position data is flushed once at scene transition
 *       by {@code PlayerMovement}, because position changes every few frames and only
 *       matters at scene boundaries. This is the <b>only</b> exception to write-through.</li>
 * </ul>
 *
 * <h3>Adding new fields</h3>
 * <p>When a new plan adds fields to this class:</p>
 * <ol>
 *   <li>Add the field with a sensible default (null for objects, 0 for numbers)</li>
 *   <li>Gson handles missing fields gracefully — old save files deserialize with defaults</li>
 *   <li>The component that owns the field must follow write-through or document why not</li>
 * </ol>
 */
public class PlayerData {

    // --- Position context (scene-data-persistence) ---
    // Written by PlayerMovement via onBeforeSceneUnload
    public String lastOverworldScene;
    public int lastGridX;
    public int lastGridY;
    public Direction lastDirection;
    public boolean returningFromBattle;

    // --- Player identity (pokemon-ecs) ---
    // Set during new game, read by PokemonFactory for OT name
    public String playerName;

    // --- Money (item-inventory) ---
    // Written by PlayerInventoryComponent via write-through
    public int money;

    // --- Box names (pokemon-ecs) ---
    // Written by PokemonStorageComponent via write-through
    public List<String> boxNames;

    // --- Fields added by other plans when their types exist ---
    // team: List<PokemonInstanceData>           — added by pokemon-ecs
    // inventory: InventoryData                  — added by item-inventory
    // boxes: List<List<PokemonInstanceData>>    — added by pokemon-ecs

    // --- Persistence ---

    /**
     * Loads PlayerData from SaveManager globalState.
     * Returns a fresh instance with defaults if no data exists.
     */
    public static PlayerData load() {
        String json = SaveManager.getGlobal("player", "data", "");
        if (json.isEmpty()) return new PlayerData();
        return Serializer.fromJson(json, PlayerData.class);
    }

    /**
     * Saves this PlayerData to SaveManager globalState (in-memory).
     * Disk write only happens on explicit {@code SaveManager.save()} calls.
     */
    public void save() {
        SaveManager.setGlobal("player", "data", Serializer.toJson(this));
    }
}
