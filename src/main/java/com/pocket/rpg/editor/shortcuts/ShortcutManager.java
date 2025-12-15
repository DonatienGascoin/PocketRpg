package com.pocket.rpg.editor.shortcuts;

import com.pocket.rpg.editor.shortcuts.commands.Command;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ShortcutManager {

    private static final ShortcutManager INSTANCE = new ShortcutManager();

    private final List<ShortcutScope> scopes = new ArrayList<>();

    private ShortcutManager() {
    }

    public static void pushScope(ShortcutScope scope) {
        System.out.println("Push scope: " + scope.name());
        INSTANCE.pushScopeInternal(scope);
    }

    public static void popScope(ShortcutScope scope) {
        System.out.println("Pop scope: " + scope.name());
        INSTANCE.popScopeInternal(scope);
    }

    public static void update() {
        INSTANCE.updateInternal();
    }

    private void pushScopeInternal(ShortcutScope scope) {
        scopes.add(scope);
        scopes.sort(Comparator.comparingInt(ShortcutScope::priority).reversed());
    }

    private void popScopeInternal(ShortcutScope scope) {
        scopes.remove(scope);
    }

    private void updateInternal() {
        if (ImGui.getIO().getWantTextInput()) return;

        for (ShortcutScope scope : scopes) {
            for (Shortcut sc : scope.shortcuts()) {
                Command cmd = sc.command();
                if (cmd.isEnabled() && sc.isTriggered()) {
                    cmd.execute();
                    return; // stop after first match
                }
            }
        }
    }
}
