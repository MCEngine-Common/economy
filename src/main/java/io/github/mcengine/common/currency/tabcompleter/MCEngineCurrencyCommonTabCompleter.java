package io.github.mcengine.common.currency.tabcompleter;

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
 * Tab completer for the /currency command, providing suggestions for subcommands, players, coin types, and extensions.
 */
public class MCEngineCurrencyCommonTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("check", "add", "addon", "dlc");
    private static final List<String> COIN_TYPES = Arrays.asList("coin", "copper", "silver", "gold");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // /currency <subcommand>
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        // /currency addon|dlc <list>
        if (args.length == 2 && (args[0].equalsIgnoreCase("addon") || args[0].equalsIgnoreCase("dlc"))) {
            return filter(Collections.singletonList("list"), args[1]);
        }

        // /currency check <coinType> or /currency check <player>
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            List<String> suggestions = new ArrayList<>(COIN_TYPES);
            if (sender.hasPermission("mcengine.currency.check.player")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    suggestions.add(online.getName());
                }
            }
            return filter(suggestions, args[1]);
        }

        // /currency check <player> <coinType>
        if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            if (sender.hasPermission("mcengine.currency.check.player")) {
                return filter(COIN_TYPES, args[2]);
            }
        }

        // /currency add <player>
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            if (sender.hasPermission("mcengine.currency.add")) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filter(names, args[1]);
            }
        }

        // /currency add <player> <coinType>
        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            if (sender.hasPermission("mcengine.currency.add")) {
                return filter(COIN_TYPES, args[2]);
            }
        }

        // /currency add <player> <coinType> <amount>
        if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            if (sender.hasPermission("mcengine.currency.add")) {
                return Collections.singletonList("<amount>");
            }
        }

        return Collections.emptyList();
    }

    /**
     * Filters a list of strings based on input (case-insensitive).
     *
     * @param options The options to filter.
     * @param input   The user input.
     * @return Filtered list of suggestions.
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
