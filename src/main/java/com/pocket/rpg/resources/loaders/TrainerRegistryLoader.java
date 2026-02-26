package com.pocket.rpg.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pocket.rpg.dialogue.Dialogue;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.pokemon.TrainerDefinition;
import com.pocket.rpg.pokemon.TrainerPokemonSpec;
import com.pocket.rpg.pokemon.TrainerRegistry;
import com.pocket.rpg.resources.Assets;

import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for TrainerRegistry files ({@code .trainers.json}).
 */
public class TrainerRegistryLoader extends JsonAssetLoader<TrainerRegistry> {

    private static final String[] EXTENSIONS = {".trainers.json"};

    @Override
    protected TrainerRegistry fromJson(JsonObject json, String path) {
        TrainerRegistry registry = new TrainerRegistry();

        if (json.has("trainers") && json.get("trainers").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("trainers")) {
                registry.addTrainer(parseTrainer(elem.getAsJsonObject()));
            }
        }

        return registry;
    }

    private TrainerDefinition parseTrainer(JsonObject json) {
        TrainerDefinition def = new TrainerDefinition();

        def.setTrainerId(json.get("trainerId").getAsString());

        if (json.has("trainerName") && !json.get("trainerName").isJsonNull())
            def.setTrainerName(json.get("trainerName").getAsString());
        if (json.has("tag") && !json.get("tag").isJsonNull())
            def.setTag(json.get("tag").getAsString());
        if (json.has("spriteId") && !json.get("spriteId").isJsonNull())
            def.setSpriteId(json.get("spriteId").getAsString());
        if (json.has("defeatMoney"))
            def.setDefeatMoney(json.get("defeatMoney").getAsInt());

        // Dialogue assets — stored as path strings, loaded via Assets
        if (json.has("preDialogue") && !json.get("preDialogue").isJsonNull()) {
            def.setPreDialogue(Assets.load(json.get("preDialogue").getAsString(), Dialogue.class));
        }
        if (json.has("postDialogue") && !json.get("postDialogue").isJsonNull()) {
            def.setPostDialogue(Assets.load(json.get("postDialogue").getAsString(), Dialogue.class));
        }

        // Party
        if (json.has("party") && json.get("party").isJsonArray()) {
            List<TrainerPokemonSpec> party = new ArrayList<>();
            for (JsonElement elem : json.getAsJsonArray("party")) {
                party.add(parseSpec(elem.getAsJsonObject()));
            }
            def.setParty(party);
        }

        return def;
    }

    private TrainerPokemonSpec parseSpec(JsonObject json) {
        TrainerPokemonSpec spec = new TrainerPokemonSpec();
        if (json.has("speciesId"))
            spec.setSpeciesId(json.get("speciesId").getAsString());
        if (json.has("level"))
            spec.setLevel(json.get("level").getAsInt());
        if (json.has("moves") && json.get("moves").isJsonArray()) {
            List<String> moves = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("moves")) {
                moves.add(e.getAsString());
            }
            spec.setMoves(moves);
        }
        return spec;
    }

    @Override
    protected JsonObject toJson(TrainerRegistry registry) {
        JsonObject root = new JsonObject();

        JsonArray trainersArray = new JsonArray();
        for (TrainerDefinition def : registry.getAllTrainers()) {
            trainersArray.add(serializeTrainer(def));
        }
        root.add("trainers", trainersArray);

        return root;
    }

    private JsonObject serializeTrainer(TrainerDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("trainerId", def.getTrainerId());
        json.addProperty("trainerName", def.getTrainerName());
        json.addProperty("tag", def.getTag());
        json.addProperty("spriteId", def.getSpriteId());
        json.addProperty("defeatMoney", def.getDefeatMoney());

        // Dialogue assets — stored as path strings
        String prePath = def.getPreDialogue() != null
                ? Assets.getPathForResource(def.getPreDialogue()) : null;
        String postPath = def.getPostDialogue() != null
                ? Assets.getPathForResource(def.getPostDialogue()) : null;
        json.addProperty("preDialogue", prePath);
        json.addProperty("postDialogue", postPath);

        // Party
        JsonArray partyArray = new JsonArray();
        if (def.getParty() != null) {
            for (TrainerPokemonSpec spec : def.getParty()) {
                partyArray.add(serializeSpec(spec));
            }
        }
        json.add("party", partyArray);

        return json;
    }

    private JsonObject serializeSpec(TrainerPokemonSpec spec) {
        JsonObject json = new JsonObject();
        json.addProperty("speciesId", spec.getSpeciesId());
        json.addProperty("level", spec.getLevel());
        if (spec.getMoves() != null && !spec.getMoves().isEmpty()) {
            JsonArray moves = new JsonArray();
            for (String m : spec.getMoves()) {
                moves.add(m);
            }
            json.add("moves", moves);
        }
        return json;
    }

    @Override
    protected TrainerRegistry createPlaceholder() {
        return new TrainerRegistry();
    }

    @Override
    protected String[] extensions() {
        return EXTENSIONS;
    }

    @Override
    protected String iconCodepoint() {
        return MaterialIcons.SportsKabaddi;
    }

    @Override
    protected void copyInto(TrainerRegistry existing, TrainerRegistry fresh) {
        existing.copyFrom(fresh);
    }
}
