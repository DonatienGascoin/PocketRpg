package com.pocket.rpg.editor.shortcut;

import imgui.ImGui;

/**
 * Helper for panels to report their visibility and focus state.
 * Collects state during the render loop, then builds a context for shortcut processing.
 */
public class ShortcutContextBuilder {

    private final ShortcutContext.Builder builder;

    public ShortcutContextBuilder() {
        this.builder = ShortcutContext.builder()
                .popupOpen(ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup))
                .textInputActive(ImGui.getIO().getWantTextInput());
    }

    /**
     * Call after ImGui.begin() for a panel to report its state.
     * 
     * @param panelId The panel identifier (use EditorShortcuts.PanelIds constants)
     */
    public void reportPanel(String panelId) {
        // In ImGui, a window is visible if begin() returned true
        // and focused if it has window focus
        builder.panelVisible(panelId);

        if (ImGui.isWindowFocused()) {
            builder.panelFocused(panelId);
        }
    }

    /**
     * Call when a popup is active.
     */
    public void reportPopup(String popupId) {
        builder.activePopup(popupId);
    }

    /**
     * Builds the final context.
     */
    public ShortcutContext build() {
        return builder.build();
    }

    /**
     * Resets for next frame.
     */
    public void reset() {
        // Create new builder with fresh ImGui state
        // This is handled by creating a new ShortcutContextBuilder each frame
    }
}
