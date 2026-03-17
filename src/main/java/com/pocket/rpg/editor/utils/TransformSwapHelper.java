package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Utility class for Transform/UITransform swap operations.
 * Helps determine when transforms are "problematic" (wrong type for context)
 * and whether entities can swap their transform type.
 */
public class TransformSwapHelper {

    /**
     * Checks if the entity is under a Canvas hierarchy (excluding the Canvas itself).
     *
     * @param entity Entity to check
     * @return true if entity is a descendant of a Canvas (but not a Canvas itself)
     */
    public static boolean isUnderCanvas(EditorGameObject entity) {
        if (entity == null) return false;

        // Canvas entities themselves are not "under" a canvas
        if (entity.hasComponent(UICanvas.class)) {
            return false;
        }

        // Check ancestors for UICanvas
        var parent = entity.getParent();
        while (parent != null) {
            if (parent.getComponent(UICanvas.class) != null) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Checks if the entity has a "problematic" transform (wrong type for its context).
     * <ul>
     *   <li>Transform under Canvas (except Canvas itself) = problematic - should be UITransform</li>
     *   <li>UITransform outside Canvas = problematic - should be Transform</li>
     * </ul>
     *
     * @param entity Entity to check
     * @return true if the transform type doesn't match the entity's hierarchy context
     */
    public static boolean hasProblematicTransform(EditorGameObject entity) {
        if (entity == null) return false;

        // Canvas entities always use regular Transform - that's correct
        if (entity.hasComponent(UICanvas.class)) {
            return false;
        }

        boolean hasUITransform = entity.hasComponent(UITransform.class);
        boolean underCanvas = isUnderCanvas(entity);

        // Regular Transform under Canvas = problematic (should be UITransform)
        if (!hasUITransform && underCanvas) {
            return true;
        }

        // UITransform outside Canvas = problematic (should be Transform)
        if (hasUITransform && !underCanvas) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the entity has UI components that require UITransform.
     * This prevents swapping to regular Transform when UI components are present.
     *
     * @param entity Entity to check
     * @return true if any component requires UITransform
     */
    public static boolean hasUIComponentsRequiringUITransform(EditorGameObject entity) {
        if (entity == null) return false;

        for (Component comp : entity.getComponents()) {
            // Skip transform components themselves
            if (comp instanceof Transform) continue;

            // Check if this component requires UITransform
            Class<?> clazz = comp.getClass();
            while (clazz != null && clazz != Component.class && clazz != Object.class) {
                RequiredComponent[] requirements = clazz.getDeclaredAnnotationsByType(RequiredComponent.class);
                for (RequiredComponent req : requirements) {
                    if (req.value() == UITransform.class) {
                        return true;
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
        return false;
    }

    /**
     * Determines if the entity should have UITransform based on its hierarchy position.
     *
     * @param entity Entity to check
     * @return true if entity should have UITransform, false for regular Transform
     */
    public static boolean shouldHaveUITransform(EditorGameObject entity) {
        if (entity == null) return false;

        // Canvas always uses regular Transform
        if (entity.hasComponent(UICanvas.class)) {
            return false;
        }

        // Check if under a Canvas
        return isUnderCanvas(entity);
    }

    /**
     * Checks if the entity can swap from its current transform type.
     * Returns false if:
     * <ul>
     *   <li>Entity is a Canvas (cannot swap)</li>
     *   <li>Entity has UI components requiring UITransform and trying to swap to Transform</li>
     * </ul>
     *
     * @param entity Entity to check
     * @param toUITransform true if swapping to UITransform, false if swapping to Transform
     * @return true if the swap is allowed
     */
    public static boolean canSwapTransform(EditorGameObject entity, boolean toUITransform) {
        if (entity == null) return false;

        // Canvas entities cannot swap
        if (entity.hasComponent(UICanvas.class)) {
            return false;
        }

        // If swapping to Transform (from UITransform), check for UI component dependencies
        if (!toUITransform && hasUIComponentsRequiringUITransform(entity)) {
            return false;
        }

        return true;
    }

    /**
     * Creates a UITransform from a regular Transform, preserving position, rotation, and scale.
     */
    public static UITransform createUITransformFrom(Transform transform) {
        UITransform uiTransform = new UITransform();

        // Copy position -> offset
        Vector3f localPos = transform.getLocalPosition();
        uiTransform.setOffset(localPos.x, localPos.y);

        // Copy Z rotation -> rotation2D
        Vector3f localRot = transform.getLocalRotation();
        uiTransform.setRotation2D(localRot.z);

        // Copy scale -> scale2D
        Vector3f localScale = transform.getLocalScale();
        uiTransform.setScale2D(localScale.x, localScale.y);

        // Set default UI-specific values
        uiTransform.setWidth(100f);
        uiTransform.setHeight(100f);
        uiTransform.setAnchor(0f, 0f);
        uiTransform.setPivot(0f, 0f);

        // Set the owner reference
        uiTransform.setGameObject(transform.getGameObject());

        return uiTransform;
    }

    /**
     * Creates a regular Transform from a UITransform, preserving offset, rotation, and scale.
     */
    public static Transform createTransformFrom(UITransform uiTransform) {
        Transform transform = new Transform();

        // Copy offset -> position
        Vector2f offset = uiTransform.getOffset();
        Vector3f localPos = uiTransform.getLocalPosition();
        transform.setLocalPosition(offset.x, offset.y, localPos.z);

        // Copy rotation2D -> Z rotation
        float rotation2D = uiTransform.getLocalRotation2D();
        transform.setLocalRotation(0, 0, rotation2D);

        // Copy scale2D -> scale
        Vector2f scale2D = uiTransform.getLocalScale2D();
        transform.setLocalScale(scale2D.x, scale2D.y, 1f);

        // Set the owner reference
        transform.setGameObject(uiTransform.getGameObject());

        return transform;
    }
}
