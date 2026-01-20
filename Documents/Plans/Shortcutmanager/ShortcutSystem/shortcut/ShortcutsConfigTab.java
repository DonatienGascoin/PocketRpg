package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.shortcut.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;

import java.util.*;

/**
 * Configuration tab for viewing and rebinding keyboard shortcuts.
 */
public class ShortcutsConfigTab implements ConfigTab {

    private final EditorContext context;
    private final ShortcutRegistry registry;

    private boolean dirty = false;

    // Filter
    private final ImString filterText = new ImString(64);
    private String selectedCategory = null;

    // Rebinding state
    private String rebindingActionId = null;
    private boolean waitingForKey = false;

    // Pending changes (applied on save)
    private final Map<String, ShortcutBinding> pendingChanges = new HashMap<>();
    private final Set<String> pendingResets = new HashSet<>();

    public ShortcutsConfigTab(EditorContext context) {
        this.context = context;
        this.registry = ShortcutRegistry.getInstance();
    }

    @Override
    public String getTabName() {
        return FontAwesomeIcons.Keyboard + " Shortcuts";
    }

    @Override
    public void initialize() {
        dirty = false;
        pendingChanges.clear();
        pendingResets.clear();
        rebindingActionId = null;
        waitingForKey = false;
        filterText.set("");
        selectedCategory = null;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void renderContent() {
        renderToolbar();
        ImGui.separator();

        if (waitingForKey) {
            renderRebindOverlay();
        }

        renderShortcutList();
    }

    @Override
    public void save() {
        // Apply pending changes
        for (Map.Entry<String, ShortcutBinding> entry : pendingChanges.entrySet()) {
            registry.setBinding(entry.getKey(), entry.getValue());
        }

        // Apply pending resets
        for (String actionId : pendingResets) {
            registry.resetToDefault(actionId);
        }

        pendingChanges.clear();
        pendingResets.clear();
        dirty = false;
    }

    @Override
    public void resetToDefaults() {
        // Mark all actions for reset
        for (ShortcutAction action : registry.getAllActions()) {
            if (registry.hasCustomBinding(action.getId())) {
                pendingResets.add(action.getId());
            }
        }
        pendingChanges.clear();
        dirty = !pendingResets.isEmpty();
    }

    // ========================================================================
    // UI RENDERING
    // ========================================================================

    private void renderToolbar() {
        // Filter input
        ImGui.setNextItemWidth(200);
        if (ImGui.inputTextWithHint("##filter", "Filter shortcuts...", filterText)) {
            // Filter applied automatically
        }

        ImGui.sameLine();

        // Category dropdown
        Set<String> categories = registry.getCategories();
        String currentCategory = selectedCategory != null ? selectedCategory : "All";

        ImGui.setNextItemWidth(150);
        if (ImGui.beginCombo("##category", currentCategory)) {
            if (ImGui.selectable("All", selectedCategory == null)) {
                selectedCategory = null;
            }
            for (String cat : categories) {
                String displayName = formatCategoryName(cat);
                if (ImGui.selectable(displayName, cat.equals(selectedCategory))) {
                    selectedCategory = cat;
                }
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();

        // Reset all button
        if (ImGui.button(FontAwesomeIcons.Undo + " Reset All")) {
            resetToDefaults();
        }
    }

    private void renderShortcutList() {
        String filter = filterText.get().toLowerCase();

        if (ImGui.beginChild("ShortcutList", 0, 0, true)) {
            String currentCategory = null;

            for (ShortcutAction action : registry.getAllActions()) {
                // Category filter
                if (selectedCategory != null && !action.getCategory().equals(selectedCategory)) {
                    continue;
                }

                // Text filter
                if (!filter.isEmpty()) {
                    boolean matches = action.getDisplayName().toLowerCase().contains(filter)
                            || action.getId().toLowerCase().contains(filter);
                    if (!matches) {
                        continue;
                    }
                }

                // Category header
                if (!action.getCategory().equals(currentCategory)) {
                    currentCategory = action.getCategory();
                    if (ImGui.getScrollY() > 0 || currentCategory != null) {
                        ImGui.spacing();
                    }
                    ImGui.textDisabled(formatCategoryName(currentCategory));
                    ImGui.separator();
                }

                renderShortcutRow(action);
            }
        }
        ImGui.endChild();
    }

    private void renderShortcutRow(ShortcutAction action) {
        String actionId = action.getId();
        boolean isRebinding = actionId.equals(rebindingActionId);
        boolean hasCustom = registry.hasCustomBinding(actionId) || pendingChanges.containsKey(actionId);
        boolean isPendingReset = pendingResets.contains(actionId);

        ImGui.pushID(actionId);

        // Display name
        if (hasCustom && !isPendingReset) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);
        }
        ImGui.text(action.getDisplayName());
        if (hasCustom && !isPendingReset) {
            ImGui.popStyleColor();
        }

        ImGui.sameLine(250);

        // Binding display/button
        ShortcutBinding binding = getEffectiveBinding(actionId);
        String bindingDisplay = binding != null ? binding.getDisplayString() : "(none)";

        if (isRebinding) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.4f, 0.1f, 1.0f);
            ImGui.button("Press key...", 150, 0);
            ImGui.popStyleColor();
        } else {
            if (ImGui.button(bindingDisplay + "##bind", 150, 0)) {
                startRebinding(actionId);
            }
        }

        // Scope indicator
        ImGui.sameLine();
        ImGui.textDisabled("(" + action.getScope().name().toLowerCase() + ")");

        // Reset button (if custom)
        if ((hasCustom && !isPendingReset) || pendingChanges.containsKey(actionId)) {
            ImGui.sameLine();
            if (ImGui.smallButton(FontAwesomeIcons.Undo + "##reset")) {
                if (pendingChanges.containsKey(actionId)) {
                    pendingChanges.remove(actionId);
                } else {
                    pendingResets.add(actionId);
                }
                updateDirtyState();
            }
            if (ImGui.isItemHovered()) {
                ShortcutBinding def = action.getDefaultBinding();
                ImGui.setTooltip("Reset to: " + (def != null ? def.getDisplayString() : "none"));
            }
        }

        // Conflict warning
        if (binding != null) {
            List<String> conflicts = registry.findConflicts(actionId, binding);
            if (!conflicts.isEmpty()) {
                ImGui.sameLine();
                ImGui.textColored(1f, 0.5f, 0.2f, 1f, FontAwesomeIcons.ExclamationTriangle);
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text("Conflicts with:");
                    for (String conflictId : conflicts) {
                        ShortcutAction conflictAction = registry.getAction(conflictId);
                        if (conflictAction != null) {
                            ImGui.bulletText(conflictAction.getDisplayName());
                        }
                    }
                    ImGui.endTooltip();
                }
            }
        }

        ImGui.popID();
    }

    private void renderRebindOverlay() {
        // Semi-transparent overlay
        ImGui.setNextWindowPos(ImGui.getWindowPosX(), ImGui.getWindowPosY());
        ImGui.setNextWindowSize(ImGui.getWindowWidth(), ImGui.getWindowHeight());

        if (ImGui.begin("##rebindOverlay",
                imgui.flag.ImGuiWindowFlags.NoTitleBar |
                        imgui.flag.ImGuiWindowFlags.NoResize |
                        imgui.flag.ImGuiWindowFlags.NoMove |
                        imgui.flag.ImGuiWindowFlags.NoScrollbar)) {

            float centerX = ImGui.getWindowWidth() / 2;
            float centerY = ImGui.getWindowHeight() / 2;

            ShortcutAction action = registry.getAction(rebindingActionId);
            String actionName = action != null ? action.getDisplayName() : rebindingActionId;

            String text1 = "Rebinding: " + actionName;
            String text2 = "Press a key combination or Escape to cancel";

            ImGui.setCursorPos(centerX - ImGui.calcTextSize(text1).x / 2, centerY - 20);
            ImGui.text(text1);

            ImGui.setCursorPos(centerX - ImGui.calcTextSize(text2).x / 2, centerY + 5);
            ImGui.textDisabled(text2);

            // Capture input
            ShortcutBinding captured = captureKeyBinding();
            if (captured != null) {
                if (captured.getKey() == ImGuiKey.Escape && !captured.isCtrl() && !captured.isShift() && !captured.isAlt()) {
                    // Cancel
                    cancelRebinding();
                } else {
                    // Apply
                    applyRebinding(captured);
                }
            }
        }
        ImGui.end();
    }

    // ========================================================================
    // REBINDING LOGIC
    // ========================================================================

    private void startRebinding(String actionId) {
        rebindingActionId = actionId;
        waitingForKey = true;
    }

    private void cancelRebinding() {
        rebindingActionId = null;
        waitingForKey = false;
    }

    private void applyRebinding(ShortcutBinding newBinding) {
        if (rebindingActionId == null) return;

        pendingChanges.put(rebindingActionId, newBinding);
        pendingResets.remove(rebindingActionId);
        updateDirtyState();

        rebindingActionId = null;
        waitingForKey = false;
    }

    private ShortcutBinding captureKeyBinding() {
        // Check all possible keys
        for (int key = ImGuiKey.NamedKey_BEGIN; key < ImGuiKey.NamedKey_END; key++) {
            // Skip modifier keys themselves
            if (isModifierKey(key)) continue;

            if (ImGui.isKeyPressed(key)) {
                boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
                boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
                boolean alt = ImGui.isKeyDown(ImGuiKey.LeftAlt) || ImGui.isKeyDown(ImGuiKey.RightAlt);

                return new ShortcutBinding(key, ctrl, shift, alt);
            }
        }
        return null;
    }

    private boolean isModifierKey(int key) {
        return key == ImGuiKey.LeftCtrl || key == ImGuiKey.RightCtrl ||
                key == ImGuiKey.LeftShift || key == ImGuiKey.RightShift ||
                key == ImGuiKey.LeftAlt || key == ImGuiKey.RightAlt ||
                key == ImGuiKey.LeftSuper || key == ImGuiKey.RightSuper;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private ShortcutBinding getEffectiveBinding(String actionId) {
        // Check pending changes first
        if (pendingChanges.containsKey(actionId)) {
            return pendingChanges.get(actionId);
        }

        // Check pending resets
        if (pendingResets.contains(actionId)) {
            ShortcutAction action = registry.getAction(actionId);
            return action != null ? action.getDefaultBinding() : null;
        }

        return registry.getBinding(actionId);
    }

    private void updateDirtyState() {
        dirty = !pendingChanges.isEmpty() || !pendingResets.isEmpty();
    }

    private String formatCategoryName(String category) {
        // "editor.file" -> "File"
        int lastDot = category.lastIndexOf('.');
        String name = lastDot >= 0 ? category.substring(lastDot + 1) : category;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
