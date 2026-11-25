package com.donkolia.santumrandomspawns.spawning;

import com.donkolia.santumrandomspawns.SantumRandomSpawns;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@code DisableVanillaSpawns}: when enabled, the only mobs that exist in the world are Citizens NPCs
 * (the ones from this plugin and any other Citizens NPC). Vanilla spawns are cancelled and every mob that already
 * existed (loaded now, or loaded later from disk) is removed.
 */
public final class VanillaMobCleaner implements Listener {

    private final SantumRandomSpawns plugin;

    public VanillaMobCleaner(SantumRandomSpawns plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("DisableVanillaSpawns", false);
    }

    /** A "vanilla" mob is any mob that is not a Citizens NPC. Players, armor stands, items, etc. are never touched. */
    private static boolean isVanillaMob(Entity entity) {
        return entity instanceof Mob && !entity.hasMetadata("NPC");
    }

    /** Removes every mob currently loaded in every world. Returns how many were removed. */
    public int sweepLoadedWorlds() {
        if (!isEnabled()) {
            return 0;
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isVanillaMob(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0 && plugin.isDebug()) {
            plugin.getLogger().info("DisableVanillaSpawns: removed " + removed + " existing mob(s).");
        }
        return removed;
    }

    /**
     * Citizens creates its entities with {@link CreatureSpawnEvent.SpawnReason#CUSTOM} (and tags them only after
     * the event), so that reason is the one we let through. Everything else the game or players trigger is cancelled:
     * natural, chunk generation, spawners, eggs, breeding, raids, patrols, reinforcements, /summon, etc.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!isEnabled() || !(event.getEntity() instanceof Mob) || event.getEntity().hasMetadata("NPC")) {
            return;
        }
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    /** Mobs persisted on disk from before (chunks loaded after startup). Removed one tick later to be safe. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!isEnabled()) {
            return;
        }
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : event.getEntities()) {
            if (isVanillaMob(entity)) {
                targets.add(entity);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity entity : targets) {
                if (entity.isValid() && isVanillaMob(entity)) {
                    entity.remove();
                }
            }
        });
    }
}
