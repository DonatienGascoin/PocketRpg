package com.pocket.rpg.editor.shortcut;

import imgui.ImGui;
import imgui.flag.ImGuiKey;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a keyboard shortcut binding (key + modifiers).
 * Immutable and serializable to JSON.
 */
@Getter
@EqualsAndHashCode
public class ShortcutBinding {

    private final int key;          // ImGuiKey value
    private final boolean ctrl;
    private final boolean shift;
    private final boolean alt;

    public ShortcutBinding(int key, boolean ctrl, boolean shift, boolean alt) {
        this.key = key;
        this.ctrl = ctrl;
        this.shift = shift;
        this.alt = alt;
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    public static ShortcutBinding key(int key) {
        return new ShortcutBinding(key, false, false, false);
    }

    public static ShortcutBinding ctrl(int key) {
        return new ShortcutBinding(key, true, false, false);
    }

    public static ShortcutBinding ctrlShift(int key) {
        return new ShortcutBinding(key, true, true, false);
    }

    public static ShortcutBinding ctrlAlt(int key) {
        return new ShortcutBinding(key, true, false, true);
    }

    public static ShortcutBinding alt(int key) {
        return new ShortcutBinding(key, false, false, true);
    }

    public static ShortcutBinding shift(int key) {
        return new ShortcutBinding(key, false, true, false);
    }

    public static ShortcutBinding of(int key, boolean ctrl, boolean shift, boolean alt) {
        return new ShortcutBinding(key, ctrl, shift, alt);
    }

    // ========================================================================
    // INPUT MATCHING
    // ========================================================================

    /**
     * Checks if this binding is currently pressed.
     * Verifies exact modifier state (no extra modifiers pressed).
     */
    public boolean isPressed() {
        if (!ImGui.isKeyPressed(key)) {
            return false;
        }

        boolean ctrlDown = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shiftDown = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
        boolean altDown = ImGui.isKeyDown(ImGuiKey.LeftAlt) || ImGui.isKeyDown(ImGuiKey.RightAlt);

        return ctrlDown == ctrl && shiftDown == shift && altDown == alt;
    }

    /**
     * Checks if modifiers match (for detecting conflicts).
     */
    public boolean modifiersMatch(ShortcutBinding other) {
        return this.ctrl == other.ctrl && this.shift == other.shift && this.alt == other.alt;
    }

    // ========================================================================
    // DISPLAY & SERIALIZATION
    // ========================================================================

    /**
     * Returns display string like "Ctrl+Shift+S".
     */
    public String getDisplayString() {
        List<String> parts = new ArrayList<>();

        if (ctrl) parts.add("Ctrl");
        if (shift) parts.add("Shift");
        if (alt) parts.add("Alt");
        parts.add(getKeyName(key));

        return String.join("+", parts);
    }

    /**
     * Parses a display string back into a binding.
     * Returns null if parsing fails.
     */
    public static ShortcutBinding parse(String displayString) {
        if (displayString == null || displayString.isEmpty()) {
            return null;
        }

        String[] parts = displayString.split("\\+");
        boolean ctrl = false, shift = false, alt = false;
        int key = -1;

        for (String part : parts) {
            String p = part.trim();
            switch (p.toLowerCase()) {
                case "ctrl" -> ctrl = true;
                case "shift" -> shift = true;
                case "alt" -> alt = true;
                default -> key = parseKeyName(p);
            }
        }

        if (key < 0) {
            return null;
        }

        return new ShortcutBinding(key, ctrl, shift, alt);
    }

    /**
     * Converts to a compact string for JSON storage.
     * Format: "MODIFIERS:KEY" e.g., "CS:S" for Ctrl+Shift+S
     */
    public String toConfigString() {
        StringBuilder sb = new StringBuilder();
        if (ctrl) sb.append('C');
        if (shift) sb.append('S');
        if (alt) sb.append('A');
        sb.append(':');
        sb.append(getKeyName(key));
        return sb.toString();
    }

    /**
     * Parses config string format.
     */
    public static ShortcutBinding fromConfigString(String config) {
        if (config == null || !config.contains(":")) {
            return null;
        }

        int colonIndex = config.indexOf(':');
        String modifiers = config.substring(0, colonIndex);
        String keyName = config.substring(colonIndex + 1);

        boolean ctrl = modifiers.contains("C");
        boolean shift = modifiers.contains("S");
        boolean alt = modifiers.contains("A");
        int key = parseKeyName(keyName);

        if (key < 0) {
            return null;
        }

        return new ShortcutBinding(key, ctrl, shift, alt);
    }

    // ========================================================================
    // KEY NAME UTILITIES
    // ========================================================================

    private static String getKeyName(int key) {
        // Common keys
        if (key >= ImGuiKey.A && key <= ImGuiKey.Z) {
            return String.valueOf((char) ('A' + (key - ImGuiKey.A)));
        }
        if (key >= ImGuiKey._0 && key <= ImGuiKey._9) {
            return String.valueOf((char) ('0' + (key - ImGuiKey._0)));
        }
        if (key >= ImGuiKey.F1 && key <= ImGuiKey.F12) {
            return "F" + (1 + (key - ImGuiKey.F1));
        }

        // Special keys
        return switch (key) {
            case ImGuiKey.Space -> "Space";
            case ImGuiKey.Enter -> "Enter";
            case ImGuiKey.Escape -> "Escape";
            case ImGuiKey.Tab -> "Tab";
            case ImGuiKey.Backspace -> "Backspace";
            case ImGuiKey.Delete -> "Delete";
            case ImGuiKey.Insert -> "Insert";
            case ImGuiKey.Home -> "Home";
            case ImGuiKey.End -> "End";
            case ImGuiKey.PageUp -> "PageUp";
            case ImGuiKey.PageDown -> "PageDown";
            case ImGuiKey.UpArrow -> "Up";
            case ImGuiKey.DownArrow -> "Down";
            case ImGuiKey.LeftArrow -> "Left";
            case ImGuiKey.RightArrow -> "Right";
            case ImGuiKey.Minus -> "-";
            case ImGuiKey.Equal -> "=";
            case ImGuiKey.LeftBracket -> "[";
            case ImGuiKey.RightBracket -> "]";
            case ImGuiKey.Semicolon -> ";";
            case ImGuiKey.Apostrophe -> "'";
            case ImGuiKey.Comma -> ",";
            case ImGuiKey.Period -> ".";
            case ImGuiKey.Slash -> "/";
            case ImGuiKey.Backslash -> "\\";
            case ImGuiKey.GraveAccent -> "`";
            default -> "Key" + key;
        };
    }

    private static int parseKeyName(String name) {
        if (name == null || name.isEmpty()) {
            return -1;
        }

        // Single letter
        if (name.length() == 1) {
            char c = Character.toUpperCase(name.charAt(0));
            if (c >= 'A' && c <= 'Z') {
                return ImGuiKey.A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return ImGuiKey._0 + (c - '0');
            }
        }

        // Function keys
        if (name.toUpperCase().startsWith("F") && name.length() <= 3) {
            try {
                int num = Integer.parseInt(name.substring(1));
                if (num >= 1 && num <= 12) {
                    return ImGuiKey.F1 + (num - 1);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Special keys
        return switch (name.toLowerCase()) {
            case "space" -> ImGuiKey.Space;
            case "enter", "return" -> ImGuiKey.Enter;
            case "escape", "esc" -> ImGuiKey.Escape;
            case "tab" -> ImGuiKey.Tab;
            case "backspace" -> ImGuiKey.Backspace;
            case "delete", "del" -> ImGuiKey.Delete;
            case "insert", "ins" -> ImGuiKey.Insert;
            case "home" -> ImGuiKey.Home;
            case "end" -> ImGuiKey.End;
            case "pageup", "pgup" -> ImGuiKey.PageUp;
            case "pagedown", "pgdn" -> ImGuiKey.PageDown;
            case "up" -> ImGuiKey.UpArrow;
            case "down" -> ImGuiKey.DownArrow;
            case "left" -> ImGuiKey.LeftArrow;
            case "right" -> ImGuiKey.RightArrow;
            case "-", "minus" -> ImGuiKey.Minus;
            case "=", "equal", "equals" -> ImGuiKey.Equal;
            case "[" -> ImGuiKey.LeftBracket;
            case "]" -> ImGuiKey.RightBracket;
            case ";" -> ImGuiKey.Semicolon;
            case "'" -> ImGuiKey.Apostrophe;
            case "," -> ImGuiKey.Comma;
            case "." -> ImGuiKey.Period;
            case "/" -> ImGuiKey.Slash;
            case "\\" -> ImGuiKey.Backslash;
            case "`" -> ImGuiKey.GraveAccent;
            default -> -1;
        };
    }

    @Override
    public String toString() {
        return getDisplayString();
    }
}
