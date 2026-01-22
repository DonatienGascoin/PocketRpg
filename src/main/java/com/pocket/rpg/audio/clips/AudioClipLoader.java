package com.pocket.rpg.audio.clips;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.AssetLoader;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Asset loader for audio clips.
 * Supports WAV and OGG Vorbis formats.
 */
public class AudioClipLoader implements AssetLoader<AudioClip> {

    @Override
    public AudioClip load(String path) throws IOException {
        String lowerPath = path.toLowerCase();

        if (lowerPath.endsWith(".wav")) {
            return loadWav(path);
        } else if (lowerPath.endsWith(".ogg")) {
            return loadOgg(path);
        } else {
            throw new IOException("Unsupported audio format: " + path);
        }
    }

    @Override
    public void save(AudioClip audioClip, String path) throws IOException {
        throw new UnsupportedOperationException("Audio clip saving not supported");
    }

    @Override
    public AudioClip getPlaceholder() {
        // No placeholder for audio - return null
        return null;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".wav", ".ogg"};
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.AudioFile;
    }

    // ========================================================================
    // WAV LOADING
    // ========================================================================

    private AudioClip loadWav(String path) throws IOException {
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new FileInputStream(path)))) {

            AudioFormat format = audioInputStream.getFormat();

            // Convert to PCM if needed
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16, // 16-bit
                    format.getChannels(),
                    format.getChannels() * 2, // 2 bytes per sample per channel
                    format.getSampleRate(),
                    false // little-endian
            );

            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);

            // Read all audio data
            byte[] audioData = pcmStream.readAllBytes();

            // Convert to shorts
            short[] samples = new short[audioData.length / 2];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) ((audioData[i * 2] & 0xFF) | (audioData[i * 2 + 1] << 8));
            }

            // Get audio backend
            AudioBackend backend = getBackend();
            if (backend == null) {
                throw new IOException("Audio system not initialized");
            }

            // Determine format
            int alFormat = targetFormat.getChannels() == 1
                    ? backend.getFormatMono16()
                    : backend.getFormatStereo16();

            // Create buffer
            int bufferId = backend.createBuffer(samples, alFormat, (int) targetFormat.getSampleRate());

            // Calculate duration
            float duration = (float) samples.length / targetFormat.getChannels() / targetFormat.getSampleRate();

            // Extract name from path
            String name = extractName(path);

            return new AudioClip(name, path, bufferId, duration,
                    (int) targetFormat.getSampleRate(), targetFormat.getChannels());

        } catch (Exception e) {
            throw new IOException("Failed to load WAV file: " + path, e);
        }
    }

    // ========================================================================
    // OGG LOADING
    // ========================================================================

    private AudioClip loadOgg(String path) throws IOException {
        // Read entire file into memory
        byte[] fileData = Files.readAllBytes(Path.of(path));
        ByteBuffer buffer = MemoryUtil.memAlloc(fileData.length);
        buffer.put(fileData).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuffer = stack.mallocInt(1);
            IntBuffer sampleRateBuffer = stack.mallocInt(1);

            ShortBuffer audioBuffer = STBVorbis.stb_vorbis_decode_memory(
                    buffer, channelsBuffer, sampleRateBuffer);

            if (audioBuffer == null) {
                throw new IOException("Failed to decode OGG file: " + path);
            }

            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);

            // Convert to short array
            short[] samples = new short[audioBuffer.remaining()];
            audioBuffer.get(samples);

            // Free STB buffer
            MemoryUtil.memFree(audioBuffer);

            // Get audio backend
            AudioBackend backend = getBackend();
            if (backend == null) {
                throw new IOException("Audio system not initialized");
            }

            // Determine format
            int alFormat = channels == 1
                    ? backend.getFormatMono16()
                    : backend.getFormatStereo16();

            // Create buffer
            int bufferId = backend.createBuffer(samples, alFormat, sampleRate);

            // Calculate duration
            float duration = (float) samples.length / channels / sampleRate;

            // Extract name from path
            String name = extractName(path);

            return new AudioClip(name, path, bufferId, duration, sampleRate, channels);

        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private AudioBackend getBackend() {
        if (Audio.isInitialized() && Audio.getEngine() != null) {
            return Audio.getEngine().getBackend();
        }
        return null;
    }

    private String extractName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(0, lastDot) : filename;
    }
}
