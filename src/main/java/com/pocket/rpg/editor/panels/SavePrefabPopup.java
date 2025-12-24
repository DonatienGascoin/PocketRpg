package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import lombok.Getter;

import java.util.function.Consumer;

/**
 * Popup dialog for saving an entity as a JSON prefab.
 */
public class SavePrefabPopup {

    private static final String POPUP_ID = "Save as Prefab";

    private boolean shouldOpen = false;
    private EditorEntity sourceEntity;
    private Consumer<JsonPrefab> onSaved;

    private final ImString idBuffer = new ImString(64);
    private final ImString displayNameBuffer = new ImString(128);
    private final ImString categoryBuffer = new ImString(64);

    @Getter
    private String lastError = null;

    /**
     * Opens the popup for the given entity.
     */
    public void open(EditorEntity entity, Consumer<JsonPrefab> callback) {
        this.sourceEntity = entity;
        this.onSaved = callback;
        this.shouldOpen = true;
        this.lastError = null;

        // Pre-fill from entity
        String suggestedId = entity.getName()
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_");

        idBuffer.set(suggestedId);
        displayNameBuffer.set(entity.getName());
        categoryBuffer.set("Custom");
    }

    /**
     * Renders the popup. Call every frame.
     */
    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        ImGui.setNextWindowSize(400, 300);

        if (ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoResize)) {
            if (sourceEntity == null) {
                ImGui.text("No entity selected");
                if (ImGui.button("Close")) {
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
                return;
            }

            ImGui.text("Save entity as reusable prefab");
            ImGui.separator();

            // ID field
            ImGui.text("Prefab ID:");
            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##id", idBuffer);
            ImGui.textDisabled("Lowercase, underscores only (e.g., wooden_barrel)");

            ImGui.spacing();

            // Display name
            ImGui.text("Display Name:");
            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##displayName", displayNameBuffer);

            ImGui.spacing();

            // Category
            ImGui.text("Category:");
            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##category", categoryBuffer);
            ImGui.textDisabled("For grouping in prefab browser");

            ImGui.spacing();

            // Component summary
            ImGui.separator();
            ImGui.text("Components to include:");
            ImGui.beginChild("ComponentList", 0);
            for (ComponentData comp : sourceEntity.getComponents()) {
                ImGui.bulletText(comp.getDisplayName());
            }
            if (sourceEntity.getComponents().isEmpty()) {
                ImGui.textDisabled("(no components)");
            }
            ImGui.endChild();

            // Error display
            if (lastError != null) {
                ImGui.separator();
                ImGui.textColored(1f, 0.3f, 0.3f, 1f, lastError);
            }

            ImGui.separator();

            // Buttons
            if (ImGui.button("Save", 120, 0)) {
                trySave();
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void trySave() {
        String id = idBuffer.get().trim();
        String displayName = displayNameBuffer.get().trim();
        String category = categoryBuffer.get().trim();

        // Validation
        if (id.isEmpty()) {
            lastError = "Prefab ID is required";
            return;
        }

        if (!id.matches("^[a-z][a-z0-9_]*$")) {
            lastError = "ID must be lowercase letters, numbers, underscores";
            return;
        }

        if (PrefabRegistry.getInstance().hasPrefab(id)) {
            lastError = "A prefab with this ID already exists";
            return;
        }

        if (displayName.isEmpty()) {
            displayName = id;
        }

        // Create the prefab
        JsonPrefab prefab = new JsonPrefab(id, displayName);
        prefab.setCategory(category.isEmpty() ? null : category);

        // Copy components from entity
        for (ComponentData comp : sourceEntity.getComponents()) {
            ComponentData copy = new ComponentData(comp.getType());
            copy.getFields().putAll(comp.getFields());
            prefab.getComponents().add(copy);
        }

        // Save to disk
        try {
            String filename = id + ".prefab.json";
            PrefabRegistry.getInstance().saveJsonPrefab(prefab, filename);

            if (onSaved != null) {
                onSaved.accept(prefab);
            }

            ImGui.closeCurrentPopup();
        } catch (Exception e) {
            lastError = "Save failed: " + e.getMessage();
        }
    }
}