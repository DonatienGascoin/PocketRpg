package com.pocket.rpg.serialization;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups components by package/category for menu organization.
 */
public record ComponentCategory(
        String name,
        String displayName,
        List<ComponentMeta> components
) {
    public ComponentCategory(String name, String displayName) {
        this(name, displayName, new ArrayList<>());
    }

    public void add(ComponentMeta meta) {
        components.add(meta);
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    /**
     * Extracts category from package name.
     * "com.pocket.rpg.components.ui.UICanvas" -> "ui"
     * "com.pocket.rpg.components.rendering.SpriteRenderer" -> "other"
     */
    public static String extractCategory(String className) {
        String prefix = "com.pocket.rpg.components.";
        if (!className.startsWith(prefix)) {
            return "other";
        }

        String remainder = className.substring(prefix.length());
        int dotIndex = remainder.indexOf('.');
        if (dotIndex > 0) {
            return remainder.substring(0, dotIndex);
        }
        return "other";
    }

    /**
     * Converts category name to display name.
     * "ui" -> "UI"
     * "physics" -> "Physics"
     */
    public static String toDisplayName(String category) {
        if (category == null || category.isEmpty()) {
            return "Other";
        }
        if (category.equalsIgnoreCase("ui")) {
            return "UI";
        }
        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
    }
}
