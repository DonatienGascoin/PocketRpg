package com.pocket.rpg.config;

import com.pocket.rpg.input.InputAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputConfig {

    public Map<InputAction, List<Integer>> actionBindings;

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
    }

    /**
     * Bind an action to a specific key/button.
     * Thread-safe for runtime rebinding.
     */
    public synchronized void bindAction(InputAction inputAction, List<Integer> binding) {
        // Add new binding
        actionBindings.put(inputAction, binding);
    }

    /**
     * Get the current binding for an action.
     */
    public List<Integer> getBindingForAction(InputAction inputAction) {
        return actionBindings.getOrDefault(inputAction, inputAction.getBinding());
    }

    /**
     * Check if a key/button is already bound to any action.
     */
    public boolean isBindingUsed(int binding) {
        return actionBindings.containsValue(binding);
    }

    /**
     * Save bindings to file (JSON/Properties).
     * TODO: Implement serialization
     */
    public void save(String filepath) {
        System.out.println("TODO: Save input config to " + filepath);
    }

    /**
     * Load bindings from file.
     * TODO: Implement deserialization
     */
    public void load(String filepath) {
        System.out.println("TODO: Load input config from " + filepath);
    }
}
