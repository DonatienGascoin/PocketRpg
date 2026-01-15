package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for adding a component to an entity.
 */
public class AddComponentCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final Component component;

    public AddComponentCommand(EditorGameObject entity, Component component) {
        this.entity = entity;
        this.component = component;
    }

    @Override
    public void execute() {
        entity.addComponent(component);

        // Apply editor defaults for specific component types
        if (component instanceof UIText uiText && uiText.getFontPath() == null) {
            try {
                EditorConfig config = ConfigLoader.getConfig(ConfigLoader.ConfigType.EDITOR);
                uiText.setFontPath(config.getDefaultUiFont());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void undo() {
        entity.removeComponent(component);
    }

    @Override
    public String getDescription() {
        return "Add " + component.getClass().getSimpleName();
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        return false;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        // Not used
    }
}
