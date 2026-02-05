package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.rendering.resources.Shader;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;

/**
 * Loader for shader assets.
 * Supports GLSL shaders with hot reload capability.
 */
public class ShaderLoader implements AssetLoader<Shader> {

    @Override
    public Shader load(String path) throws IOException {
        try {
            Shader shader = new Shader(path);
            shader.compileAndLink();
            return shader;
        } catch (RuntimeException e) {
            // Shader constructor or compileAndLink throws RuntimeException on failure
            throw new IOException("Failed to load shader: " + path, e);
        }
    }

    @Override
    public void save(Shader shader, String path) throws IOException {
        throw new UnsupportedOperationException("Shader saving not supported");
    }

    @Override
    public Shader getPlaceholder() {
        // Shaders don't have a meaningful placeholder
        // Return null and let the system handle it
        return null;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".glsl", ".shader", ".vs", ".fs"};
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public Shader reload(Shader existing, String path) throws IOException {
        if (existing == null) {
            return load(path);
        }
        // Mutate existing shader in place
        try {
            existing.reloadFromDisk(path);
            return existing; // Same reference
        } catch (RuntimeException e) {
            throw new IOException("Failed to reload shader: " + path, e);
        }
    }

    // ========================================================================
    // EDITOR INSTANTIATION SUPPORT
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return false; // Shaders cannot be instantiated as entities
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.Code;
    }
}
