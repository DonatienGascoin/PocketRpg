package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.pokemon.Pokedex;
import com.pocket.rpg.pokemon.PokemonInstance;
import com.pocket.rpg.pokemon.PokemonInstanceData;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.PlayerData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PC box storage for Pokemon beyond the party. Uses write-through persistence to {@link PlayerData}.
 * <p>
 * Supports {@value #DEFAULT_BOX_COUNT} boxes with {@value #BOX_CAPACITY} slots each.
 */
@ComponentMeta(category = "Player")
public class PokemonStorageComponent extends Component {

    public static final int DEFAULT_BOX_COUNT = 8;
    public static final int BOX_CAPACITY = 30;
    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    private transient List<List<PokemonInstance>> boxes;
    private transient List<String> boxNames;

    @Override
    protected void onStart() {
        PlayerData data = PlayerData.load();
        if (data.boxes != null) {
            boxes = data.boxes.stream()
                    .map(box -> box.stream()
                            .map(PokemonInstanceData::toPokemonInstance)
                            .collect(Collectors.toCollection(ArrayList::new)))
                    .collect(Collectors.toCollection(ArrayList::new));
            boxNames = data.boxNames != null
                    ? new ArrayList<>(data.boxNames)
                    : initDefaultBoxNames();
            resolveSpeciesData();
        } else {
            boxes = initEmptyBoxes();
            boxNames = initDefaultBoxNames();
        }
    }

    /**
     * Deposits a Pokemon into a specific box.
     *
     * @return true if deposited, false if the box is full
     */
    public boolean deposit(PokemonInstance pokemon, int boxIndex) {
        List<PokemonInstance> box = boxes.get(boxIndex);
        if (box.size() >= BOX_CAPACITY) return false;
        box.add(pokemon);
        flushToPlayerData();
        return true;
    }

    /**
     * Deposits a Pokemon into the first box that has space.
     *
     * @return true if deposited, false if all boxes are full
     */
    public boolean depositToFirstAvailable(PokemonInstance pokemon) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).size() < BOX_CAPACITY) {
                boxes.get(i).add(pokemon);
                flushToPlayerData();
                return true;
            }
        }
        return false;
    }

    /**
     * Withdraws a Pokemon from the specified box and slot.
     *
     * @return the withdrawn Pokemon
     * @throws IndexOutOfBoundsException if indices are out of range
     */
    public PokemonInstance withdraw(int boxIndex, int slotIndex) {
        PokemonInstance removed = boxes.get(boxIndex).remove(slotIndex);
        flushToPlayerData();
        return removed;
    }

    /**
     * Returns an unmodifiable view of the specified box.
     */
    public List<PokemonInstance> getBox(int index) {
        return Collections.unmodifiableList(boxes.get(index));
    }

    public String getBoxName(int index) {
        return boxNames.get(index);
    }

    public void setBoxName(int index, String name) {
        boxNames.set(index, name);
        flushToPlayerData();
    }

    public int getBoxCount() {
        return boxes.size();
    }

    /**
     * Searches all boxes for a Pokemon with the given species ID.
     *
     * @return int array [boxIndex, slotIndex], or null if not found
     */
    public int[] findPokemon(String speciesId) {
        for (int b = 0; b < boxes.size(); b++) {
            List<PokemonInstance> box = boxes.get(b);
            for (int s = 0; s < box.size(); s++) {
                if (speciesId.equals(box.get(s).getSpecies())) {
                    return new int[]{b, s};
                }
            }
        }
        return null;
    }

    /**
     * Returns the total number of Pokemon stored across all boxes.
     */
    public int getTotalStored() {
        int total = 0;
        for (List<PokemonInstance> box : boxes) {
            total += box.size();
        }
        return total;
    }

    // --- Persistence ---

    private void flushToPlayerData() {
        PlayerData data = PlayerData.load();
        data.boxes = boxes.stream()
                .map(box -> box.stream()
                        .map(PokemonInstanceData::fromPokemonInstance)
                        .toList())
                .toList();
        data.boxNames = new ArrayList<>(boxNames);
        data.save();
    }

    /**
     * Sets speciesData on all stored Pokemon from the Pokedex.
     * Required for stat calculations if Pokemon are withdrawn and used.
     */
    private void resolveSpeciesData() {
        Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
        if (pokedex == null) return;
        for (List<PokemonInstance> box : boxes) {
            for (PokemonInstance p : box) {
                if (p.getSpecies() != null) {
                    var species = pokedex.getSpecies(p.getSpecies());
                    if (species != null) {
                        p.setSpeciesData(species);
                    }
                }
            }
        }
    }

    // --- Initialization ---

    private static List<List<PokemonInstance>> initEmptyBoxes() {
        List<List<PokemonInstance>> result = new ArrayList<>(DEFAULT_BOX_COUNT);
        for (int i = 0; i < DEFAULT_BOX_COUNT; i++) {
            result.add(new ArrayList<>());
        }
        return result;
    }

    private static List<String> initDefaultBoxNames() {
        List<String> names = new ArrayList<>(DEFAULT_BOX_COUNT);
        for (int i = 0; i < DEFAULT_BOX_COUNT; i++) {
            names.add("Box " + (i + 1));
        }
        return names;
    }
}
