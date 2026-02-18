package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.utils.FuzzyMatcher;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.postfx.PostEffectMeta;
import com.pocket.rpg.rendering.postfx.PostEffectRegistry;
import com.pocket.rpg.editor.core.EditorColors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Popup window for selecting a PostEffect to add.
 * Features fuzzy search, keyboard navigation, and effect descriptions.
 * <p>
 * Usage:
 * PostEffectBrowserPopup popup = new PostEffectBrowserPopup();
 * popup.open(effect -> postProcessor.addEffect(effect));
 * <p>
 * // In render loop:
 * popup.render();
 */
public class PostEffectBrowserPopup {

    private static final String POPUP_ID = "Add Post Effect";

    private boolean shouldOpen = false;
    private boolean focusSearchNextFrame = false;
    private boolean focusFirstItemNextFrame = false;
    private Consumer<PostEffect> onEffectSelected;

    private final ImString searchBuffer = new ImString(64);
    private List<PostEffectMeta> filteredEffects = new ArrayList<>();
    private int hoveredIndex = -1;

    /**
     * Opens the popup with a callback for when an effect is selected.
     */
    public void open(Consumer<PostEffect> callback) {
        this.onEffectSelected = callback;
        this.shouldOpen = true;
        this.focusSearchNextFrame = true;
        this.searchBuffer.set("");
        this.hoveredIndex = -1;
    }

    /**
     * Renders the popup. Call every frame.
     */
    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        ImGui.setNextWindowSize(400, 500);

        if (ImGui.beginPopup(POPUP_ID)) {
            // Close on Escape
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
            }

            renderSearchBar();
            ImGui.separator();
            renderEffectList();
            renderDescriptionBox();
            renderButtons();

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
                "##effectSearch",
                searchBuffer,
                ImGuiInputTextFlags.AutoSelectAll
        );

        // Down arrow from search -> focus first effect
        if (ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            focusFirstItemNextFrame = true;
        }
    }

    private void renderEffectList() {
        // Calculate height: total popup minus search bar, description box, buttons
        float listHeight = ImGui.getContentRegionAvailY() - 100; // Reserve space for description + buttons

        if (ImGui.beginChild("EffectList", 0, listHeight, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            String filter = searchBuffer.get().trim();
            boolean searching = !filter.isEmpty();

            // Build filtered list
            filteredEffects.clear();
            List<PostEffectMeta> allEffects = PostEffectRegistry.getAll();

            for (PostEffectMeta meta : allEffects) {
                if (!searching || FuzzyMatcher.matches(filter, meta.displayName())) {
                    filteredEffects.add(meta);
                }
            }

            boolean isFirstItem = true;
            for (int i = 0; i < filteredEffects.size(); i++) {
                PostEffectMeta meta = filteredEffects.get(i);
                boolean canInstantiate = meta.hasNoArgConstructor();

                // Disabled style for non-instantiable effects
                if (!canInstantiate) {
                    ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.DISABLED_TEXT[0], EditorColors.DISABLED_TEXT[1], EditorColors.DISABLED_TEXT[2], EditorColors.DISABLED_TEXT[3]);
                }

                // Focus first item when navigating from search
                if (isFirstItem && focusFirstItemNextFrame) {
                    ImGui.setKeyboardFocusHere();
                    focusFirstItemNextFrame = false;
                }

                boolean selected = (hoveredIndex == i);
                if (ImGui.selectable(meta.displayName(), selected, canInstantiate ? 0 : ImGuiSelectableFlags.Disabled)) {
                    if (canInstantiate) {
                        selectEffect(meta);
                        ImGui.closeCurrentPopup();
                    }
                }

                // Track hovered item for description box
                if (ImGui.isItemHovered()) {
                    hoveredIndex = i;
                }

                // Enter to confirm when focused
                if (ImGui.isItemFocused()) {
                    hoveredIndex = i;
                    if (ImGui.isKeyPressed(ImGuiKey.Enter) && canInstantiate) {
                        selectEffect(meta);
                        ImGui.closeCurrentPopup();
                    }
                }

                // Up arrow from first item -> back to search box
                if (isFirstItem && ImGui.isItemFocused() && ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
                    focusSearchNextFrame = true;
                }

                if (!canInstantiate) {
                    ImGui.popStyleColor();
                }

                isFirstItem = false;
            }

            ImGui.endChild();
        }
    }

    private void renderDescriptionBox() {
        ImGui.separator();

        // Description box with fixed height
        if (ImGui.beginChild("DescriptionBox", 0, 60, true)) {
            if (hoveredIndex >= 0 && hoveredIndex < filteredEffects.size()) {
                PostEffectMeta meta = filteredEffects.get(hoveredIndex);

                if (!meta.description().isEmpty()) {
                    ImGui.textWrapped(meta.description());
                } else {
                    ImGui.textDisabled("No description available");
                }

                if (!meta.hasNoArgConstructor()) {
                    ImGui.spacing();
                    EditorColors.textColored(EditorColors.DANGER, "Requires parameters - not available");
                }
            } else {
                ImGui.textDisabled("Hover an effect to see its description");
            }
            ImGui.endChild();
        }
    }

    private void renderButtons() {
        ImGui.spacing();
        if (ImGui.button("Cancel", 100, 0)) {
            ImGui.closeCurrentPopup();
        }
    }

    private void selectEffect(PostEffectMeta meta) {
        if (onEffectSelected != null) {
            PostEffect effect = PostEffectRegistry.instantiate(meta.simpleName());
            if (effect != null) {
                onEffectSelected.accept(effect);
            }
        }
    }
}
