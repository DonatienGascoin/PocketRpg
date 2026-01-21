package com.pocket.rpg.editor.shortcut;

/**
 * Keyboard layout for shortcut bindings.
 * Affects which physical keys are used for shortcuts like Undo/Redo.
 */
public enum KeyboardLayout {
    /**
     * QWERTY layout (US, UK, etc.)
     * Undo = Ctrl+Z, Redo = Ctrl+Shift+Z or Ctrl+Y
     */
    QWERTY,

    /**
     * AZERTY layout (French, Belgian, etc.)
     * Undo = Ctrl+W, Redo = Ctrl+Shift+W or Ctrl+Y
     */
    AZERTY
}
