package com.pocket.rpg.editor.panels;

/**
 * Metadata for asset file creation. Content types that support creating
 * new assets return this from {@link AssetEditorContent#getCreationInfo()}.
 *
 * @param subdirectory Asset subdirectory under asset root (e.g., "animators/")
 * @param extension    File extension including dots (e.g., ".animator.json")
 */
public record AssetCreationInfo(String subdirectory, String extension) {}
