package com.pocket.rpg.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which component types can "drive" this component.
 * A driven component's fields are managed by the driver (e.g. UIButton drives UIPanel/UIImage).
 * <p>
 * Used by the editor to:
 * <ul>
 *   <li>Show "driven by" warnings in inspectors</li>
 *   <li>Prevent removal of driven components when a driver sibling exists</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DrivableBy {
    Class<? extends Component>[] value();
}
