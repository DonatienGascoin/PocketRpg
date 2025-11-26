package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.rendering.Shader;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;

/**
 * Loader for shader assets.
 * Wraps the existing Shader class and provides hot reload support.
 * 
 * Shaders are loaded from .glsl files containing both vertex and fragment shaders
 * separated by #type directives.
 * 
 * Hot Reload Support:
 * - Edit shader file in external editor
 * - Save changes
 * - Shader automatically recompiles and updates in game
 * - Invalid shaders revert to last working version
 * 
 * Usage:
 * <pre>
 * AssetManager manager = AssetManager.getInstance();
 * manager.registerLoader("shader", new ShaderLoader());
 * 
 * ResourceHandle&lt;Shader&gt; shader = manager.load("sprite.glsl");
 * </pre>
 */
public class ShaderLoader implements AssetLoader<Shader> {

    /**
     * Loads a shader from the specified file path.
     * The shader is compiled and linked immediately.
     *
     * @param path Path to the shader file
     * @return Loaded and compiled shader
     * @throws IOException if shader loading or compilation fails
     */
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

    /**
     * Unloads a shader and releases GPU resources.
     *
     * @param shader The shader to unload
     */
    @Override
    public void unload(Shader shader) {
        if (shader != null) {
            shader.delete();
        }
    }

    /**
     * Returns supported shader file extensions.
     *
     * @return Array of supported extensions
     */
    @Override
    public String[] getSupportedExtensions() {
        return new String[]{
                ".glsl",
                ".shader",
                ".vs", ".fs"  // Separate vertex/fragment shader files
        };
    }

    /**
     * Shaders don't have a meaningful placeholder.
     * Returns null - shader will be null until loaded.
     *
     * @return null
     */
    @Override
    public Shader getPlaceholder() {
        return null;
    }

    /**
     * Shaders fully support hot reloading.
     * This is one of the most useful features - edit shaders while game runs!
     *
     * @return true
     */
    @Override
    public boolean supportsHotReload() {
        return true;
    }

    /**
     * Reloads a shader from disk with hot swap support.
     * 
     * Process:
     * 1. Read shader source from disk
     * 2. Compile new shader program
     * 3. If successful, delete old program and swap
     * 4. If failed, keep old shader and log error
     * 
     * This allows iterating on shaders without restarting the game!
     *
     * @param existing The existing shader
     * @param path     Path to reload from
     * @return Updated shader (may be same instance with new program)
     * @throws IOException if reload fails critically
     */
    @Override
    public Shader reload(Shader existing, String path) throws IOException {
        if (existing == null) {
            // No existing shader - just load fresh
            return load(path);
        }

        try {
            // Create new shader with same path
            Shader newShader = new Shader(path);
            newShader.compileAndLink();

            // Success! Delete old shader
            existing.delete();

            return newShader;

        } catch (RuntimeException e) {
            // Compilation failed - keep old shader
            System.err.println("Hot reload failed for shader: " + path);
            System.err.println("Keeping previous version. Error: " + e.getMessage());
            
            // Return existing shader (still works)
            return existing;
        }
    }

    /**
     * Estimates shader memory usage.
     * Shaders are relatively small - mainly GPU program storage.
     *
     * @param shader The shader to measure
     * @return Estimated size in bytes (rough approximation)
     */
    @Override
    public long estimateSize(Shader shader) {
        if (shader == null) return 0;

        // Rough estimate: shader programs are typically 1-10KB
        // This is a conservative estimate
        return 5 * 1024; // 5KB average
    }

    /**
     * Gets the loader type name.
     *
     * @return "shader"
     */
    @Override
    public String getTypeName() {
        return "shader";
    }
}
