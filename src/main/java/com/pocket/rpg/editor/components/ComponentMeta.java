package com.pocket.rpg.editor.components;

import com.pocket.rpg.components.Component;

import java.util.List;

/**
 * Metadata about a component class.
 * Cached at startup for fast lookup.
 */
public record ComponentMeta(
        String className,           // Full class name: "com.pocket.rpg.components.SpriteRenderer"
        String simpleName,          // Simple name: "SpriteRenderer"
        String displayName,         // Display name: "Sprite Renderer"
        Class<? extends Component> componentClass,
        List<FieldMeta> fields,     // Editable fields
        boolean hasNoArgConstructor // Can we instantiate it?
) {
    /**
     * Gets a display-friendly name from class name.
     * "SpriteRenderer" → "Sprite Renderer"
     */
    public static String toDisplayName(String simpleName) {
        return simpleName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}