package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.PostEffectBrowserPopup;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.postfx.PostEffectMeta;
import com.pocket.rpg.rendering.postfx.PostEffectRegistry;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/**
 * Configuration tab for rendering settings.
 * Uses live editing model - edits apply directly to the live RenderingConfig.
 * <p>
 * Has inner tabs for "General" (batching, visual, world units, scaling) and
 * "Post-Processing" (effects).
 */
public class RenderingConfigTab implements ConfigTab {

    private final EditorContext context;
    private final Runnable markDirty;

    // Post-effect browser popup
    private final PostEffectBrowserPopup effectBrowserPopup = new PostEffectBrowserPopup();

    // Inner tab selection
    private int selectedInnerTab = 0;

    public RenderingConfigTab(EditorContext context, Runnable markDirty) {
        this.context = context;
        this.markDirty = markDirty;
    }

    @Override
    public String getTabName() {
        return "Rendering";
    }

    @Override
    public void save() {
        ConfigLoader.saveConfigToFile(context.getRenderingConfig(), ConfigLoader.ConfigType.RENDERING);
    }

    @Override
    public void revert() {
        ConfigLoader.loadAllConfigs();
    }

    @Override
    public void resetToDefaults() {
        RenderingConfig config = context.getRenderingConfig();
        RenderingConfig defaults = new RenderingConfig();

        config.setSortingStrategy(defaults.getSortingStrategy());
        config.setEnableStatistics(defaults.isEnableStatistics());
        config.setStatisticsInterval(defaults.getStatisticsInterval());
        config.getClearColor().set(defaults.getClearColor());
        config.setPixelsPerUnit(defaults.getPixelsPerUnit());
        config.setDefaultOrthographicSize(defaults.getDefaultOrthographicSize());
        config.setScalingMode(defaults.getScalingMode());
        config.setEnablePillarBox(defaults.isEnablePillarBox());
        config.setPillarboxAspectRatio(defaults.getPillarboxAspectRatio());
        config.getPostProcessingEffects().clear();
        config.getPostProcessingEffects().addAll(defaults.getPostProcessingEffects());
    }

    @Override
    public void renderContent() {
        ImGui.pushID("RenderingTab");

        // Inner tab bar
        if (ImGui.beginTabBar("RenderingInnerTabs")) {
            if (ImGui.beginTabItem("General##RenderGeneral")) {
                selectedInnerTab = 0;
                ImGui.spacing();
                renderGeneralContent();
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Post-Processing##RenderPostFx")) {
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
        RenderingConfig config = context.getRenderingConfig();

        // Scaling section (moved from GameConfigTab)
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

        // Batching section
        if (ImGui.collapsingHeader("Batching", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            ImGui.text("Max Batch Size: " + config.getMaxBatchSize());
            tooltip("Maximum sprites per draw call. Requires restart to modify.");

            FieldEditors.drawEnum("Sorting Strategy", "sortingStrategy",
                    config::getSortingStrategy,
                    v -> { config.setSortingStrategy(v); markDirty.run(); },
                    SpriteBatch.SortingStrategy.class);
            tooltip("Strategy for sorting sprites within a batch");

            ImGui.unindent();
        }

        // Statistics section
        if (ImGui.collapsingHeader("Statistics", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawBoolean("Enable Statistics", "enableStats",
                    config::isEnableStatistics,
                    v -> { config.setEnableStatistics(v); markDirty.run(); });
            tooltip("Collect rendering statistics (minor performance overhead)");

            if (config.isEnableStatistics()) {
                FieldEditors.drawInt("Statistics Interval", "statsInterval",
                        config::getStatisticsInterval,
                        v -> { config.setStatisticsInterval(Math.max(1, v)); markDirty.run(); });
                tooltip("Frames between statistics reports");
            }

            ImGui.unindent();
        }

        // Visual section
        if (ImGui.collapsingHeader("Visual", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawColor("Clear Color", "clearColor",
                    config::getClearColor,
                    v -> { config.getClearColor().set(v); markDirty.run(); });
            tooltip("Background color for the rendering viewport");

            ImGui.unindent();
        }

        // World Units section
        if (ImGui.collapsingHeader("World Units", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawFloat("Pixels Per Unit", "pixelsPerUnit",
                    () -> (double) config.getPixelsPerUnit(),
                    v -> { config.setPixelsPerUnit(Math.max(1.0f, (float) v)); markDirty.run(); },
                    0.5f, 1.0f, 256.0f, "%.1f");
            tooltip("Texture pixels per world unit. Common values: 16, 32, 64");

            Float orthoValue = config.getDefaultOrthographicSize();
            boolean autoOrtho = orthoValue == null;

            FieldEditors.drawBoolean("Auto Orthographic Size", "autoOrtho",
                    () -> config.getDefaultOrthographicSize() == null,
                    v -> {
                        if (!v) {
                            float calculated = context.getGameConfig().getGameHeight() / (2f * config.getPixelsPerUnit());
                            config.setDefaultOrthographicSize(calculated);
                        } else {
                            config.setDefaultOrthographicSize(null);
                        }
                        markDirty.run();
                    });
            tooltip("If checked, orthographic size is auto-calculated for pixel-perfect rendering");

            if (!autoOrtho && orthoValue != null) {
                FieldEditors.drawFloat("Orthographic Size", "orthoSize",
                        () -> config.getDefaultOrthographicSize() != null ? config.getDefaultOrthographicSize().doubleValue() : 5.0,
                        v -> { config.setDefaultOrthographicSize(Math.max(1.0f, (float) v)); markDirty.run(); },
                        0.5f, 1.0f, 100.0f, "%.1f");
                tooltip("Camera half-height in world units");
            }

            float effectiveOrtho = config.getDefaultOrthographicSize(context.getGameConfig().getGameHeight());
            ImGui.textDisabled(String.format("Effective size: %.2f (visible height: %.1f world units)",
                    effectiveOrtho, effectiveOrtho * 2));

            ImGui.unindent();
        }
    }

    private void renderPostProcessingContent() {
        RenderingConfig config = context.getRenderingConfig();
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

                boolean nodeOpen = ImGui.treeNode("effect_node", effectName);

                ImGui.sameLine(ImGui.getContentRegionAvailX() - 120);

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

                if (ImGui.smallButton(MaterialIcons.Delete)) {
                    effects.remove(i);
                    markDirty.run();
                    ImGui.popID();
                    if (nodeOpen) ImGui.treePop();
                    break;
                }

                ImGui.sameLine();

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

    private void renderEffectFields(PostEffect effect, int index) {
        Class<?> clazz = effect.getClass();
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();

        for (java.lang.reflect.Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) continue;
            if (field.getType().getSimpleName().equals("Shader")) continue;

            field.setAccessible(true);
            String fieldName = field.getName();
            if (fieldName.contains("Shader") || fieldName.equals("initialized")) continue;

            try {
                Object value = field.get(effect);
                String displayName = formatFieldName(fieldName);
                String key = "effect_" + index + "_" + fieldName;

                if (field.getType() == float.class || field.getType() == Float.class) {
                    FieldEditors.drawFloat(displayName, key,
                            () -> {
                                try { return ((Number) field.get(effect)).doubleValue(); }
                                catch (Exception e) { return 0.0; }
                            },
                            v -> {
                                try { field.set(effect, (float) v); markDirty.run(); }
                                catch (Exception e) { System.err.println("Failed to set field: " + e.getMessage()); }
                            },
                            0.01f, 0.0f, 10.0f, "%.3f");
                } else if (field.getType() == int.class || field.getType() == Integer.class) {
                    FieldEditors.drawInt(displayName, key,
                            () -> {
                                try { return ((Number) field.get(effect)).intValue(); }
                                catch (Exception e) { return 0; }
                            },
                            v -> {
                                try { field.set(effect, v); markDirty.run(); }
                                catch (Exception e) { System.err.println("Failed to set field: " + e.getMessage()); }
                            });
                } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    FieldEditors.drawBoolean(displayName, key,
                            () -> {
                                try { return (boolean) field.get(effect); }
                                catch (Exception e) { return false; }
                            },
                            v -> {
                                try { field.set(effect, v); markDirty.run(); }
                                catch (Exception e) { System.err.println("Failed to set field: " + e.getMessage()); }
                            });
                } else {
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
