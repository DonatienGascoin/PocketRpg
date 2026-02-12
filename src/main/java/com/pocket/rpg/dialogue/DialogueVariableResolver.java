package com.pocket.rpg.dialogue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Resolves AUTO variables from game state.
 * <p>
 * Each auto variable has a registered {@link Supplier}{@code <String>} that evaluates
 * the current value on demand. Registration happens once (e.g. in
 * {@code PlayerDialogueManager.onStart()}); the suppliers are evaluated fresh
 * each time {@link #resolveAutoVariables()} is called.
 */
public class DialogueVariableResolver {

    private final Map<String, Supplier<String>> resolvers = new LinkedHashMap<>();

    /**
     * Register a supplier for an auto variable.
     *
     * @param variableName the variable name (e.g. "PLAYER_NAME")
     * @param supplier     evaluated each time variables are resolved
     */
    public void register(String variableName, Supplier<String> supplier) {
        resolvers.put(variableName, supplier);
    }

    /**
     * Evaluates all registered auto variables and returns their current values.
     * Suppliers returning {@code null} are excluded from the result.
     */
    public Map<String, String> resolveAutoVariables() {
        Map<String, String> result = new HashMap<>();
        for (var entry : resolvers.entrySet()) {
            String value = entry.getValue().get();
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * Merges variables from three sources in order: AUTO → STATIC → RUNTIME.
     * Each layer overrides the previous for the same key.
     *
     * @param autoVars    auto-resolved variables (may be null)
     * @param staticVars  editor-set static variables (may be null)
     * @param runtimeVars programmatically provided runtime variables (may be null)
     * @return merged variable map
     */
    public static Map<String, String> mergeVariables(
            Map<String, String> autoVars,
            Map<String, String> staticVars,
            Map<String, String> runtimeVars) {
        Map<String, String> merged = new HashMap<>();
        if (autoVars != null) merged.putAll(autoVars);
        if (staticVars != null) merged.putAll(staticVars);
        if (runtimeVars != null) merged.putAll(runtimeVars);
        return merged;
    }
}
