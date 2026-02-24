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

/**
 * Player's active team of up to 6 Pokemon. Uses write-through persistence to {@link PlayerData}.
 * <p>
 * Every mutation immediately flushes to {@code PlayerData.save()}, which writes
 * to {@code SaveManager.globalState} in memory (cheap). This guarantees
 * {@code SaveManager.save()} always captures the latest state.
 */
@ComponentMeta(category = "Player")
public class PlayerPartyComponent extends Component {

    public static final int MAX_PARTY_SIZE = 6;
    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    private transient List<PokemonInstance> party = new ArrayList<>();

    @Override
    protected void onStart() {
        PlayerData data = PlayerData.load();
        if (data.team != null) {
            party.clear();
            for (PokemonInstanceData pid : data.team) {
                party.add(pid.toPokemonInstance());
            }
            resolveSpeciesData();
        }
    }

    /**
     * Returns an unmodifiable view of the party.
     */
    public List<PokemonInstance> getParty() {
        return Collections.unmodifiableList(party);
    }

    /**
     * Adds a Pokemon to the party.
     *
     * @return true if added, false if party is full (size >= 6)
     */
    public boolean addToParty(PokemonInstance pokemon) {
        if (party.size() >= MAX_PARTY_SIZE) return false;
        party.add(pokemon);
        flushToPlayerData();
        return true;
    }

    /**
     * Removes a Pokemon from the party at the given index.
     *
     * @return the removed Pokemon
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public PokemonInstance removeFromParty(int index) {
        PokemonInstance removed = party.remove(index);
        flushToPlayerData();
        return removed;
    }

    /**
     * Swaps two Pokemon in the party.
     */
    public void swapPositions(int i, int j) {
        Collections.swap(party, i, j);
        flushToPlayerData();
    }

    /**
     * Returns the first alive (HP > 0) party member, or null if all fainted.
     */
    public PokemonInstance getFirstAlive() {
        for (PokemonInstance p : party) {
            if (p.isAlive()) return p;
        }
        return null;
    }

    /**
     * Returns true if any party member is alive.
     */
    public boolean isTeamAlive() {
        return party.stream().anyMatch(PokemonInstance::isAlive);
    }

    public int partySize() {
        return party.size();
    }

    /**
     * Fully heals all party Pokemon: restores HP, cures status, restores all move PP.
     */
    public void healAll() {
        for (PokemonInstance p : party) {
            p.healFull();
        }
        flushToPlayerData();
    }

    private void flushToPlayerData() {
        PlayerData data = PlayerData.load();
        data.team = party.stream()
                .map(PokemonInstanceData::fromPokemonInstance)
                .toList();
        data.save();
    }

    /**
     * Sets speciesData on all party members from the Pokedex.
     * Required for stat calculations (healFull, calcMaxHp, etc.).
     */
    private void resolveSpeciesData() {
        Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
        if (pokedex == null) return;
        for (PokemonInstance p : party) {
            if (p.getSpecies() != null) {
                var species = pokedex.getSpecies(p.getSpecies());
                if (species != null) {
                    p.setSpeciesData(species);
                }
            }
        }
    }
}
