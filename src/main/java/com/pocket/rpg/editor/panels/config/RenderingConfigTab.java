package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.RequiredArgsConstructor;
import org.joml.Vector4f;

@RequiredArgsConstructor
public class RenderingConfigTab implements ConfigTab {

    private final EditorContext context;

    private RenderingConfig working;
    private RenderingConfig original;
    private boolean dirty = false;

    private final float[] clearColorBuffer = new float[4];

    @Override
    public void initialize() {
        working = cloneConfig(context.getRenderingConfig());
        original = cloneConfig(context.getRenderingConfig());
        syncBuffers();
        dirty = false;
    }

    @Override
    public String getTabName() {
        return "Rendering";
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void save() {
        applyToLive();
        ConfigLoader.saveConfigToFile(context.getRenderingConfig(), ConfigLoader.ConfigType.RENDERING);
        original = cloneConfig(context.getRenderingConfig());
        dirty = false;
    }

    @Override
    public void resetToDefaults() {
        working = new RenderingConfig();
        original = new RenderingConfig(); // Reset original too!
        syncBuffers();
        dirty = false; // Explicitly set to false
    }

    @Override
    public void renderContent() {
        // Scope ALL IDs to this tab
        ImGui.pushID("RenderingTab");

        if (ImGui.button(FontAwesomeIcons.Undo + " Reset to Defaults")) {
            resetToDefaults();
        }

        ImGui.separator();

        if (ImGui.beginChild("Content", 0, 0, false)) {
            renderBatchingSection();
            renderStatisticsSection();
            renderVisualSection();
            renderWorldUnitSection();
            ImGui.endChild();
        }

        ImGui.popID();
    }

    private void renderBatchingSection() {
        ImGui.text("Batching");
        ImGui.indent();

        ImGui.text("Max Batch Size: " + working.getMaxBatchSize());
        tooltip("Maximum sprites per draw call. Requires restart to modify.");

        ImGui.setNextItemWidth(200);
        if (ImGui.beginCombo("Sorting Strategy", working.getSortingStrategy().name())) {
            for (SpriteBatch.SortingStrategy strategy : SpriteBatch.SortingStrategy.values()) {
                boolean selected = strategy == working.getSortingStrategy();
                if (ImGui.selectable(strategy.name(), selected)) {
                    working.setSortingStrategy(strategy);
                    updateDirtyFlag();
                }
            }
            ImGui.endCombo();
        }
        tooltip("Strategy for sorting sprites within a batch");

        ImGui.unindent();
    }

    private void renderStatisticsSection() {
        ImGui.spacing();
        ImGui.text("Statistics");
        ImGui.indent();

        boolean enableStats = working.isEnableStatistics();
        if (ImGui.checkbox("Enable Statistics", enableStats)) {
            working.setEnableStatistics(!enableStats);
            updateDirtyFlag();
        }
        tooltip("Collect rendering statistics (minor performance overhead)");

        if (working.isEnableStatistics()) {
            ImInt interval = new ImInt(working.getStatisticsInterval());
            ImGui.setNextItemWidth(100);
            if (ImGui.inputInt("Statistics Interval", interval)) {
                working.setStatisticsInterval(Math.max(1, interval.get()));
                updateDirtyFlag();
            }
            tooltip("Frames between statistics reports");
        }

        ImGui.unindent();
    }

    private void renderVisualSection() {
        ImGui.spacing();
        ImGui.text("Visual");
        ImGui.indent();

        if (ImGui.colorEdit4("Clear Color", clearColorBuffer)) {
            working.getClearColor().set(
                    clearColorBuffer[0],
                    clearColorBuffer[1],
                    clearColorBuffer[2],
                    clearColorBuffer[3]
            );
            updateDirtyFlag();
        }
        tooltip("Background color for the rendering viewport");

        ImGui.unindent();
    }

    private void renderWorldUnitSection() {
        ImGui.spacing();
        ImGui.text("World Units");
        ImGui.indent();

        float[] ppu = {working.getPixelsPerUnit()};
        ImGui.setNextItemWidth(100);
        if (ImGui.dragFloat("Pixels Per Unit", ppu, 0.5f, 1.0f, 256.0f, "%.1f")) {
            working.setPixelsPerUnit(Math.max(1.0f, ppu[0]));
            updateDirtyFlag();
        }
        tooltip("Texture pixels per world unit. Common values: 16, 32, 64");

        Float orthoValue = working.getDefaultOrthographicSize();
        boolean autoOrtho = orthoValue == null;

        if (ImGui.checkbox("Auto Orthographic Size", autoOrtho)) {
            if (autoOrtho) {
                float calculated = context.getGameConfig().getGameHeight() / (2f * working.getPixelsPerUnit());
                working.setDefaultOrthographicSize(calculated);
            } else {
                working.setDefaultOrthographicSize(null);
            }
            updateDirtyFlag();
        }
        tooltip("If checked, orthographic size is auto-calculated for pixel-perfect rendering");

        if (!autoOrtho && orthoValue != null) {
            ImGui.indent();
            float[] ortho = {orthoValue};
            ImGui.setNextItemWidth(100);
            if (ImGui.dragFloat("Orthographic Size", ortho, 0.5f, 1.0f, 100.0f, "%.1f")) {
                working.setDefaultOrthographicSize(Math.max(1.0f, ortho[0]));
                updateDirtyFlag();
            }
            tooltip("Camera half-height in world units");
            ImGui.unindent();
        }

        float effectiveOrtho = working.getDefaultOrthographicSize(context.getGameConfig().getGameHeight());
        ImGui.textDisabled(String.format("Effective size: %.2f (visible height: %.1f world units)",
                effectiveOrtho, effectiveOrtho * 2));

        ImGui.unindent();
    }

    private void syncBuffers() {
        Vector4f color = working.getClearColor();
        clearColorBuffer[0] = color.x;
        clearColorBuffer[1] = color.y;
        clearColorBuffer[2] = color.z;
        clearColorBuffer[3] = color.w;
    }

    private void updateDirtyFlag() {
        dirty = !isConfigEqual(working, original);
    }

    private boolean isConfigEqual(RenderingConfig a, RenderingConfig b) {
        return a.getMaxBatchSize() == b.getMaxBatchSize()
                && a.getSortingStrategy() == b.getSortingStrategy()
                && a.isEnableStatistics() == b.isEnableStatistics()
                && a.getStatisticsInterval() == b.getStatisticsInterval()
                && a.getClearColor().equals(b.getClearColor())
                && a.getPixelsPerUnit() == b.getPixelsPerUnit()
                && java.util.Objects.equals(a.getDefaultOrthographicSize(), b.getDefaultOrthographicSize());
    }

    private void applyToLive() {
        RenderingConfig live = context.getRenderingConfig();
        live.setSortingStrategy(working.getSortingStrategy());
        live.setEnableStatistics(working.isEnableStatistics());
        live.setStatisticsInterval(working.getStatisticsInterval());
        live.getClearColor().set(working.getClearColor());
        live.setPixelsPerUnit(working.getPixelsPerUnit());
        live.setDefaultOrthographicSize(working.getDefaultOrthographicSize());
    }

    private RenderingConfig cloneConfig(RenderingConfig source) {
        RenderingConfig clone = new RenderingConfig();
        clone.setMaxBatchSize(source.getMaxBatchSize());
        clone.setSortingStrategy(source.getSortingStrategy());
        clone.setEnableStatistics(source.isEnableStatistics());
        clone.setStatisticsInterval(source.getStatisticsInterval());
        clone.setClearColor(new Vector4f(source.getClearColor()));
        clone.setPixelsPerUnit(source.getPixelsPerUnit());
        clone.setDefaultOrthographicSize(source.getDefaultOrthographicSize());
        return clone;
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
