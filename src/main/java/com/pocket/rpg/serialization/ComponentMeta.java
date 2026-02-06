package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;

import java.util.List;

/**
 * Metadata about a component class.
 * Cached at startup for fast lookup during serialization and in the editor.
 */
public record ComponentMeta(
        String className,                               // Full class name
        String simpleName,                              // Simple name
        String displayName,                             // Display name
        Class<? extends Component> componentClass,
        List<FieldMeta> fields,                         // Serializable fields
        List<ComponentReferenceMeta> componentReferences, // @ComponentReference fields (unified)
        boolean hasNoArgConstructor                     // Can we instantiate it?
) {
    /**
     * Gets a display-friendly name from class name.
     * "SpriteRenderer" → "Sprite Renderer"
     */
    public static String toDisplayName(String simpleName) {
        return simpleName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /**
     * Gets the hierarchy-source references (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE).
     */
    public List<ComponentReferenceMeta> hierarchyReferences() {
        return componentReferences.stream()
                .filter(ComponentReferenceMeta::isHierarchySource)
                .toList();
    }

    /**
     * Gets the key-source references (KEY).
     */
    public List<ComponentReferenceMeta> keyReferences() {
        return componentReferences.stream()
                .filter(ComponentReferenceMeta::isKeySource)
                .toList();
    }
}
