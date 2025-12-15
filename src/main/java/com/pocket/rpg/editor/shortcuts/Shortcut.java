package com.pocket.rpg.editor.shortcuts;

import com.pocket.rpg.editor.shortcuts.commands.Command;
import imgui.ImGui;
import lombok.Builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Builder(setterPrefix = "with")
public final class Shortcut {

    private final Set<Integer> requiredKeys;
    private final Command command;

    public Shortcut(Set<Integer> requiredKeys, Command command) {
        this.requiredKeys = Set.copyOf(requiredKeys);
        this.command = command;
    }

    public static class ShortcutBuilder {
        private Set<Integer> requiredKeys;

        public ShortcutBuilder withKeys(Integer... keys) {
            if (requiredKeys == null) {
                requiredKeys = new HashSet<>();
            }
            requiredKeys.addAll(Arrays.stream(keys).toList());
            return this;
        }

    }

    public Command command() {
        return command;
    }

    public boolean isTriggered() {
        // 1. All required keys must be held down
        for (int key : requiredKeys) {
            if (!ImGui.isKeyDown(key)) {
                return false;
            }
        }

        // 2. At least one required key must be pressed this frame
        for (int key : requiredKeys) {
            if (ImGui.isKeyPressed(key, false)) {
                return true;
            }
        }

        return false;
    }
}
