package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.prefab.JsonPrefab;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Undo command for changing prefab metadata (displayName, category).
 */
public class SetPrefabMetadataCommand implements EditorCommand {

    private final JsonPrefab prefab;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final BiConsumer<JsonPrefab, String> setter;
    private final Runnable onChanged;

    public SetPrefabMetadataCommand(JsonPrefab prefab, String fieldName,
                                     String oldValue, String newValue,
                                     BiConsumer<JsonPrefab, String> setter,
                                     Runnable onChanged) {
        this.prefab = prefab;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.setter = setter;
        this.onChanged = onChanged;
    }

    @Override
    public void execute() {
        setter.accept(prefab, newValue);
        if (onChanged != null) onChanged.run();
    }

    @Override
    public void undo() {
        setter.accept(prefab, oldValue);
        if (onChanged != null) onChanged.run();
    }

    @Override
    public String getDescription() {
        return "Set prefab " + fieldName;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (other instanceof SetPrefabMetadataCommand cmd) {
            return cmd.prefab == this.prefab && cmd.fieldName.equals(this.fieldName);
        }
        return false;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        // Keep oldValue from this, apply newValue from other
        if (other instanceof SetPrefabMetadataCommand cmd) {
            setter.accept(prefab, cmd.newValue);
        }
    }
}
