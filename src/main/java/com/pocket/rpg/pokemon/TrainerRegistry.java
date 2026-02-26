package com.pocket.rpg.pokemon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of all trainer definitions, loaded from {@code .trainers.json} files.
 */
public class TrainerRegistry {
    private Map<String, TrainerDefinition> trainers = new LinkedHashMap<>();

    public TrainerRegistry() {}

    public TrainerDefinition getTrainer(String id) {
        return trainers.get(id);
    }

    public void addTrainer(TrainerDefinition def) {
        trainers.put(def.getTrainerId(), def);
    }

    public void removeTrainer(String id) {
        trainers.remove(id);
    }

    public Collection<TrainerDefinition> getAllTrainers() {
        return trainers.values();
    }

    /**
     * Mutates this instance in place to match the other.
     * Required by the hot-reload contract.
     */
    public void copyFrom(TrainerRegistry other) {
        this.trainers.clear();
        this.trainers.putAll(other.trainers);
    }
}
