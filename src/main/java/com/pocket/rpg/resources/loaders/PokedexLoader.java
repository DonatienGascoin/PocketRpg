package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for Pokedex files ({@code .pokedex.json}).
 * <p>
 * Loads species and move definitions from JSON. Supports hot-reload
 * by mutating the existing Pokedex instance in place.
 */
public class PokedexLoader implements AssetLoader<Pokedex> {

    private static final String[] EXTENSIONS = {".pokedex.json"};
    private static final Logger LOG = Log.getLogger(PokedexLoader.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Pokedex placeholder;

    // ========================================================================
    // LOADING
    // ========================================================================

    @Override
    public Pokedex load(String path) throws IOException {
        try {
            String jsonContent = Files.readString(Paths.get(path));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            return fromJson(json);
        } catch (Exception e) {
            throw new IOException("Failed to load pokedex: " + path, e);
        }
    }

    private Pokedex fromJson(JsonObject json) {
        Pokedex pokedex = new Pokedex();

        if (json.has("species") && json.get("species").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("species")) {
                pokedex.addSpecies(parseSpecies(elem.getAsJsonObject()));
            }
        }

        if (json.has("moves") && json.get("moves").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("moves")) {
                pokedex.addMove(parseMove(elem.getAsJsonObject()));
            }
        }

        return pokedex;
    }

    private PokemonSpecies parseSpecies(JsonObject json) {
        JsonObject statsJson = json.getAsJsonObject("baseStats");
        Stats baseStats = new Stats(
                statsJson.get("hp").getAsInt(),
                statsJson.get("atk").getAsInt(),
                statsJson.get("def").getAsInt(),
                statsJson.get("spAtk").getAsInt(),
                statsJson.get("spDef").getAsInt(),
                statsJson.get("spd").getAsInt()
        );

        List<LearnedMove> learnset = new ArrayList<>();
        if (json.has("learnset") && json.get("learnset").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("learnset")) {
                JsonObject lm = elem.getAsJsonObject();
                learnset.add(new LearnedMove(lm.get("level").getAsInt(), lm.get("moveId").getAsString()));
            }
        }

        return new PokemonSpecies(
                json.get("speciesId").getAsString(),
                json.get("name").getAsString(),
                PokemonType.valueOf(json.get("type").getAsString()),
                baseStats,
                learnset,
                json.get("baseExpYield").getAsInt(),
                json.get("catchRate").getAsInt(),
                GrowthRate.valueOf(json.get("growthRate").getAsString()),
                json.has("spriteId") && !json.get("spriteId").isJsonNull()
                        ? json.get("spriteId").getAsString() : null,
                json.has("evolutionMethod") && !json.get("evolutionMethod").isJsonNull()
                        ? EvolutionMethod.valueOf(json.get("evolutionMethod").getAsString()) : EvolutionMethod.NONE,
                json.has("evolutionLevel") ? json.get("evolutionLevel").getAsInt() : 0,
                json.has("evolutionItem") && !json.get("evolutionItem").isJsonNull()
                        ? json.get("evolutionItem").getAsString() : null,
                json.has("evolvesInto") && !json.get("evolvesInto").isJsonNull()
                        ? json.get("evolvesInto").getAsString() : null
        );
    }

    private Move parseMove(JsonObject json) {
        return new Move(
                json.get("moveId").getAsString(),
                json.get("name").getAsString(),
                PokemonType.valueOf(json.get("type").getAsString()),
                MoveCategory.valueOf(json.get("category").getAsString()),
                json.get("power").getAsInt(),
                json.get("accuracy").getAsInt(),
                json.get("pp").getAsInt(),
                json.has("effect") && !json.get("effect").isJsonNull()
                        ? json.get("effect").getAsString() : "",
                json.has("effectChance") ? json.get("effectChance").getAsInt() : 0,
                json.has("priority") ? json.get("priority").getAsInt() : 0
        );
    }

    // ========================================================================
    // SAVING
    // ========================================================================

    @Override
    public void save(Pokedex pokedex, String path) throws IOException {
        JsonObject root = new JsonObject();

        JsonArray speciesArray = new JsonArray();
        for (PokemonSpecies sp : pokedex.getAllSpecies()) {
            speciesArray.add(serializeSpecies(sp));
        }
        root.add("species", speciesArray);

        JsonArray movesArray = new JsonArray();
        for (Move move : pokedex.getAllMoves()) {
            movesArray.add(serializeMove(move));
        }
        root.add("moves", movesArray);

        Files.writeString(Paths.get(path), gson.toJson(root));
    }

    private JsonObject serializeSpecies(PokemonSpecies sp) {
        JsonObject json = new JsonObject();
        json.addProperty("speciesId", sp.getSpeciesId());
        json.addProperty("name", sp.getName());
        json.addProperty("type", sp.getType().name());

        JsonObject stats = new JsonObject();
        stats.addProperty("hp", sp.getBaseStats().hp());
        stats.addProperty("atk", sp.getBaseStats().atk());
        stats.addProperty("def", sp.getBaseStats().def());
        stats.addProperty("spAtk", sp.getBaseStats().spAtk());
        stats.addProperty("spDef", sp.getBaseStats().spDef());
        stats.addProperty("spd", sp.getBaseStats().spd());
        json.add("baseStats", stats);

        JsonArray learnset = new JsonArray();
        for (LearnedMove lm : sp.getLearnset()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("level", lm.getLevel());
            entry.addProperty("moveId", lm.getMoveId());
            learnset.add(entry);
        }
        json.add("learnset", learnset);

        json.addProperty("baseExpYield", sp.getBaseExpYield());
        json.addProperty("catchRate", sp.getCatchRate());
        json.addProperty("growthRate", sp.getGrowthRate().name());
        json.addProperty("spriteId", sp.getSpriteId());
        json.addProperty("evolutionMethod", sp.getEvolutionMethod().name());
        json.addProperty("evolutionLevel", sp.getEvolutionLevel());
        json.addProperty("evolutionItem", sp.getEvolutionItem());
        json.addProperty("evolvesInto", sp.getEvolvesInto());
        return json;
    }

    private JsonObject serializeMove(Move move) {
        JsonObject json = new JsonObject();
        json.addProperty("moveId", move.getMoveId());
        json.addProperty("name", move.getName());
        json.addProperty("type", move.getType().name());
        json.addProperty("category", move.getCategory().name());
        json.addProperty("power", move.getPower());
        json.addProperty("accuracy", move.getAccuracy());
        json.addProperty("pp", move.getPp());
        json.addProperty("effect", move.getEffect());
        json.addProperty("effectChance", move.getEffectChance());
        json.addProperty("priority", move.getPriority());
        return json;
    }

    // ========================================================================
    // HOT RELOAD
    // ========================================================================

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public Pokedex reload(Pokedex existing, String path) throws IOException {
        Pokedex fresh = load(path);
        existing.copyFrom(fresh);
        return existing;
    }

    // ========================================================================
    // METADATA
    // ========================================================================

    @Override
    public String[] getSupportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public Pokedex getPlaceholder() {
        if (placeholder == null) {
            placeholder = new Pokedex();
        }
        return placeholder;
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.MenuBook;
    }
}
