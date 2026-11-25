package com.donkolia.santumrandomspawns.listeners;

import com.donkolia.santumrandomspawns.SantumRandomSpawns;
import com.donkolia.santumrandomspawns.spawning.NpcSpawnerService;
import com.donkolia.santumrandomspawns.spawning.TrackedNpc;
import net.citizensnpcs.api.event.NPCClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Cleans up tracked ephemeral Citizens2 NPCs on death or chunk unload.
 */
public final class SpawningListener implements Listener {

    private final SantumRandomSpawns plugin;
    private final NpcSpawnerService npcService;

    public SpawningListener(SantumRandomSpawns plugin, NpcSpawnerService npcService) {
        this.plugin = plugin;
        this.npcService = npcService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        npcService.handleEntityDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (plugin.getConfig().getBoolean("despawn.despawn-on-chunk-unload", true)) {
            npcService.handleChunkUnload(event.getChunk());
        }
    }

    /**
     * Server-side guarantee on top of the packet hiding: a player that does not meet the spawn conditions
     * can neither hurt the NPC (melee or projectiles) nor be hurt by it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        TrackedNpc victimNpc = npcService.getTracked(event.getEntity());
        if (victimNpc != null && victimNpc.hasConditions()) {
            Player attacker = resolvePlayer(event.getDamager());
            if (attacker != null && !npcService.meetsConditionsNow(victimNpc.getSpawnerName(), attacker)) {
                event.setCancelled(true);
                return;
            }
        }

        TrackedNpc attackerNpc = npcService.getTracked(event.getDamager());
        if (attackerNpc != null && attackerNpc.hasConditions() && event.getEntity() instanceof Player victim
                && !npcService.meetsConditionsNow(attackerNpc.getSpawnerName(), victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        TrackedNpc tracked = npcService.getTracked(event.getEntity());
        if (tracked != null && tracked.hasConditions() && event.getTarget() instanceof Player player
                && !npcService.meetsConditions(tracked.getSpawnerName(), player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcClick(NPCClickEvent event) {
        TrackedNpc tracked = npcService.getTracked(event.getNPC().getEntity());
        if (tracked != null && tracked.hasConditions()
                && !npcService.meetsConditionsNow(tracked.getSpawnerName(), event.getClicker())) {
            event.setCancelled(true);
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
