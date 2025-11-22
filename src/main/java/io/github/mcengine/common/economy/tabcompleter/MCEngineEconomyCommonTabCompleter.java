package io.github.mcengine.common.economy.tabcompleter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer for the /economy command.
 *
 * Provides context-aware suggestions for:
 * <ul>
 *     <li>top-level subcommands (check, send, plus, minus)</li>
 *     <li>player names (online)</li>
 *     <li>coin types (coin, copper, silver, gold)</li>
 * </ul>
 *
 * Behavior:
 * <ul>
 *     <li>If the sender has operator permission (or appropriate perms), show operator subcommands.</li>
 *     <li>For the "check" subcommand: if the sender may check others, suggest player names on arg2,
 *         otherwise suggest coin types.</li>
 *     <li>For send/plus/minus: arg2 -> player names; arg3 -> coin types; arg4 -> no suggestions (amount).</li>
 * </ul>
 */
public class MCEngineEconomyCommonTabCompleter implements TabCompleter {

    /** Supported /economy subcommands available to regular players. */
    private static final List<String> SUBCOMMANDS_FOR_PLAYER = Arrays.asList("check", "send");

    /** Supported /economy subcommands available to operators/admins. */
    private static final List<String> SUBCOMMANDS_FOR_OP = Arrays.asList("check", "send", "plus", "minus");

    /** Supported coin buckets. */
    private static final List<String> COIN_TYPES = Arrays.asList("coin", "copper", "silver", "gold");

    private static final String PERM_CHECK_OTHER = "mcengine.economy.check.player";
    private static final String PERM_PLUS = "mcengine.economy.plus";
    private static final String PERM_MINUS = "mcengine.economy.minus";

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // no args -> suggest top-level subcommands
        if (args == null || args.length == 0) {
            return new ArrayList<>(senderHasOpLike(sender) ? SUBCOMMANDS_FOR_OP : SUBCOMMANDS_FOR_PLAYER);
        }

        String sub = args[0].toLowerCase();

        // ARG 1: suggest subcommands filtered by prefix
        if (args.length == 1) {
            List<String> choices = senderHasOpLike(sender) ? SUBCOMMANDS_FOR_OP : SUBCOMMANDS_FOR_PLAYER;
            return filterPrefix(choices, sub);
        }

        // ARG 2+ : context-aware suggestions depending on subcommand
        switch (sub) {
            case "check":
                return handleCheckTab(sender, args);

            case "send":
            case "plus":
            case "minus":
                return handleTransferTab(sender, args, sub);

            default:
                return Collections.emptyList();
        }
    }

    /**
     * Handle tab completion for the "check" subcommand.
     *
     * Variants:
     * - /economy check                 -> (handled in command) no further args
     * - /economy check <coinType>      -> suggest coin types
     * - /economy check <player>        -> suggest player names (if permitted)
     * - /economy check <player> <type> -> suggest coin types
     */
    private List<String> handleCheckTab(CommandSender sender, String[] args) {
        // args[0] == "check"
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();

            // If sender can check other players, suggest player names (and also coin types if they want)
            if (senderHasPermission(sender, PERM_CHECK_OTHER) || senderHasOpLike(sender)) {
                List<String> players = onlinePlayerNames();
                List<String> merged = new ArrayList<>(players);
                merged.addAll(COIN_TYPES);
                return filterPrefix(merged, prefix);
            }

            // otherwise suggest coin types for self-check
            return filterPrefix(COIN_TYPES, prefix);
        }

        // args.length >= 3 -> assume arg2 is player, arg3 is coin type
        if (args.length >= 3) {
            String prefix = args[2].toLowerCase();
            return filterPrefix(COIN_TYPES, prefix);
        }

        return Collections.emptyList();
    }

    /**
     * Handle tab completion for transfer-like subcommands: send, plus, minus.
     *
     * Expected forms:
     * - /economy send <player> <coinType> <amount>
     * - /economy plus <player> <coinType> <amount>
     * - /economy minus <player> <coinType> <amount>
     */
    private List<String> handleTransferTab(CommandSender sender, String[] args, String sub) {
        // arg index: args[0]=sub, args[1]=player, args[2]=coinType, args[3]=amount
        if (args.length == 2) {
            // suggest online player names for the player argument
            String prefix = args[1].toLowerCase();
            return filterPrefix(onlinePlayerNames(), prefix);
        }

        if (args.length == 3) {
            // suggest coin types
            String prefix = args[2].toLowerCase();
            return filterPrefix(COIN_TYPES, prefix);
        }

        // arg 4 (amount) - no sensible tab completions
        return Collections.emptyList();
    }

    /**
     * Return a list of online player names.
     */
    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getName())
                .collect(Collectors.toList());
    }

    /**
     * Case-insensitive prefix filter.
     *
     * @param choices full list of choices
     * @param prefix  typed prefix
     * @return filtered and sorted list
     */
    private List<String> filterPrefix(List<String> choices, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            List<String> out = new ArrayList<>(choices);
            Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
            return out;
        }
        String low = prefix.toLowerCase();
        return choices.stream()
                .filter(c -> c != null && c.toLowerCase().startsWith(low))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Determine whether the sender should get operator-level suggestions.
     *
     * We treat actual operators and senders with the top-level permissions as "op-like".
     */
    private boolean senderHasOpLike(CommandSender sender) {
        return sender.isOp() || senderHasPermission(sender, PERM_PLUS) || senderHasPermission(sender, PERM_MINUS) || senderHasPermission(sender, PERM_CHECK_OTHER);
    }

    /**
     * Safe permission check (handles null sender).
     */
    private boolean senderHasPermission(CommandSender sender, String perm) {
        return sender != null && sender.hasPermission(perm);
    }
}
