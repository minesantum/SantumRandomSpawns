package com.donkolia.santumrandomspawns.model;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable configuration snapshot of a single random spawn entry loaded from a YAML file.
 */
public final class RandomSpawnConfig {

    private final String name;
    private final File sourceFile;
    private volatile boolean enabled;
    private final List<Integer> npcIds;
    private final IntRange separationBlockRadius;
    private final double chance;
    private final IntRange spawnRadius;
    private final IntRange size;
    private final List<WorldSpawnRule> worldRules;
    private final List<String> conditions;
    private final boolean targetPlayers;
    private final double targetRange;

    public RandomSpawnConfig(
            String name,
            File sourceFile,
            boolean enabled,
            List<Integer> npcIds,
            IntRange separationBlockRadius,
            double chance,
            IntRange spawnRadius,
            IntRange size,
            List<WorldSpawnRule> worldRules,
            List<String> conditions,
            boolean targetPlayers,
            double targetRange
    ) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.enabled = enabled;
        this.npcIds = Collections.unmodifiableList(npcIds);
        this.separationBlockRadius = separationBlockRadius;
        this.chance = chance;
        this.spawnRadius = spawnRadius;
        this.size = size;
        this.worldRules = Collections.unmodifiableList(worldRules);
        this.conditions = Collections.unmodifiableList(conditions);
        this.targetPlayers = targetPlayers;
        this.targetRange = targetRange;
    }

    public String getName() {
        return name;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Integer> getNpcIds() {
        return npcIds;
    }

    public int pickRandomNpcId() {
        if (npcIds.size() == 1) {
            return npcIds.get(0);
        }
        return npcIds.get(ThreadLocalRandom.current().nextInt(npcIds.size()));
    }

    public IntRange getSeparationBlockRadius() {
        return separationBlockRadius;
    }

    public double getChance() {
        return chance;
    }

    public IntRange getSpawnRadius() {
        return spawnRadius;
    }

    public IntRange getSize() {
        return size;
    }

    public List<WorldSpawnRule> getWorldRules() {
        return worldRules;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public boolean isTargetPlayers() {
        return targetPlayers;
    }

    public double getTargetRange() {
        return targetRange;
    }
}
