package com.pocket.rpg.editor.panels;

import com.pocket.rpg.serialization.ComponentCategory;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

import java.util.Locale;
import java.util.function.Consumer;

public class ComponentBrowserPopup {

    private static final String POPUP_ID = "AddComponentPopup";

    private boolean shouldOpen = false;
    private boolean focusSearch = false;
    private ImString search = new ImString(64);
    private Consumer<ComponentMeta> onSelect;

    public void open(Consumer<ComponentMeta> onSelect) {
        this.onSelect = onSelect;
        this.shouldOpen = true;
        this.focusSearch = true;
        this.search = new ImString(64);
    }

    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        ImGui.setNextWindowSize(350, 420);
        if (ImGui.beginPopup(POPUP_ID)) {
            renderSearchBar();
            ImGui.separator();
            renderComponentList();
            ImGui.endPopup();
        }
    }

    private void renderSearchBar() {
        ImGui.setNextItemWidth(-1);

        if (focusSearch) {
            ImGui.setKeyboardFocusHere();
            focusSearch = false;
        }

        ImGui.inputText(
                "##ComponentSearch",
                search,
                ImGuiInputTextFlags.AutoSelectAll
        );
    }

    private void renderComponentList() {
        ImGui.beginChild("ComponentList", 0, -1, false);

        String filter = search.get().trim().toLowerCase(Locale.ROOT);
        boolean searching = !filter.isEmpty();

        for (ComponentCategory category : ComponentRegistry.getCategories()) {

            // First pass: detect matches
            boolean hasMatches = false;
            for (ComponentMeta meta : category.components()) {
                if (!meta.hasNoArgConstructor()) continue;
                if (!searching ||
                        meta.displayName().toLowerCase(Locale.ROOT).contains(filter)) {
                    hasMatches = true;
                    break;
                }
            }

            // Hide category entirely if searching and no matches
            if (searching && !hasMatches) {
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
                if (searching &&
                        !meta.displayName().toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }

                if (ImGui.selectable(meta.displayName())) {
                    if (onSelect != null) {
                        onSelect.accept(meta);
                    }
                    ImGui.closeCurrentPopup();
                }
            }

            ImGui.unindent();
            ImGui.spacing();
        }

        ImGui.endChild();
    }


}
