package com.donkolia.santumrandomspawns;

import com.donkolia.santumrandomspawns.commands.SantumCommand;
import com.donkolia.santumrandomspawns.conditions.ConditionEvaluator;
import com.donkolia.santumrandomspawns.config.RandomSpawnLoader;
import com.donkolia.santumrandomspawns.listeners.SpawningListener;
import com.donkolia.santumrandomspawns.model.RandomSpawnConfig;
import com.donkolia.santumrandomspawns.spawning.ClusterRandomSpawner;
import com.donkolia.santumrandomspawns.spawning.NpcSpawnerService;
import com.donkolia.santumrandomspawns.spawning.VanillaMobCleaner;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Main plugin entry point for SantumRandomSpawns.
 *
 * @author DonKolia
 */
public final class SantumRandomSpawns extends JavaPlugin {

    private RandomSpawnLoader loader;
    private ConditionEvaluator conditionEvaluator;
    private NpcSpawnerService npcService;
    private ClusterRandomSpawner clusterSpawner;
    private Map<String, RandomSpawnConfig> spawners = Collections.emptyMap();

    private BukkitTask spawnTask;
    private BukkitTask despawnTask;
    private BukkitTask targetTask;
    private BukkitTask conditionTask;
    private VanillaMobCleaner vanillaMobCleaner;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.loader = new RandomSpawnLoader(this);
        this.conditionEvaluator = new ConditionEvaluator();
        this.npcService = new NpcSpawnerService(this, conditionEvaluator);
        this.clusterSpawner = new ClusterRandomSpawner(this, npcService, conditionEvaluator);

        this.spawners = Collections.unmodifiableMap(new LinkedHashMap<>(loader.loadAll()));

        PluginCommand cmd = getCommand("santumrandomspawns");
        if (cmd != null) {
            SantumCommand executor = new SantumCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        Bukkit.getPluginManager().registerEvents(new SpawningListener(this, npcService), this);
        this.vanillaMobCleaner = new VanillaMobCleaner(this);
        Bukkit.getPluginManager().registerEvents(vanillaMobCleaner, this);
        // One tick later so every world is fully loaded.
        Bukkit.getScheduler().runTask(this, () -> vanillaMobCleaner.sweepLoadedWorlds());
        startTasks();

        getLogger().info("SantumRandomSpawns v" + getPluginMeta().getVersion() + " by DonKolia enabled. Loaded "
                + spawners.size() + " random spawn(s).");
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (npcService != null) {
            npcService.destroyAll();
        }
    }

    public int reloadPlugin() {
        stopTasks();
        reloadConfig();
        if (npcService != null) {
            npcService.destroyAll();
        }
        if (clusterSpawner != null) {
            clusterSpawner.clearCooldowns();
        }
        this.spawners = Collections.unmodifiableMap(new LinkedHashMap<>(loader.loadAll()));
        startTasks();
        if (vanillaMobCleaner != null) {
            vanillaMobCleaner.sweepLoadedWorlds();
        }
        return spawners.size();
    }

    private void startTasks() {
        long spawnInterval = Math.max(1L, getConfig().getLong("spawning.interval-ticks", 40L));
        long despawnInterval = 40L;

        this.spawnTask = Bukkit.getScheduler().runTaskTimer(this, clusterSpawner::tick, spawnInterval, spawnInterval);
        this.despawnTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            double despawnDist = getConfig().getDouble("despawn.despawn-distance", 64.0);
            boolean despawnOnChunkUnload = getConfig().getBoolean("despawn.despawn-on-chunk-unload", true);
            npcService.tickDespawn(despawnDist, despawnOnChunkUnload);
        }, despawnInterval, despawnInterval);
        this.targetTask = Bukkit.getScheduler().runTaskTimer(this, npcService::tickTargeting, 10L, 10L);
        long conditionInterval = Math.max(1L, getConfig().getLong("conditions.recheck-ticks", 10L));
        this.conditionTask = Bukkit.getScheduler().runTaskTimer(this, npcService::tickConditions, conditionInterval, conditionInterval);
    }

    private void stopTasks() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (despawnTask != null) {
            despawnTask.cancel();
            despawnTask = null;
        }
        if (targetTask != null) {
            targetTask.cancel();
            targetTask = null;
        }
        if (conditionTask != null) {
            conditionTask.cancel();
            conditionTask = null;
        }
    }

    public RandomSpawnLoader getLoader() {
        return loader;
    }

    public NpcSpawnerService getNpcService() {
        return npcService;
    }

    public Map<String, RandomSpawnConfig> getSpawners() {
        return spawners;
    }

    public RandomSpawnConfig getSpawner(String name) {
        if (name == null) {
            return null;
        }
        return spawners.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean isDebug() {
        return getConfig().getBoolean("debug", false);
    }
}
