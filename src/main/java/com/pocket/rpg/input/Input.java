package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import org.joml.Vector2f;

/**
 * Static access point for input handling.
 */
public record Input(InputInterface inputInterface) {

    private static Input instance;

    private static InputConfig config;

    public Input(/*InputConfig config,*/ InputInterface inputInterface) {
        this.inputInterface = inputInterface;
        instance = this;
    }

    public void endFrame() {
        instance.inputInterface.endFrame();
    }

    public static boolean wasPressedThisFrame(InputAction inputAction) {
//        return instance.inputInterface.wasPressedThisFrame(config.getBindingForAction(inputAction));
        return instance.inputInterface.wasPressedThisFrame(inputAction.getBinding());
    }

    public static boolean isPressed(InputAction inputAction) {
//        return instance.inputInterface.isPressed(config.getBindingForAction(inputAction));
        return instance.inputInterface.isPressed(inputAction.getBinding());
    }

    public static boolean wasReleasedThisFrame(InputAction inputAction) {
//        return instance.inputInterface.wasReleasedThisFrame(config.getBindingForAction(inputAction));
        return instance.inputInterface.wasReleasedThisFrame(inputAction.getBinding());
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

}
