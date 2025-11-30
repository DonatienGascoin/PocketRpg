package com.pocket.rpg.glfw;

import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.input.events.KeyEvent;
import com.pocket.rpg.input.events.MouseButtonEvent;
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

    private static final Map<Integer, MouseButtonEvent.Action> glfwMouseButtonToAction = new HashMap<>();

    private static final Map<Integer, KeyEvent.Action> glfwKeyActionToAction = new HashMap<>();

    static {
        // Initialize mappings
        mapKeyCode(GLFW.GLFW_KEY_A, KeyCode.A, "A");
        mapKeyCode(GLFW.GLFW_KEY_B, KeyCode.B, "B");
        mapKeyCode(GLFW.GLFW_KEY_C, KeyCode.C, "C");
        mapKeyCode(GLFW.GLFW_KEY_D, KeyCode.D, "D");
        mapKeyCode(GLFW.GLFW_KEY_E, KeyCode.E, "E");
        mapKeyCode(GLFW.GLFW_KEY_F, KeyCode.F, "F");
        mapKeyCode(GLFW.GLFW_KEY_G, KeyCode.G, "G");
        mapKeyCode(GLFW.GLFW_KEY_H, KeyCode.H, "H");
        mapKeyCode(GLFW.GLFW_KEY_I, KeyCode.I, "I");
        mapKeyCode(GLFW.GLFW_KEY_J, KeyCode.J, "J");
        mapKeyCode(GLFW.GLFW_KEY_K, KeyCode.K, "K");
        mapKeyCode(GLFW.GLFW_KEY_L, KeyCode.L, "L");
        mapKeyCode(GLFW.GLFW_KEY_M, KeyCode.M, "M");
        mapKeyCode(GLFW.GLFW_KEY_N, KeyCode.N, "N");
        mapKeyCode(GLFW.GLFW_KEY_O, KeyCode.O, "O");
        mapKeyCode(GLFW.GLFW_KEY_P, KeyCode.P, "P");
        mapKeyCode(GLFW.GLFW_KEY_Q, KeyCode.Q, "Q");
        mapKeyCode(GLFW.GLFW_KEY_R, KeyCode.R, "R");
        mapKeyCode(GLFW.GLFW_KEY_S, KeyCode.S, "S");
        mapKeyCode(GLFW.GLFW_KEY_T, KeyCode.T, "T");
        mapKeyCode(GLFW.GLFW_KEY_U, KeyCode.U, "U");
        mapKeyCode(GLFW.GLFW_KEY_V, KeyCode.V, "V");
        mapKeyCode(GLFW.GLFW_KEY_W, KeyCode.W, "W");
        mapKeyCode(GLFW.GLFW_KEY_X, KeyCode.X, "X");
        mapKeyCode(GLFW.GLFW_KEY_Y, KeyCode.Y, "Y");
        mapKeyCode(GLFW.GLFW_KEY_Z, KeyCode.Z, "Z");

        // Numbers
        mapKeyCode(GLFW.GLFW_KEY_0, KeyCode.NUM_0, "0");
        mapKeyCode(GLFW.GLFW_KEY_1, KeyCode.NUM_1, "1");
        mapKeyCode(GLFW.GLFW_KEY_2, KeyCode.NUM_2, "2");
        mapKeyCode(GLFW.GLFW_KEY_3, KeyCode.NUM_3, "3");
        mapKeyCode(GLFW.GLFW_KEY_4, KeyCode.NUM_4, "4");
        mapKeyCode(GLFW.GLFW_KEY_5, KeyCode.NUM_5, "5");
        mapKeyCode(GLFW.GLFW_KEY_6, KeyCode.NUM_6, "6");
        mapKeyCode(GLFW.GLFW_KEY_7, KeyCode.NUM_7, "7");
        mapKeyCode(GLFW.GLFW_KEY_8, KeyCode.NUM_8, "8");
        mapKeyCode(GLFW.GLFW_KEY_9, KeyCode.NUM_9, "9");

        // Function keys
        mapKeyCode(GLFW.GLFW_KEY_F1, KeyCode.F1, "F1");
        mapKeyCode(GLFW.GLFW_KEY_F2, KeyCode.F2, "F2");
        mapKeyCode(GLFW.GLFW_KEY_F3, KeyCode.F3, "F3");
        mapKeyCode(GLFW.GLFW_KEY_F4, KeyCode.F4, "F4");
        mapKeyCode(GLFW.GLFW_KEY_F5, KeyCode.F5, "F5");
        mapKeyCode(GLFW.GLFW_KEY_F6, KeyCode.F6, "F6");
        mapKeyCode(GLFW.GLFW_KEY_F7, KeyCode.F7, "F7");
        mapKeyCode(GLFW.GLFW_KEY_F8, KeyCode.F8, "F8");
        mapKeyCode(GLFW.GLFW_KEY_F9, KeyCode.F9, "F9");
        mapKeyCode(GLFW.GLFW_KEY_F10, KeyCode.F10, "F10");
        mapKeyCode(GLFW.GLFW_KEY_F11, KeyCode.F11, "F11");
        mapKeyCode(GLFW.GLFW_KEY_F12, KeyCode.F12, "F12");

        // Arrow keys
        mapKeyCode(GLFW.GLFW_KEY_UP, KeyCode.UP, "Up Arrow");
        mapKeyCode(GLFW.GLFW_KEY_DOWN, KeyCode.DOWN, "Down Arrow");
        mapKeyCode(GLFW.GLFW_KEY_LEFT, KeyCode.LEFT, "Left Arrow");
        mapKeyCode(GLFW.GLFW_KEY_RIGHT, KeyCode.RIGHT, "Right Arrow");

        // Special keys
        mapKeyCode(GLFW.GLFW_KEY_SPACE, KeyCode.SPACE, "Space");
        mapKeyCode(GLFW.GLFW_KEY_ENTER, KeyCode.ENTER, "Enter");
        mapKeyCode(GLFW.GLFW_KEY_ESCAPE, KeyCode.ESCAPE, "Escape");
        mapKeyCode(GLFW.GLFW_KEY_TAB, KeyCode.TAB, "Tab");
        mapKeyCode(GLFW.GLFW_KEY_BACKSPACE, KeyCode.BACKSPACE, "Backspace");
        mapKeyCode(GLFW.GLFW_KEY_DELETE, KeyCode.DELETE, "Delete");
        mapKeyCode(GLFW.GLFW_KEY_LEFT_SHIFT, KeyCode.LEFT_SHIFT, "Left Shift");
        mapKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT, KeyCode.RIGHT_SHIFT, "Right Shift");
        mapKeyCode(GLFW.GLFW_KEY_LEFT_CONTROL, KeyCode.LEFT_CONTROL, "Left Control");
        mapKeyCode(GLFW.GLFW_KEY_RIGHT_CONTROL, KeyCode.RIGHT_CONTROL, "Right Control");
        mapKeyCode(GLFW.GLFW_KEY_LEFT_ALT, KeyCode.LEFT_ALT, "Left Alt");
        mapKeyCode(GLFW.GLFW_KEY_RIGHT_ALT, KeyCode.RIGHT_ALT, "Right Alt");
        mapKeyCode(GLFW.GLFW_KEY_CAPS_LOCK, KeyCode.CAPS_LOCK, "Caps Lock");

        // Numpad
        mapKeyCode(GLFW.GLFW_KEY_KP_0, KeyCode.NUMPAD_0, "Numpad 0");
        mapKeyCode(GLFW.GLFW_KEY_KP_1, KeyCode.NUMPAD_1, "Numpad 1");
        mapKeyCode(GLFW.GLFW_KEY_KP_2, KeyCode.NUMPAD_2, "Numpad 2");
        mapKeyCode(GLFW.GLFW_KEY_KP_3, KeyCode.NUMPAD_3, "Numpad 3");
        mapKeyCode(GLFW.GLFW_KEY_KP_4, KeyCode.NUMPAD_4, "Numpad 4");
        mapKeyCode(GLFW.GLFW_KEY_KP_5, KeyCode.NUMPAD_5, "Numpad 5");
        mapKeyCode(GLFW.GLFW_KEY_KP_6, KeyCode.NUMPAD_6, "Numpad 6");
        mapKeyCode(GLFW.GLFW_KEY_KP_7, KeyCode.NUMPAD_7, "Numpad 7");
        mapKeyCode(GLFW.GLFW_KEY_KP_8, KeyCode.NUMPAD_8, "Numpad 8");
        mapKeyCode(GLFW.GLFW_KEY_KP_9, KeyCode.NUMPAD_9, "Numpad 9");
        mapKeyCode(GLFW.GLFW_KEY_KP_ADD, KeyCode.NUMPAD_ADD, "Numpad +");
        mapKeyCode(GLFW.GLFW_KEY_KP_SUBTRACT, KeyCode.NUMPAD_SUBTRACT, "Numpad -");
        mapKeyCode(GLFW.GLFW_KEY_KP_MULTIPLY, KeyCode.NUMPAD_MULTIPLY, "Numpad *");
        mapKeyCode(GLFW.GLFW_KEY_KP_DIVIDE, KeyCode.NUMPAD_DIVIDE, "Numpad /");
        mapKeyCode(GLFW.GLFW_KEY_KP_DECIMAL, KeyCode.NUMPAD_DECIMAL, "Numpad .");
        mapKeyCode(GLFW.GLFW_KEY_KP_ENTER, KeyCode.NUMPAD_ENTER, "Numpad Enter");

        // Other keys
        mapKeyCode(GLFW.GLFW_KEY_PAGE_UP, KeyCode.PAGE_UP, "Page Up");
        mapKeyCode(GLFW.GLFW_KEY_PAGE_DOWN, KeyCode.PAGE_DOWN, "Page Down");
        mapKeyCode(GLFW.GLFW_KEY_HOME, KeyCode.HOME, "Home");
        mapKeyCode(GLFW.GLFW_KEY_END, KeyCode.END, "End");
        mapKeyCode(GLFW.GLFW_KEY_INSERT, KeyCode.INSERT, "Insert");
        mapKeyCode(GLFW.GLFW_KEY_LEFT_BRACKET, KeyCode.LEFT_BRACKET, "[");
        mapKeyCode(GLFW.GLFW_KEY_RIGHT_BRACKET, KeyCode.RIGHT_BRACKET, "]");
        mapKeyCode(GLFW.GLFW_KEY_SEMICOLON, KeyCode.SEMICOLON, ";");
        mapKeyCode(GLFW.GLFW_KEY_APOSTROPHE, KeyCode.APOSTROPHE, "'");
        mapKeyCode(GLFW.GLFW_KEY_COMMA, KeyCode.COMMA, ",");
        mapKeyCode(GLFW.GLFW_KEY_PERIOD, KeyCode.PERIOD, ".");
        mapKeyCode(GLFW.GLFW_KEY_SLASH, KeyCode.SLASH, "/");
        mapKeyCode(GLFW.GLFW_KEY_BACKSLASH, KeyCode.BACKSLASH, "\\");
        mapKeyCode(GLFW.GLFW_KEY_GRAVE_ACCENT, KeyCode.GRAVE_ACCENT, "`");
        mapKeyCode(GLFW.GLFW_KEY_MINUS, KeyCode.MINUS, "-");
        mapKeyCode(GLFW.GLFW_KEY_EQUAL, KeyCode.EQUAL, "=");

        // Mouse buttons (using negative values to distinguish from keyboard)
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_LEFT, KeyCode.MOUSE_BUTTON_LEFT, "Left Mouse");
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_RIGHT, KeyCode.MOUSE_BUTTON_RIGHT, "Right Mouse");
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_MIDDLE, KeyCode.MOUSE_BUTTON_MIDDLE, "Middle Mouse");
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_4, KeyCode.MOUSE_BUTTON_4, "Mouse Button 4");
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_5, KeyCode.MOUSE_BUTTON_5, "Mouse Button 5");
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_6, KeyCode.MOUSE_BUTTON_6, "Mouse Button 6");
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_7, KeyCode.MOUSE_BUTTON_7, "Mouse Button 7");
        mapKeyCode(-GLFW.GLFW_MOUSE_BUTTON_8, KeyCode.MOUSE_BUTTON_8, "Mouse Button 8");
    }

    static {
        // Initialize key action mappings
        mapKeyAction(GLFW.GLFW_PRESS, KeyEvent.Action.PRESS);
        mapKeyAction(GLFW.GLFW_RELEASE, KeyEvent.Action.RELEASE);
        mapKeyAction(GLFW.GLFW_REPEAT, KeyEvent.Action.REPEAT);
    }

    static {
        // Initialize mouse button action mappings
        mapMouseButtonAction(GLFW.GLFW_PRESS, MouseButtonEvent.Action.PRESS);
        mapMouseButtonAction(GLFW.GLFW_RELEASE, MouseButtonEvent.Action.RELEASE);
    }

    private static void mapKeyCode(int glfwKey, KeyCode keyCode, String name) {
        glfwToKeyCode.put(glfwKey, keyCode);
        keyCodeToGlfw.put(keyCode, glfwKey);
        keyNames.put(keyCode, name);
    }

    private static void mapKeyAction(int glfwAction, KeyEvent.Action action) {
        glfwKeyActionToAction.put(glfwAction, action);
    }

    private static void mapMouseButtonAction(int glfwAction, MouseButtonEvent.Action action) {
        glfwMouseButtonToAction.put(glfwAction, action);
    }


    @Override
    public KeyCode getKeyCode(int backendKeyCode) {
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

    public KeyEvent.Action getKeyAction(int glfwAction) {
        return glfwKeyActionToAction.getOrDefault(glfwAction, KeyEvent.Action.PRESS);
    }

    public MouseButtonEvent.Action getMouseButtonAction(int glfwAction) {
        return glfwMouseButtonToAction.getOrDefault(glfwAction, MouseButtonEvent.Action.PRESS);
    }
}