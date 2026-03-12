package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

import java.util.LinkedHashMap;
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
        // Deep copy current override values before clearing
        savedOverrides = deepCopyOverrides(entity.getComponentOverrides());
        // Clear overriddenFields and re-clone all components from template
        entity.resetAllOverrides();
    }

    @Override
    public void undo() {
        // Re-apply saved overrides (sets values on components and marks as overridden)
        entity.applySerializedOverrides(savedOverrides);
    }

    @Override
    public String getDescription() {
        return "Reset All Overrides";
    }

    private static Map<String, Map<String, Object>> deepCopyOverrides(Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        if (source != null) {
            for (var entry : source.entrySet()) {
                Map<String, Object> fieldCopy = new LinkedHashMap<>();
                for (var fieldEntry : entry.getValue().entrySet()) {
                    fieldCopy.put(fieldEntry.getKey(),
                            ComponentReflectionUtils.deepCopyValue(fieldEntry.getValue()));
                }
                copy.put(entry.getKey(), fieldCopy);
            }
        }
        return copy;
    }
}
