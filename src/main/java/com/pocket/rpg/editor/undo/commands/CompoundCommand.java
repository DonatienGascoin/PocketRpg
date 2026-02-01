package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.List;

/**
 * Groups multiple commands into a single undo/redo entry.
 * Execute runs all commands forward; undo runs all in reverse order.
 */
public class CompoundCommand implements EditorCommand {

    private final List<EditorCommand> commands;
    private final String description;

    public CompoundCommand(String description, List<EditorCommand> commands) {
        this.description = description;
        this.commands = List.copyOf(commands);
    }

    public CompoundCommand(String description, EditorCommand... commands) {
        this.description = description;
        this.commands = List.of(commands);
    }

    @Override
    public void execute() {
        for (EditorCommand command : commands) {
            command.execute();
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    @Override
    public String getDescription() {
        return description;
    }
}
