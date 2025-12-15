package com.pocket.rpg.editor.shortcuts;

import java.util.ArrayList;
import java.util.List;

public final class ShortcutScope {

    private final String name;
    private final int priority;
    private final List<Shortcut> shortcuts = new ArrayList<>();

    public ShortcutScope(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    public String name() {
        return name;
    }

    public int priority() {
        return priority;
    }

    public List<Shortcut> shortcuts() {
        return shortcuts;
    }

    public ShortcutScope add(Shortcut shortcut) {
        shortcuts.add(shortcut);
        return this;
    }
}
