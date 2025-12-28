package com.pocket.rpg.editor.panels;

import com.pocket.rpg.serialization.ComponentCategory;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import imgui.ImGui;

import java.util.function.Consumer;

/**
 * Popup menu for browsing and selecting components.
 * Organizes components into category submenus.
 */
public class ComponentBrowserPopup {

    private static final String POPUP_ID = "AddComponentPopup";

    private boolean shouldOpen = false;
    private Consumer<ComponentMeta> onSelect;

    /**
     * Opens the popup.
     *
     * @param onSelect Callback when a component is selected
     */
    public void open(Consumer<ComponentMeta> onSelect) {
        this.onSelect = onSelect;
        this.shouldOpen = true;
    }

    /**
     * Renders the popup. Call every frame.
     */
    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        if (ImGui.beginPopup(POPUP_ID)) {
            renderCategorizedMenu();
            ImGui.endPopup();
        }
    }

    /**
     * Renders the component menu with categories as submenus.
     * Use this inline in context menus.
     */
    public static void renderInlineMenu(Consumer<ComponentMeta> onSelect) {
        for (ComponentCategory category : ComponentRegistry.getCategories()) {
            if (category.isEmpty()) continue;

            if (ImGui.beginMenu(category.displayName())) {
                for (ComponentMeta meta : category.components()) {
                    if (!meta.hasNoArgConstructor()) continue;

                    if (ImGui.menuItem(meta.displayName())) {
                        if (onSelect != null) {
                            onSelect.accept(meta);
                        }
                    }
                }
                ImGui.endMenu();
            }
        }
    }

    /**
     * Renders UI-specific components menu.
     * Use for "Create UI" context menus.
     */
    public static void renderUIMenu(Consumer<ComponentMeta> onSelect) {
        ComponentCategory uiCategory = ComponentRegistry.getCategory("ui");
        if (uiCategory == null || uiCategory.isEmpty()) {
            ImGui.textDisabled("No UI components found");
            ImGui.textDisabled("Move UI components to");
            ImGui.textDisabled("com.pocket.rpg.components.ui");
            return;
        }

        for (ComponentMeta meta : uiCategory.components()) {
            if (!meta.hasNoArgConstructor()) continue;

            // Use icons for common UI types
            String icon = getUIIcon(meta.simpleName());
            String label = icon + " " + meta.displayName();

            if (ImGui.menuItem(label)) {
                if (onSelect != null) {
                    onSelect.accept(meta);
                }
            }
        }
    }

    private void renderCategorizedMenu() {
        renderInlineMenu(meta -> {
            if (onSelect != null) {
                onSelect.accept(meta);
            }
            ImGui.closeCurrentPopup();
        });
    }

    private static String getUIIcon(String simpleName) {
        return switch (simpleName) {
            case "UICanvas" -> "\uF03E";  // frame icon
            case "UIPanel" -> "\uF0C8";   // square
            case "UIImage" -> "\uF03E";   // image
            case "UIButton" -> "\uF0A6";  // hand pointer
            case "UIText" -> "T";
            default -> "\uF054";          // chevron
        };
    }
}
