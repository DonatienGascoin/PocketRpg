package com.pocket.rpg.audio.backend;

import org.joml.Vector3f;
import org.lwjgl.openal.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * OpenAL implementation of AudioBackend.
 * Uses LWJGL's OpenAL bindings for cross-platform audio.
 */
public class OpenALAudioBackend implements AudioBackend {

    private long device;
    private long context;
    private boolean initialized = false;

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }

        // Open default device
        String defaultDevice = alcGetString(0, ALC_DEFAULT_DEVICE_SPECIFIER);
        device = alcOpenDevice(defaultDevice);

        if (device == NULL) {
            throw new RuntimeException("Failed to open OpenAL device");
        }

        // Create context
        context = alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) {
            alcCloseDevice(device);
            throw new RuntimeException("Failed to create OpenAL context");
        }

        // Make context current
        if (!alcMakeContextCurrent(context)) {
            alcDestroyContext(context);
            alcCloseDevice(device);
            throw new RuntimeException("Failed to make OpenAL context current");
        }

        // Create AL capabilities
        ALCCapabilities alcCapabilities = ALC.createCapabilities(device);
        AL.createCapabilities(alcCapabilities);

        // Set default listener position
        alListener3f(AL_POSITION, 0f, 0f, 0f);
        alListener3f(AL_VELOCITY, 0f, 0f, 0f);
        alListenerfv(AL_ORIENTATION, new float[]{0f, 0f, -1f, 0f, 1f, 0f});

        initialized = true;
        System.out.println("OpenAL audio backend initialized");
    }

    @Override
    public void destroy() {
        if (!initialized) {
            return;
        }

        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);

        initialized = false;
        System.out.println("OpenAL audio backend destroyed");
    }

    @Override
    public int createBuffer(short[] data, int format, int sampleRate) {
        int bufferId = alGenBuffers();

        // Convert short[] to ByteBuffer
        ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(data.length * 2);
        for (short s : data) {
            buffer.putShort(s);
        }
        buffer.flip();

        alBufferData(bufferId, format, buffer, sampleRate);

        int error = alGetError();
        if (error != AL_NO_ERROR) {
            alDeleteBuffers(bufferId);
            throw new RuntimeException("Failed to create audio buffer: " + getErrorString(error));
        }

        return bufferId;
    }

    @Override
    public void deleteBuffer(int bufferId) {
        alDeleteBuffers(bufferId);
    }

    @Override
    public int createSource() {
        int sourceId = alGenSources();

        int error = alGetError();
        if (error != AL_NO_ERROR) {
            throw new RuntimeException("Failed to create audio source: " + getErrorString(error));
        }

        return sourceId;
    }

    @Override
    public void deleteSource(int sourceId) {
        alDeleteSources(sourceId);
    }

    @Override
    public void setSourceBuffer(int sourceId, int bufferId) {
        alSourcei(sourceId, AL_BUFFER, bufferId);
    }

    @Override
    public void playSource(int sourceId) {
        alSourcePlay(sourceId);
    }

    @Override
    public void pauseSource(int sourceId) {
        alSourcePause(sourceId);
    }

    @Override
    public void stopSource(int sourceId) {
        alSourceStop(sourceId);
    }

    @Override
    public boolean isSourcePlaying(int sourceId) {
        return alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING;
    }

    @Override
    public boolean isSourcePaused(int sourceId) {
        return alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PAUSED;
    }

    @Override
    public void setSourceVolume(int sourceId, float volume) {
        alSourcef(sourceId, AL_GAIN, volume);
    }

    @Override
    public void setSourcePitch(int sourceId, float pitch) {
        alSourcef(sourceId, AL_PITCH, pitch);
    }

    @Override
    public void setSourceLooping(int sourceId, boolean loop) {
        alSourcei(sourceId, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
    }

    @Override
    public void setSourcePosition(int sourceId, Vector3f position) {
        alSource3f(sourceId, AL_POSITION, position.x, position.y, position.z);
    }

    @Override
    public void setSourceAttenuation(int sourceId, float minDistance, float maxDistance, float rolloffFactor) {
        alSourcef(sourceId, AL_REFERENCE_DISTANCE, minDistance);
        alSourcef(sourceId, AL_MAX_DISTANCE, maxDistance);
        alSourcef(sourceId, AL_ROLLOFF_FACTOR, rolloffFactor);
    }

    @Override
    public float getSourcePlaybackTime(int sourceId) {
        return alGetSourcef(sourceId, AL_SEC_OFFSET);
    }

    @Override
    public void setSourcePlaybackTime(int sourceId, float seconds) {
        alSourcef(sourceId, AL_SEC_OFFSET, seconds);
    }

    @Override
    public void setListenerPosition(Vector3f position) {
        alListener3f(AL_POSITION, position.x, position.y, position.z);
    }

    @Override
    public void setListenerOrientation(Vector3f forward, Vector3f up) {
        float[] orientation = new float[]{
                forward.x, forward.y, forward.z,
                up.x, up.y, up.z
        };
        alListenerfv(AL_ORIENTATION, orientation);
    }

    @Override
    public int getFormatMono16() {
        return AL_FORMAT_MONO16;
    }

    @Override
    public int getFormatStereo16() {
        return AL_FORMAT_STEREO16;
    }

    private String getErrorString(int error) {
        return switch (error) {
            case AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> "Unknown error: " + error;
        };
    }
}
