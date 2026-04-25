package com.hapatapa.lod;

import com.hapatapa.lod.engine.LODDistance;
import com.hapatapa.lod.engine.LODQuality;
import com.hapatapa.lod.engine.PlayerSession;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class LODCommand implements BasicCommand {

    private final LODPlugin plugin;

    public LODCommand(LODPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(Component.text("This command is for players only.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("--- LOD System ---", NamedTextColor.AQUA));
            player.sendMessage(Component.text("/lod distance <high|balanced|performance>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/lod quality <high|medium|low>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/lod clear", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/lod menu", NamedTextColor.YELLOW));
            return;
        }

        if (args[0].equalsIgnoreCase("distance") && args.length > 1) {
            String input = args[1].toUpperCase();
            LODDistance distance = null;

            if (input.equals("HIGH") || input.equals("HIGH_FIDELITY")) {
                distance = LODDistance.HIGH_FIDELITY;
            } else if (input.equals("BALANCED")) {
                distance = LODDistance.BALANCED;
            } else if (input.equals("PERFORMANCE")) {
                distance = LODDistance.PERFORMANCE;
            }

            if (distance != null) {
                PlayerSession session = plugin.getLodManager().getSession(player);
                if (session != null) {
                    session.setDistance(distance);
                    player.sendMessage(Component.text("LOD Distance set to " + distance.name(), NamedTextColor.GREEN));
                }
            } else {
                player.sendMessage(
                        Component.text("Invalid distance. Use: high, balanced, performance", NamedTextColor.RED));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("quality") && args.length > 1) {
            String input = args[1].toUpperCase();
            LODQuality quality = null;

            if (input.equals("EXTREME") || input.equals("HIGH")) {
                quality = LODQuality.EXTREME;
            } else if (input.equals("MEDIUM")) {
                quality = LODQuality.MEDIUM;
            } else if (input.equals("LOW")) {
                quality = LODQuality.LOW;
            }

            if (quality != null) {
                PlayerSession session = plugin.getLodManager().getSession(player);
                if (session != null) {
                    session.setQuality(quality);
                    player.sendMessage(Component.text("LOD Quality set to " + quality.name(), NamedTextColor.GREEN));
                }
            } else {
                player.sendMessage(Component.text("Invalid quality. Use: extreme, medium, low", NamedTextColor.RED));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            plugin.getLodManager().clearCache();
            player.sendMessage(Component.text("LOD Cache cleared and sessions reset.", NamedTextColor.GREEN));
            return;
        }

        if (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("settings")) {
            plugin.getDialogManager().openSettingsMenu(player);
            return;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!player.isOp()) {
                player.sendMessage(Component.text("You must be OP to use this command.", NamedTextColor.RED));
                return;
            }
            plugin.getDialogManager().openAdminMenu(player);
            return;
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new java.util.ArrayList<>(
                    List.of("distance", "quality", "clear", "menu", "settings"));
            if (stack.getSender().isOp())
                suggestions.add("admin");
            return suggestions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("distance")) {
            return List.of("high", "balanced", "performance");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("quality")) {
            return List.of("extreme", "medium", "low");
        }
        return List.of();
    }
}
