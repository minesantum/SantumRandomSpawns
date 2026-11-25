package com.donkolia.santumrandomspawns.config;

import com.donkolia.santumrandomspawns.SantumRandomSpawns;
import com.donkolia.santumrandomspawns.model.IntRange;
import com.donkolia.santumrandomspawns.model.RandomSpawnConfig;
import com.donkolia.santumrandomspawns.model.WorldSpawnRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Recursively loads all {@code .yml} files inside {@code plugins/SantumRandomSpawns/randomspawns/}
 * and all nested subdirectories.
 */
public final class RandomSpawnLoader {

    private final SantumRandomSpawns plugin;

    public RandomSpawnLoader(SantumRandomSpawns plugin) {
        this.plugin = plugin;
    }

    public Map<String, RandomSpawnConfig> loadAll() {
        File rootDir = new File(plugin.getDataFolder(), "randomspawns");
        if (!rootDir.exists()) {
            if (rootDir.mkdirs()) {
                plugin.saveResource("randomspawns/ejemplo.yml", false);
            }
        }

        List<File> yamlFiles = new ArrayList<>();
        collectYamlFiles(rootDir, yamlFiles);

        Map<String, RandomSpawnConfig> loaded = new LinkedHashMap<>();
        for (File file : yamlFiles) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                RandomSpawnConfig config = parseSection(key, file, section);
                if (config != null) {
                    if (loaded.containsKey(key.toLowerCase(Locale.ROOT))) {
                        plugin.getLogger().warning("Duplicate RandomSpawn name '" + key + "' in " + file.getPath() + ". Overwriting previous entry.");
                    }
                    loaded.put(key.toLowerCase(Locale.ROOT), config);
                }
            }
        }
        return loaded;
    }

    public boolean setEnabledInFile(RandomSpawnConfig config, boolean enabled) {
        config.setEnabled(enabled);
        File file = config.getSourceFile();
        if (file == null || !file.exists()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection(config.getName());
        if (section == null) {
            return false;
        }
        section.set("enable", enabled);
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save enable state to " + file.getPath(), ex);
            return false;
        }
    }

    private void collectYamlFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectYamlFiles(file, out);
            } else if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                out.add(file);
            }
        }
    }

    private RandomSpawnConfig parseSection(String name, File file, ConfigurationSection section) {
        boolean enabled = section.getBoolean("enable", true);
        List<Integer> npcIds = parseNpcIds(section.get("npc_id"), name, file);
        if (npcIds.isEmpty()) {
            plugin.getLogger().warning("RandomSpawn '" + name + "' in " + file.getName() + " has no valid npc_id; skipping.");
            return null;
        }

        IntRange separation = IntRange.parse(section.get("separation_block_radius"), 5);
        double chance = Math.max(0.0, Math.min(100.0, section.getDouble("chance", 100.0)));
        IntRange spawnRadius = IntRange.parse(section.get("spawn_radius"), 5);
        IntRange size = IntRange.parse(section.get("size"), 1);

        List<WorldSpawnRule> worldRules = parseWorldRules(section.getConfigurationSection("worlds_spawn"));
        List<String> conditions = parseConditions(section);
        boolean targetPlayers = section.getBoolean("target_players", false);
        double targetRange = Math.max(1.0, section.getDouble("target_range", 16.0));

        return new RandomSpawnConfig(
                name,
                file,
                enabled,
                npcIds,
                separation,
                chance,
                spawnRadius,
                size,
                worldRules,
                conditions,
                targetPlayers,
                targetRange
        );
    }

    private List<Integer> parseNpcIds(Object raw, String name, File file) {
        List<Integer> ids = new ArrayList<>();
        if (raw == null) {
            return ids;
        }
        if (raw instanceof Number num) {
            ids.add(num.intValue());
            return ids;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    parseIntInto(item.toString(), ids, name, file);
                }
            }
            return ids;
        }
        String[] parts = raw.toString().split(",");
        for (String part : parts) {
            parseIntInto(part, ids, name, file);
        }
        return ids;
    }

    private void parseIntInto(String text, List<Integer> out, String name, File file) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            out.add(Integer.parseInt(trimmed));
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Invalid npc_id '" + trimmed + "' in RandomSpawn '" + name + "' (" + file.getName() + ")");
        }
    }

    private List<WorldSpawnRule> parseWorldRules(ConfigurationSection worldsSection) {
        if (worldsSection == null) {
            return Collections.emptyList();
        }
        List<WorldSpawnRule> rules = new ArrayList<>();
        for (String key : worldsSection.getKeys(false)) {
            ConfigurationSection entry = worldsSection.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }

            Set<World.Environment> dimensions = EnumSet.noneOf(World.Environment.class);
            for (String dim : readStringTokens(entry.get("dimensions"))) {
                switch (dim.toLowerCase(Locale.ROOT)) {
                    case "overworld", "normal" -> dimensions.add(World.Environment.NORMAL);
                    case "nether", "the_nether" -> dimensions.add(World.Environment.NETHER);
                    case "end", "the_end" -> dimensions.add(World.Environment.THE_END);
                    default -> {
                        try {
                            dimensions.add(World.Environment.valueOf(dim.toUpperCase(Locale.ROOT)));
                        } catch (IllegalArgumentException ignored) {
                            plugin.getLogger().warning("Unknown dimension '" + dim + "' in worlds_spawn." + key);
                        }
                    }
                }
            }

            Set<String> biomes = new HashSet<>();
            for (String b : readStringTokens(entry.get("biomes"))) {
                biomes.add(b.toUpperCase(Locale.ROOT));
            }

            Set<String> worlds = new HashSet<>(readStringTokens(entry.get("worlds")));

            Set<CreatureSpawnEvent.SpawnReason> reasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
            for (String r : readStringTokens(entry.get("reason"))) {
                try {
                    reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(r.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown SpawnReason '" + r + "' in worlds_spawn." + key);
                }
            }

            rules.add(new WorldSpawnRule(key, dimensions, biomes, worlds, reasons));
        }
        return rules;
    }

    private List<String> parseConditions(ConfigurationSection section) {
        if (section.isList("conditions_to_spawn")) {
            return section.getStringList("conditions_to_spawn");
        }
        // Legacy spelling, still accepted so existing spawn files keep working.
        if (section.isList("Conditions_to_spawn")) {
            return section.getStringList("Conditions_to_spawn");
        }
        return Collections.emptyList();
    }

    private List<String> readStringTokens(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String s = item.toString().trim();
                    if (!s.isEmpty()) {
                        tokens.add(s);
                    }
                }
            }
            return tokens;
        }
        for (String part : raw.toString().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }
}
