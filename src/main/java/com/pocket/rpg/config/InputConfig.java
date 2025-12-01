package com.pocket.rpg.config;

import com.pocket.rpg.input.AxisConfig;
import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.input.InputAxis;
import com.pocket.rpg.input.KeyCode;

import java.util.*;

/**
 * Configuration for input bindings and axis settings.
 * <p>
 * Provides default configurations from enums, which can be overridden
 * by loading from a config file or changing at runtime (for rebinding UI).
 */
public class InputConfig {

    private final Map<InputAxis, AxisConfig> axisConfigs;
    public final Map<InputAction, List<KeyCode>> actionBindings;

    public InputConfig() {
        actionBindings = new HashMap<>();
        axisConfigs = new HashMap<>();
        loadDefaults();
    }

    /**
     * Load default bindings from InputAction enum.
     */
    private void loadDefaults() {
        // Load default action bindings
        for (InputAction action : InputAction.values()) {
            actionBindings.put(action, new ArrayList<>(action.getDefaultBinding()));
        }

        // Load default axis configurations
        for (InputAxis axis : InputAxis.values()) {
            axisConfigs.put(axis, axis.getDefaultConfig());
        }

    }// ========================================
    // Action Bindings
    // ========================================

    /**
     * Gets the current key bindings for an action.
     *
     * @param action The action to query
     * @return List of key codes bound to this action (never null)
     */
    public List<KeyCode> getBindingForAction(InputAction action) {
        return actionBindings.getOrDefault(action, List.of());
    }

    /**
     * Sets the key bindings for an action.
     * Replaces any existing bindings.
     *
     * @param action The action to bind
     * @param keys   The key codes to bind to this action
     */
    public void setBindingForAction(InputAction action, List<KeyCode> keys) {
        actionBindings.put(action, new ArrayList<>(keys));
    }

    /**
     * Convenience method to set bindings with varargs.
     *
     * @param action The action to bind
     * @param keys   The key codes to bind to this action
     */
    public void setBindingForAction(InputAction action, KeyCode... keys) {
        setBindingForAction(action, Arrays.asList(keys));
    }

    /**
     * Adds a key binding to an action.
     * Does not remove existing bindings.
     *
     * @param action The action to add a binding to
     * @param key    The key code to add
     */
    public void addBindingToAction(InputAction action, KeyCode key) {
        List<KeyCode> current = new ArrayList<>(getBindingForAction(action));
        if (!current.contains(key)) {
            current.add(key);
            actionBindings.put(action, current);
        }
    }

    /**
     * Removes a key binding from an action.
     *
     * @param action The action to remove a binding from
     * @param key    The key code to remove
     */
    public void removeBindingFromAction(InputAction action, KeyCode key) {
        List<KeyCode> current = new ArrayList<>(getBindingForAction(action));
        current.remove(key);
        actionBindings.put(action, current);
    }

    /**
     * Resets an action's bindings to defaults.
     *
     * @param action The action to reset
     */
    public void resetActionToDefault(InputAction action) {
        actionBindings.put(action, new ArrayList<>(action.getDefaultBinding()));
    }

    // ========================================
    // Axis Configurations
    // ========================================

    /**
     * Gets the current configuration for an axis.
     *
     * @param axis The axis to query
     * @return The axis configuration (never null)
     */
    public AxisConfig getAxisConfig(InputAxis axis) {
        return axisConfigs.get(axis);
    }

    /**
     * Sets the configuration for an axis.
     *
     * @param axis   The axis to configure
     * @param config The new configuration
     */
    public void setAxisConfig(InputAxis axis, AxisConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Axis config cannot be null");
        }
        axisConfigs.put(axis, config);
    }

    /**
     * Resets an axis configuration to defaults.
     *
     * @param axis The axis to reset
     */
    public void resetAxisToDefault(InputAxis axis) {
        axisConfigs.put(axis, axis.getDefaultConfig());
    }

    // ========================================
    // Global Operations
    // ========================================

    /**
     * Resets all bindings and axis configurations to defaults.
     */
    public void resetAllToDefaults() {
        actionBindings.clear();
        axisConfigs.clear();
        loadDefaults();
        System.out.println("InputConfig: Reset all to defaults");
    }

    /**
     * Gets all configured actions.
     *
     * @return Set of all actions with bindings
     */
    public Set<InputAction> getAllActions() {
        return EnumSet.allOf(InputAction.class);
    }

    /**
     * Gets all configured axes.
     *
     * @return Set of all axes with configurations
     */
    public Set<InputAxis> getAllAxes() {
        return EnumSet.allOf(InputAxis.class);
    }
}
