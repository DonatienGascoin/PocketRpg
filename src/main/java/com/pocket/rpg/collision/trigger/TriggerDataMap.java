package com.pocket.rpg.collision.trigger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores trigger metadata for tiles that require configuration.
 * <p>
 * Works alongside CollisionMap - CollisionMap stores the type (WARP, DOOR, etc.),
 * TriggerDataMap stores the configuration data for that tile.
 * <p>
 * Uses packed long keys for memory efficiency.
 */
public class TriggerDataMap {

    private final Map<Long, TriggerData> triggers = new HashMap<>();

    /**
     * Gets trigger data at coordinates.
     *
     * @return TriggerData or null if not configured
     */
    public TriggerData get(int x, int y, int elevation) {
        return triggers.get(new TileCoord(x, y, elevation).pack());
    }

    /**
     * Gets trigger data at coordinates.
     */
    public TriggerData get(TileCoord coord) {
        return triggers.get(coord.pack());
    }

    /**
     * Sets trigger data at coordinates.
     */
    public void set(int x, int y, int elevation, TriggerData data) {
        triggers.put(new TileCoord(x, y, elevation).pack(), data);
    }

    /**
     * Sets trigger data at coordinates.
     */
    public void set(TileCoord coord, TriggerData data) {
        triggers.put(coord.pack(), data);
    }

    /**
     * Removes trigger data at coordinates.
     *
     * @return The removed data, or null if none existed
     */
    public TriggerData remove(int x, int y, int elevation) {
        return triggers.remove(new TileCoord(x, y, elevation).pack());
    }

    /**
     * Removes trigger data at coordinates.
     *
     * @return The removed data, or null if none existed
     */
    public TriggerData remove(TileCoord coord) {
        return triggers.remove(coord.pack());
    }

    /**
     * Checks if coordinates have trigger data configured.
     */
    public boolean has(int x, int y, int elevation) {
        return triggers.containsKey(new TileCoord(x, y, elevation).pack());
    }

    /**
     * Checks if coordinates have trigger data configured.
     */
    public boolean has(TileCoord coord) {
        return triggers.containsKey(coord.pack());
    }

    /**
     * Returns all configured triggers as a map of coordinates to data.
     */
    public Map<TileCoord, TriggerData> getAll() {
        Map<TileCoord, TriggerData> result = new HashMap<>();
        for (var entry : triggers.entrySet()) {
            result.put(TileCoord.unpack(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Returns all triggers of a specific type.
     */
    public List<Map.Entry<TileCoord, TriggerData>> getByType(Class<? extends TriggerData> type) {
        return triggers.entrySet().stream()
                .filter(e -> type.isInstance(e.getValue()))
                .map(e -> Map.entry(TileCoord.unpack(e.getKey()), e.getValue()))
                .toList();
    }

    /**
     * Clears all trigger data.
     */
    public void clear() {
        triggers.clear();
    }

    /**
     * Returns number of configured triggers.
     */
    public int size() {
        return triggers.size();
    }

    /**
     * Returns true if no triggers are configured.
     */
    public boolean isEmpty() {
        return triggers.isEmpty();
    }

    /**
     * Copies all trigger data from another map.
     */
    public void copyFrom(TriggerDataMap other) {
        triggers.putAll(other.triggers);
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(TriggerData.class, new TriggerDataTypeAdapter())
            .create();

    /**
     * Converts this map to a serializable format for scene files.
     * Keys are "x,y,z" coordinate strings, values are serialized TriggerData.
     *
     * @return Map suitable for JSON serialization
     */
    public Map<String, Object> toSerializableMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : triggers.entrySet()) {
            TileCoord coord = TileCoord.unpack(entry.getKey());
            String key = coord.x() + "," + coord.y() + "," + coord.elevation();

            // Serialize TriggerData to JSON then parse as Map for clean embedding
            String json = GSON.toJson(entry.getValue(), TriggerData.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = GSON.fromJson(json, Map.class);
            result.put(key, dataMap);
        }
        return result;
    }

    /**
     * Loads trigger data from a serialized map.
     *
     * @param data Map from scene file (keys are "x,y,z" strings)
     */
    @SuppressWarnings("unchecked")
    public void fromSerializableMap(Map<String, Object> data) {
        triggers.clear();
        if (data == null) return;

        for (var entry : data.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(",");
            if (parts.length != 3) {
                System.err.println("Invalid trigger key format: " + key);
                continue;
            }

            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                // Convert value back to JSON string for proper deserialization
                String json = GSON.toJson(entry.getValue());
                TriggerData triggerData = GSON.fromJson(json, TriggerData.class);

                if (triggerData != null) {
                    set(x, y, z, triggerData);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid trigger coordinates: " + key);
            }
        }
    }
}
