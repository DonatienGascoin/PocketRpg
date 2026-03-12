package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for adding a component to an entity.
 * <p>
 * Automatically adds any components declared by {@link RequiredComponent}
 * on the component's class if they are not already present.
 * Undo removes both the main component and any auto-added dependencies.
 */
public class AddComponentCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final Component component;
    private final List<Component> autoAdded = new ArrayList<>();

    public AddComponentCommand(EditorGameObject entity, Component component) {
        this.entity = entity;
        this.component = component;
    }

    @Override
    public void execute() {
        // Snapshot components before adding to track auto-added dependencies
        List<Component> before = new java.util.ArrayList<>(entity.getComponents());

        entity.addComponent(component);

        // Track auto-added required components (added by EGO.addComponent)
        for (Component c : entity.getComponents()) {
            if (c != component && !before.contains(c)) {
                autoAdded.add(c);
            }
        }

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

        // Remove auto-added dependencies in reverse order
        for (int i = autoAdded.size() - 1; i >= 0; i--) {
            entity.removeComponent(autoAdded.get(i));
        }
        autoAdded.clear();
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
