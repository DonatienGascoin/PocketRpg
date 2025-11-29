package com.pocket.rpg.inputNew;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * GLFW implementation of the input backend.
 */
public class GLFWInputBackend implements InputBackend {
    private static final Map<Integer, KeyCode> glfwToKeyCode = new HashMap<>();
    private static final Map<KeyCode, Integer> keyCodeToGlfw = new HashMap<>();
    private static final Map<KeyCode, String> keyNames = new HashMap<>();

    static {
        // Initialize mappings
        mapKey(GLFW.GLFW_KEY_A, KeyCode.A, "A");
        mapKey(GLFW.GLFW_KEY_B, KeyCode.B, "B");
        mapKey(GLFW.GLFW_KEY_C, KeyCode.C, "C");
        mapKey(GLFW.GLFW_KEY_D, KeyCode.D, "D");
        mapKey(GLFW.GLFW_KEY_E, KeyCode.E, "E");
        mapKey(GLFW.GLFW_KEY_F, KeyCode.F, "F");
        mapKey(GLFW.GLFW_KEY_G, KeyCode.G, "G");
        mapKey(GLFW.GLFW_KEY_H, KeyCode.H, "H");
        mapKey(GLFW.GLFW_KEY_I, KeyCode.I, "I");
        mapKey(GLFW.GLFW_KEY_J, KeyCode.J, "J");
        mapKey(GLFW.GLFW_KEY_K, KeyCode.K, "K");
        mapKey(GLFW.GLFW_KEY_L, KeyCode.L, "L");
        mapKey(GLFW.GLFW_KEY_M, KeyCode.M, "M");
        mapKey(GLFW.GLFW_KEY_N, KeyCode.N, "N");
        mapKey(GLFW.GLFW_KEY_O, KeyCode.O, "O");
        mapKey(GLFW.GLFW_KEY_P, KeyCode.P, "P");
        mapKey(GLFW.GLFW_KEY_Q, KeyCode.Q, "Q");
        mapKey(GLFW.GLFW_KEY_R, KeyCode.R, "R");
        mapKey(GLFW.GLFW_KEY_S, KeyCode.S, "S");
        mapKey(GLFW.GLFW_KEY_T, KeyCode.T, "T");
        mapKey(GLFW.GLFW_KEY_U, KeyCode.U, "U");
        mapKey(GLFW.GLFW_KEY_V, KeyCode.V, "V");
        mapKey(GLFW.GLFW_KEY_W, KeyCode.W, "W");
        mapKey(GLFW.GLFW_KEY_X, KeyCode.X, "X");
        mapKey(GLFW.GLFW_KEY_Y, KeyCode.Y, "Y");
        mapKey(GLFW.GLFW_KEY_Z, KeyCode.Z, "Z");

        // Numbers
        mapKey(GLFW.GLFW_KEY_0, KeyCode.NUM_0, "0");
        mapKey(GLFW.GLFW_KEY_1, KeyCode.NUM_1, "1");
        mapKey(GLFW.GLFW_KEY_2, KeyCode.NUM_2, "2");
        mapKey(GLFW.GLFW_KEY_3, KeyCode.NUM_3, "3");
        mapKey(GLFW.GLFW_KEY_4, KeyCode.NUM_4, "4");
        mapKey(GLFW.GLFW_KEY_5, KeyCode.NUM_5, "5");
        mapKey(GLFW.GLFW_KEY_6, KeyCode.NUM_6, "6");
        mapKey(GLFW.GLFW_KEY_7, KeyCode.NUM_7, "7");
        mapKey(GLFW.GLFW_KEY_8, KeyCode.NUM_8, "8");
        mapKey(GLFW.GLFW_KEY_9, KeyCode.NUM_9, "9");

        // Function keys
        mapKey(GLFW.GLFW_KEY_F1, KeyCode.F1, "F1");
        mapKey(GLFW.GLFW_KEY_F2, KeyCode.F2, "F2");
        mapKey(GLFW.GLFW_KEY_F3, KeyCode.F3, "F3");
        mapKey(GLFW.GLFW_KEY_F4, KeyCode.F4, "F4");
        mapKey(GLFW.GLFW_KEY_F5, KeyCode.F5, "F5");
        mapKey(GLFW.GLFW_KEY_F6, KeyCode.F6, "F6");
        mapKey(GLFW.GLFW_KEY_F7, KeyCode.F7, "F7");
        mapKey(GLFW.GLFW_KEY_F8, KeyCode.F8, "F8");
        mapKey(GLFW.GLFW_KEY_F9, KeyCode.F9, "F9");
        mapKey(GLFW.GLFW_KEY_F10, KeyCode.F10, "F10");
        mapKey(GLFW.GLFW_KEY_F11, KeyCode.F11, "F11");
        mapKey(GLFW.GLFW_KEY_F12, KeyCode.F12, "F12");

        // Arrow keys
        mapKey(GLFW.GLFW_KEY_UP, KeyCode.UP, "Up Arrow");
        mapKey(GLFW.GLFW_KEY_DOWN, KeyCode.DOWN, "Down Arrow");
        mapKey(GLFW.GLFW_KEY_LEFT, KeyCode.LEFT, "Left Arrow");
        mapKey(GLFW.GLFW_KEY_RIGHT, KeyCode.RIGHT, "Right Arrow");

        // Special keys
        mapKey(GLFW.GLFW_KEY_SPACE, KeyCode.SPACE, "Space");
        mapKey(GLFW.GLFW_KEY_ENTER, KeyCode.ENTER, "Enter");
        mapKey(GLFW.GLFW_KEY_ESCAPE, KeyCode.ESCAPE, "Escape");
        mapKey(GLFW.GLFW_KEY_TAB, KeyCode.TAB, "Tab");
        mapKey(GLFW.GLFW_KEY_BACKSPACE, KeyCode.BACKSPACE, "Backspace");
        mapKey(GLFW.GLFW_KEY_DELETE, KeyCode.DELETE, "Delete");
        mapKey(GLFW.GLFW_KEY_LEFT_SHIFT, KeyCode.LEFT_SHIFT, "Left Shift");
        mapKey(GLFW.GLFW_KEY_RIGHT_SHIFT, KeyCode.RIGHT_SHIFT, "Right Shift");
        mapKey(GLFW.GLFW_KEY_LEFT_CONTROL, KeyCode.LEFT_CONTROL, "Left Control");
        mapKey(GLFW.GLFW_KEY_RIGHT_CONTROL, KeyCode.RIGHT_CONTROL, "Right Control");
        mapKey(GLFW.GLFW_KEY_LEFT_ALT, KeyCode.LEFT_ALT, "Left Alt");
        mapKey(GLFW.GLFW_KEY_RIGHT_ALT, KeyCode.RIGHT_ALT, "Right Alt");
        mapKey(GLFW.GLFW_KEY_CAPS_LOCK, KeyCode.CAPS_LOCK, "Caps Lock");

        // Numpad
        mapKey(GLFW.GLFW_KEY_KP_0, KeyCode.NUMPAD_0, "Numpad 0");
        mapKey(GLFW.GLFW_KEY_KP_1, KeyCode.NUMPAD_1, "Numpad 1");
        mapKey(GLFW.GLFW_KEY_KP_2, KeyCode.NUMPAD_2, "Numpad 2");
        mapKey(GLFW.GLFW_KEY_KP_3, KeyCode.NUMPAD_3, "Numpad 3");
        mapKey(GLFW.GLFW_KEY_KP_4, KeyCode.NUMPAD_4, "Numpad 4");
        mapKey(GLFW.GLFW_KEY_KP_5, KeyCode.NUMPAD_5, "Numpad 5");
        mapKey(GLFW.GLFW_KEY_KP_6, KeyCode.NUMPAD_6, "Numpad 6");
        mapKey(GLFW.GLFW_KEY_KP_7, KeyCode.NUMPAD_7, "Numpad 7");
        mapKey(GLFW.GLFW_KEY_KP_8, KeyCode.NUMPAD_8, "Numpad 8");
        mapKey(GLFW.GLFW_KEY_KP_9, KeyCode.NUMPAD_9, "Numpad 9");
        mapKey(GLFW.GLFW_KEY_KP_ADD, KeyCode.NUMPAD_ADD, "Numpad +");
        mapKey(GLFW.GLFW_KEY_KP_SUBTRACT, KeyCode.NUMPAD_SUBTRACT, "Numpad -");
        mapKey(GLFW.GLFW_KEY_KP_MULTIPLY, KeyCode.NUMPAD_MULTIPLY, "Numpad *");
        mapKey(GLFW.GLFW_KEY_KP_DIVIDE, KeyCode.NUMPAD_DIVIDE, "Numpad /");
        mapKey(GLFW.GLFW_KEY_KP_DECIMAL, KeyCode.NUMPAD_DECIMAL, "Numpad .");
        mapKey(GLFW.GLFW_KEY_KP_ENTER, KeyCode.NUMPAD_ENTER, "Numpad Enter");

        // Other keys
        mapKey(GLFW.GLFW_KEY_PAGE_UP, KeyCode.PAGE_UP, "Page Up");
        mapKey(GLFW.GLFW_KEY_PAGE_DOWN, KeyCode.PAGE_DOWN, "Page Down");
        mapKey(GLFW.GLFW_KEY_HOME, KeyCode.HOME, "Home");
        mapKey(GLFW.GLFW_KEY_END, KeyCode.END, "End");
        mapKey(GLFW.GLFW_KEY_INSERT, KeyCode.INSERT, "Insert");
        mapKey(GLFW.GLFW_KEY_LEFT_BRACKET, KeyCode.LEFT_BRACKET, "[");
        mapKey(GLFW.GLFW_KEY_RIGHT_BRACKET, KeyCode.RIGHT_BRACKET, "]");
        mapKey(GLFW.GLFW_KEY_SEMICOLON, KeyCode.SEMICOLON, ";");
        mapKey(GLFW.GLFW_KEY_APOSTROPHE, KeyCode.APOSTROPHE, "'");
        mapKey(GLFW.GLFW_KEY_COMMA, KeyCode.COMMA, ",");
        mapKey(GLFW.GLFW_KEY_PERIOD, KeyCode.PERIOD, ".");
        mapKey(GLFW.GLFW_KEY_SLASH, KeyCode.SLASH, "/");
        mapKey(GLFW.GLFW_KEY_BACKSLASH, KeyCode.BACKSLASH, "\\");
        mapKey(GLFW.GLFW_KEY_GRAVE_ACCENT, KeyCode.GRAVE_ACCENT, "`");
        mapKey(GLFW.GLFW_KEY_MINUS, KeyCode.MINUS, "-");
        mapKey(GLFW.GLFW_KEY_EQUAL, KeyCode.EQUAL, "=");

        // Mouse buttons (using negative values to distinguish from keyboard)
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_LEFT, KeyCode.MOUSE_BUTTON_LEFT, "Left Mouse");
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_RIGHT, KeyCode.MOUSE_BUTTON_RIGHT, "Right Mouse");
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_MIDDLE, KeyCode.MOUSE_BUTTON_MIDDLE, "Middle Mouse");
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_4, KeyCode.MOUSE_BUTTON_4, "Mouse Button 4");
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_5, KeyCode.MOUSE_BUTTON_5, "Mouse Button 5");
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_6, KeyCode.MOUSE_BUTTON_6, "Mouse Button 6");
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_7, KeyCode.MOUSE_BUTTON_7, "Mouse Button 7");
        mapKey(-GLFW.GLFW_MOUSE_BUTTON_8, KeyCode.MOUSE_BUTTON_8, "Mouse Button 8");
    }

    private static void mapKey(int glfwKey, KeyCode keyCode, String name) {
        glfwToKeyCode.put(glfwKey, keyCode);
        keyCodeToGlfw.put(keyCode, glfwKey);
        keyNames.put(keyCode, name);
    }

    @Override
    public KeyCode mapKeyCode(int backendKeyCode) {
        return glfwToKeyCode.getOrDefault(backendKeyCode, KeyCode.UNKNOWN);
    }

    @Override
    public int mapToBackend(KeyCode keyCode) {
        return keyCodeToGlfw.getOrDefault(keyCode, -1);
    }

    @Override
    public String getKeyName(KeyCode keyCode) {
        return keyNames.getOrDefault(keyCode, "Unknown");
    }

    /**
     * Helper method to convert GLFW mouse button to our internal representation
     */
    public static int mouseButtonToInternal(int glfwMouseButton) {
        return -glfwMouseButton; // Negative to distinguish from keyboard keys
    }
}