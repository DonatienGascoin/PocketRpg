package com.pocket.rpg.editor.panels.content;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link com.pocket.rpg.editor.panels.AssetEditorContent} implementation
 * for auto-discovery and registration.
 * <p>
 * The value specifies which asset class this content handles. During editor startup,
 * all annotated classes are discovered via Reflections and registered automatically.
 * <p>
 * Example:
 * <pre>
 * {@literal @}EditorContentFor(Dialogue.class)
 * public class DialogueEditorContent implements AssetEditorContent { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EditorContentFor {
    /** The asset class this content handles. */
    Class<?> value();
}
