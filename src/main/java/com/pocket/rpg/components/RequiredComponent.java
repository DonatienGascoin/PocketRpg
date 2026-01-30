package com.pocket.rpg.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a component requires another component on the same GameObject.
 * <p>
 * When a component annotated with @RequiredComponent is added to a GameObject,
 * the system automatically adds the required component if it's not already present.
 * <p>
 * In the editor, undo removes both the component and any auto-added dependencies.
 * <p>
 * Example:
 * <pre>
 * {@literal @}RequiredComponent(TriggerZone.class)
 * public class WarpZone extends Component {
 *     // TriggerZone will be auto-added if not already on the GameObject
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(RequiredComponents.class)
public @interface RequiredComponent {
    /**
     * The component class that is required on the same GameObject.
     */
    Class<? extends Component> value();
}
