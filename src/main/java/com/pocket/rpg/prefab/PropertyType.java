package com.pocket.rpg.prefab;

/**
 * Supported property types for prefab instance overrides.
 * Used by the editor to render appropriate UI controls.
 */
public enum PropertyType {
    /**
     * String value - rendered as InputText
     */
    STRING,

    /**
     * Integer value - rendered as InputInt
     */
    INT,

    /**
     * Float value - rendered as DragFloat
     */
    FLOAT,

    /**
     * Boolean value - rendered as Checkbox
     */
    BOOLEAN,

    /**
     * List of strings - rendered as multi-line or list editor
     */
    STRING_LIST,

    /**
     * 2D vector (x, y) - rendered as DragFloat2
     */
    VECTOR2,

    /**
     * 3D vector (x, y, Z) - rendered as DragFloat3
     */
    VECTOR3,

    /**
     * Reference to another asset - future use
     */
    ASSET_REF
}
