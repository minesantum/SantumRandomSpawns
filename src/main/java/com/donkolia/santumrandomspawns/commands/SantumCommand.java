package com.donkolia.santumrandomspawns.commands;

import com.donkolia.santumrandomspawns.SantumRandomSpawns;
import com.donkolia.santumrandomspawns.model.RandomSpawnConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /santumrandomspawns <reload|enable|disable> [randomspawn-name]} with full tab-completion.
 */
public final class SantumCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "enable", "disable");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final SantumRandomSpawns plugin;

    public SantumCommand(SantumRandomSpawns plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("santumrandomspawns.admin")) {
            sendMessage(sender, "messages.no-permission", "&cNo tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendMessage(sender, "messages.usage", "&eUso: &f/santumrandomspawns <reload|enable|disable> [nombre]");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                int count = plugin.reloadPlugin();
                String raw = plugin.getConfig().getString(
                        "messages.reloaded",
                        "&aConfiguración y &e{count} &arandom spawns recargados correctamente en tiempo real."
                ).replace("{count}", String.valueOf(count));
                sendRaw(sender, raw);
            }
            case "enable" -> handleToggle(sender, args, true);
            case "disable" -> handleToggle(sender, args, false);
            default -> sendMessage(sender, "messages.usage", "&eUso: &f/santumrandomspawns <reload|enable|disable> [nombre]");
        }
        return true;
    }

    private void handleToggle(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            sendMessage(sender, "messages.usage", "&eUso: &f/santumrandomspawns <reload|enable|disable> [nombre]");
            return;
        }
        String targetName = args[1];
        RandomSpawnConfig config = plugin.getSpawner(targetName);
        if (config == null) {
            String raw = plugin.getConfig().getString(
                    "messages.not-found",
                    "&cNo se encontró ningún RandomSpawn llamado &e{name}&c."
            ).replace("{name}", targetName);
            sendRaw(sender, raw);
            return;
        }

        if (enable && config.isEnabled()) {
            String raw = plugin.getConfig().getString(
                    "messages.already-enabled",
                    "&eEl RandomSpawn &6{name} &eya está habilitado."
            ).replace("{name}", config.getName());
            sendRaw(sender, raw);
            return;
        }
        if (!enable && !config.isEnabled()) {
            String raw = plugin.getConfig().getString(
                    "messages.already-disabled",
                    "&eEl RandomSpawn &6{name} &eya está deshabilitado."
            ).replace("{name}", config.getName());
            sendRaw(sender, raw);
            return;
        }

        plugin.getLoader().setEnabledInFile(config, enable);
        if (!enable) {
            plugin.getNpcService().destroyBySpawner(config.getName());
        }

        String key = enable ? "messages.enabled" : "messages.disabled";
        String def = enable
                ? "&aRandomSpawn &e{name} &ahabilitado correctamente."
                : "&cRandomSpawn &e{name} &cdeshabilitado correctamente.";
        String raw = plugin.getConfig().getString(key, def).replace("{name}", config.getName());
        sendRaw(sender, raw);
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("santumrandomspawns.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    matches.add(sub);
                }
            }
            return matches;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("enable".equals(sub) || "disable".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                List<String> matches = new ArrayList<>();
                for (RandomSpawnConfig config : plugin.getSpawners().values()) {
                    if (config.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        matches.add(config.getName());
                    }
                }
                Collections.sort(matches);
                return matches;
            }
        }

        return Collections.emptyList();
    }

    private void sendMessage(CommandSender sender, String path, String def) {
        sendRaw(sender, plugin.getConfig().getString(path, def));
    }

    private void sendRaw(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&6SantumRandomSpawns&8] ");
        sender.sendMessage(LEGACY.deserialize(prefix + message));
    }
}
