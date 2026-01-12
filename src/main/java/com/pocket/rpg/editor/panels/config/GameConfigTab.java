package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.PostEffectBrowserPopup;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class GameConfigTab implements ConfigTab {

    private final EditorContext context;

    private GameConfig working;
    private GameConfig original;
    private boolean dirty = false;

    private final ImString titleBuffer = new ImString(128);
    
    // Post-effect browser popup
    private final PostEffectBrowserPopup effectBrowserPopup = new PostEffectBrowserPopup();

    @Override
    public void initialize() {
        working = cloneConfig(context.getGameConfig());
        original = cloneConfig(context.getGameConfig());
        titleBuffer.set(working.getTitle());
        dirty = false;
    }

    @Override
    public String getTabName() {
        return "Game";
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void save() {
        applyToLive();
        ConfigLoader.saveConfigToFile(context.getGameConfig(), ConfigLoader.ConfigType.GAME);
        original = cloneConfig(context.getGameConfig());
        dirty = false;
    }

    @Override
    public void resetToDefaults() {
        working = new GameConfig();
        original = new GameConfig(); // Reset original too!
        titleBuffer.set(working.getTitle());
        dirty = false; // Explicitly set to false
    }

    @Override
    public void renderContent() {
        // Scope ALL IDs to this tab
        ImGui.pushID("GameTab");

        if (ImGui.button(FontAwesomeIcons.Undo + " Reset to Defaults")) {
            resetToDefaults();
        }

        ImGui.separator();

        if (ImGui.beginChild("Content", 0, 0, false)) {
            renderWindowSection();
            renderGameResolutionSection();
            renderDisplaySection();
            renderScalingSection();
            renderUISection();
            renderPostProcessingSection();
            ImGui.endChild();
        }

        // Render popup (must be at same level, not inside child)
        effectBrowserPopup.render();

        ImGui.popID();
    }

    private void renderWindowSection() {
        ImGui.setNextItemWidth(200);
        if (ImGui.inputText("Window Title", titleBuffer)) {
            working.setTitle(titleBuffer.get());
            updateDirtyFlag();
        }
        tooltip("The title displayed in the window title bar");

        ImGui.spacing();
        ImGui.text("Window Resolution");
        ImGui.indent();

        ImInt windowWidth = new ImInt(working.getWindowWidth());
        ImGui.setNextItemWidth(100);
        if (ImGui.inputInt("Window Width", windowWidth)) {
            working.setWindowWidth(Math.max(1, windowWidth.get()));
            updateDirtyFlag();
        }
        tooltip("Physical window width in pixels. Changes apply on restart.");

        ImInt windowHeight = new ImInt(working.getWindowHeight());
        ImGui.setNextItemWidth(100);
        if (ImGui.inputInt("Window Height", windowHeight)) {
            working.setWindowHeight(Math.max(1, windowHeight.get()));
            updateDirtyFlag();
        }
        tooltip("Physical window height in pixels. Changes apply on restart.");

        ImGui.unindent();
    }

    private void renderGameResolutionSection() {
        ImGui.spacing();
        ImGui.text("Game Resolution");
        ImGui.indent();

        ImInt gameWidth = new ImInt(working.getGameWidth());
        ImGui.setNextItemWidth(100);
        if (ImGui.inputInt("Game Width", gameWidth)) {
            working.setGameWidth(Math.max(1, gameWidth.get()));
            updateDirtyFlag();
        }
        tooltip("Internal game resolution width. Changes apply on restart.");

        ImInt gameHeight = new ImInt(working.getGameHeight());
        ImGui.setNextItemWidth(100);
        if (ImGui.inputInt("Game Height", gameHeight)) {
            working.setGameHeight(Math.max(1, gameHeight.get()));
            updateDirtyFlag();
        }
        tooltip("Internal game resolution height. Changes apply on restart.");

        ImGui.unindent();
    }

    private void renderDisplaySection() {
        ImGui.spacing();

        boolean fullscreen = working.isFullscreen();
        if (ImGui.checkbox("Fullscreen", fullscreen)) {
            working.setFullscreen(!fullscreen);
            updateDirtyFlag();
        }
        tooltip("Enable fullscreen mode");

        boolean vsync = working.isVsync();
        if (ImGui.checkbox("VSync", vsync)) {
            working.setVsync(!vsync);
            updateDirtyFlag();
        }
        tooltip("Synchronize frame rate with monitor refresh rate");
    }

    private void renderScalingSection() {
        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Scaling");
        ImGui.spacing();

        ImGui.setNextItemWidth(200);
        if (ImGui.beginCombo("Scaling Mode", working.getScalingMode().name())) {
            for (PostProcessor.ScalingMode mode : PostProcessor.ScalingMode.values()) {
                boolean selected = mode == working.getScalingMode();
                if (ImGui.selectable(mode.name(), selected)) {
                    working.setScalingMode(mode);
                    updateDirtyFlag();
                }
            }
            ImGui.endCombo();
        }
        tooltip("How the game image is scaled to fit the window");

        boolean pillarbox = working.isEnablePillarBox();
        if (ImGui.checkbox("Enable Pillarbox", pillarbox)) {
            working.setEnablePillarBox(!pillarbox);
            updateDirtyFlag();
        }
        tooltip("Add black bars to maintain aspect ratio");

        if (working.isEnablePillarBox()) {
            ImGui.indent();
            float[] aspect = {working.getPillarboxAspectRatio()};
            ImGui.setNextItemWidth(150);
            if (ImGui.dragFloat("Aspect Ratio", aspect, 0.01f, 0.0f, 3.0f, "%.3f")) {
                working.setPillarboxAspectRatio(aspect[0]);
                updateDirtyFlag();
            }
            tooltip("Target aspect ratio. 0 = auto-calculate from game resolution");
            ImGui.unindent();
        }
    }

    private void renderUISection() {
        ImGui.spacing();
        ImGui.separator();
        ImGui.text("UI");
        ImGui.spacing();

        float[] hoverTint = {working.getUiButtonHoverTint()};
        ImGui.setNextItemWidth(150);
        if (ImGui.sliderFloat("Button Hover Tint", hoverTint, 0.0f, 1.0f, "%.2f")) {
            working.setUiButtonHoverTint(hoverTint[0]);
            updateDirtyFlag();
        }
        tooltip("How much buttons darken when hovered");
    }

    private void renderPostProcessingSection() {
        ImGui.spacing();
        ImGui.separator();

        if (ImGui.collapsingHeader("Post-Processing Effects")) {
            List<PostEffect> effects = working.getPostProcessingEffects();

            if (effects == null || effects.isEmpty()) {
                ImGui.textDisabled("No effects configured");
            } else {
                ImGui.textDisabled("Effects are applied in order (top to bottom)");
                ImGui.spacing();

                for (int i = 0; i < effects.size(); i++) {
                    PostEffect effect = effects.get(i);
                    ImGui.pushID(i);

                    String effectName = effect.getClass().getSimpleName();
                    
                    // Expandable tree node for each effect
                    boolean nodeOpen = ImGui.treeNode("effect_node", effectName);
                    
                    // Buttons on the same line
                    ImGui.sameLine(ImGui.getContentRegionAvailX() - 80);
                    if (i > 0 && ImGui.smallButton(FontAwesomeIcons.ArrowUp)) {
                        swapEffects(effects, i, i - 1);
                        updateDirtyFlag();
                    }

                    ImGui.sameLine();
                    if (i < effects.size() - 1 && ImGui.smallButton(FontAwesomeIcons.ArrowDown)) {
                        swapEffects(effects, i, i + 1);
                        updateDirtyFlag();
                    }

                    ImGui.sameLine();
                    if (ImGui.smallButton(FontAwesomeIcons.Trash)) {
                        effects.remove(i);
                        updateDirtyFlag();
                        ImGui.popID();
                        if (nodeOpen) ImGui.treePop();
                        break;
                    }

                    // Show editable fields if node is open
                    if (nodeOpen) {
                        PostEffect newEffect = renderEffectFields(effect, i);
                        if (newEffect != null && newEffect != effect) {
                            effects.set(i, newEffect);
                            updateDirtyFlag();
                        }
                        ImGui.treePop();
                    }

                    ImGui.popID();
                }
            }

            ImGui.spacing();
            if (ImGui.button(FontAwesomeIcons.Plus + " Add Effect")) {
                effectBrowserPopup.open(effect -> {
                    if (effects != null) {
                        effects.add(effect);
                        updateDirtyFlag();
                    }
                });
            }
        }
    }

    /**
     * Renders editable fields for a post-effect.
     * Returns a new effect instance if any field was changed, null otherwise.
     */
    private PostEffect renderEffectFields(PostEffect effect, int index) {
        Class<?> clazz = effect.getClass();
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
        
        boolean anyChanged = false;
        java.util.Map<String, Object> newValues = new java.util.LinkedHashMap<>();
        
        for (java.lang.reflect.Field field : fields) {
            // Skip static, transient, and shader fields
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) continue;
            if (field.getType().getSimpleName().equals("Shader")) continue;
            
            field.setAccessible(true);
            String fieldName = field.getName();
            
            // Skip internal fields
            if (fieldName.contains("Shader") || fieldName.equals("initialized")) continue;
            
            try {
                Object value = field.get(effect);
                String displayName = formatFieldName(fieldName);
                
                if (field.getType() == float.class || field.getType() == Float.class) {
                    float[] floatVal = {(float) value};
                    ImGui.setNextItemWidth(120);
                    if (ImGui.dragFloat(displayName, floatVal, 0.01f, 0.0f, 10.0f, "%.3f")) {
                        newValues.put(fieldName, floatVal[0]);
                        anyChanged = true;
                    } else {
                        newValues.put(fieldName, value);
                    }
                } else if (field.getType() == int.class || field.getType() == Integer.class) {
                    int[] intVal = {(int) value};
                    ImGui.setNextItemWidth(120);
                    if (ImGui.dragInt(displayName, intVal, 1, 0, 100)) {
                        newValues.put(fieldName, intVal[0]);
                        anyChanged = true;
                    } else {
                        newValues.put(fieldName, value);
                    }
                } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    boolean boolVal = (boolean) value;
                    if (ImGui.checkbox(displayName, boolVal)) {
                        newValues.put(fieldName, !boolVal);
                        anyChanged = true;
                    } else {
                        newValues.put(fieldName, value);
                    }
                } else {
                    // Show non-editable fields as text
                    ImGui.textDisabled(String.format("%s: %s", displayName, value));
                    newValues.put(fieldName, value);
                }
            } catch (Exception e) {
                // Skip fields that can't be accessed
            }
        }
        
        // If any value changed, create new effect instance
        if (anyChanged) {
            return createEffectWithValues(clazz, newValues);
        }
        
        return null;
    }

    private String formatFieldName(String fieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private PostEffect createEffectWithValues(Class<?> clazz, java.util.Map<String, Object> values) {
        try {
            // Try to find a constructor that matches the field types
            for (java.lang.reflect.Constructor<?> constructor : clazz.getConstructors()) {
                java.lang.reflect.Parameter[] params = constructor.getParameters();
                
                if (params.length == 0) {
                    // Use no-arg constructor then set fields
                    PostEffect effect = (PostEffect) constructor.newInstance();
                    for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
                        try {
                            java.lang.reflect.Field field = clazz.getDeclaredField(entry.getKey());
                            field.setAccessible(true);
                            field.set(effect, entry.getValue());
                        } catch (Exception ignored) {}
                    }
                    return effect;
                }
                
                // Try to match constructor parameters to our values
                if (params.length == values.size()) {
                    Object[] args = new Object[params.length];
                    boolean matches = true;
                    int idx = 0;
                    for (Object value : values.values()) {
                        if (idx < params.length && isAssignable(params[idx].getType(), value)) {
                            args[idx] = value;
                        } else {
                            matches = false;
                            break;
                        }
                        idx++;
                    }
                    if (matches) {
                        return (PostEffect) constructor.newInstance(args);
                    }
                }
            }
            
            // Fallback: try no-arg constructor
            return (PostEffect) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("Failed to create effect: " + e.getMessage());
            return null;
        }
    }

    private boolean isAssignable(Class<?> paramType, Object value) {
        if (value == null) return !paramType.isPrimitive();
        Class<?> valueType = value.getClass();
        if (paramType.isPrimitive()) {
            if (paramType == float.class) return valueType == Float.class;
            if (paramType == int.class) return valueType == Integer.class;
            if (paramType == boolean.class) return valueType == Boolean.class;
            if (paramType == double.class) return valueType == Double.class;
        }
        return paramType.isAssignableFrom(valueType);
    }

    private void swapEffects(List<PostEffect> effects, int i, int j) {
        PostEffect temp = effects.get(i);
        effects.set(i, effects.get(j));
        effects.set(j, temp);
    }

    private void updateDirtyFlag() {
        dirty = !isConfigEqual(working, original);
    }

    private boolean isConfigEqual(GameConfig a, GameConfig b) {
        if (!a.getTitle().equals(b.getTitle())) return false;
        if (a.getWindowWidth() != b.getWindowWidth()) return false;
        if (a.getWindowHeight() != b.getWindowHeight()) return false;
        if (a.getGameWidth() != b.getGameWidth()) return false;
        if (a.getGameHeight() != b.getGameHeight()) return false;
        if (a.isFullscreen() != b.isFullscreen()) return false;
        if (a.isVsync() != b.isVsync()) return false;
        if (a.getScalingMode() != b.getScalingMode()) return false;
        if (a.isEnablePillarBox() != b.isEnablePillarBox()) return false;
        if (a.getPillarboxAspectRatio() != b.getPillarboxAspectRatio()) return false;
        if (a.getUiButtonHoverTint() != b.getUiButtonHoverTint()) return false;
        
        // Compare post-processing effects (by count and class)
        return areEffectsEqual(a.getPostProcessingEffects(), b.getPostProcessingEffects());
    }

    private boolean areEffectsEqual(List<PostEffect> a, List<PostEffect> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            PostEffect ea = a.get(i);
            PostEffect eb = b.get(i);
            if (!ea.getClass().equals(eb.getClass())) return false;
            
            // Compare field values
            if (!areEffectFieldsEqual(ea, eb)) return false;
        }
        return true;
    }

    private boolean areEffectFieldsEqual(PostEffect a, PostEffect b) {
        Class<?> clazz = a.getClass();
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) continue;
            if (field.getType().getSimpleName().equals("Shader")) continue;
            
            try {
                field.setAccessible(true);
                Object valA = field.get(a);
                Object valB = field.get(b);
                if (!java.util.Objects.equals(valA, valB)) {
                    return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    private void applyToLive() {
        GameConfig live = context.getGameConfig();
        live.setTitle(working.getTitle());
        live.setWindowWidth(working.getWindowWidth());
        live.setWindowHeight(working.getWindowHeight());
        live.setGameWidth(working.getGameWidth());
        live.setGameHeight(working.getGameHeight());
        live.setFullscreen(working.isFullscreen());
        live.setVsync(working.isVsync());
        live.setScalingMode(working.getScalingMode());
        live.setEnablePillarBox(working.isEnablePillarBox());
        live.setPillarboxAspectRatio(working.getPillarboxAspectRatio());
        live.setUiButtonHoverTint(working.getUiButtonHoverTint());
    }

    private GameConfig cloneConfig(GameConfig source) {
        return GameConfig.builder()
                .title(source.getTitle())
                .windowWidth(source.getWindowWidth())
                .windowHeight(source.getWindowHeight())
                .gameWidth(source.getGameWidth())
                .gameHeight(source.getGameHeight())
                .fullscreen(source.isFullscreen())
                .vsync(source.isVsync())
                .scalingMode(source.getScalingMode())
                .enablePillarBox(source.isEnablePillarBox())
                .pillarboxAspectRatio(source.getPillarboxAspectRatio())
                .uiButtonHoverTint(source.getUiButtonHoverTint())
                .postProcessingEffects(cloneEffects(source.getPostProcessingEffects()))
                .defaultTransitionConfig(source.getDefaultTransitionConfig())
                .build();
    }

    private List<PostEffect> cloneEffects(List<PostEffect> source) {
        List<PostEffect> cloned = new ArrayList<>();
        for (PostEffect effect : source) {
            PostEffect copy = cloneEffect(effect);
            if (copy != null) {
                cloned.add(copy);
            } else {
                // Fallback: just use the same instance (won't detect field changes)
                cloned.add(effect);
            }
        }
        return cloned;
    }

    @SuppressWarnings("unchecked")
    private PostEffect cloneEffect(PostEffect effect) {
        Class<?> clazz = effect.getClass();
        try {
            // Collect field values
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
            List<Object> constructorArgs = new ArrayList<>();
            
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) continue;
                if (field.getType().getSimpleName().equals("Shader")) continue;
                
                field.setAccessible(true);
                Object value = field.get(effect);
                values.put(field.getName(), value);
                
                // Primitive types are likely constructor parameters
                if (field.getType().isPrimitive() || field.getType() == Float.class || 
                    field.getType() == Integer.class || field.getType() == Boolean.class) {
                    constructorArgs.add(value);
                }
            }
            
            // Try to find matching constructor
            for (java.lang.reflect.Constructor<?> constructor : clazz.getConstructors()) {
                java.lang.reflect.Parameter[] params = constructor.getParameters();
                
                if (params.length == constructorArgs.size()) {
                    try {
                        return (PostEffect) constructor.newInstance(constructorArgs.toArray());
                    } catch (Exception ignored) {}
                }
            }
            
            // Fallback: use no-arg constructor
            return (PostEffect) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
