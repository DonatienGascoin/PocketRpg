package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.utils.FuzzyMatcher;
import com.pocket.rpg.serialization.ComponentCategory;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ComponentBrowserPopup {

    private static final String POPUP_ID = "AddComponentPopup";

    private boolean shouldOpen = false;
    private boolean focusSearchNextFrame = false;
    private boolean focusFirstItemNextFrame = false;
    private ImString search = new ImString(64);
    private Consumer<ComponentMeta> onSelect;
    private List<ComponentMeta> filteredComponents = new ArrayList<>();

    public void open(Consumer<ComponentMeta> onSelect) {
        this.onSelect = onSelect;
        this.shouldOpen = true;
        this.focusSearchNextFrame = true;
        this.search = new ImString(64);
    }

    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        ImGui.setNextWindowSize(350, 420);
        if (ImGui.beginPopup(POPUP_ID)) {
            // Close on Escape
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
            }

            renderSearchBar();
            ImGui.separator();
            renderComponentList();
            ImGui.endPopup();
        }
    }

    private void renderSearchBar() {
        ImGui.setNextItemWidth(-1);

        if (focusSearchNextFrame) {
            ImGui.setKeyboardFocusHere();
            focusSearchNextFrame = false;
        }

        ImGui.inputText(
                "##ComponentSearch",
                search,
                ImGuiInputTextFlags.AutoSelectAll
        );

        // Down arrow from search -> focus first component
        if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            focusFirstItemNextFrame = true;
        }
    }

    private void renderComponentList() {
        ImGui.beginChild("ComponentList", 0, -1, false);

        String filter = search.get().trim();
        boolean searching = !filter.isEmpty();

        // Collect all matching components for keyboard navigation
        filteredComponents.clear();
        for (ComponentCategory category : ComponentRegistry.getCategories()) {
            for (ComponentMeta meta : category.components()) {
                if (!meta.hasNoArgConstructor()) continue;
                if (!searching || FuzzyMatcher.matches(filter, meta.displayName())) {
                    filteredComponents.add(meta);
                }
            }
        }

        // Build map of which categories have matches
        Set<String> categoriesWithMatches = new HashSet<>();
        for (ComponentMeta meta : filteredComponents) {
            // Find category for this component
            for (ComponentCategory category : ComponentRegistry.getCategories()) {
                if (category.components().contains(meta)) {
                    categoriesWithMatches.add(category.name());
                    break;
                }
            }
        }

        boolean isFirstItem = true;
        for (ComponentCategory category : ComponentRegistry.getCategories()) {
            // Hide category entirely if searching and no matches
            if (searching && !categoriesWithMatches.contains(category.name())) {
                continue;
            }

            // Category header
            boolean open;
            if (searching) {
                ImGui.textDisabled(category.displayName());
                open = true;
            } else {
                open = ImGui.collapsingHeader(category.displayName());
            }

            if (!open) continue;

            ImGui.indent();

            for (ComponentMeta meta : category.components()) {
                if (!meta.hasNoArgConstructor()) continue;
                if (searching && !FuzzyMatcher.matches(filter, meta.displayName())) {
                    continue;
                }

                // Focus first item when navigating from search
                if (isFirstItem && focusFirstItemNextFrame) {
                    ImGui.setKeyboardFocusHere();
                    focusFirstItemNextFrame = false;
                }

                if (ImGui.selectable(meta.displayName(), false)) {
                    selectComponent(meta);
                }

                // Enter to confirm when focused
                if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.Enter)) {
                    selectComponent(meta);
                }

                // Up arrow from first item -> back to search box
                if (isFirstItem && ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
                    focusSearchNextFrame = true;
                }

                isFirstItem = false;
            }

            ImGui.unindent();
            ImGui.spacing();
        }

        ImGui.endChild();
    }

    private void selectComponent(ComponentMeta meta) {
        if (onSelect != null) {
            onSelect.accept(meta);
        }
        ImGui.closeCurrentPopup();
    }
}
