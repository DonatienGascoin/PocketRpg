package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for toggling a component's enabled state.
 */
public class ToggleComponentEnabledCommand implements EditorCommand {

    private final Component component;
    private final boolean newEnabled;

    public ToggleComponentEnabledCommand(Component component, boolean newEnabled) {
        this.component = component;
        this.newEnabled = newEnabled;
    }

    @Override
    public void execute() {
        component.setEnabled(newEnabled);
    }

    @Override
    public void undo() {
        component.setEnabled(!newEnabled);
    }

    @Override
    public String getDescription() {
        return (newEnabled ? "Enable" : "Disable") + " " + component.getClass().getSimpleName();
    }
}
