package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.PostEffectBrowserPopup;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.postfx.PostEffectMeta;
import com.pocket.rpg.rendering.postfx.PostEffectRegistry;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/**
 * Configuration tab for game settings.
 * Uses live editing model - edits apply directly to the live GameConfig.
 * <p>
 * Has inner tabs for "General" (all settings) and "Post-Processing" (effects only).
 */
public class GameConfigTab implements ConfigTab {

    private final EditorContext context;
    private final Runnable markDirty;

    // Post-effect browser popup
    private final PostEffectBrowserPopup effectBrowserPopup = new PostEffectBrowserPopup();

    // Inner tab selection
    private int selectedInnerTab = 0;

    public GameConfigTab(EditorContext context, Runnable markDirty) {
        this.context = context;
        this.markDirty = markDirty;
    }

    @Override
    public String getTabName() {
        return "Game";
    }

    @Override
    public void save() {
        ConfigLoader.saveConfigToFile(context.getGameConfig(), ConfigLoader.ConfigType.GAME);
    }

    @Override
    public void revert() {
        // Reload from disk by loading config again
        ConfigLoader.loadAllConfigs();
    }

    @Override
    public void resetToDefaults() {
        GameConfig config = context.getGameConfig();
        GameConfig defaults = new GameConfig();

        // Copy default values to live config
        config.setTitle(defaults.getTitle());
        config.setWindowWidth(defaults.getWindowWidth());
        config.setWindowHeight(defaults.getWindowHeight());
        config.setGameWidth(defaults.getGameWidth());
        config.setGameHeight(defaults.getGameHeight());
        config.setFullscreen(defaults.isFullscreen());
        config.setVsync(defaults.isVsync());
        config.setScalingMode(defaults.getScalingMode());
        config.setEnablePillarBox(defaults.isEnablePillarBox());
        config.setPillarboxAspectRatio(defaults.getPillarboxAspectRatio());
        config.setUiButtonHoverTint(defaults.getUiButtonHoverTint());
        config.getPostProcessingEffects().clear();
        config.getPostProcessingEffects().addAll(defaults.getPostProcessingEffects());
    }

    @Override
    public void renderContent() {
        ImGui.pushID("GameTab");

        // Inner tab bar
        if (ImGui.beginTabBar("GameInnerTabs")) {
            if (ImGui.beginTabItem("General##GameGeneral")) {
                selectedInnerTab = 0;
                ImGui.spacing();
                renderGeneralContent();
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Post-Processing##GamePostFx")) {
                selectedInnerTab = 1;
                ImGui.spacing();
                renderPostProcessingContent();
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }

        // Render popup (must be at same level, not inside child)
        effectBrowserPopup.render();

        ImGui.popID();
    }

    private void renderGeneralContent() {
        GameConfig config = context.getGameConfig();

        // Window section
        if (ImGui.collapsingHeader("Window", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawString("Title", "gameTitle",
                    config::getTitle,
                    v -> { config.setTitle(v); markDirty.run(); });

            FieldEditors.drawInt("Window Width", "windowWidth",
                    config::getWindowWidth,
                    v -> { config.setWindowWidth(Math.max(1, v)); markDirty.run(); });
            tooltip("Physical window width in pixels. Changes apply on restart.");

            FieldEditors.drawInt("Window Height", "windowHeight",
                    config::getWindowHeight,
                    v -> { config.setWindowHeight(Math.max(1, v)); markDirty.run(); });
            tooltip("Physical window height in pixels. Changes apply on restart.");

            ImGui.unindent();
        }

        // Game Resolution section
        if (ImGui.collapsingHeader("Game Resolution", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawInt("Game Width", "gameWidth",
                    config::getGameWidth,
                    v -> { config.setGameWidth(Math.max(1, v)); markDirty.run(); });
            tooltip("Internal game resolution width. Changes apply on restart.");

            FieldEditors.drawInt("Game Height", "gameHeight",
                    config::getGameHeight,
                    v -> { config.setGameHeight(Math.max(1, v)); markDirty.run(); });
            tooltip("Internal game resolution height. Changes apply on restart.");

            ImGui.unindent();
        }

        // Display section
        if (ImGui.collapsingHeader("Display", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawBoolean("Fullscreen", "fullscreen",
                    config::isFullscreen,
                    v -> { config.setFullscreen(v); markDirty.run(); });
            tooltip("Enable fullscreen mode");

            FieldEditors.drawBoolean("VSync", "vsync",
                    config::isVsync,
                    v -> { config.setVsync(v); markDirty.run(); });
            tooltip("Synchronize frame rate with monitor refresh rate");

            ImGui.unindent();
        }

        // Scaling section
        if (ImGui.collapsingHeader("Scaling", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawEnum("Scaling Mode", "scalingMode",
                    config::getScalingMode,
                    v -> { config.setScalingMode(v); markDirty.run(); },
                    PostProcessor.ScalingMode.class);
            tooltip("How the game image is scaled to fit the window");

            FieldEditors.drawBoolean("Enable Pillarbox", "enablePillarbox",
                    config::isEnablePillarBox,
                    v -> { config.setEnablePillarBox(v); markDirty.run(); });
            tooltip("Add black bars to maintain aspect ratio");

            if (config.isEnablePillarBox()) {
                FieldEditors.drawFloat("Aspect Ratio", "pillarboxAspect",
                        () -> (double) config.getPillarboxAspectRatio(),
                        v -> { config.setPillarboxAspectRatio((float) v); markDirty.run(); },
                        0.01f, 0.0f, 3.0f, "%.3f");
                tooltip("Target aspect ratio. 0 = auto-calculate from game resolution");
            }

            ImGui.unindent();
        }

        // UI section
        if (ImGui.collapsingHeader("UI", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawFloatSlider("Button Hover Tint", "buttonHoverTint",
                    () -> (double) config.getUiButtonHoverTint(),
                    v -> { config.setUiButtonHoverTint((float) v); markDirty.run(); },
                    0.0f, 1.0f);
            tooltip("How much buttons darken when hovered");

            ImGui.unindent();
        }
    }

    private void renderPostProcessingContent() {
        GameConfig config = context.getGameConfig();
        List<PostEffect> effects = config.getPostProcessingEffects();

        ImGui.textDisabled("Effects are applied in order (top to bottom)");
        ImGui.spacing();

        if (effects == null || effects.isEmpty()) {
            ImGui.textDisabled("No effects configured");
        } else {
            for (int i = 0; i < effects.size(); i++) {
                PostEffect effect = effects.get(i);
                ImGui.pushID(i);

                String effectName = effect.getClass().getSimpleName();

                // Expandable tree node for each effect
                boolean nodeOpen = ImGui.treeNode("effect_node", effectName);

                // Buttons on the same line
                ImGui.sameLine(ImGui.getContentRegionAvailX() - 100);

                // Move up button
                if (i > 0) {
                    if (ImGui.smallButton(MaterialIcons.ArrowUpward)) {
                        swapEffects(effects, i, i - 1);
                        markDirty.run();
                    }
                } else {
                    ImGui.beginDisabled();
                    ImGui.smallButton(MaterialIcons.ArrowUpward);
                    ImGui.endDisabled();
                }

                ImGui.sameLine();

                // Move down button
                if (i < effects.size() - 1) {
                    if (ImGui.smallButton(MaterialIcons.ArrowDownward)) {
                        swapEffects(effects, i, i + 1);
                        markDirty.run();
                    }
                } else {
                    ImGui.beginDisabled();
                    ImGui.smallButton(MaterialIcons.ArrowDownward);
                    ImGui.endDisabled();
                }

                ImGui.sameLine();

                // Delete button
                if (ImGui.smallButton(MaterialIcons.Delete)) {
                    effects.remove(i);
                    markDirty.run();
                    ImGui.popID();
                    if (nodeOpen) ImGui.treePop();
                    break;
                }

                ImGui.sameLine();

                // Help button with tooltip
                ImGui.smallButton(MaterialIcons.HelpOutline);
                if (ImGui.isItemHovered()) {
                    PostEffectMeta meta = PostEffectRegistry.getBySimpleName(effectName);
                    ImGui.beginTooltip();
                    ImGui.pushTextWrapPos(ImGui.getCursorPosX() + 250);
                    if (meta != null && !meta.description().isEmpty()) {
                        ImGui.textWrapped(meta.description());
                    } else {
                        ImGui.text("No description available");
                    }
                    ImGui.popTextWrapPos();
                    ImGui.endTooltip();
                }

                // Show editable fields if node is open
                if (nodeOpen) {
                    renderEffectFields(effect, i);
                    ImGui.treePop();
                }

                ImGui.popID();
            }
        }

        ImGui.spacing();
        if (ImGui.button(MaterialIcons.Add + " Add Effect")) {
            effectBrowserPopup.open(effect -> {
                if (effects != null) {
                    effects.add(effect);
                    markDirty.run();
                }
            });
        }
    }

    /**
     * Renders editable fields for a post-effect using reflection.
     */
    private void renderEffectFields(PostEffect effect, int index) {
        Class<?> clazz = effect.getClass();
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();

        for (java.lang.reflect.Field field : fields) {
            // Skip static, transient, and shader fields
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) continue;
            if (field.getType().getSimpleName().equals("Shader")) continue;

            field.setAccessible(true);
            String fieldName = field.getName();

            // Skip internal fields
            if (fieldName.contains("Shader") || fieldName.equals("initialized")) continue;

            try {
                Object value = field.get(effect);
                String displayName = formatFieldName(fieldName);
                String key = "effect_" + index + "_" + fieldName;

                if (field.getType() == float.class || field.getType() == Float.class) {
                    float floatVal = (float) value;
                    FieldEditors.drawFloat(displayName, key,
                            () -> {
                                try {
                                    return ((Number) field.get(effect)).doubleValue();
                                } catch (Exception e) {
                                    return 0.0;
                                }
                            },
                            v -> {
                                try {
                                    field.set(effect, (float) v);
                                    markDirty.run();
                                } catch (Exception e) {
                                    System.err.println("Failed to set field: " + e.getMessage());
                                }
                            },
                            0.01f, 0.0f, 10.0f, "%.3f");
                } else if (field.getType() == int.class || field.getType() == Integer.class) {
                    FieldEditors.drawInt(displayName, key,
                            () -> {
                                try {
                                    return ((Number) field.get(effect)).intValue();
                                } catch (Exception e) {
                                    return 0;
                                }
                            },
                            v -> {
                                try {
                                    field.set(effect, v);
                                    markDirty.run();
                                } catch (Exception e) {
                                    System.err.println("Failed to set field: " + e.getMessage());
                                }
                            });
                } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    FieldEditors.drawBoolean(displayName, key,
                            () -> {
                                try {
                                    return (boolean) field.get(effect);
                                } catch (Exception e) {
                                    return false;
                                }
                            },
                            v -> {
                                try {
                                    field.set(effect, v);
                                    markDirty.run();
                                } catch (Exception e) {
                                    System.err.println("Failed to set field: " + e.getMessage());
                                }
                            });
                } else {
                    // Show non-editable fields as text
                    ImGui.textDisabled(String.format("%s: %s", displayName, value));
                }
            } catch (Exception e) {
                // Skip fields that can't be accessed
            }
        }
    }

    private String formatFieldName(String fieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return result.toString();
    }

    private void swapEffects(List<PostEffect> effects, int i, int j) {
        PostEffect temp = effects.get(i);
        effects.set(i, effects.get(j));
        effects.set(j, temp);
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
