package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a custom inspector class for automatic registration.
 * <p>
 * Classes annotated with @InspectorFor will be automatically discovered and
 * registered with {@link CustomComponentEditorRegistry} at startup.
 * <p>
 * Requirements for annotated classes:
 * <ul>
 *     <li>Must extend {@link CustomComponentInspector}</li>
 *     <li>Must have a public no-arg constructor</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * {@literal @}InspectorFor(Transform.class)
 * public class TransformInspector extends CustomComponentInspector&lt;Transform&gt; {
 *     // ...
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InspectorFor {
    /**
     * The component class this inspector handles.
     *
     * @return The component class
     */
    Class<? extends Component> value();
}
