package com.pocket.rpg.prefab.prefabs;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.dialogue.DialogueUIBuilder;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.rendering.resources.Sprite;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Prefab for the dialogue UI hierarchy.
 * <p>
 * Overrides {@link #instantiate} to build the full UI tree via
 * {@link DialogueUIBuilder#build()} instead of the default flat-component approach.
 * <p>
 * Drop this into any scene that uses the dialogue system. The
 * {@link com.pocket.rpg.components.dialogue.PlayerDialogueManager} will
 * find the UI elements via {@link com.pocket.rpg.ui.ComponentKeyRegistry}.
 */
public class DialogueUIPrefab implements Prefab {

    @Override
    public String getId() {
        return "dialogue_ui";
    }

    @Override
    public String getDisplayName() {
        return "Dialogue UI";
    }

    @Override
    public String getCategory() {
        return "UI";
    }

    @Override
    public List<Component> getComponents() {
        // The hierarchy is built in instantiate() — no flat component list
        return List.of();
    }

    @Override
    public Sprite getPreviewSprite() {
        return null;
    }

    @Override
    public GameObject instantiate(Vector3f position, Map<String, Map<String, Object>> overrides) {
        return DialogueUIBuilder.build();
    }
}
