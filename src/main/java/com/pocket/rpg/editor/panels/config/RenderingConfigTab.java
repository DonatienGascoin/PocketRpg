package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * Configuration tab for rendering settings.
 * Uses live editing model - edits apply directly to the live RenderingConfig.
 */
public class RenderingConfigTab implements ConfigTab {

    private final EditorContext context;
    private final Runnable markDirty;

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
    }

    @Override
    public void renderContent() {
        ImGui.pushID("RenderingTab");

        RenderingConfig config = context.getRenderingConfig();

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
                            // Switching from auto to manual - calculate current value
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

        ImGui.popID();
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
