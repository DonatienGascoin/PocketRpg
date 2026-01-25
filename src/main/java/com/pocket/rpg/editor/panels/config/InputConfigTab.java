package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.input.AxisConfig;
import com.pocket.rpg.input.AxisType;
import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.input.InputAxis;
import com.pocket.rpg.input.KeyCode;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration tab for input settings.
 * Uses live editing model - edits apply directly to the live InputConfig.
 */
public class InputConfigTab implements ConfigTab {

    private final EditorContext context;
    private final Runnable markDirty;

    // Popup state
    private InputAction selectedAction = null;
    private boolean openBindingPopup = false;

    public InputConfigTab(EditorContext context, Runnable markDirty) {
        this.context = context;
        this.markDirty = markDirty;
    }

    @Override
    public String getTabName() {
        return "Input";
    }

    @Override
    public void save() {
        ConfigLoader.saveConfigToFile(context.getInputConfig(), ConfigLoader.ConfigType.INPUT);
    }

    @Override
    public void revert() {
        ConfigLoader.loadAllConfigs();
    }

    @Override
    public void resetToDefaults() {
        context.getInputConfig().resetAllToDefaults();
    }

    @Override
    public void renderContent() {
        ImGui.pushID("InputTab");

        if (ImGui.collapsingHeader("Action Bindings", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderActionBindings();
        }

        if (ImGui.collapsingHeader("Axis Configurations")) {
            renderAxisConfigs();
        }

        // Popup must be rendered at same level as the trigger
        if (openBindingPopup) {
            ImGui.openPopup("BindingPopup");
            openBindingPopup = false;
        }
        renderBindingEditPopup();

        ImGui.popID();
    }

    private void renderActionBindings() {
        ImGui.indent();

        InputConfig config = context.getInputConfig();

        for (InputAction action : InputAction.values()) {
            ImGui.pushID(action.ordinal());

            List<KeyCode> bindings = config.getBindingForAction(action);
            StringBuilder bindingText = new StringBuilder();
            for (int i = 0; i < bindings.size(); i++) {
                if (i > 0) bindingText.append(", ");
                bindingText.append(formatKeyName(bindings.get(i)));
            }

            ImGui.text(action.name());
            ImGui.sameLine(180);
            ImGui.textDisabled(bindingText.length() > 0 ? bindingText.toString() : "(none)");

            ImGui.sameLine(ImGui.getContentRegionAvailX() - 60);
            if (ImGui.smallButton("Edit")) {
                selectedAction = action;
                openBindingPopup = true;
            }

            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.Undo)) {
                config.resetActionToDefault(action);
                markDirty.run();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Reset to default binding");
            }

            ImGui.popID();
        }

        ImGui.unindent();
    }

    private void renderBindingEditPopup() {
        if (ImGui.beginPopup("BindingPopup")) {
            if (selectedAction != null) {
                InputConfig config = context.getInputConfig();

                ImGui.text("Edit bindings for: " + selectedAction.name());
                ImGui.separator();

                List<KeyCode> bindings = new ArrayList<>(config.getBindingForAction(selectedAction));
                boolean modified = false;

                for (int i = 0; i < bindings.size(); i++) {
                    ImGui.pushID(i);
                    KeyCode current = bindings.get(i);

                    ImGui.setNextItemWidth(150);
                    if (ImGui.beginCombo("Key", formatKeyName(current))) {
                        for (KeyCode key : KeyCode.values()) {
                            boolean isSelected = key == current;
                            if (ImGui.selectable(formatKeyName(key), isSelected)) {
                                bindings.set(i, key);
                                modified = true;
                            }
                            if (isSelected) {
                                ImGui.setItemDefaultFocus();
                            }
                        }
                        ImGui.endCombo();
                    }

                    ImGui.sameLine();
                    if (ImGui.smallButton(MaterialIcons.Delete)) {
                        bindings.remove(i);
                        modified = true;
                        ImGui.popID();
                        break;
                    }

                    ImGui.popID();
                }

                if (modified) {
                    config.setBindingForAction(selectedAction, bindings);
                    markDirty.run();
                }

                ImGui.spacing();
                if (ImGui.button(MaterialIcons.Add + " Add Key")) {
                    bindings.add(KeyCode.SPACE); // Default to space, not unknown
                    config.setBindingForAction(selectedAction, bindings);
                    markDirty.run();
                }

                ImGui.separator();
                if (ImGui.button("Done")) {
                    ImGui.closeCurrentPopup();
                    selectedAction = null;
                }
            }
            ImGui.endPopup();
        }
    }

    private void renderAxisConfigs() {
        ImGui.indent();

        InputConfig config = context.getInputConfig();

        for (InputAxis axis : InputAxis.values()) {
            ImGui.pushID(axis.ordinal());

            AxisConfig axisConfig = config.getAxisConfig(axis);

            if (axisConfig == null) {
                ImGui.textDisabled(axis.name() + ": (no config)");
                ImGui.popID();
                continue;
            }

            if (ImGui.treeNode(axis.name())) {
                renderAxisConfig(axisConfig);
                ImGui.treePop();
            } else {
                ImGui.sameLine();
                ImGui.textDisabled(getAxisSummary(axisConfig));
            }

            ImGui.popID();
        }

        ImGui.unindent();
    }

    private void renderAxisConfig(AxisConfig config) {
        ImGui.text("Type: ");
        ImGui.sameLine();
        ImGui.textColored(0.5f, 0.8f, 1.0f, 1.0f, config.type().name());

        switch (config.type()) {
            case KEYBOARD -> renderKeyboardAxisConfig(config);
            case GAMEPAD -> renderGamepadAxisConfig(config);
            case MOUSE_DELTA, MOUSE_WHEEL -> ImGui.textDisabled("Mouse input - no key bindings");
            case COMPOSITE -> renderCompositeAxisConfig(config);
        }

        ImGui.spacing();
        ImGui.textDisabled("Settings:");
        ImGui.indent();

        ImGui.text(String.format("Sensitivity: %.2f", config.sensitivity()));

        if (config.type() == AxisType.KEYBOARD || config.type() == AxisType.GAMEPAD) {
            ImGui.text(String.format("Gravity: %.2f", config.gravity()));
            ImGui.text(String.format("Dead Zone: %.3f", config.deadZone()));
            ImGui.text(String.format("Snap: %s", config.snap() ? "Yes" : "No"));
        }

        ImGui.unindent();
    }

    private void renderKeyboardAxisConfig(AxisConfig config) {
        ImGui.spacing();
        ImGui.textDisabled("Key Bindings:");
        ImGui.indent();

        if (config.positiveKey() != null) {
            ImGui.text("Positive: ");
            ImGui.sameLine();
            renderKeyBadge(config.positiveKey());
        }

        if (config.negativeKey() != null) {
            ImGui.text("Negative: ");
            ImGui.sameLine();
            renderKeyBadge(config.negativeKey());
        }

        if (config.altPositiveKey() != null) {
            ImGui.text("Alt Positive: ");
            ImGui.sameLine();
            renderKeyBadge(config.altPositiveKey());
        }

        if (config.altNegativeKey() != null) {
            ImGui.text("Alt Negative: ");
            ImGui.sameLine();
            renderKeyBadge(config.altNegativeKey());
        }

        ImGui.unindent();
    }

    private void renderGamepadAxisConfig(AxisConfig config) {
        ImGui.spacing();
        ImGui.textDisabled("Gamepad:");
        ImGui.indent();

        if (config.gamepadAxis() != null) {
            ImGui.text("Analog Axis: ");
            ImGui.sameLine();
            ImGui.textColored(0.8f, 0.8f, 0.2f, 1.0f, config.gamepadAxis().name());
        }

        if (config.positiveButton() != null) {
            ImGui.text("Positive Button: ");
            ImGui.sameLine();
            ImGui.textColored(0.8f, 0.8f, 0.2f, 1.0f, config.positiveButton().name());
        }

        if (config.negativeButton() != null) {
            ImGui.text("Negative Button: ");
            ImGui.sameLine();
            ImGui.textColored(0.8f, 0.8f, 0.2f, 1.0f, config.negativeButton().name());
        }

        ImGui.unindent();
    }

    private void renderCompositeAxisConfig(AxisConfig config) {
        ImGui.spacing();
        ImGui.textDisabled("Sources:");
        ImGui.indent();

        if (config.sources() != null) {
            for (int i = 0; i < config.sources().length; i++) {
                AxisConfig source = config.sources()[i];
                ImGui.text(String.format("[%d] %s", i, source.type().name()));
                ImGui.sameLine();
                ImGui.textDisabled(getAxisSummary(source));
            }
        }

        ImGui.unindent();
    }

    private void renderKeyBadge(KeyCode key) {
        String name = formatKeyName(key);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.5f, 1.0f);
        ImGui.smallButton(name);
        ImGui.popStyleColor();
    }

    private String getAxisSummary(AxisConfig config) {
        if (config == null) return "";

        return switch (config.type()) {
            case KEYBOARD -> {
                StringBuilder sb = new StringBuilder();
                if (config.positiveKey() != null && config.negativeKey() != null) {
                    sb.append(formatKeyName(config.negativeKey()))
                            .append("/")
                            .append(formatKeyName(config.positiveKey()));
                }
                if (config.altPositiveKey() != null && config.altNegativeKey() != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(formatKeyName(config.altNegativeKey()))
                            .append("/")
                            .append(formatKeyName(config.altPositiveKey()));
                }
                yield sb.toString();
            }
            case GAMEPAD -> {
                if (config.gamepadAxis() != null) {
                    yield config.gamepadAxis().name();
                } else if (config.positiveButton() != null) {
                    yield config.positiveButton().name() + "/" + config.negativeButton().name();
                }
                yield "";
            }
            case MOUSE_DELTA -> "Mouse Delta";
            case MOUSE_WHEEL -> "Mouse Wheel";
            case COMPOSITE -> config.sources().length + " sources";
        };
    }

    private String formatKeyName(KeyCode key) {
        if (key == null) return "None";
        String name = key.name();
        if (name.startsWith("KEY_")) {
            return name.substring(4);
        }
        return name;
    }
}
