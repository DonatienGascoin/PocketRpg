package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.*;

/**
 * Facade for all field editor utilities.
 * <p>
 * Delegates to specialized editor classes:
 * <ul>
 *   <li>{@link PrimitiveEditors} - int, float, boolean, string</li>
 *   <li>{@link VectorEditors} - Vector2f, Vector3f, Vector4f, color</li>
 *   <li>{@link EnumEditor} - Enum combo boxes</li>
 *   <li>{@link AssetEditor} - Asset picker</li>
 *   <li>{@link TransformEditors} - Position/Rotation/Scale with undo</li>
 * </ul>
 * <p>
 * Override context is managed via {@link FieldEditorContext}.
 * <p>
 * Usage in custom editors:
 * <pre>
 * // Context is set by ComponentFieldEditor, just call draw methods:
 * boolean changed = FieldEditors.drawFloat("Speed", component, "speed", 0.1f);
 * changed |= FieldEditors.drawSprite("Sprite", component, "sprite", entity);
 * </pre>
 */
public final class FieldEditors {

    private FieldEditors() {}

    // ========================================================================
    // OVERRIDE CONTEXT
    // ========================================================================

    /**
     * Begins override context for prefab instance editing.
     * When active, fields show override styling and reset buttons.
     */
    public static void beginOverrideContext(EditorGameObject entity, Component component) {
        FieldEditorContext.begin(entity, component);
    }

    /**
     * Ends override context.
     */
    public static void endOverrideContext() {
        FieldEditorContext.end();
    }

    // ========================================================================
    // LAYOUT
    // ========================================================================

    /**
     * Draws a standard inspector row with label and field.
     */
    public static void inspectorRow(String label, Runnable field) {
        FieldEditorUtils.inspectorRow(label, field);
    }

    /**
     * Sets the width for the next field only.
     * Consumed after the next inspectorRow() call.
     */
    public static void setNextFieldWidth(float width) {
        FieldEditorUtils.setNextFieldWidth(width);
    }

    /**
     * Sets content to draw between label and field for the next inspectorRow() call.
     * Use for buttons/icons that appear after the label but before the field.
     * Consumed after the next inspectorRow() call.
     */
    public static void setNextMiddleContent(Runnable content) {
        FieldEditorUtils.setNextMiddleContent(content);
    }

    /**
     * Draws a compact inline field with label immediately followed by field.
     * Uses RELATIVE positioning - suitable for side-by-side layouts.
     */
    public static void inlineField(String label, Runnable field) {
        FieldEditorUtils.inlineField(label, field);
    }

    // ========================================================================
    // PRIMITIVES
    // ========================================================================

    public static boolean drawInt(String label, Component component, String fieldName) {
        return PrimitiveEditors.drawInt(label, component, fieldName);
    }

    public static boolean drawFloat(String label, Component component, String fieldName, float speed) {
        return PrimitiveEditors.drawFloat(label, component, fieldName, speed);
    }

    public static boolean drawFloat(String label, Component component, String fieldName,
                                    float speed, float min, float max) {
        return PrimitiveEditors.drawFloat(label, component, fieldName, speed, min, max);
    }

    public static boolean drawFloatSlider(String label, Component component, String fieldName,
                                          float min, float max) {
        return PrimitiveEditors.drawFloatSlider(label, component, fieldName, min, max);
    }

    public static boolean drawBoolean(String label, Component component, String fieldName) {
        return PrimitiveEditors.drawBoolean(label, component, fieldName);
    }

    public static boolean drawString(String label, Component component, String fieldName) {
        return PrimitiveEditors.drawString(label, component, fieldName);
    }

    // ========================================================================
    // VECTORS
    // ========================================================================

    public static boolean drawVector2f(String label, Component component, String fieldName) {
        return VectorEditors.drawVector2f(label, component, fieldName);
    }

    public static boolean drawVector2f(String label, Component component, String fieldName, float speed) {
        return VectorEditors.drawVector2f(label, component, fieldName, speed);
    }

    public static boolean drawVector3f(String label, Component component, String fieldName) {
        return VectorEditors.drawVector3f(label, component, fieldName);
    }

    public static boolean drawVector3f(String label, Component component, String fieldName, float speed) {
        return VectorEditors.drawVector3f(label, component, fieldName, speed);
    }

    public static boolean drawVector4f(String label, Component component, String fieldName) {
        return VectorEditors.drawVector4f(label, component, fieldName);
    }

    public static boolean drawVector4f(String label, Component component, String fieldName, float speed) {
        return VectorEditors.drawVector4f(label, component, fieldName, speed);
    }

    public static boolean drawColor(String label, Component component, String fieldName) {
        return VectorEditors.drawColor(label, component, fieldName);
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    public static boolean drawEnum(String label, Component component, String fieldName, Class<?> enumClass) {
        return EnumEditor.drawEnum(label, component, fieldName, enumClass);
    }

    // ========================================================================
    // ASSETS
    // ========================================================================

    public static boolean drawAsset(String label, Component component, String fieldName,
                                    Class<?> assetType, EditorGameObject entity) {
        return AssetEditor.drawAsset(label, component, fieldName, assetType, entity);
    }

    public static void renderAssetPicker() {
        AssetEditor.renderAssetPicker();
    }

    // ========================================================================
    // TRANSFORM (Entity-level)
    // ========================================================================

    /**
     * Draws position editor with XY fields, override styling, reset button, and undo.
     */
    public static boolean drawPosition(String label, EditorGameObject entity) {
        return TransformEditors.drawPosition(label, entity);
    }

    /**
     * Draws rotation editor with Z field, override styling, reset button, and undo.
     */
    public static boolean drawRotation(String label, EditorGameObject entity) {
        return TransformEditors.drawRotation(label, entity);
    }

    /**
     * Draws scale editor with XY fields, override styling, reset button, and undo.
     */
    public static boolean drawScale(String label, EditorGameObject entity) {
        return TransformEditors.drawScale(label, entity);
    }

    // ========================================================================
    // PRIMITIVES (getter/setter with undo support)
    // ========================================================================

    public static boolean drawInt(String label, String key, IntSupplier getter, IntConsumer setter) {
        return PrimitiveEditors.drawInt(label, key, getter, setter);
    }

    public static boolean drawFloat(String label, String key,
                                    DoubleSupplier getter, DoubleConsumer setter,
                                    float speed) {
        return PrimitiveEditors.drawFloat(label, key, getter, setter, speed);
    }

    public static boolean drawFloat(String label, String key,
                                    DoubleSupplier getter, DoubleConsumer setter,
                                    float speed, float min, float max, String format) {
        return PrimitiveEditors.drawFloat(label, key, getter, setter, speed, min, max, format);
    }

    public static boolean drawFloatSlider(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float min, float max) {
        return PrimitiveEditors.drawFloatSlider(label, key, getter, setter, min, max);
    }

    public static boolean drawBoolean(String label, String key,
                                      BooleanSupplier getter, Consumer<Boolean> setter) {
        return PrimitiveEditors.drawBoolean(label, key, getter, setter);
    }

    public static boolean drawString(String label, String key,
                                     Supplier<String> getter, Consumer<String> setter) {
        return PrimitiveEditors.drawString(label, key, getter, setter);
    }

    // ========================================================================
    // INLINE VARIANTS (for side-by-side layouts)
    // ========================================================================

    /**
     * Draws a compact inline float field with label immediately followed by field.
     * Uses RELATIVE positioning - suitable for side-by-side layouts like "X: [__] Y: [__]".
     * Width must be set via ImGui.setNextItemWidth() BEFORE calling this method.
     */
    public static boolean drawFloatInline(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float speed) {
        return PrimitiveEditors.drawFloatInline(label, key, getter, setter, speed);
    }

    /**
     * Draws a compact inline float field with min/max limits.
     */
    public static boolean drawFloatInline(String label, String key,
                                          DoubleSupplier getter, DoubleConsumer setter,
                                          float speed, float min, float max, String format) {
        return PrimitiveEditors.drawFloatInline(label, key, getter, setter, speed, min, max, format);
    }

    // ========================================================================
    // VECTORS (getter/setter with undo support)
    // ========================================================================

    public static boolean drawVector2f(String label, String key,
                                       Supplier<Vector2f> getter, Consumer<Vector2f> setter,
                                       float speed) {
        return VectorEditors.drawVector2f(label, key, getter, setter, speed);
    }

    public static boolean drawVector3f(String label, String key,
                                       Supplier<Vector3f> getter, Consumer<Vector3f> setter,
                                       float speed) {
        return VectorEditors.drawVector3f(label, key, getter, setter, speed);
    }

    public static boolean drawVector4f(String label, String key,
                                       Supplier<Vector4f> getter, Consumer<Vector4f> setter,
                                       float speed) {
        return VectorEditors.drawVector4f(label, key, getter, setter, speed);
    }

    public static boolean drawColor(String label, String key,
                                    Supplier<Vector4f> getter, Consumer<Vector4f> setter) {
        return VectorEditors.drawColor(label, key, getter, setter);
    }

    // ========================================================================
    // ENUMS (getter/setter with undo support)
    // ========================================================================

    public static <E extends Enum<E>> boolean drawEnum(String label, String key,
                                                        Supplier<E> getter, Consumer<E> setter,
                                                        Class<E> enumClass) {
        return EnumEditor.drawEnum(label, key, getter, setter, enumClass);
    }

    // ========================================================================
    // ASSETS (getter/setter with undo support)
    // ========================================================================

    public static <T> boolean drawAsset(String label, String key,
                                         Supplier<T> getter, Consumer<T> setter,
                                         Class<T> assetType) {
        return AssetEditor.drawAsset(label, key, getter, setter, assetType);
    }

    // ========================================================================
    // AUDIOCLIP (with play/stop preview)
    // ========================================================================

    public static boolean drawAudioClip(String label, Component component, String fieldName,
                                         EditorGameObject entity) {
        return AudioClipFieldEditor.drawAudioClip(label, component, fieldName, entity);
    }

    public static boolean drawAudioClip(String label, String key,
                                         Supplier<AudioClip> getter, Consumer<AudioClip> setter) {
        return AudioClipFieldEditor.drawAudioClip(label, key, getter, setter);
    }


    // ========================================================================
    // LISTS
    // ========================================================================

    public static boolean drawList(String label, Component component,
                                    com.pocket.rpg.serialization.FieldMeta meta,
                                    EditorGameObject entity) {
        return ListEditor.drawList(label, component, meta, entity);
    }

    // ========================================================================
    // MAPS
    // ========================================================================

    public static boolean drawMap(String label, Component component,
                                   com.pocket.rpg.serialization.FieldMeta meta,
                                   EditorGameObject entity) {
        return MapEditor.drawMap(label, component, meta, entity);
    }

    // ========================================================================
    // ENUM SET (flags-style checkboxes)
    // ========================================================================

    public static <E extends Enum<E>> boolean drawEnumSet(String label, Component component,
                                                           String fieldName, Class<E> enumClass,
                                                           EditorGameObject entity) {
        return EnumSetEditor.draw(label, component, fieldName, enumClass, entity);
    }

    public static <E extends Enum<E>> boolean drawEnumSet(String label, String key,
                                                           java.util.function.Supplier<java.util.List<E>> getter,
                                                           java.util.function.Consumer<java.util.List<E>> setter,
                                                           Class<E> enumClass) {
        return EnumSetEditor.draw(label, key, getter, setter, enumClass);
    }

    // ========================================================================
    // STRING COMBO (select from dynamic list)
    // ========================================================================

    public static boolean drawStringCombo(String label, String key,
                                           java.util.function.Supplier<String> getter,
                                           java.util.function.Consumer<String> setter,
                                           java.util.List<String> options) {
        return StringComboEditor.draw(label, key, getter, setter, options);
    }

    public static boolean drawStringCombo(String label, String key,
                                           java.util.function.Supplier<String> getter,
                                           java.util.function.Consumer<String> setter,
                                           java.util.List<String> options, boolean nullable) {
        return StringComboEditor.draw(label, key, getter, setter, options, nullable);
    }

    // ========================================================================
    // READ-ONLY
    // ========================================================================

    public static void drawReadOnly(String label, Component component, String fieldName, String typeName) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
        String display = value != null ? value.toString() : "(null)";

        ImGui.pushID(fieldName);

        inspectorRow(label, () -> {
            ImGui.textDisabled(display);
            ImGui.sameLine();
            ImGui.textDisabled("(read-only: " + typeName + ")");
        });

        ImGui.popID();
    }

    // ========================================================================
    // VECTOR GETTERS (for custom editors)
    // ========================================================================

    public static Vector2f getVector2f(Component component, String fieldName) {
        return FieldEditorUtils.getVector2f(component, fieldName);
    }

    public static Vector3f getVector3f(Component component, String fieldName) {
        return FieldEditorUtils.getVector3f(component, fieldName);
    }

    public static Vector4f getVector4f(Component component, String fieldName) {
        return FieldEditorUtils.getVector4f(component, fieldName);
    }

    public static float getFloat(Component component, String fieldName, float defaultValue) {
        return ComponentReflectionUtils.getFloat(component, fieldName, defaultValue);
    }

    public static int getInt(Component component, String fieldName, int defaultValue) {
        return ComponentReflectionUtils.getInt(component, fieldName, defaultValue);
    }

    public static boolean getBoolean(Component component, String fieldName, boolean defaultValue) {
        return ComponentReflectionUtils.getBoolean(component, fieldName, defaultValue);
    }

    public static String getString(Component component, String fieldName, String defaultValue) {
        return ComponentReflectionUtils.getString(component, fieldName, defaultValue);
    }

    // ========================================================================
    // NON-REFLECTION METHODS (getter/setter pattern)
    // ========================================================================
    // These methods use getter/setter lambdas instead of reflection,
    // allowing editing of computed properties like UITransform.offset.

    // Reusable buffers to avoid allocations per frame
    private static final float[] floatBuf = new float[1];
    private static final float[] floatBuf2 = new float[1];
    private static final float[] floatBuf3 = new float[1];
    private static final float[] floatBuf4 = new float[1];
    private static final int[] intBuf = new int[1];
    private static final ImString stringBuf = new ImString(256);
    private static final ImBoolean boolBuf = new ImBoolean();

    /**
     * Draws a float drag field with getter/setter pattern.
     * Directly updates the value via setter if changed.
     *
     * @param label  Field label
     * @param getter Supplier to get current value
     * @param setter Consumer to set new value
     * @param speed  Drag speed
     * @param min    Minimum value
     * @param max    Maximum value
     * @return true if value was changed
     */
    public static boolean dragFloat(String label, DoubleSupplier getter, DoubleConsumer setter,
                                    float speed, float min, float max) {
        floatBuf[0] = ((float) getter.getAsDouble());
        if (ImGui.dragFloat(label, floatBuf, speed, min, max)) {
            setter.accept(floatBuf[0]);
            return true;
        }
        return false;
    }

    /**
     * Draws a float drag field with getter/setter pattern and format string.
     */
    public static boolean dragFloat(String label, DoubleSupplier getter, DoubleConsumer setter,
                                    float speed, float min, float max, String format) {
        floatBuf[0] = ((float) getter.getAsDouble());
        if (ImGui.dragFloat(label, floatBuf, speed, min, max, format)) {
            setter.accept(floatBuf[0]);
            return true;
        }
        return false;
    }

    /**
     * Draws an int drag field with getter/setter pattern.
     */
    public static boolean dragInt(String label, IntSupplier getter, IntConsumer setter,
                                  float speed, int min, int max) {
        intBuf[0] = (getter.getAsInt());
        if (ImGui.dragInt(label, intBuf, speed, min, max)) {
            setter.accept(intBuf[0]);
            return true;
        }
        return false;
    }

    /**
     * Draws a checkbox with getter/setter pattern.
     */
    public static boolean checkbox(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        boolBuf.set(getter.getAsBoolean());
        if (ImGui.checkbox(label, boolBuf)) {
            setter.accept(boolBuf.get());
            return true;
        }
        return false;
    }

    /**
     * Draws a text input with getter/setter pattern.
     */
    public static boolean inputText(String label, Supplier<String> getter, Consumer<String> setter) {
        stringBuf.set(getter.get() != null ? getter.get() : "");
        if (ImGui.inputText(label, stringBuf)) {
            setter.accept(stringBuf.get());
            return true;
        }
        return false;
    }

    /**
     * Draws a Vector2f drag field with getter/setter pattern.
     *
     * @param label  Field label
     * @param getter Supplier to get current Vector2f value
     * @param setter BiConsumer to set new x, y values
     * @param speed  Drag speed
     * @return true if value was changed
     */
    public static boolean dragVector2f(String label, Supplier<Vector2f> getter,
                                       BiConsumer<Float, Float> setter, float speed) {
        Vector2f value = getter.get();
        floatBuf[0] = (value.x);
        floatBuf2[0] = (value.y);
        boolean changed = false;

        ImGui.pushID(label);
        ImGui.text(label);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f - 20);
        if (ImGui.dragFloat("X", floatBuf, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0]);
            changed = true;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 10);
        if (ImGui.dragFloat("Y", floatBuf2, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0]);
            changed = true;
        }
        ImGui.popID();

        return changed;
    }

    /**
     * Draws a Vector3f drag field with getter/setter pattern.
     */
    public static boolean dragVector3f(String label, Supplier<Vector3f> getter,
                                       TriConsumer<Float, Float, Float> setter, float speed) {
        Vector3f value = getter.get();
        floatBuf[0] = (value.x);
        floatBuf2[0] = (value.y);
        floatBuf3[0] = (value.z);
        boolean changed = false;

        ImGui.pushID(label);
        ImGui.text(label);
        float fieldWidth = (ImGui.getContentRegionAvailX() - 20) / 3f;
        ImGui.setNextItemWidth(fieldWidth);
        if (ImGui.dragFloat("X", floatBuf, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0], floatBuf3[0]);
            changed = true;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(fieldWidth);
        if (ImGui.dragFloat("Y", floatBuf2, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0], floatBuf3[0]);
            changed = true;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(fieldWidth);
        if (ImGui.dragFloat("Z", floatBuf3, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0], floatBuf3[0]);
            changed = true;
        }
        ImGui.popID();

        return changed;
    }

    /**
     * Draws a Vector4f drag field with getter/setter pattern.
     */
    public static boolean dragVector4f(String label, Supplier<Vector4f> getter,
                                       QuadConsumer<Float, Float, Float, Float> setter, float speed) {
        Vector4f value = getter.get();
        floatBuf[0] = (value.x);
        floatBuf2[0] = (value.y);
        floatBuf3[0] = (value.z);
        floatBuf4[0] = (value.w);
        boolean changed = false;

        ImGui.pushID(label);
        ImGui.text(label);
        float fieldWidth = (ImGui.getContentRegionAvailX() - 30) / 4f;
        ImGui.setNextItemWidth(fieldWidth);
        if (ImGui.dragFloat("X", floatBuf, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0], floatBuf3[0], floatBuf4[0]);
            changed = true;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(fieldWidth);
        if (ImGui.dragFloat("Y", floatBuf2, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0], floatBuf3[0], floatBuf4[0]);
            changed = true;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(fieldWidth);
        if (ImGui.dragFloat("Z", floatBuf3, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0], floatBuf3[0], floatBuf4[0]);
            changed = true;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(fieldWidth);
        if (ImGui.dragFloat("W", floatBuf4, speed)) {
            setter.accept(floatBuf[0], floatBuf2[0], floatBuf3[0], floatBuf4[0]);
            changed = true;
        }
        ImGui.popID();

        return changed;
    }

    /**
     * Draws a color picker with getter/setter pattern.
     */
    public static boolean colorEdit4(String label, Supplier<Vector4f> getter,
                                     QuadConsumer<Float, Float, Float, Float> setter) {
        Vector4f color = getter.get();
        float[] colorArr = {color.x, color.y, color.z, color.w};

        ImGui.pushID(label);
        boolean changed = ImGui.colorEdit4(label, colorArr);
        if (changed) {
            setter.accept(colorArr[0], colorArr[1], colorArr[2], colorArr[3]);
        }
        ImGui.popID();

        return changed;
    }

    // ========================================================================
    // FUNCTIONAL INTERFACES for multi-parameter setters
    // ========================================================================

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    @FunctionalInterface
    public interface QuadConsumer<A, B, C, D> {
        void accept(A a, B b, C c, D d);
    }
}
