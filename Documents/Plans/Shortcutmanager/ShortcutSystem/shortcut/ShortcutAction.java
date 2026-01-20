package com.pocket.rpg.editor.shortcut;

import lombok.Getter;

import java.util.Objects;

/**
 * Defines a shortcut action with its default binding, scope, and handler.
 */
@Getter
public class ShortcutAction {

    private final String id;                    // Unique ID like "editor.file.save"
    private final String displayName;           // Human-readable name
    private final String category;              // For grouping in UI (derived from ID)
    private final ShortcutBinding defaultBinding;
    private final ShortcutScope scope;
    private final String panelId;               // Required for PANEL_FOCUSED and PANEL_VISIBLE scopes
    private final Runnable handler;
    private final boolean allowInTextInput;     // If true, fires even when typing in text field

    private ShortcutAction(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Action ID required");
        this.displayName = builder.displayName != null ? builder.displayName : id;
        this.category = deriveCategory(id);
        this.defaultBinding = builder.defaultBinding;
        this.scope = builder.scope != null ? builder.scope : ShortcutScope.GLOBAL;
        this.panelId = builder.panelId;
        this.handler = Objects.requireNonNull(builder.handler, "Handler required");
        this.allowInTextInput = builder.allowInTextInput;

        // Validate panel scope has panel ID
        if ((scope == ShortcutScope.PANEL_FOCUSED || scope == ShortcutScope.PANEL_VISIBLE)
                && panelId == null) {
            throw new IllegalArgumentException(
                    "Panel ID required for scope " + scope + " on action " + id);
        }
    }

    private static String deriveCategory(String id) {
        int lastDot = id.lastIndexOf('.');
        if (lastDot > 0) {
            return id.substring(0, lastDot);
        }
        return "general";
    }

    /**
     * Executes the action handler.
     */
    public void execute() {
        handler.run();
    }

    /**
     * Checks if this action is applicable in the given context.
     */
    public boolean isApplicable(ShortcutContext context) {
        // Check text input restriction
        if (context.isTextInputActive() && !allowInTextInput) {
            return false;
        }

        return switch (scope) {
            case POPUP -> context.isPopupOpen();
            case PANEL_FOCUSED -> context.isPanelFocused(panelId);
            case PANEL_VISIBLE -> context.isPanelVisible(panelId);
            case GLOBAL -> true;
        };
    }

    @Override
    public String toString() {
        return id + " [" + (defaultBinding != null ? defaultBinding.getDisplayString() : "unbound") + "]";
    }

    // ========================================================================
    // BUILDER
    // ========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String displayName;
        private ShortcutBinding defaultBinding;
        private ShortcutScope scope;
        private String panelId;
        private Runnable handler;
        private boolean allowInTextInput = false;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder defaultBinding(ShortcutBinding binding) {
            this.defaultBinding = binding;
            return this;
        }

        public Builder scope(ShortcutScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder panelId(String panelId) {
            this.panelId = panelId;
            return this;
        }

        public Builder global() {
            this.scope = ShortcutScope.GLOBAL;
            return this;
        }

        public Builder popup() {
            this.scope = ShortcutScope.POPUP;
            return this;
        }

        public Builder panelFocused(String panelId) {
            this.scope = ShortcutScope.PANEL_FOCUSED;
            this.panelId = panelId;
            return this;
        }

        public Builder panelVisible(String panelId) {
            this.scope = ShortcutScope.PANEL_VISIBLE;
            this.panelId = panelId;
            return this;
        }

        public Builder handler(Runnable handler) {
            this.handler = handler;
            return this;
        }

        public Builder allowInTextInput(boolean allow) {
            this.allowInTextInput = allow;
            return this;
        }

        public ShortcutAction build() {
            return new ShortcutAction(this);
        }
    }
}
