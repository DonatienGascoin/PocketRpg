package com.pocket.rpg.editor.shortcuts.commands;

import com.pocket.rpg.editor.EditorApplication;

public class CommandUtils {

    public static void checkUnsavedChanges(EditorApplication application, Runnable action) {
        if (application.getCurrentScene() != null && application.getCurrentScene().isDirty()) {
            application.getMenuBar().setShowUnsavedChangesDialog(true);
            application.getMenuBar().setPendingAction(action);
        } else {
            action.run();
        }
    }
}
