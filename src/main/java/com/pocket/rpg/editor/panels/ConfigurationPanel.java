package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.config.*;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Dockable panel for editing engine configuration files.
 * Replaces the old modal-based ConfigPanel with a panel that can be
 * docked next to the Inspector.
 * <p>
 * Uses live editing model - edits apply directly to the live config,
 * with explicit Save (persist to disk) and Revert (reload from disk) actions.
 */
public class ConfigurationPanel extends EditorPanel {

    private final EditorContext context;

    private final List<ConfigTab> tabs = new ArrayList<>();
    private boolean tabsInitialized = false;

    @Getter
    private boolean dirty = false;

    private int selectedTabIndex = 0;

    public ConfigurationPanel(EditorContext context) {
        super("configuration", false);
        this.context = context;
    }

    /**
     * Marks the configuration as having unsaved changes.
     * Called by ConfigTabs when any field is modified.
     */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Clears the dirty flag after save or revert.
     */
    public void clearDirty() {
        dirty = false;
    }

    @Override
    public void render() {
        if (!isOpen()) return;

        initializeTabsIfNeeded();

        ImBoolean open = new ImBoolean(true);

        // Add unsaved indicator to title
        String title = dirty ? "Game Configuration *###GameConfiguration" : "Game Configuration###GameConfiguration";

        int flags = ImGuiWindowFlags.None;
        if (ImGui.begin(title, open, flags)) {
            setContentVisible(true);

            ImGui.pushID("ConfigurationPanel");

            renderHeader();
            ImGui.separator();
            renderTabBar();

            ImGui.popID();
        } else {
            setContentVisible(false);
        }

        ImGui.end();

        if (!open.get()) {
            setOpen(false);
        }
    }

    private void initializeTabsIfNeeded() {
        if (!tabsInitialized) {
            tabs.clear();
            tabs.add(new GameConfigTab(context, this::markDirty));
            tabs.add(new InputConfigTab(context, this::markDirty));
            tabs.add(new RenderingConfigTab(context, this::markDirty));
            tabs.add(new TransitionConfigTab(context, this::markDirty));
            tabsInitialized = true;
        }
    }

    private void renderHeader() {
        // CRITICAL: Capture dirty state BEFORE buttons - state may change during button clicks
        // and we need the same condition for beginDisabled/endDisabled (see common-pitfalls.md)
        boolean wasDirty = dirty;

        // Save button
        if (!wasDirty) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Save + " Save")) {
            saveAll();
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip("Save all configuration changes to disk");
        }
        if (!wasDirty) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();

        // Revert button
        if (!wasDirty) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Undo + " Revert")) {
            revertAll();
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip("Discard changes and reload from disk");
        }
        if (!wasDirty) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();

        // Reset to Defaults button
        if (ImGui.button(MaterialIcons.RestartAlt + " Reset to Defaults")) {
            resetAllToDefaults();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reset all settings to default values");
        }

        // Dirty indicator on the right (use current state, not captured)
        if (dirty) {
            float availWidth = ImGui.getContentRegionAvailX();
            float textWidth = ImGui.calcTextSize(MaterialIcons.FiberManualRecord + " Unsaved").x;
            ImGui.sameLine(ImGui.getCursorPosX() + availWidth - textWidth - 10);
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, MaterialIcons.FiberManualRecord + " Unsaved");
        }
    }

    private void renderTabBar() {
        if (ImGui.beginTabBar("ConfigTabs")) {
            for (int i = 0; i < tabs.size(); i++) {
                ConfigTab tab = tabs.get(i);

                // Use stable ID that doesn't change with tab state
                String tabLabel = tab.getTabName() + "##ConfigTab" + i;

                if (ImGui.beginTabItem(tabLabel)) {
                    selectedTabIndex = i;

                    ImGui.spacing();

                    // Render tab content in a child region for proper scrolling
                    if (ImGui.beginChild("TabContent##" + i, 0, 0, false)) {
                        tab.renderContent();
                    }
                    ImGui.endChild();

                    ImGui.endTabItem();
                }
            }
            ImGui.endTabBar();
        }
    }

    private void saveAll() {
        for (ConfigTab tab : tabs) {
            tab.save();
        }
        dirty = false;
        System.out.println("ConfigurationPanel: All configurations saved");
    }

    private void revertAll() {
        for (ConfigTab tab : tabs) {
            tab.revert();
        }
        dirty = false;
        System.out.println("ConfigurationPanel: All configurations reverted");
    }

    private void resetAllToDefaults() {
        for (ConfigTab tab : tabs) {
            tab.resetToDefaults();
        }
        dirty = true; // Mark dirty since we changed values but haven't saved
        System.out.println("ConfigurationPanel: All configurations reset to defaults");
    }

    @Override
    public String getDisplayName() {
        return "Game Configuration";
    }
}
