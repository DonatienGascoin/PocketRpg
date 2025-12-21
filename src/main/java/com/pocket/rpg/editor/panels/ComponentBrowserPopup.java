package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.components.ComponentMeta;
import com.pocket.rpg.editor.components.ComponentRegistry;
import com.pocket.rpg.editor.serialization.ComponentData;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popup window for selecting a component to add.
 * <p>
 * Usage:
 * ComponentBrowserPopup popup = new ComponentBrowserPopup();
 * popup.open(componentData -> entity.addComponent(componentData));
 * <p>
 * // In render loop:
 * popup.render();
 */
public class ComponentBrowserPopup {

    private static final String POPUP_ID = "Add Component";

    private boolean shouldOpen = false;
    private Consumer<ComponentData> onComponentSelected;

    private final ImString searchBuffer = new ImString(64);

    /**
     * Opens the popup with a callback for when a component is selected.
     */
    public void open(Consumer<ComponentData> callback) {
        this.onComponentSelected = callback;
        this.shouldOpen = true;
        this.searchBuffer.set("");
    }

    /**
     * Renders the popup. Call every frame.
     */
    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        // Center popup
        ImGui.setNextWindowSize(400, 500);

        if (ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar| ImGuiWindowFlags.NoScrollWithMouse)) {
            // Search bar
            ImGui.text("Search:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##search", searchBuffer);

            ImGui.separator();

            // Component list
            if (ImGui.beginChild("ComponentList", ImGuiWindowFlags.AlwaysVerticalScrollbar)) {


                String filter = searchBuffer.get().toLowerCase();
                List<ComponentMeta> components = ComponentRegistry.getInstantiable();

                for (ComponentMeta meta : components) {
                    // Apply search filter
                    if (!filter.isEmpty()) {
                        if (!meta.simpleName().toLowerCase().contains(filter) &&
                                !meta.displayName().toLowerCase().contains(filter)) {
                            continue;
                        }
                    }

                    // Show component button
                    if (ImGui.selectable(meta.displayName())) {
                        selectComponent(meta);
                        ImGui.closeCurrentPopup();
                    }

                    // Tooltip with details
                    if (ImGui.isItemHovered()) {
                        ImGui.beginTooltip();
                        ImGui.text(meta.className());
                        ImGui.textDisabled(meta.fields().size() + " editable fields");
                        ImGui.endTooltip();
                    }
                }
            }
            ImGui.endChild();

            // Cancel button
            ImGui.separator();
            if (ImGui.button("Cancel", 100, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void selectComponent(ComponentMeta meta) {
        if (onComponentSelected != null) {
            // Create empty component data
            ComponentData data = new ComponentData(meta.className());
            onComponentSelected.accept(data);
        }
    }
}