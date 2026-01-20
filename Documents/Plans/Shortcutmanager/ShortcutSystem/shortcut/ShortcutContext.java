package com.pocket.rpg.editor.shortcut;

import imgui.ImGui;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * Captures the current UI state for shortcut context resolution.
 * Built each frame before processing shortcuts.
 */
@Getter
public class ShortcutContext {

    private final boolean popupOpen;
    private final boolean textInputActive;
    private final Set<String> visiblePanels;
    private final Set<String> focusedPanels;
    private final String activePopupId;

    private ShortcutContext(Builder builder) {
        this.popupOpen = builder.popupOpen;
        this.textInputActive = builder.textInputActive;
        this.visiblePanels = Set.copyOf(builder.visiblePanels);
        this.focusedPanels = Set.copyOf(builder.focusedPanels);
        this.activePopupId = builder.activePopupId;
    }

    public boolean isPanelVisible(String panelId) {
        return visiblePanels.contains(panelId);
    }

    public boolean isPanelFocused(String panelId) {
        return focusedPanels.contains(panelId);
    }

    public boolean isActivePopup(String popupId) {
        return popupId != null && popupId.equals(activePopupId);
    }

    // ========================================================================
    // BUILDER
    // ========================================================================

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a context with current ImGui state.
     * Call this at the start of frame processing.
     */
    public static ShortcutContext current() {
        return builder()
                .popupOpen(ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup))
                .textInputActive(ImGui.getIO().getWantTextInput())
                .build();
    }

    public static class Builder {
        private boolean popupOpen;
        private boolean textInputActive;
        private final Set<String> visiblePanels = new HashSet<>();
        private final Set<String> focusedPanels = new HashSet<>();
        private String activePopupId;

        public Builder popupOpen(boolean popupOpen) {
            this.popupOpen = popupOpen;
            return this;
        }

        public Builder textInputActive(boolean textInputActive) {
            this.textInputActive = textInputActive;
            return this;
        }

        public Builder panelVisible(String panelId) {
            this.visiblePanels.add(panelId);
            return this;
        }

        public Builder panelFocused(String panelId) {
            this.focusedPanels.add(panelId);
            return this;
        }

        public Builder activePopup(String popupId) {
            this.activePopupId = popupId;
            this.popupOpen = true;
            return this;
        }

        public ShortcutContext build() {
            return new ShortcutContext(this);
        }
    }

    @Override
    public String toString() {
        return "ShortcutContext{popup=" + popupOpen +
                ", textInput=" + textInputActive +
                ", visible=" + visiblePanels +
                ", focused=" + focusedPanels + "}";
    }
}
