package com.pocket.rpg.input;

/**
 * Platform-independent key codes.
 * These are abstract key identifiers that get mapped to backend-specific codes.
 */
public enum KeyCode {
    // Alphanumeric keys
    A, B, C, D, E, F, G, H, I, J, K, L, M,
    N, O, P, Q, R, S, T, U, V, W, X, Y, Z,

    // Number keys
    NUM_0, NUM_1, NUM_2, NUM_3, NUM_4,
    NUM_5, NUM_6, NUM_7, NUM_8, NUM_9,

    // Function keys
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,

    // Arrow keys
    UP, DOWN, LEFT, RIGHT,

    // Special keys
    SPACE, ENTER, ESCAPE, TAB, BACKSPACE, DELETE,
    LEFT_SHIFT, RIGHT_SHIFT, LEFT_CONTROL, RIGHT_CONTROL,
    LEFT_ALT, RIGHT_ALT, CAPS_LOCK,

    // Numpad
    NUMPAD_0, NUMPAD_1, NUMPAD_2, NUMPAD_3, NUMPAD_4,
    NUMPAD_5, NUMPAD_6, NUMPAD_7, NUMPAD_8, NUMPAD_9,
    NUMPAD_ADD, NUMPAD_SUBTRACT, NUMPAD_MULTIPLY, NUMPAD_DIVIDE,
    NUMPAD_DECIMAL, NUMPAD_ENTER,

    // Other
    PAGE_UP, PAGE_DOWN, HOME, END, INSERT,
    LEFT_BRACKET, RIGHT_BRACKET, SEMICOLON, APOSTROPHE,
    COMMA, PERIOD, SLASH, BACKSLASH, GRAVE_ACCENT,
    MINUS, EQUAL,

    // Mouse buttons (treating them as keys for unified handling)
    MOUSE_BUTTON_LEFT, MOUSE_BUTTON_RIGHT, MOUSE_BUTTON_MIDDLE,
    MOUSE_BUTTON_4, MOUSE_BUTTON_5, MOUSE_BUTTON_6, MOUSE_BUTTON_7, MOUSE_BUTTON_8,

    UNKNOWN
}