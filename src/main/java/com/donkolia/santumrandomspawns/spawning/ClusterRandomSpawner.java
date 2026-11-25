package com.donkolia.santumrandomspawns.spawning;

import com.donkolia.santumrandomspawns.SantumRandomSpawns;
import com.donkolia.santumrandomspawns.conditions.ConditionEvaluator;
import com.donkolia.santumrandomspawns.model.RandomSpawnConfig;
import com.donkolia.santumrandomspawns.model.WorldSpawnRule;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MythicMobs-inspired cluster random spawner:
 * groups nearby players into clusters so multiple players in the same area
 * do not cause duplicate/stacked spawns or server lag.
 */
public final class ClusterRandomSpawner {

    private final SantumRandomSpawns plugin;
    private final NpcSpawnerService npcService;
    private final ConditionEvaluator conditionEvaluator;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public ClusterRandomSpawner(
            SantumRandomSpawns plugin,
            NpcSpawnerService npcService,
            ConditionEvaluator conditionEvaluator
    ) {
        this.plugin = plugin;
        this.npcService = npcService;
        this.conditionEvaluator = conditionEvaluator;
    }

    public void tick() {
        Map<String, RandomSpawnConfig> spawners = plugin.getSpawners();
        if (spawners.isEmpty()) {
            return;
        }

        double despawnDistance = plugin.getConfig().getDouble("despawn.despawn-distance", 64.0);
        int maxLocalNpcs = plugin.getConfig().getInt("spawning.max-local-npcs", 10);
        double groupDistance = despawnDistance;
        double groupMultiplier = 1.33;
        int maxAttempts = 20;
        long maxGenTimeMs = 10L;
        long cooldownMillis = 5000L;

        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) {
                continue;
            }

            List<List<Player>> clusters = clusterPlayers(players, groupDistance);
            Collections.shuffle(clusters);

            for (List<Player> cluster : clusters) {
                evaluateCluster(
                        world,
                        cluster,
                        spawners,
                        groupDistance,
                        maxLocalNpcs,
                        groupMultiplier,
                        maxAttempts,
                        maxGenTimeMs,
                        cooldownMillis
                );
            }
        }
    }

    private void evaluateCluster(
            World world,
            List<Player> cluster,
            Map<String, RandomSpawnConfig> spawners,
            double groupDistance,
            int maxLocalNpcs,
            double groupMultiplier,
            int maxAttempts,
            long maxGenTimeMs,
            long cooldownMillis
    ) {
        if (cluster.isEmpty()) {
            return;
        }

        double localCap = maxLocalNpcs;
        if (cluster.size() > 1) {
            localCap *= 1.0 + (cluster.size() - 1) * (groupMultiplier - 1.0);
        }

        Player anchorPlayer = cluster.get(ThreadLocalRandom.current().nextInt(cluster.size()));
        int currentLocal = npcService.countNearbyTrackedNpcs(anchorPlayer.getLocation(), groupDistance);
        if (currentLocal >= localCap) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID clusterKey = cluster.get(0).getUniqueId();

        for (RandomSpawnConfig config : spawners.values()) {
            if (!config.isEnabled()) {
                continue;
            }

            String cdKey = config.getName() + ":" + world.getName() + ":" + clusterKey;
            Long readyAt = cooldowns.get(cdKey);
            if (readyAt != null && now < readyAt) {
                continue;
            }

            if (ThreadLocalRandom.current().nextDouble(100.0) >= config.getChance()) {
                continue;
            }

            if (!matchesAnyWorldRule(config, world, CreatureSpawnEvent.SpawnReason.NATURAL)) {
                continue;
            }

            Player triggerPlayer = findMatchingPlayer(config, cluster);
            if (triggerPlayer == null) {
                continue;
            }

            // Do not respawn more NPCs for this spawner if the player already has active NPCs in radius
            if (npcService.countNearbyTrackedNpcsForSpawner(config.getName(), triggerPlayer.getLocation(), groupDistance) > 0) {
                continue;
            }

            int spawnCount = Math.max(1, config.getSize().random());
            List<Location> chosenLocations = new ArrayList<>(spawnCount);
            int separation = config.getSeparationBlockRadius().random();

            for (int i = 0; i < spawnCount; i++) {
                if (currentLocal + chosenLocations.size() >= localCap) {
                    break;
                }
                Location candidate = findSafeSpawnLocation(
                        config,
                        world,
                        triggerPlayer.getLocation(),
                        chosenLocations,
                        separation,
                        maxAttempts,
                        maxGenTimeMs
                );
                if (candidate != null) {
                    chosenLocations.add(candidate);
                }
            }

            if (chosenLocations.isEmpty()) {
                continue;
            }

            int spawnedNow = 0;
            for (Location loc : chosenLocations) {
                int npcId = config.pickRandomNpcId();
                if (npcService.spawnFromTemplate(npcId, loc, config) != null) {
                    spawnedNow++;
                }
            }

            if (spawnedNow > 0) {
                cooldowns.put(cdKey, now + cooldownMillis);
                currentLocal += spawnedNow;
                if (plugin.isDebug()) {
                    plugin.getLogger().info("[Debug] Spawned " + spawnedNow + " NPC(s) for '" + config.getName()
                            + "' near " + triggerPlayer.getName() + " in " + world.getName());
                }
            }
        }
    }

    private Player findMatchingPlayer(RandomSpawnConfig config, List<Player> cluster) {
        if (config.getConditions().isEmpty()) {
            return cluster.get(ThreadLocalRandom.current().nextInt(cluster.size()));
        }
        for (Player player : cluster) {
            if (conditionEvaluator.evaluateAll(config.getConditions(), player, player.getLocation())) {
                return player;
            }
        }
        return null;
    }

    private boolean matchesAnyWorldRule(
            RandomSpawnConfig config,
            World world,
            CreatureSpawnEvent.SpawnReason reason
    ) {
        if (config.getWorldRules().isEmpty()) {
            return true;
        }
        for (WorldSpawnRule rule : config.getWorldRules()) {
            if (rule.matchesWorld(world) && rule.matchesReason(reason)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesLocationRules(
            RandomSpawnConfig config,
            World world,
            Biome biome,
            CreatureSpawnEvent.SpawnReason reason
    ) {
        if (config.getWorldRules().isEmpty()) {
            return true;
        }
        for (WorldSpawnRule rule : config.getWorldRules()) {
            if (rule.matchesWorld(world) && rule.matchesBiome(biome) && rule.matchesReason(reason)) {
                return true;
            }
        }
        return false;
    }

    private Location findSafeSpawnLocation(
            RandomSpawnConfig config,
            World world,
            Location center,
            List<Location> pendingBatch,
            int separation,
            int maxAttempts,
            long maxGenTimeMs
    ) {
        long startTime = System.currentTimeMillis();
        int minRadius = config.getSpawnRadius().getMin();
        int maxRadius = Math.max(minRadius, config.getSpawnRadius().getMax());
        double sepSq = (double) separation * separation;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (System.currentTimeMillis() - startTime > maxGenTimeMs) {
                return null;
            }

            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
            double dist = (minRadius == maxRadius)
                    ? minRadius
                    : ThreadLocalRandom.current().nextDouble(minRadius, maxRadius + 0.0001);

            int x = (int) Math.floor(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * dist);

            int cx = x >> 4;
            int cz = z >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                continue;
            }
            Chunk chunk = world.getChunkAt(cx, cz);
            if (!chunk.isLoaded()) {
                continue;
            }

            Location ground = findSafeY(world, x, center.getBlockY(), z);
            if (ground == null) {
                continue;
            }

            Biome biome = ground.getBlock().getBiome();
            if (!matchesLocationRules(config, world, biome, CreatureSpawnEvent.SpawnReason.NATURAL)) {
                continue;
            }

            if (separation > 0) {
                if (npcService.hasNearbyNpcWithin(ground, separation)) {
                    continue;
                }
                boolean tooCloseToBatch = false;
                for (Location other : pendingBatch) {
                    if (other.distanceSquared(ground) < sepSq) {
                        tooCloseToBatch = true;
                        break;
                    }
                }
                if (tooCloseToBatch) {
                    continue;
                }
            }

            ground.setYaw((float) (ThreadLocalRandom.current().nextDouble() * 360.0));
            return ground;
        }
        return null;
    }

    private Location findSafeY(World world, int x, int referenceY, int z) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;

        // First search around the player's current Y level so NPCs spawn on the ground near them
        // (even under trees, roofs, or inside caves).
        int searchStart = Math.clamp(referenceY + 6, minY, maxY - 2);
        int searchEnd = Math.clamp(referenceY - 8, minY, maxY - 2);
        for (int y = searchStart; y >= searchEnd; y--) {
            if (isStandable(world, x, y, z)) {
                return new Location(world, x + 0.5, y + 1.0, z + 0.5);
            }
        }

        if (world.getEnvironment() == World.Environment.NETHER) {
            return null;
        }

        int highestY = Math.clamp(world.getHighestBlockYAt(x, z), minY, maxY - 2);
        for (int y = highestY; y >= Math.max(minY, highestY - 10); y--) {
            if (isStandable(world, x, y, z)) {
                return new Location(world, x + 0.5, y + 1.0, z + 0.5);
            }
        }
        return null;
    }

    private boolean isStandable(World world, int x, int groundY, int z) {
        Block ground = world.getBlockAt(x, groundY, z);
        Material groundType = ground.getType();
        if (!groundType.isSolid()
                || Tag.LEAVES.isTagged(groundType)
                || groundType == Material.MAGMA_BLOCK
                || groundType == Material.CACTUS) {
            return false;
        }
        Block feet = world.getBlockAt(x, groundY + 1, z);
        Block head = world.getBlockAt(x, groundY + 2, z);
        return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid();
    }

    /**
     * Groups players in the same world using single-linkage / DBSCAN clustering (epsilon = groupDistance).
     */
    private List<List<Player>> clusterPlayers(List<Player> players, double groupDistance) {
        int n = players.size();
        boolean[] visited = new boolean[n];
        double epsSq = groupDistance * groupDistance;
        List<List<Player>> clusters = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (visited[i]) {
                continue;
            }
            visited[i] = true;
            List<Player> cluster = new ArrayList<>();
            List<Integer> queue = new ArrayList<>();
            queue.add(i);

            for (int q = 0; q < queue.size(); q++) {
                int idx = queue.get(q);
                Player current = players.get(idx);
                cluster.add(current);
                Location currentLoc = current.getLocation();

                for (int j = 0; j < n; j++) {
                    if (!visited[j] && players.get(j).getLocation().distanceSquared(currentLoc) <= epsSq) {
                        visited[j] = true;
                        queue.add(j);
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }
}
