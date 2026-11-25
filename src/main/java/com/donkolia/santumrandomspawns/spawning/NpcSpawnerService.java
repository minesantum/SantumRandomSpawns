package com.donkolia.santumrandomspawns.spawning;

import com.donkolia.santumrandomspawns.SantumRandomSpawns;
import com.donkolia.santumrandomspawns.conditions.ConditionEvaluator;
import com.donkolia.santumrandomspawns.model.RandomSpawnConfig;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.EntityTarget;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.event.NPCCloneEvent;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.trait.trait.PlayerFilter;
import net.citizensnpcs.api.util.MemoryDataKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages ephemeral Citizens2 NPCs in an in-memory {@link NPCRegistry} so random spawns
 * never pollute Citizens' {@code saves.yml}, and handles MythicMobs-style {@code Despawn: NORMAL}.
 */
public final class NpcSpawnerService {

    private final SantumRandomSpawns plugin;
    private final NPCRegistry ephemeralRegistry;
    private final Map<UUID, TrackedNpc> trackedNpcs = new ConcurrentHashMap<>();
    private final ConditionEvaluator conditionEvaluator;
    /** spawner name -> (player uuid -> do the conditions_to_spawn currently hold for that player?) */
    private final Map<String, Map<UUID, Boolean>> conditionCache = new ConcurrentHashMap<>();

    public NpcSpawnerService(SantumRandomSpawns plugin, ConditionEvaluator conditionEvaluator) {
        this.plugin = plugin;
        this.conditionEvaluator = conditionEvaluator;
        this.ephemeralRegistry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
    }

    public NPC findTemplateNpc(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null) {
            return npc;
        }
        for (NPCRegistry registry : CitizensAPI.getNPCRegistries()) {
            if (registry == ephemeralRegistry) {
                continue;
            }
            NPC candidate = registry.getById(npcId);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Clones the Citizens template NPC into the ephemeral registry (exactly like {@code /npc copy} does:
     * serialize with {@link NPC#save} -> {@link NPC#load}, so every persisted trait is copied: skin, equipment,
     * Sentinel config, etc.) and spawns the clone at the given location.
     */
    public TrackedNpc spawnFromTemplate(int templateId, Location location, RandomSpawnConfig config) {
        NPC template = findTemplateNpc(templateId);
        if (template == null) {
            plugin.getLogger().warning("RandomSpawn '" + config.getName() + "': template NPC ID " + templateId
                    + " not found in Citizens2.");
            return null;
        }

        NPC spawned = cloneTemplate(template);
        if (spawned == null) {
            return null;
        }

        boolean hasConditions = !config.getConditions().isEmpty();
        if (hasConditions) {
            // Must be installed BEFORE spawning so players that fail the conditions never receive the NPC.
            final String spawnerName = config.getName();
            PlayerFilter filter = spawned.getOrAddTrait(PlayerFilter.class);
            filter.setPlayerFilter(player -> !meetsConditions(spawnerName, player));
        }

        if (!spawned.spawn(location)) {
            spawned.destroy();
            if (plugin.isDebug()) {
                plugin.getLogger().warning("Could not spawn clone of NPC " + templateId + " at " + location);
            }
            return null;
        }

        TrackedNpc tracked = new TrackedNpc(spawned, config.getName(), config.isTargetPlayers(), config.getTargetRange(), hasConditions);
        trackedNpcs.put(spawned.getUniqueId(), tracked);
        return tracked;
    }

    private NPC cloneTemplate(NPC template) {
        try {
            EntityType type = template.getOrAddTrait(MobType.class).getType();
            if (type == null) {
                type = EntityType.PLAYER;
            }

            MemoryDataKey key = new MemoryDataKey();
            template.save(key);
            if (key.getString("traitnames").isEmpty()) {
                plugin.getLogger().warning("Template NPC " + template.getId()
                        + " has no savable data (should-save disabled?); cannot clone it.");
                return null;
            }
            // The Spawned trait would make load() respawn the clone on the template's own location.
            key.setBoolean("traits.spawned", false);

            NPC copy = ephemeralRegistry.createNPC(type, template.getRawName());
            copy.load(key);
            // Clones are disposable: they must never come back by themselves after dying.
            copy.data().set(NPC.Metadata.RESPAWN_DELAY, -1);

            for (Trait trait : copy.getTraits()) {
                trait.onCopy();
            }
            Bukkit.getPluginManager().callEvent(new NPCCloneEvent(template, copy));
            return copy;
        } catch (Throwable ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Failed to clone template NPC " + template.getId(), ex);
            return null;
        }
    }

    /**
     * Makes spawned clones that opted in ({@code target_players: true}) chase and attack the nearest player.
     * NPCs that carry the Sentinel trait are skipped, because Sentinel handles its own targeting.
     */
    public void tickTargeting() {
        if (trackedNpcs.isEmpty()) {
            return;
        }
        Class<? extends Trait> sentinelClass = CitizensAPI.getTraitFactory().getTraitClass("sentinel");

        for (TrackedNpc tracked : trackedNpcs.values()) {
            if (!tracked.isTargetPlayers()) {
                continue;
            }
            NPC npc = tracked.getNpc();
            if (!npc.isSpawned() || (sentinelClass != null && npc.hasTrait(sentinelClass))) {
                continue;
            }
            Entity self = npc.getEntity();
            if (self == null || !self.isValid()) {
                continue;
            }

            double range = tracked.getTargetRange();
            Navigator navigator = npc.getNavigator();
            EntityTarget current = navigator.getEntityTarget();
            if (current != null && isValidTarget(current.getTarget(), self, range * 1.5, tracked.getSpawnerName())) {
                continue;
            }

            Player best = null;
            double bestSq = range * range;
            for (Player player : self.getWorld().getPlayers()) {
                if (!isValidTarget(player, self, range, tracked.getSpawnerName())) {
                    continue;
                }
                double d = player.getLocation().distanceSquared(self.getLocation());
                if (d <= bestSq) {
                    bestSq = d;
                    best = player;
                }
            }

            if (best != null) {
                navigator.setTarget(best, true);
            } else if (current != null) {
                navigator.cancelNavigation();
            }
        }
    }

    private boolean isValidTarget(Entity target, Entity self, double range, String spawnerName) {
        if (!(target instanceof Player player)) {
            return false;
        }
        if (!player.isOnline() || player.isDead() || !player.isValid()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return false;
        }
        return player.getWorld().equals(self.getWorld())
                && player.getLocation().distanceSquared(self.getLocation()) <= range * range
                && meetsConditions(spawnerName, player);
    }

    /** Returns the tracked NPC that owns this entity, or null if the entity is not one of ours. */
    public TrackedNpc getTracked(Entity entity) {
        if (entity == null) {
            return null;
        }
        NPC npc = ephemeralRegistry.getNPC(entity);
        return npc == null ? null : trackedNpcs.get(npc.getUniqueId());
    }

    /** Cached check, refreshed by {@link #tickConditions()}. Evaluates lazily for players not in the cache yet. */
    public boolean meetsConditions(String spawnerName, Player player) {
        Map<UUID, Boolean> cache = conditionCache.computeIfAbsent(spawnerName, k -> new ConcurrentHashMap<>());
        Boolean cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        boolean result = evaluateConditions(spawnerName, player);
        cache.put(player.getUniqueId(), result);
        return result;
    }

    /** Uncached check (always evaluates now and refreshes the cache). Used for attacks, where lag is not acceptable. */
    public boolean meetsConditionsNow(String spawnerName, Player player) {
        boolean result = evaluateConditions(spawnerName, player);
        conditionCache.computeIfAbsent(spawnerName, k -> new ConcurrentHashMap<>()).put(player.getUniqueId(), result);
        return result;
    }

    private boolean evaluateConditions(String spawnerName, Player player) {
        RandomSpawnConfig config = plugin.getSpawner(spawnerName);
        if (config == null || config.getConditions().isEmpty()) {
            return true;
        }
        return conditionEvaluator.evaluateAll(config.getConditions(), player, player.getLocation());
    }

    /**
     * Real-time re-evaluation of {@code conditions_to_spawn}: only players near an NPC of the spawner are evaluated.
     * Players that stop meeting the conditions get the NPC hidden (and cannot hit it); players that meet them again
     * see it again.
     */
    public void tickConditions() {
        if (trackedNpcs.isEmpty()) {
            conditionCache.clear();
            return;
        }
        double radius = plugin.getConfig().getDouble("despawn.despawn-distance", 64.0);
        double radiusSq = radius * radius;

        Map<String, List<Location>> locationsBySpawner = new HashMap<>();
        for (TrackedNpc tracked : trackedNpcs.values()) {
            if (!tracked.hasConditions() || !tracked.getNpc().isSpawned()) {
                continue;
            }
            Location loc = tracked.getNpc().getStoredLocation();
            if (loc != null && loc.getWorld() != null) {
                locationsBySpawner.computeIfAbsent(tracked.getSpawnerName(), k -> new ArrayList<>()).add(loc);
            }
        }
        conditionCache.keySet().removeIf(name -> !locationsBySpawner.containsKey(name));

        for (Map.Entry<String, List<Location>> entry : locationsBySpawner.entrySet()) {
            Map<UUID, Boolean> cache = conditionCache.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>());
            Set<UUID> evaluated = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location playerLoc = player.getLocation();
                boolean near = false;
                for (Location npcLoc : entry.getValue()) {
                    if (npcLoc.getWorld().equals(playerLoc.getWorld()) && npcLoc.distanceSquared(playerLoc) <= radiusSq) {
                        near = true;
                        break;
                    }
                }
                if (!near) {
                    continue;
                }
                cache.put(player.getUniqueId(), evaluateConditions(entry.getKey(), player));
                evaluated.add(player.getUniqueId());
            }
            // Far-away players are dropped so they are evaluated fresh when they come close again.
            cache.keySet().retainAll(evaluated);
        }

        // Apply visibility changes right now instead of waiting for Citizens' own packet update.
        for (TrackedNpc tracked : trackedNpcs.values()) {
            if (!tracked.hasConditions()) {
                continue;
            }
            NPC npc = tracked.getNpc();
            PlayerFilter filter = npc.getTraitNullable(PlayerFilter.class);
            if (filter != null && npc.isSpawned()) {
                filter.recalculate();
            }
        }
    }

    public boolean hasNearbyNpcWithin(Location location, double radius) {
        if (radius <= 0.0 || location.getWorld() == null) {
            return false;
        }
        double radiusSq = radius * radius;
        World world = location.getWorld();

        for (TrackedNpc tracked : trackedNpcs.values()) {
            NPC npc = tracked.getNpc();
            if (!npc.isSpawned()) {
                continue;
            }
            Location npcLoc = npc.getStoredLocation();
            if (npcLoc != null && world.equals(npcLoc.getWorld()) && npcLoc.distanceSquared(location) < radiusSq) {
                return true;
            }
        }
        return false;
    }

    public int countNearbyTrackedNpcs(Location center, double radius) {
        if (center.getWorld() == null) {
            return 0;
        }
        double radiusSq = radius * radius;
        World world = center.getWorld();
        int count = 0;

        for (TrackedNpc tracked : trackedNpcs.values()) {
            NPC npc = tracked.getNpc();
            if (!npc.isSpawned()) {
                continue;
            }
            Location npcLoc = npc.getStoredLocation();
            if (npcLoc != null && world.equals(npcLoc.getWorld()) && npcLoc.distanceSquared(center) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    public int countNearbyTrackedNpcsForSpawner(String spawnerName, Location center, double radius) {
        if (center.getWorld() == null) {
            return 0;
        }
        double radiusSq = radius * radius;
        World world = center.getWorld();
        int count = 0;

        for (TrackedNpc tracked : trackedNpcs.values()) {
            if (!tracked.getSpawnerName().equalsIgnoreCase(spawnerName)) {
                continue;
            }
            NPC npc = tracked.getNpc();
            if (!npc.isSpawned()) {
                continue;
            }
            Location npcLoc = npc.getStoredLocation();
            if (npcLoc != null && world.equals(npcLoc.getWorld()) && npcLoc.distanceSquared(center) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    /**
     * Simple MythicMobs-style Despawn NORMAL:
     * despawns NPCs when their chunk is unloaded (if enabled) or when no player is within {@code despawnDistance}.
     */
    public void tickDespawn(double despawnDistance, boolean despawnOnChunkUnload) {
        if (trackedNpcs.isEmpty()) {
            return;
        }
        double distSq = despawnDistance * despawnDistance;
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, TrackedNpc>> it = trackedNpcs.entrySet().iterator();
        while (it.hasNext()) {
            TrackedNpc tracked = it.next().getValue();
            NPC npc = tracked.getNpc();

            if (!npc.isSpawned()) {
                it.remove();
                npc.destroy();
                continue;
            }

            Entity entity = npc.getEntity();
            if (entity == null || !entity.isValid() || entity.isDead()) {
                it.remove();
                npc.destroy();
                continue;
            }

            Location npcLoc = entity.getLocation();
            World world = npcLoc.getWorld();
            if (world == null) {
                it.remove();
                npc.despawn(DespawnReason.PLUGIN);
                npc.destroy();
                continue;
            }

            if (despawnOnChunkUnload && !world.isChunkLoaded(npcLoc.getBlockX() >> 4, npcLoc.getBlockZ() >> 4)) {
                it.remove();
                npc.despawn(DespawnReason.CHUNK_UNLOAD);
                npc.destroy();
                continue;
            }

            boolean playerNearby = false;
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(npcLoc) <= distSq) {
                    playerNearby = true;
                    break;
                }
            }

            if (playerNearby) {
                tracked.resetNoPlayerChecks();
                continue;
            }

            // Small 3s spawn grace + 2-check hysteresis (~4s) to prevent border flickering
            if (now - tracked.getSpawnTimeMillis() < 3000L) {
                continue;
            }

            if (tracked.incrementNoPlayerChecks() >= 2) {
                it.remove();
                npc.despawn(DespawnReason.PLUGIN);
                npc.destroy();
            }
        }
    }

    public void handleChunkUnload(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        Iterator<Map.Entry<UUID, TrackedNpc>> it = trackedNpcs.entrySet().iterator();
        while (it.hasNext()) {
            TrackedNpc tracked = it.next().getValue();
            NPC npc = tracked.getNpc();
            if (!npc.isSpawned()) {
                it.remove();
                npc.destroy();
                continue;
            }
            Location loc = npc.getStoredLocation();
            if (loc != null && world.equals(loc.getWorld())
                    && (loc.getBlockX() >> 4) == cx
                    && (loc.getBlockZ() >> 4) == cz) {
                it.remove();
                npc.despawn(DespawnReason.CHUNK_UNLOAD);
                npc.destroy();
            }
        }
    }

    public void handleEntityDeath(Entity entity) {
        NPC npc = ephemeralRegistry.getNPC(entity);
        if (npc == null) {
            return;
        }
        TrackedNpc removed = trackedNpcs.remove(npc.getUniqueId());
        if (removed != null) {
            Bukkit.getScheduler().runTask(plugin, () -> npc.destroy());
        }
    }

    public void destroyBySpawner(String spawnerName) {
        conditionCache.remove(spawnerName);
        Iterator<Map.Entry<UUID, TrackedNpc>> it = trackedNpcs.entrySet().iterator();
        while (it.hasNext()) {
            TrackedNpc tracked = it.next().getValue();
            if (tracked.getSpawnerName().equalsIgnoreCase(spawnerName)) {
                it.remove();
                NPC npc = tracked.getNpc();
                if (npc.isSpawned()) {
                    npc.despawn(DespawnReason.PLUGIN);
                }
                npc.destroy();
            }
        }
    }

    public void destroyAll() {
        conditionCache.clear();
        Iterator<Map.Entry<UUID, TrackedNpc>> it = trackedNpcs.entrySet().iterator();
        while (it.hasNext()) {
            TrackedNpc tracked = it.next().getValue();
            it.remove();
            NPC npc = tracked.getNpc();
            if (npc.isSpawned()) {
                npc.despawn(DespawnReason.PLUGIN);
            }
            npc.destroy();
        }
        ephemeralRegistry.deregisterAll();
    }

    public int getActiveCount() {
        return trackedNpcs.size();
    }
}
