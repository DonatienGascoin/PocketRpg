package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.pokemon.TrainerComponent;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.OpenAssetEditorEvent;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;

import java.util.*;

/**
 * Custom inspector for {@link TrainerComponent}.
 * <p>
 * Shows a trainer ID dropdown, a read-only preview of the selected
 * trainer definition, and a button to open the Trainer Registry editor.
 */
@InspectorFor(TrainerComponent.class)
public class TrainerComponentInspector extends CustomComponentInspector<TrainerComponent> {

    private static final String REGISTRY_PATH = "data/pokemon/trainers.trainers.json";

    private TrainerRegistry cachedRegistry;
    private String[] trainerOptions;
    private String[] trainerLabels;

    @Override
    public boolean draw() {
        boolean changed = false;

        buildTrainerOptions();

        changed |= drawTrainerCombo();

        ImGui.separator();
        ImGui.spacing();

        drawPreview();

        return changed;
    }

    private boolean drawTrainerCombo() {
        if (trainerOptions == null || trainerOptions.length == 0) {
            ImGui.textDisabled("No Trainer Registry loaded");
            return false;
        }

        String current = component.getTrainerId() != null ? component.getTrainerId() : "";

        // Find current index
        int currentIdx = 0;
        for (int i = 0; i < trainerOptions.length; i++) {
            if (trainerOptions[i].equals(current)) {
                currentIdx = i + 1; // +1 because first option is "(none)"
                break;
            }
        }

        String[] labels = new String[trainerLabels.length + 1];
        labels[0] = "(none)";
        System.arraycopy(trainerLabels, 0, labels, 1, trainerLabels.length);

        imgui.type.ImInt selected = new imgui.type.ImInt(currentIdx);
        float btnWidth = ImGui.getFrameHeight();
        FieldEditors.inspectorRow("Trainer", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - btnWidth - ImGui.getStyle().getItemSpacingX());
            ImGui.combo("##trainerId", selected, labels);
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.OpenInNew + "##openRegistry", btnWidth, 0)) {
                EditorEventBus.get().publish(new OpenAssetEditorEvent(REGISTRY_PATH, component.getTrainerId()));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Open in Trainer Registry");
            }
        });

        int newIdx = selected.get();
        if (newIdx != currentIdx) {
            String newId = newIdx == 0 ? "" : trainerOptions[newIdx - 1];
            component.setTrainerId(newId);
            markSceneDirty();
            return true;
        }

        return false;
    }

    private void drawPreview() {
        TrainerDefinition def = component.getDefinition();
        if (def == null) {
            ImGui.textDisabled("No trainer selected");
            return;
        }

        ImGui.text("Preview");
        ImGui.separator();

        ImGui.textDisabled("Name:    ");
        ImGui.sameLine();
        ImGui.text(def.getTrainerName() != null ? def.getTrainerName() : "");

        ImGui.textDisabled("Tag:     ");
        ImGui.sameLine();
        ImGui.text(def.getTag() != null ? def.getTag() : "");

        ImGui.textDisabled("Money:   ");
        ImGui.sameLine();
        ImGui.text("$" + def.getDefeatMoney());

        // Party summary
        ImGui.textDisabled("Party:   ");
        ImGui.sameLine();
        List<TrainerPokemonSpec> party = def.getParty();
        if (party == null || party.isEmpty()) {
            ImGui.textDisabled("(empty)");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < party.size(); i++) {
                TrainerPokemonSpec spec = party.get(i);
                if (i > 0) sb.append(", ");
                sb.append(spec.getSpeciesId()).append(" Lv.").append(spec.getLevel());
            }
            ImGui.text(sb.toString());
        }

        // Dialogue info
        if (def.getPreDialogue() != null) {
            ImGui.textDisabled("Pre:     ");
            ImGui.sameLine();
            String path = Assets.getPathForResource(def.getPreDialogue());
            ImGui.text(path != null ? path : "(set)");
        }
        if (def.getPostDialogue() != null) {
            ImGui.textDisabled("Post:    ");
            ImGui.sameLine();
            String path = Assets.getPathForResource(def.getPostDialogue());
            ImGui.text(path != null ? path : "(set)");
        }
    }

    private void buildTrainerOptions() {
        if (trainerOptions != null) return;
        cachedRegistry = Assets.load(REGISTRY_PATH, TrainerRegistry.class);
        if (cachedRegistry == null) {
            trainerOptions = new String[0];
            trainerLabels = new String[0];
            return;
        }
        List<TrainerDefinition> allTrainers = new ArrayList<>(cachedRegistry.getAllTrainers());
        allTrainers.sort(Comparator.comparing(TrainerDefinition::getTrainerId));
        trainerOptions = new String[allTrainers.size()];
        trainerLabels = new String[allTrainers.size()];
        for (int i = 0; i < allTrainers.size(); i++) {
            TrainerDefinition def = allTrainers.get(i);
            trainerOptions[i] = def.getTrainerId();
            String name = def.getTrainerName() != null && !def.getTrainerName().isEmpty()
                    ? def.getTrainerName() : "";
            String cls = def.getTag() != null && !def.getTag().isEmpty()
                    ? " (" + def.getTag() + ")" : "";
            trainerLabels[i] = def.getTrainerId() + (name.isEmpty() ? "" : " - " + name) + cls;
        }
    }

    private void markSceneDirty() {
        if (FieldEditorContext.getCurrentScene() != null) {
            FieldEditorContext.getCurrentScene().markDirty();
        }
    }

    @Override
    public void unbind() {
        super.unbind();
        trainerOptions = null;
        trainerLabels = null;
        cachedRegistry = null;
    }
}
