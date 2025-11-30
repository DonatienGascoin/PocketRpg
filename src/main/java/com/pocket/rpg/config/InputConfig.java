package com.pocket.rpg.config;

import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.input.InputAxis;
import com.pocket.rpg.input.AxisConfig;
import com.pocket.rpg.input.KeyCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputConfig {

    private final Map<String, AxisConfig> axisConfigs = new HashMap<>();
    public Map<InputAction, List<KeyCode>> actionBindings;

    public InputConfig() {
        actionBindings = new HashMap<>();
        loadDefaults();
    }

    /**
     * Load default bindings from InputAction enum.
     */
    private void loadDefaults() {
        for (InputAction inputAction : InputAction.values()) {
            bindAction(inputAction, inputAction.getBinding());
        }

        for (InputAxis axis : InputAxis.values()) {
            registerAxis(axis.name(), axis.getAxisConfig());
        }
    }

    public synchronized void registerAxis(String axisName, AxisConfig config) {
        axisConfigs.put(axisName, config);
    }

    /**
     * Bind an action to a specific key/button.
     * Thread-safe for runtime rebinding.
     */
    public synchronized void bindAction(InputAction inputAction, List<KeyCode> binding) {
        // Add new binding
        actionBindings.put(inputAction, binding);
    }

    /**
     * Get the current binding for an action.
     */
    public List<KeyCode> getBindingForAction(InputAction inputAction) {
        return actionBindings.getOrDefault(inputAction, inputAction.getBinding());
    }

    /**
     * Check if a key/button is already bound to any action.
     */
    public boolean isBindingUsed(int binding) {
        return actionBindings.containsValue(binding);
    }
}
