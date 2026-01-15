package com.pocket.rpg.serialization;

import java.lang.annotation.*;

/**
 * Marks a component field as required.
 * When the field is null or empty, the inspector will show a red highlight.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {
}
