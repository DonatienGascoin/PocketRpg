package com.pocket.rpg.editor.shortcuts.commands;

import com.pocket.rpg.editor.EditorApplication;
import com.pocket.rpg.editor.ui.StatusBar;

public class BrushToolCommand implements Command {

    private final EditorApplication application;
    private final StatusBar statusBar;

    public BrushToolCommand(EditorApplication application, StatusBar statusBar) {
        this.application = application;
        this.statusBar = statusBar;
    }

    @Override
    public String id() {
        return "BrushTool";
    }

    @Override
    public boolean isEnabled() {
        return application.isSceneFocused();
    }

    @Override
    public void execute() {
        application.getToolManager().setActiveTool("Brush");
        statusBar.showMessage("Brush Tool");
    }
}
