package io.github.mcengine.common.economy.tabcompleter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab completer for the /economy command, providing suggestions for subcommands,
 * players, coin types, and extensions.
 */
public class MCEngineEconomyCommonTabCompleter implements TabCompleter {

    /** Supported /economy subcommands. */
    private static final List<String> SUBCOMMANDS = Arrays.asList("check", "add", "addon", "dlc");

    /** Supported coin buckets. */
    private static final List<String> COIN_TYPES = Arrays.asList("coin", "copper", "silver", "gold");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // /economy <subcommand>
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        // /economy addon|dlc <list>
        if (args.length == 2 && (args[0].equalsIgnoreCase("addon") || args[0].equalsIgnoreCase("dlc"))) {
            return filter(Collections.singletonList("list"), args[1]);
        }

        // /economy check <coinType> or /economy check <player>
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            List<String> suggestions = new ArrayList<>(COIN_TYPES);
            if (sender.hasPermission("mcengine.economy.check.player")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    suggestions.add(online.getName());
                }
            }
            return filter(suggestions, args[1]);
        }

        // /economy check <player> <coinType>
        if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            if (sender.hasPermission("mcengine.economy.check.player")) {
                return filter(COIN_TYPES, args[2]);
            }
        }

        // /economy add <player>
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            if (sender.hasPermission("mcengine.economy.add")) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filter(names, args[1]);
            }
        }

        // /economy add <player> <coinType>
        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            if (sender.hasPermission("mcengine.economy.add")) {
                return filter(COIN_TYPES, args[2]);
            }
        }

        // /economy add <player> <coinType> <amount>
        if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            if (sender.hasPermission("mcengine.economy.add")) {
                return Collections.singletonList("<amount>");
            }
        }

        return Collections.emptyList();
    }

    /**
     * Filters a list of strings based on input (case-insensitive).
     *
     * @param options options to filter
     * @param input   user input
     * @return filtered suggestions
     */
    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
}
