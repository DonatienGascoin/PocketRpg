package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import org.joml.Vector2f;

import java.util.Objects;

/**
 * Static access point for input handling.
 */
public final class Input {

    private static Input instance;
    private final InputConfig config;
    private final InputInterface inputInterface;


    private Input(InputConfig config, InputInterface inputInterface) {
        this.config = config;
        this.inputInterface = inputInterface;
        instance = this;
    }

    public static void init(InputConfig config, InputInterface inputInterface) {
        if (instance != null) {
            throw new IllegalStateException("Input has already been initialized.");
        } else {
            instance = new Input(config, inputInterface);
        }
    }

    public static void endFrame() {
        instance.inputInterface.endFrame();
    }

    public static boolean wasPressedThisFrame(InputAction inputAction) {
        return instance.inputInterface.wasPressedThisFrame(instance.config.getBindingForAction(inputAction));
    }

    public static boolean isPressed(InputAction inputAction) {
        return instance.inputInterface.isPressed(instance.config.getBindingForAction(inputAction));
    }

    public static boolean wasReleasedThisFrame(InputAction inputAction) {
        return instance.inputInterface.wasReleasedThisFrame(instance.config.getBindingForAction(inputAction));
    }

    public static Vector2f getMousePosition() {
        return instance.inputInterface.getMousePosition();
    }

    public Vector2f getAxis(String axisName) {
//        return instance.inputInterface.getAxis(axisName);
        return new Vector2f();
    }


    public static boolean wasMouseButtonPressedThisFrame(int button) {
        return instance.inputInterface.wasMouseButtonPressedThisFrame(button);
    }

    public static boolean isMouseButtonPressed(int button) {
        return instance.inputInterface.isMouseButtonPressed(button);
    }

    public static boolean wasMouseButtonReleasedThisFrame(int button) {
        return instance.inputInterface.wasMouseButtonReleasedThisFrame(button);
    }

    public static Vector2f getScroll() {
        return instance.inputInterface.getScroll();
    }

    public InputConfig config() {
        return config;
    }

    public InputInterface inputInterface() {
        return inputInterface;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Input) obj;
        return Objects.equals(this.config, that.config) &&
                Objects.equals(this.inputInterface, that.inputInterface);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, inputInterface);
    }

    @Override
    public String toString() {
        return "Input[" +
                "config=" + config + ", " +
                "inputInterface=" + inputInterface + ']';
    }


}
