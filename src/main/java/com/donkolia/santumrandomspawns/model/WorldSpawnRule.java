package com.donkolia.santumrandomspawns.model;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Represents a single entry under {@code worlds_spawn}, filtering by world, dimension, biome, and spawn reason.
 */
public final class WorldSpawnRule {

    private final String key;
    private final Set<World.Environment> dimensions;
    private final Set<String> biomeKeys;
    private final Set<String> worlds;
    private final Set<CreatureSpawnEvent.SpawnReason> reasons;

    public WorldSpawnRule(
            String key,
            Set<World.Environment> dimensions,
            Set<String> biomeKeys,
            Set<String> worlds,
            Set<CreatureSpawnEvent.SpawnReason> reasons
    ) {
        this.key = key;
        this.dimensions = dimensions.isEmpty() ? Collections.emptySet() : EnumSet.copyOf(dimensions);
        this.biomeKeys = biomeKeys.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(biomeKeys));
        this.worlds = worlds.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(worlds));
        this.reasons = reasons.isEmpty() ? Collections.emptySet() : EnumSet.copyOf(reasons);
    }

    public String getKey() {
        return key;
    }

    public Set<World.Environment> getDimensions() {
        return dimensions;
    }

    public Set<String> getBiomeKeys() {
        return biomeKeys;
    }

    public Set<String> getWorlds() {
        return worlds;
    }

    public Set<CreatureSpawnEvent.SpawnReason> getReasons() {
        return reasons;
    }

    public boolean matchesWorld(World world) {
        if (!worlds.isEmpty()) {
            boolean found = false;
            for (String w : worlds) {
                if (w.equalsIgnoreCase(world.getName())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        } else if (key != null && !key.isEmpty() && !key.equalsIgnoreCase(world.getName())) {
            return false;
        }

        if (!dimensions.isEmpty() && !dimensions.contains(world.getEnvironment())) {
            return false;
        }

        return true;
    }

    public boolean matchesBiome(Biome biome) {
        if (biomeKeys.isEmpty()) {
            return true;
        }
        String keyName = biome.getKey().getKey().toUpperCase(Locale.ROOT);
        String fullKey = biome.getKey().toString().toUpperCase(Locale.ROOT);
        return biomeKeys.contains(keyName) || biomeKeys.contains(fullKey);
    }

    public boolean matchesReason(CreatureSpawnEvent.SpawnReason reason) {
        if (reasons.isEmpty()) {
            return reason == CreatureSpawnEvent.SpawnReason.NATURAL;
        }
        return reasons.contains(reason);
    }
}
