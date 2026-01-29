package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Command for resetting all prefab overrides on an entity.
 * Captures the full override state before clearing so it can be restored on undo.
 */
public class ResetAllOverridesCommand implements EditorCommand {

    private final EditorGameObject entity;
    private Map<String, Map<String, Object>> savedOverrides;

    public ResetAllOverridesCommand(EditorGameObject entity) {
        this.entity = entity;
    }

    @Override
    public void execute() {
        // Deep copy current overrides before clearing
        savedOverrides = copyOverrides(entity.getComponentOverrides());
        entity.resetAllOverrides();
        entity.invalidateComponentCache();
    }

    @Override
    public void undo() {
        // Restore saved overrides
        entity.getComponentOverrides().putAll(savedOverrides);
        entity.invalidateComponentCache();
    }

    @Override
    public String getDescription() {
        return "Reset All Overrides";
    }

    private static Map<String, Map<String, Object>> copyOverrides(Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        if (source != null) {
            for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
                copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
        }
        return copy;
    }
}
