package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.panels.content.EditorContentFor;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;

import static org.reflections.scanners.Scanners.SubTypes;

/**
 * Registry that maps asset types to their {@link AssetEditorContent} implementations.
 * When no specific content is registered for an asset type, the default
 * (ReflectionEditorContent) is used.
 */
public class AssetEditorContentRegistry {

    private final Map<Class<?>, Supplier<AssetEditorContent>> contentFactories = new LinkedHashMap<>();
    private Supplier<AssetEditorContent> defaultFactory;

    /**
     * Registers a content implementation for a specific asset type.
     *
     * @param assetClass The asset class (e.g., Dialogue.class)
     * @param factory    Supplier that creates a new content instance
     */
    public void register(Class<?> assetClass, Supplier<AssetEditorContent> factory) {
        contentFactories.put(assetClass, factory);
    }

    /**
     * Sets the default content factory used when no specific mapping exists.
     */
    public void setDefaultFactory(Supplier<AssetEditorContent> factory) {
        this.defaultFactory = factory;
    }

    /**
     * Scans for {@link AssetEditorContent} implementations annotated with
     * {@link EditorContentFor} and registers them automatically.
     * <p>
     * Each annotated class must have a no-arg constructor. The annotation's value
     * specifies which asset class the content handles.
     */
    public void scanAndRegisterContent() {
        try {
            Reflections reflections = new Reflections("com.pocket.rpg");
            Set<Class<?>> contentClasses = reflections.get(SubTypes.of(AssetEditorContent.class).asClass());

            int registered = 0;
            for (Class<?> clazz : contentClasses) {
                if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }

                EditorContentFor annotation = clazz.getAnnotation(EditorContentFor.class);
                if (annotation == null) {
                    continue;
                }

                Class<?> assetClass = annotation.value();

                try {
                    Constructor<?> constructor = clazz.getDeclaredConstructor();
                    constructor.setAccessible(true);

                    @SuppressWarnings("unchecked")
                    Class<? extends AssetEditorContent> contentClass = (Class<? extends AssetEditorContent>) clazz;
                    register(assetClass, () -> {
                        try {
                            return contentClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to create content: " + contentClass.getName(), e);
                        }
                    });
                    registered++;

                } catch (NoSuchMethodException e) {
                    System.err.println("AssetEditorContent must have a no-arg constructor: " + clazz.getName());
                }
            }

            System.out.println("Content scanning complete. Registered " + registered + " editor content types.");

        } catch (Exception e) {
            System.err.println("Error scanning for editor content: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets or creates a content implementation for the given asset type.
     * Returns null if no mapping exists and no default is set.
     *
     * @param assetClass The asset class
     * @return A new content instance, or null
     */
    public AssetEditorContent createContent(Class<?> assetClass) {
        Supplier<AssetEditorContent> factory = contentFactories.get(assetClass);
        if (factory != null) {
            return factory.get();
        }
        if (defaultFactory != null) {
            return defaultFactory.get();
        }
        return null;
    }

    /**
     * Returns all registered asset types (excluding the default).
     */
    public Set<Class<?>> getRegisteredTypes() {
        return Collections.unmodifiableSet(contentFactories.keySet());
    }

    /**
     * Checks if a specific content is registered for the given type
     * (not counting the default fallback).
     */
    public boolean hasContentFor(Class<?> assetClass) {
        return contentFactories.containsKey(assetClass);
    }

    /**
     * Collects all extra shortcuts from all registered content types.
     * Creates temporary content instances to query their shortcuts;
     * these instances are not initialized (no resources to clean up).
     *
     * @param layout Keyboard layout for binding choices
     * @return All content shortcuts across all registered types
     */
    public List<ShortcutAction> collectAllContentShortcuts(KeyboardLayout layout) {
        List<ShortcutAction> all = new ArrayList<>();
        for (Supplier<AssetEditorContent> factory : contentFactories.values()) {
            AssetEditorContent temp = factory.get();
            all.addAll(temp.provideExtraShortcuts(layout));
        }
        return all;
    }
}
