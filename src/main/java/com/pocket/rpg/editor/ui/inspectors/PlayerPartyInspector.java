package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.pokemon.PlayerPartyComponent;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom inspector for {@link PlayerPartyComponent}.
 * Shows the player's party Pokemon with HP, status, and moves during play mode.
 * Provides quick-add (species dropdown + level) and remove functionality.
 * <p>
 * Uses raw ImGui widgets instead of FieldEditors/PrimitiveEditors because this inspector
 * operates on transient play-mode state persisted via PlayerData write-through, not on
 * serialized component fields. There is no undo support in play mode.
 */
@InspectorFor(PlayerPartyComponent.class)
public class PlayerPartyInspector extends CustomComponentInspector<PlayerPartyComponent> {

    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    private int addLevel = 5;
    private int selectedSpeciesIndex = 0;
    private String[] speciesOptions;

    @Override
    public boolean draw() {
        if (editorEntity() != null) {
            ImGui.textDisabled("Data available during play mode");
            return false;
        }

        List<PokemonInstance> party = component.getParty();

        // --- Data display in bordered child ---
        ImGui.text("Party (" + party.size() + "/" + PlayerPartyComponent.MAX_PARTY_SIZE + ")");
        float quickAddHeight = ImGui.getFrameHeightWithSpacing() * 3;
        float availHeight = ImGui.getContentRegionAvailY() - quickAddHeight;
        float childHeight = Math.max(availHeight, 120);
        if (ImGui.beginChild("##partyList", 0, childHeight, true)) {
            float buttonX = ImGui.getContentRegionAvailX() - 20;
            int removeIndex = -1;
            for (int i = 0; i < party.size(); i++) {
                PokemonInstance p = party.get(i);
                ImGui.pushID(i);

                String label = "[" + i + "] " + p.getSpecies() + " Lv." + p.getLevel();
                boolean open = ImGui.treeNodeEx("##pokemon" + i,
                        ImGuiTreeNodeFlags.AllowOverlap, label);

                // Remove button on same line as tree node header (use fixed X to stay aligned)
                ImGui.sameLine(buttonX);
                if (ImGui.smallButton("x##rem")) {
                    removeIndex = i;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Remove from party");
                }

                if (open) {
                    // HP bar
                    int maxHp = p.getSpeciesData() != null ? p.calcMaxHp() : p.getCurrentHp();
                    float hpFraction = maxHp > 0 ? (float) p.getCurrentHp() / maxHp : 0;
                    ImGui.text("HP:");
                    ImGui.sameLine();
                    ImGui.progressBar(hpFraction, 150, 14,
                            p.getCurrentHp() + "/" + maxHp);

                    // Status
                    ImGui.text("Status: " + p.getStatusCondition());

                    // Moves
                    if (p.getMoves() != null && !p.getMoves().isEmpty()) {
                        ImGui.text("Moves:");
                        for (MoveSlot move : p.getMoves()) {
                            ImGui.text("  " + move.getMoveId()
                                    + " (" + move.getCurrentPp() + "/" + move.getMaxPp() + ")");
                        }
                    }

                    ImGui.treePop();
                }

                ImGui.popID();
            }

            if (party.isEmpty()) {
                ImGui.textDisabled("Empty");
            }

            if (removeIndex >= 0) {
                component.removeFromParty(removeIndex);
            }
        }
        ImGui.endChild();

        // --- Quick Add ---
        ImGui.text("Quick Add");
        buildSpeciesOptions();
        if (speciesOptions != null && speciesOptions.length > 0) {
            ImGui.text("Species:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(120);
            ImInt selected = new ImInt(selectedSpeciesIndex);
            if (ImGui.combo("##species", selected, speciesOptions)) {
                selectedSpeciesIndex = selected.get();
            }
            ImGui.sameLine();
            ImGui.text("Lv:");
            ImGui.sameLine();
            int[] levelBuf = {addLevel};
            ImGui.setNextItemWidth(40);
            if (ImGui.dragInt("##level", levelBuf, 0.5f, 1, 100)) {
                addLevel = Math.max(1, Math.min(100, levelBuf[0]));
            }
            ImGui.sameLine();
            if (party.size() < PlayerPartyComponent.MAX_PARTY_SIZE) {
                if (ImGui.button("+ Add")) {
                    addPokemonToParty();
                }
            } else {
                ImGui.textDisabled("Party full");
            }
        } else {
            ImGui.textDisabled("No Pokedex loaded");
        }

        return false;
    }

    private void buildSpeciesOptions() {
        if (speciesOptions != null) return;
        Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
        if (pokedex == null) {
            speciesOptions = new String[0];
            return;
        }
        List<PokemonSpecies> all = new ArrayList<>(pokedex.getAllSpecies());
        all.sort(Comparator.comparing(PokemonSpecies::getSpeciesId));
        speciesOptions = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            speciesOptions[i] = all.get(i).getSpeciesId();
        }
    }

    private void addPokemonToParty() {
        if (speciesOptions == null || selectedSpeciesIndex >= speciesOptions.length) return;
        Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
        if (pokedex == null) return;
        String speciesId = speciesOptions[selectedSpeciesIndex];
        PokemonInstance pokemon = PokemonFactory.createWild(pokedex, speciesId, addLevel);
        component.addToParty(pokemon);
    }

    @Override
    public void unbind() {
        super.unbind();
        // Don't reset combo state here — unbind is called every frame when multiple
        // custom inspectors exist on the same entity (registry tracks only one at a time).
        speciesOptions = null;
    }
}
