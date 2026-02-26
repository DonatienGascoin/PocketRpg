package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.pokemon.PokemonStorageComponent;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom inspector for {@link PokemonStorageComponent}.
 * Shows PC box storage with box selection dropdown, Pokemon list, and add/remove during play mode.
 * <p>
 * Uses raw ImGui widgets instead of FieldEditors/PrimitiveEditors because this inspector
 * operates on transient play-mode state persisted via PlayerData write-through, not on
 * serialized component fields. There is no undo support in play mode.
 */
@InspectorFor(PokemonStorageComponent.class)
public class PokemonStorageInspector extends CustomComponentInspector<PokemonStorageComponent> {

    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    private final ImInt selectedBox = new ImInt(0);
    private int addLevel = 5;
    private int selectedSpeciesIndex = 0;
    private String[] speciesOptions;

    @Override
    public boolean draw() {
        if (editorEntity() != null) {
            ImGui.textDisabled("Data available during play mode");
            return false;
        }

        ImGui.text("Total Stored: " + component.getTotalStored());
        ImGui.spacing();

        // Box dropdown
        int boxCount = component.getBoxCount();
        String[] boxLabels = new String[boxCount];
        for (int i = 0; i < boxCount; i++) {
            List<PokemonInstance> box = component.getBox(i);
            boxLabels[i] = component.getBoxName(i) + " (" + box.size()
                    + "/" + PokemonStorageComponent.BOX_CAPACITY + ")";
        }

        if (selectedBox.get() >= boxCount) selectedBox.set(0);
        ImGui.text("Box:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(200);
        ImGui.combo("##boxSelect", selectedBox, boxLabels);

        int currentBox = selectedBox.get();

        // --- Box contents in bordered child ---
        List<PokemonInstance> box = component.getBox(currentBox);
        float quickAddHeight = ImGui.getFrameHeightWithSpacing() * 3;
        float availHeight = ImGui.getContentRegionAvailY() - quickAddHeight;
        float childHeight = Math.max(availHeight, 120);
        if (ImGui.beginChild("##boxContents", 0, childHeight, true)) {
            int removeIndex = -1;

            if (box.isEmpty()) {
                ImGui.textDisabled("Empty");
            } else {
                for (int i = 0; i < box.size(); i++) {
                    PokemonInstance p = box.get(i);
                    ImGui.pushID(i);

                    int maxHp = p.getSpeciesData() != null ? p.calcMaxHp() : p.getCurrentHp();
                    String line = "[" + i + "] " + p.getSpecies() + "  Lv." + p.getLevel()
                            + "  HP: " + p.getCurrentHp() + "/" + maxHp;
                    ImGui.text(line);

                    ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                    if (ImGui.smallButton("x##rem")) {
                        removeIndex = i;
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Remove from storage");
                    }

                    ImGui.popID();
                }
            }

            if (removeIndex >= 0) {
                component.withdraw(currentBox, removeIndex);
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
            ImInt specSel = new ImInt(selectedSpeciesIndex);
            if (ImGui.combo("##species", specSel, speciesOptions)) {
                selectedSpeciesIndex = specSel.get();
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
            if (box.size() < PokemonStorageComponent.BOX_CAPACITY) {
                if (ImGui.button("+ Add")) {
                    addPokemonToBox();
                }
            } else {
                ImGui.textDisabled("Box full");
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

    private void addPokemonToBox() {
        if (speciesOptions == null || selectedSpeciesIndex >= speciesOptions.length) return;
        Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
        if (pokedex == null) return;
        String speciesId = speciesOptions[selectedSpeciesIndex];
        PokemonInstance pokemon = PokemonFactory.createWild(pokedex, speciesId, addLevel);
        component.deposit(pokemon, selectedBox.get());
    }

    @Override
    public void unbind() {
        super.unbind();
        // Don't reset combo state here — unbind is called every frame when multiple
        // custom inspectors exist on the same entity (registry tracks only one at a time).
        speciesOptions = null;
    }
}
