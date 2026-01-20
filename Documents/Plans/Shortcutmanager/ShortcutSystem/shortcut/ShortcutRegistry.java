package com.pocket.rpg.editor.shortcut;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Central registry for all editor shortcuts.
 * Handles registration, binding management, and input processing.
 */
@Slf4j
public class ShortcutRegistry {

    private static ShortcutRegistry instance;

    // All registered actions by ID
    private final Map<String, ShortcutAction> actions = new LinkedHashMap<>();

    // Custom bindings (overrides defaults)
    private final Map<String, ShortcutBinding> customBindings = new HashMap<>();

    // Lookup: binding -> list of actions (for conflict detection and processing)
    private final Map<ShortcutBinding, List<ShortcutAction>> bindingToActions = new HashMap<>();

    // Config persistence
    private ShortcutConfig config;
    private String configPath;

    // Prevent duplicate firing in same frame
    private long lastProcessedFrame = -1;

    private ShortcutRegistry() {
    }

    public static ShortcutRegistry getInstance() {
        if (instance == null) {
            instance = new ShortcutRegistry();
        }
        return instance;
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers an action with its default binding.
     */
    public void register(ShortcutAction action) {
        if (actions.containsKey(action.getId())) {
            log.warn("Shortcut action already registered: {}", action.getId());
            return;
        }

        actions.put(action.getId(), action);
        rebuildBindingIndex();
        log.debug("Registered shortcut: {}", action);
    }

    /**
     * Registers multiple actions.
     */
    public void registerAll(ShortcutAction... actionsToRegister) {
        for (ShortcutAction action : actionsToRegister) {
            if (!actions.containsKey(action.getId())) {
                actions.put(action.getId(), action);
            }
        }
        rebuildBindingIndex();
    }

    /**
     * Unregisters an action by ID.
     */
    public void unregister(String actionId) {
        actions.remove(actionId);
        rebuildBindingIndex();
    }

    // ========================================================================
    // BINDING MANAGEMENT
    // ========================================================================

    /**
     * Gets the effective binding for an action (custom or default).
     */
    public ShortcutBinding getBinding(String actionId) {
        if (customBindings.containsKey(actionId)) {
            return customBindings.get(actionId);
        }
        ShortcutAction action = actions.get(actionId);
        return action != null ? action.getDefaultBinding() : null;
    }

    /**
     * Sets a custom binding for an action.
     * Pass null to remove custom binding (revert to default).
     */
    public void setBinding(String actionId, ShortcutBinding binding) {
        if (binding == null) {
            customBindings.remove(actionId);
        } else {
            customBindings.put(actionId, binding);
        }
        rebuildBindingIndex();

        if (config != null) {
            saveConfig();
        }
    }

    /**
     * Resets an action to its default binding.
     */
    public void resetToDefault(String actionId) {
        customBindings.remove(actionId);
        rebuildBindingIndex();

        if (config != null) {
            saveConfig();
        }
    }

    /**
     * Resets all bindings to defaults.
     */
    public void resetAllToDefaults() {
        customBindings.clear();
        rebuildBindingIndex();

        if (config != null) {
            saveConfig();
        }
    }

    /**
     * Checks if an action has a custom binding.
     */
    public boolean hasCustomBinding(String actionId) {
        return customBindings.containsKey(actionId);
    }

    /**
     * Finds conflicts for a potential binding.
     * Returns list of action IDs that would conflict.
     */
    public List<String> findConflicts(String actionId, ShortcutBinding binding) {
        List<String> conflicts = new ArrayList<>();

        ShortcutAction action = actions.get(actionId);
        if (action == null || binding == null) {
            return conflicts;
        }

        List<ShortcutAction> actionsWithBinding = bindingToActions.get(binding);
        if (actionsWithBinding != null) {
            for (ShortcutAction other : actionsWithBinding) {
                if (!other.getId().equals(actionId)) {
                    // Check if scopes could conflict
                    if (scopesCanConflict(action, other)) {
                        conflicts.add(other.getId());
                    }
                }
            }
        }

        return conflicts;
    }

    private boolean scopesCanConflict(ShortcutAction a, ShortcutAction b) {
        // GLOBAL conflicts with everything
        if (a.getScope() == ShortcutScope.GLOBAL || b.getScope() == ShortcutScope.GLOBAL) {
            return true;
        }

        // Same scope and panel = conflict
        if (a.getScope() == b.getScope()) {
            String panelA = a.getPanelId();
            String panelB = b.getPanelId();
            return Objects.equals(panelA, panelB);
        }

        return false;
    }

    private void rebuildBindingIndex() {
        bindingToActions.clear();

        for (ShortcutAction action : actions.values()) {
            ShortcutBinding binding = getBinding(action.getId());
            if (binding != null) {
                bindingToActions.computeIfAbsent(binding, k -> new ArrayList<>()).add(action);
            }
        }
    }

    // ========================================================================
    // INPUT PROCESSING
    // ========================================================================

    /**
     * Processes shortcuts for the current frame.
     * Call early in the frame, before ImGui windows.
     *
     * @param context Current UI context
     * @return true if a shortcut was handled
     */
    public boolean processShortcuts(ShortcutContext context) {
        // Prevent double-processing in same frame
        long currentFrame = imgui.ImGui.getFrameCount();
        if (currentFrame == lastProcessedFrame) {
            return false;
        }

        for (Map.Entry<ShortcutBinding, List<ShortcutAction>> entry : bindingToActions.entrySet()) {
            ShortcutBinding binding = entry.getKey();

            if (binding.isPressed()) {
                ShortcutAction matched = findBestMatch(entry.getValue(), context);

                if (matched != null) {
                    log.debug("Executing shortcut: {} [{}]", matched.getId(), binding);
                    matched.execute();
                    lastProcessedFrame = currentFrame;
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Finds the best matching action for the current context.
     * Returns null if no action is applicable.
     */
    private ShortcutAction findBestMatch(List<ShortcutAction> candidates, ShortcutContext context) {
        ShortcutAction best = null;

        for (ShortcutAction action : candidates) {
            if (!action.isApplicable(context)) {
                continue;
            }

            if (best == null || action.getScope().higherPriorityThan(best.getScope())) {
                best = action;
            }
        }

        return best;
    }

    // ========================================================================
    // CONFIG PERSISTENCE
    // ========================================================================

    /**
     * Loads custom bindings from config file.
     */
    public void loadConfig(String path) {
        this.configPath = path;
        this.config = ShortcutConfig.load(path);

        if (config != null) {
            customBindings.clear();
            customBindings.putAll(config.getBindings());
            rebuildBindingIndex();
            log.info("Loaded {} custom shortcut bindings from {}", customBindings.size(), path);
        }
    }

    /**
     * Saves current custom bindings to config file.
     */
    public void saveConfig() {
        if (configPath == null) {
            log.warn("No config path set, cannot save shortcuts");
            return;
        }

        if (config == null) {
            config = new ShortcutConfig();
        }

        config.setBindings(customBindings);
        config.save(configPath);
        log.debug("Saved shortcut config to {}", configPath);
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Gets all registered actions.
     */
    public Collection<ShortcutAction> getAllActions() {
        return Collections.unmodifiableCollection(actions.values());
    }

    /**
     * Gets an action by ID.
     */
    public ShortcutAction getAction(String id) {
        return actions.get(id);
    }

    /**
     * Gets all actions in a category.
     */
    public List<ShortcutAction> getActionsByCategory(String category) {
        return actions.values().stream()
                .filter(a -> a.getCategory().equals(category))
                .toList();
    }

    /**
     * Gets all unique categories.
     */
    public Set<String> getCategories() {
        Set<String> categories = new TreeSet<>();
        for (ShortcutAction action : actions.values()) {
            categories.add(action.getCategory());
        }
        return categories;
    }

    /**
     * Gets the display string for an action's current binding.
     */
    public String getBindingDisplay(String actionId) {
        ShortcutBinding binding = getBinding(actionId);
        return binding != null ? binding.getDisplayString() : "";
    }

    /**
     * Clears all registrations (for testing or reload).
     */
    public void clear() {
        actions.clear();
        customBindings.clear();
        bindingToActions.clear();
    }
}
