package com.pocket.rpg.editor.panels;

import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.postfx.PostEffectMeta;
import com.pocket.rpg.rendering.postfx.PostEffectRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popup window for selecting a PostEffect to add.
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
    private Consumer<PostEffect> onEffectSelected;

    private final ImString searchBuffer = new ImString(64);

    /**
     * Opens the popup with a callback for when an effect is selected.
     */
    public void open(Consumer<PostEffect> callback) {
        this.onEffectSelected = callback;
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

        ImGui.setNextWindowSize(350, 400);

        if (ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            // Search bar
            ImGui.text("Search:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##effectSearch", searchBuffer);

            ImGui.separator();

            // Effect list
            if (ImGui.beginChild("EffectList", 0, -30, false, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
                String filter = searchBuffer.get().toLowerCase();
                List<PostEffectMeta> allEffects = PostEffectRegistry.getAll();

                for (PostEffectMeta meta : allEffects) {
                    // Apply search filter
                    if (!filter.isEmpty()) {
                        if (!meta.simpleName().toLowerCase().contains(filter) &&
                                !meta.displayName().toLowerCase().contains(filter)) {
                            continue;
                        }
                    }

                    // Disabled style for non-instantiable effects
                    boolean canInstantiate = meta.hasNoArgConstructor();
                    if (!canInstantiate) {
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                    }

                    // Show effect button
                    if (ImGui.selectable(meta.displayName(), false, canInstantiate ? 0 : imgui.flag.ImGuiSelectableFlags.Disabled)) {
                        if (canInstantiate) {
                            selectEffect(meta);
                            ImGui.closeCurrentPopup();
                        }
                    }

                    if (!canInstantiate) {
                        ImGui.popStyleColor();
                    }

                    // Tooltip with details
                    if (ImGui.isItemHovered()) {
                        ImGui.beginTooltip();
                        ImGui.text(meta.simpleName());
                        if (!canInstantiate) {
                            ImGui.textColored(1.0f, 0.5f, 0.5f, 1.0f, "Requires parameters - not available");
                        } else {
                            ImGui.textDisabled("Click to add with default settings");
                        }
                        ImGui.endTooltip();
                    }
                }
                ImGui.endChild();
            }

            // Cancel button
            ImGui.separator();
            if (ImGui.button("Cancel", 100, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
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
