package com.pocket.rpg.editor.shortcut;

import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.PlayModeStartedEvent;
import com.pocket.rpg.editor.events.PlayModeStoppedEvent;
import imgui.ImGui;
import imgui.flag.ImGuiPopupFlags;

import java.util.*;

/**
 * Central registry for all editor shortcuts.
 * Handles registration, binding management, and input processing.
 */
public class ShortcutRegistry {

    private static ShortcutRegistry instance;

    // All registered actions by ID
    private final Map<String, ShortcutAction> actions = new LinkedHashMap<>();

    // Custom bindings (overrides defaults)
    private final Map<String, ShortcutBinding> customBindings = new HashMap<>();

    // Lookup: binding -> list of actions (for conflict detection and processing)
    private final Map<ShortcutBinding, List<ShortcutAction>> bindingToActions = new HashMap<>();

    // Sorted bindings for processing (more modifiers first, so Ctrl+S is checked before S)
    private List<Map.Entry<ShortcutBinding, List<ShortcutAction>>> sortedBindings = List.of();

    // Config persistence
    private ShortcutConfig config;
    private String configPath;
    private KeyboardLayout keyboardLayout = KeyboardLayout.QWERTY;

    // Prevent duplicate firing in same frame
    private long lastProcessedFrame = -1;

    // Keys consumed by a modifier shortcut, suppressed until fully released.
    // Prevents e.g. Ctrl+S from also triggering S when Ctrl is released first.
    private final Set<Integer> consumedKeys = new HashSet<>();

    // Play mode state (updated via event bus)
    private boolean playModeActive = false;

    private ShortcutRegistry() {
        EditorEventBus.get().subscribe(PlayModeStartedEvent.class, e -> playModeActive = true);
        EditorEventBus.get().subscribe(PlayModeStoppedEvent.class, e -> playModeActive = false);
    }

    public static ShortcutRegistry getInstance() {
        if (instance == null) {
            instance = new ShortcutRegistry();
        }
        return instance;
    }

    /**
     * Resets the singleton instance. Only use for testing.
     */
    public static void resetInstance() {
        instance = null;
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers an action with its default binding.
     */
    public void register(ShortcutAction action) {
        if (actions.containsKey(action.getId())) {
            System.err.println("[ShortcutRegistry] Shortcut action already registered: " + action.getId());
            return;
        }

        actions.put(action.getId(), action);
        rebuildBindingIndex();
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
        // GLOBAL conflicts with everything except panel-scoped
        if (a.getScope() == ShortcutScope.GLOBAL && b.getScope() == ShortcutScope.GLOBAL) {
            return true;
        }

        // Same scope and panel = conflict
        if (a.getScope() == b.getScope()) {
            String panelA = a.getPanelId();
            String panelB = b.getPanelId();
            return Objects.equals(panelA, panelB);
        }

        // POPUP scope only conflicts with other POPUP
        if (a.getScope() == ShortcutScope.POPUP || b.getScope() == ShortcutScope.POPUP) {
            return a.getScope() == b.getScope();
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

        // Sort bindings by modifier count descending so more specific shortcuts
        // (e.g. Ctrl+S) are checked before less specific ones (e.g. S)
        sortedBindings = new ArrayList<>(bindingToActions.entrySet());
        sortedBindings.sort((a, b) -> Integer.compare(
                b.getKey().getModifierCount(),
                a.getKey().getModifierCount()
        ));
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
        long currentFrame = ImGui.getFrameCount();
        if (currentFrame == lastProcessedFrame) {
            return false;
        }

        // Don't process shortcuts when play mode is active (game owns the keyboard)
        if (playModeActive) {
            return false;
        }

        // Don't process shortcuts when a popup is open (popups handle their own input)
        if (ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopup)) {
            return false;
        }

        // Release consumed keys that are no longer held
        consumedKeys.removeIf(key -> !ImGui.isKeyDown(key));

        for (Map.Entry<ShortcutBinding, List<ShortcutAction>> entry : sortedBindings) {
            ShortcutBinding binding = entry.getKey();

            // Skip keys consumed by a previous modifier shortcut
            if (consumedKeys.contains(binding.getKey())) {
                continue;
            }

            if (binding.isPressed()) {
                ShortcutAction matched = findBestMatch(entry.getValue(), context);

                if (matched != null) {
                    matched.execute();
                    lastProcessedFrame = currentFrame;

                    // If this shortcut uses modifiers, consume the base key
                    // so releasing the modifier won't trigger the plain key shortcut
                    if (binding.getModifierCount() > 0) {
                        consumedKeys.add(binding.getKey());
                    }

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
     * Loads keyboard layout from config file (call before registerDefaults).
     * Returns the keyboard layout to use for default bindings.
     */
    public KeyboardLayout loadConfigAndGetLayout(String path) {
        this.configPath = path;
        this.config = ShortcutConfig.load(path);

        if (config != null) {
            this.keyboardLayout = config.getKeyboardLayout();
            System.out.println("[ShortcutRegistry] Keyboard layout: " + keyboardLayout);
        }

        return keyboardLayout;
    }

    /**
     * Applies bindings from config after actions are registered.
     * Call this after registerDefaults.
     */
    public void applyConfigBindings() {
        if (config == null) {
            return;
        }

        // Get bindings for the active layout
        Map<String, ShortcutBinding> layoutBindings = config.getActiveBindings();
        if (layoutBindings != null && !layoutBindings.isEmpty()) {
            for (Map.Entry<String, ShortcutBinding> entry : layoutBindings.entrySet()) {
                if (actions.containsKey(entry.getKey()) && entry.getValue() != null) {
                    customBindings.put(entry.getKey(), entry.getValue());
                }
            }
            rebuildBindingIndex();
        }
    }

    /**
     * Gets the current keyboard layout.
     */
    public KeyboardLayout getKeyboardLayout() {
        return keyboardLayout;
    }

    /**
     * Saves ALL shortcuts to config file for both layouts.
     */
    public void saveConfig() {
        if (configPath == null) {
            System.err.println("[ShortcutRegistry] No config path set, cannot save shortcuts");
            return;
        }

        if (config == null) {
            config = new ShortcutConfig();
        }

        config.setKeyboardLayout(keyboardLayout);

        // Save current bindings to the active layout
        Map<String, ShortcutBinding> currentBindings = new LinkedHashMap<>();
        for (ShortcutAction action : actions.values()) {
            ShortcutBinding binding = getBinding(action.getId());
            currentBindings.put(action.getId(), binding);
        }
        config.setBindingsForLayout(keyboardLayout, currentBindings);

        config.save(configPath);
    }

    /**
     * Generates and saves a complete config file with shortcuts for BOTH layouts.
     * Call this after all shortcuts are registered.
     *
     * @param defaultsGenerator Function that returns default bindings for a given layout
     */
    public void generateCompleteConfig(java.util.function.Function<KeyboardLayout, Map<String, ShortcutBinding>> defaultsGenerator) {
        if (configPath == null) {
            System.err.println("[ShortcutRegistry] No config path set, cannot generate config");
            return;
        }

        if (config == null) {
            config = new ShortcutConfig();
        }

        config.setKeyboardLayout(keyboardLayout);

        // Generate/update bindings for both layouts
        for (KeyboardLayout layout : KeyboardLayout.values()) {
            Map<String, ShortcutBinding> existingBindings = config.getBindingsForLayout(layout);

            if (existingBindings == null || existingBindings.isEmpty()) {
                // No existing bindings - generate defaults
                Map<String, ShortcutBinding> defaults = defaultsGenerator.apply(layout);
                config.setBindingsForLayout(layout, defaults);
                System.out.println("[ShortcutRegistry] Generated " + layout + " defaults with " + defaults.size() + " shortcuts");
            } else {
                // Merge: add any new shortcuts that aren't in existing config
                Map<String, ShortcutBinding> defaults = defaultsGenerator.apply(layout);
                Map<String, ShortcutBinding> merged = new LinkedHashMap<>(existingBindings);
                for (Map.Entry<String, ShortcutBinding> entry : defaults.entrySet()) {
                    if (!merged.containsKey(entry.getKey())) {
                        merged.put(entry.getKey(), entry.getValue());
                    }
                }
                config.setBindingsForLayout(layout, merged);
            }
        }

        config.save(configPath);
        System.out.println("[ShortcutRegistry] Generated complete config for both layouts at " + configPath);
    }

    /**
     * @deprecated Use {@link #generateCompleteConfig(java.util.function.Function)} instead
     */
    @Deprecated
    public void generateCompleteConfig() {
        saveConfig();
        System.out.println("[ShortcutRegistry] Generated config with " + actions.size() + " shortcuts at " + configPath);
    }

    // ========================================================================
    // HANDLER BINDING
    // ========================================================================

    /**
     * Binds a handler to an existing action.
     * Use this to connect actual implementations after registration.
     */
    public void bindHandler(String actionId, Runnable handler) {
        ShortcutAction action = actions.get(actionId);
        if (action != null) {
            action.setHandler(handler);
        } else {
            System.err.println("[ShortcutRegistry] Cannot bind handler: action " + actionId + " not found");
        }
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
     * Checks if a shortcut action's binding is currently held down.
     * Uses exact modifier matching, so holding Ctrl+S won't report the plain S action as held.
     *
     * @param actionId The action ID to check
     * @param context  The current shortcut context (for scope/applicability checks)
     * @return true if the action's binding is held and the action is applicable
     */
    public boolean isActionHeld(String actionId, ShortcutContext context) {
        ShortcutAction action = actions.get(actionId);
        if (action == null) {
            return false;
        }

        if (!action.isApplicable(context)) {
            return false;
        }

        ShortcutBinding binding = getBinding(actionId);
        return binding != null && binding.isHeld();
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
