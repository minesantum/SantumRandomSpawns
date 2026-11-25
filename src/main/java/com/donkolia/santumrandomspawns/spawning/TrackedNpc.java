package com.donkolia.santumrandomspawns.spawning;

import net.citizensnpcs.api.npc.NPC;

import java.util.UUID;

/**
 * Tracks an ephemeral Citizens2 NPC spawned by SantumRandomSpawns for Despawn NORMAL management.
 */
public final class TrackedNpc {

    private final UUID npcUniqueId;
    private final NPC npc;
    private final String spawnerName;
    private final long spawnTimeMillis;
    private final boolean targetPlayers;
    private final double targetRange;
    private final boolean hasConditions;
    private int noPlayerChecks;

    public TrackedNpc(NPC npc, String spawnerName, boolean targetPlayers, double targetRange, boolean hasConditions) {
        this.npcUniqueId = npc.getUniqueId();
        this.npc = npc;
        this.spawnerName = spawnerName;
        this.spawnTimeMillis = System.currentTimeMillis();
        this.noPlayerChecks = 0;
        this.targetPlayers = targetPlayers;
        this.targetRange = targetRange;
        this.hasConditions = hasConditions;
    }

    public boolean hasConditions() {
        return hasConditions;
    }

    public boolean isTargetPlayers() {
        return targetPlayers;
    }

    public double getTargetRange() {
        return targetRange;
    }

    public UUID getNpcUniqueId() {
        return npcUniqueId;
    }

    public NPC getNpc() {
        return npc;
    }

    public String getSpawnerName() {
        return spawnerName;
    }

    public long getSpawnTimeMillis() {
        return spawnTimeMillis;
    }

    public int getNoPlayerChecks() {
        return noPlayerChecks;
    }

    public int incrementNoPlayerChecks() {
        return ++noPlayerChecks;
    }

    public void resetNoPlayerChecks() {
        this.noPlayerChecks = 0;
    }
}
