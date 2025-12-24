package com.pocket.rpg.serialization;

import com.pocket.rpg.components.ComponentRef;

import java.lang.reflect.Field;

/**
 * Metadata about a @ComponentRef field.
 * These fields are NOT serialized but are displayed in the editor
 * and resolved at runtime.
 */
public record ComponentRefMeta(
        String name,                    // Field name
        Class<?> componentType,         // The component type being referenced
        ComponentRef.Source source,     // Where to search (SELF, PARENT, CHILDREN, etc.)
        boolean required,               // Is this reference required?
        boolean isList,                 // Is this a List<Component> field?
        Field field                     // The actual field for runtime resolution
) {
    /**
     * Gets a display-friendly name.
     */
    public String getDisplayName() {
        String result = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }

    /**
     * Gets a description for editor display.
     * e.g., "Transform (self)", "Collider[] (children)"
     */
    public String getEditorDescription() {
        String typeName = componentType.getSimpleName();
        if (isList) {
            typeName += "[]";
        }

        String sourceName = switch (source) {
            case SELF -> "self";
            case PARENT -> "parent";
            case CHILDREN -> "children";
            case CHILDREN_RECURSIVE -> "all children";
        };

        return typeName + " (" + sourceName + ")";
    }
}
