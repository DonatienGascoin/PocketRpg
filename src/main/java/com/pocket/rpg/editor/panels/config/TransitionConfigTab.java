package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.config.TransitionEntry;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ConfigListCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/**
 * Configuration tab for transition settings.
 * Uses live editing model - edits apply directly to the live TransitionConfig.
 */
public class TransitionConfigTab implements ConfigTab {

    private final EditorContext context;
    private final Runnable markDirty;
    private final AssetPickerPopup assetPicker = new AssetPickerPopup();

    public TransitionConfigTab(EditorContext context, Runnable markDirty) {
        this.context = context;
        this.markDirty = markDirty;
    }

    @Override
    public String getTabName() {
        return "Transition";
    }

    @Override
    public void save() {
        // TransitionConfig is now part of RenderingConfig
        ConfigLoader.saveConfigToFile(context.getRenderingConfig(), ConfigLoader.ConfigType.RENDERING);
    }

    @Override
    public void revert() {
        ConfigLoader.loadAllConfigs();
    }

    @Override
    public void resetToDefaults() {
        RenderingConfig renderingConfig = context.getRenderingConfig();
        TransitionConfig config = renderingConfig.getDefaultTransitionConfig();
        TransitionConfig defaults = new TransitionConfig();

        config.setFadeOutDuration(defaults.getFadeOutDuration());
        config.setFadeInDuration(defaults.getFadeInDuration());
        config.getFadeColor().set(defaults.getFadeColor());
        config.setTransitionName(defaults.getTransitionName());

        // Reset rendering config transition fields
        renderingConfig.getTransitions().clear();
        renderingConfig.setDefaultTransitionName("");
    }

    @Override
    public void renderContent() {
        ImGui.pushID("TransitionTab");

        RenderingConfig renderingConfig = context.getRenderingConfig();
        TransitionConfig config = renderingConfig.getDefaultTransitionConfig();

        // Timing section
        if (ImGui.collapsingHeader("Timing", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawFloat("Fade Out Duration", "fadeOut",
                    () -> (double) config.getFadeOutDuration(),
                    v -> { config.setFadeOutDuration(Math.max(0, (float) v)); markDirty.run(); },
                    0.01f, 0.0f, 5.0f, "%.2f s");
            tooltip("Duration of the fade-out phase in seconds");

            FieldEditors.drawFloat("Fade In Duration", "fadeIn",
                    () -> (double) config.getFadeInDuration(),
                    v -> { config.setFadeInDuration(Math.max(0, (float) v)); markDirty.run(); },
                    0.01f, 0.0f, 5.0f, "%.2f s");
            tooltip("Duration of the fade-in phase in seconds");

            ImGui.unindent();
        }

        // Appearance section
        if (ImGui.collapsingHeader("Appearance", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawColor("Fade Color", "fadeColor",
                    config::getFadeColor,
                    v -> { config.getFadeColor().set(v); markDirty.run(); });
            tooltip("Color of the transition overlay");

            ImGui.unindent();
        }

        // Default transition name section
        if (ImGui.collapsingHeader("Default Transition", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            drawTransitionNameDropdown("Default Transition", "defaultTransition",
                    renderingConfig.getDefaultTransitionName(),
                    name -> {
                        String oldName = renderingConfig.getDefaultTransitionName();
                        UndoManager.getInstance().execute(new SetterUndoCommand<>(
                                v -> { renderingConfig.setDefaultTransitionName(v); markDirty.run(); },
                                oldName, name, "Change default transition"));
                    },
                    renderingConfig.getTransitions(), true);
            tooltip("Default transition used when no specific transition is requested");

            ImGui.unindent();
        }

        // Transition entries list
        if (ImGui.collapsingHeader("Transition Patterns", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            drawTransitionEntries(renderingConfig);

            ImGui.unindent();
        }

        // Preview section
        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled(String.format("Total duration: %.2f seconds", config.getTotalDuration()));
        ImGui.textDisabled(String.format("Transitions defined: %d", renderingConfig.getTransitions().size()));

        // Render asset picker popup (local instance for this tab)
        assetPicker.render();

        ImGui.popID();
    }

    /**
     * Draws a dropdown for selecting a transition name.
     *
     * @param label         display label
     * @param key           unique ImGui key
     * @param currentName   current selected name
     * @param setter        callback when selection changes
     * @param entries       available transition entries
     * @param includeFade   whether to include the "(Fade)" option
     */
    private void drawTransitionNameDropdown(String label, String key, String currentName,
                                             java.util.function.Consumer<String> setter,
                                             List<TransitionEntry> entries,
                                             boolean includeFade) {
        String display;
        if (currentName == null || currentName.isEmpty()) {
            display = "(Fade)";
        } else {
            display = currentName;
        }

        FieldEditors.inspectorRow(label, () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##" + key, display)) {
                // Fade option (empty name)
                if (includeFade) {
                    boolean isFade = currentName == null || currentName.isEmpty();
                    if (ImGui.selectable("(Fade)", isFade)) {
                        setter.accept("");
                    }
                }

                // Random option
                boolean isRandom = "Random".equals(currentName);
                if (ImGui.selectable("Random", isRandom)) {
                    setter.accept("Random");
                }

                if (!entries.isEmpty()) {
                    ImGui.separator();
                }

                // Named entries
                for (TransitionEntry entry : entries) {
                    String name = entry.getName();
                    if (name == null || name.isEmpty()) continue;
                    boolean isSelected = name.equals(currentName);
                    if (ImGui.selectable(name, isSelected)) {
                        setter.accept(name);
                    }
                }

                ImGui.endCombo();
            }
        });
    }

    /**
     * Draws the list of transition entries matching the ListEditor visual style.
     * Header with count + add button, each entry shows index, name, sprite, and remove button.
     */
    private void drawTransitionEntries(RenderingConfig renderingConfig) {
        List<TransitionEntry> entries = renderingConfig.getTransitions();
        int count = entries.size();
        String headerLabel = "Entries (" + count + ")";

        ImGui.pushID("transitionEntries");

        int flags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap;
        boolean isOpen = ImGui.treeNodeEx(headerLabel, flags);

        // Add button on same line as header
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
        if (ImGui.smallButton(MaterialIcons.Add + "##addEntry")) {
            UndoManager.getInstance().execute(
                    ConfigListCommand.add(entries, new TransitionEntry("", null), "transition entry", markDirty));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add transition entry");
        }

        if (isOpen) {
            if (entries.isEmpty()) {
                ImGui.textDisabled("Empty");
            } else {
                int removeIndex = -1;

                for (int i = 0; i < entries.size(); i++) {
                    TransitionEntry entry = entries.get(i);
                    ImGui.pushID(i);

                    // Name field
                    ImGui.alignTextToFramePadding();
                    ImGui.text(String.valueOf(i));
                    ImGui.sameLine();

                    String stateKey = "transEntry_name_" + i;
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 30);
                    PrimitiveEditors.drawString("##name", stateKey,
                            entry::getName,
                            v -> { entry.setName(v); markDirty.run(); });

                    // Remove button on same line
                    ImGui.sameLine();
                    if (ImGui.smallButton(MaterialIcons.Close + "##rem")) {
                        removeIndex = i;
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Remove entry");
                    }

                    // Sprite field on next line, indented under the name
                    float indent = ImGui.calcTextSize("0").x + ImGui.getStyle().getItemSpacingX();
                    ImGui.indent(indent);
                    FieldEditorUtils.inspectorRow("Sprite", () -> {
                        if (ImGui.smallButton("...##sprite")) {
                            Sprite oldSprite = entry.getLumaSprite();
                            String currentPath = oldSprite != null
                                    ? Assets.getPathForResource(oldSprite) : null;
                            assetPicker.open(Sprite.class, currentPath, asset -> {
                                UndoManager.getInstance().execute(new SetterUndoCommand<>(
                                        v -> { entry.setLumaSprite(v); markDirty.run(); },
                                        oldSprite, (Sprite) asset, "Change luma sprite"));
                            });
                        }
                        ImGui.sameLine();
                        Sprite sprite = entry.getLumaSprite();
                        if (sprite != null) {
                            ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, sprite.getName());
                        } else {
                            ImGui.textDisabled("(none)");
                        }
                    });
                    ImGui.unindent(indent);

                    ImGui.popID();
                }

                // Process removal after iteration (with undo support)
                if (removeIndex >= 0) {
                    UndoManager.getInstance().execute(
                            ConfigListCommand.remove(entries, removeIndex, "transition entry", markDirty));
                }
            }

            ImGui.treePop();
        }

        ImGui.popID();
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
