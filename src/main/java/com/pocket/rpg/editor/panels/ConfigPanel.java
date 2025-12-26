package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.config.*;
import imgui.ImGui;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal panel for editing engine configuration files.
 * Coordinates multiple ConfigTab implementations.
 */
@RequiredArgsConstructor
public class ConfigPanel {

    private final EditorContext context;

    @Getter
    private boolean open = false;
    private boolean shouldOpen = false;

    private final List<ConfigTab> tabs = new ArrayList<>();
    private boolean tabsInitialized = false;

    private static final float MODAL_WIDTH = 600;
    private static final float MODAL_HEIGHT = 550;

    public void openModal() {
        shouldOpen = true;
    }

    public void render() {
        if (shouldOpen) {
            ImGui.openPopup("Configuration");
            shouldOpen = false;
            initializeTabs();
        }

        ImGui.setNextWindowSize(MODAL_WIDTH, MODAL_HEIGHT, imgui.flag.ImGuiCond.FirstUseEver);
        float centerX = ImGui.getIO().getDisplaySizeX() / 2 - MODAL_WIDTH / 2;
        float centerY = ImGui.getIO().getDisplaySizeY() / 2 - MODAL_HEIGHT / 2;
        ImGui.setNextWindowPos(centerX, centerY, imgui.flag.ImGuiCond.FirstUseEver);

        if (ImGui.beginPopupModal("Configuration", new ImBoolean(true), ImGuiWindowFlags.NoCollapse)) {
            open = true;

            ImGui.pushID("ConfigModal");

            renderHeader();
            ImGui.separator();
            renderTabs();

            if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.popID();

            ImGui.endPopup();
        } else {
            open = false;
        }
    }

    private void initializeTabs() {
        if (!tabsInitialized) {
            tabs.clear();
            tabs.add(new GameConfigTab(context));
            tabs.add(new InputConfigTab(context));
            tabs.add(new RenderingConfigTab(context));
            tabs.add(new TransitionConfigTab(context));
            tabsInitialized = true;
        }

        for (ConfigTab tab : tabs) {
            tab.initialize();
        }
    }

    private void renderHeader() {
        boolean anyDirty = tabs.stream().anyMatch(ConfigTab::isDirty);

        if (!anyDirty) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(FontAwesomeIcons.Save + " Save All")) {
            saveAll();
        }
        if (!anyDirty) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();

        if (ImGui.button("Close")) {
            ImGui.closeCurrentPopup();
        }

        if (anyDirty) {
            ImGui.sameLine();
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, FontAwesomeIcons.Circle + " Unsaved changes");
        }
    }

    private void renderTabs() {
        if (ImGui.beginTabBar("Tabs")) {
            for (int i = 0; i < tabs.size(); i++) {
                ConfigTab tab = tabs.get(i);
                ImGui.pushID(i);

                // Stable ID that doesn't change with dirty state
                String tabLabel = tab.getTabName() + (tab.isDirty() ? "*" : "") + "##" + tab.getTabName();
                int flags = tab.isDirty() ? ImGuiTabItemFlags.UnsavedDocument : 0;

                if (ImGui.beginTabItem(tabLabel, flags)) {
                    tab.renderContent();
                    ImGui.endTabItem();
                }

                ImGui.popID();
            }
            ImGui.endTabBar();
        }
    }

    private void saveAll() {
        for (ConfigTab tab : tabs) {
            if (tab.isDirty()) {
                tab.save();
            }
        }
        System.out.println("ConfigPanel: All configurations saved");
    }
}
