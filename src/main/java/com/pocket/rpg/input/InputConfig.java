package com.pocket.rpg.input;

import java.util.*;

/**
 * Manages input configuration and key rebinding.
 * Supports runtime rebinding and saving/loading configurations.
 */
public class InputConfig {
    // Action -> Current binding
    private Map<InputAction, Integer> actionBindings = new HashMap<>();

    // Reverse lookup: Binding -> Actions (supports multiple actions per key)
    private Map<Integer, List<InputAction>> bindingToActions = new HashMap<>();

    public InputConfig() {
        loadDefaults();
    }

    /**
     * Load default bindings from InputAction enum.
     */
    private void loadDefaults() {
        for (InputAction action : InputAction.values()) {
            bindAction(action, action.getDefaultBinding());
        }
    }

    /**
     * Bind an action to a specific key/button.
     * Thread-safe for runtime rebinding.
     */
    public synchronized void bindAction(InputAction action, int binding) {
        // Remove old binding
        Integer oldBinding = actionBindings.get(action);
        if (oldBinding != null) {
            List<InputAction> actions = bindingToActions.get(oldBinding);
            if (actions != null) {
                actions.remove(action);
                if (actions.isEmpty()) {
                    bindingToActions.remove(oldBinding);
                }
            }
        }

        // Add new binding
        actionBindings.put(action, binding);
        bindingToActions.computeIfAbsent(binding, k -> new ArrayList<>()).add(action);
    }

    /**
     * Get the current binding for an action.
     */
    public int getBindingForAction(InputAction action) {
        return actionBindings.getOrDefault(action, action.getDefaultBinding());
    }

    /**
     * Get all actions bound to a specific key/button.
     */
    public List<InputAction> getActionsForBinding(int binding) {
        return bindingToActions.getOrDefault(binding, Collections.emptyList());
    }

    /**
     * Check if a key/button is already bound to any action.
     */
    public boolean isBindingUsed(int binding) {
        return bindingToActions.containsKey(binding);
    }

    /**
     * Reset all bindings to defaults.
     */
    public void resetToDefaults() {
        actionBindings.clear();
        bindingToActions.clear();
        loadDefaults();
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