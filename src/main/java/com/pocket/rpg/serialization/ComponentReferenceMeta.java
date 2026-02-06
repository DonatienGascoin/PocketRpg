package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentReference;

import java.lang.reflect.Field;

/**
 * Metadata about a {@code @ComponentReference} field.
 * <p>
 * For hierarchy sources (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE), the field is
 * NOT serialized and is resolved from the GameObject tree at runtime.
 * <p>
 * For KEY source, the field IS serialized as a plain JSON string (the componentKey value)
 * or a JSON string array for List fields.
 *
 * @param field         The annotated field
 * @param fieldName     The field name
 * @param componentType The component type being referenced
 * @param source        Where to search (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE, KEY)
 * @param required      Whether resolution failure should log a warning
 * @param isList        Whether this is a List field
 */
public record ComponentReferenceMeta(
        Field field,
        String fieldName,
        Class<? extends Component> componentType,
        ComponentReference.Source source,
        boolean required,
        boolean isList
) {
    /**
     * Gets a display-friendly name.
     */
    public String getDisplayName() {
        String result = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }

    /**
     * Gets a description for editor display.
     * e.g., "Transform (self)", "UIText (key)", "BattlerDetailsUI[] (key)"
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
            case KEY -> "key";
        };

        return typeName + " (" + sourceName + ")";
    }

    /**
     * Whether this is a KEY source reference (serialized).
     */
    public boolean isKeySource() {
        return source == ComponentReference.Source.KEY;
    }

    /**
     * Whether this is a hierarchy source reference (not serialized).
     */
    public boolean isHierarchySource() {
        return source != ComponentReference.Source.KEY;
    }
}
