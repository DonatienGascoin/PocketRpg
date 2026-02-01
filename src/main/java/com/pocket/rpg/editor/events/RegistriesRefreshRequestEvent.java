package com.pocket.rpg.editor.events;

/**
 * Published when registries should re-scan the classpath.
 * <p>
 * Subscribers (ComponentRegistry, PostEffectRegistry, CustomComponentEditorRegistry,
 * and any future scannable registries) should clear their caches and re-scan.
 * <p>
 * This event is published by the scene reload flow, before the scene is rebuilt.
 * Subscribers must NOT access or modify the current scene.
 */
public record RegistriesRefreshRequestEvent() implements EditorEvent {}
