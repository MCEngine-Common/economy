package io.github.mcengine.common.economy.command;

import io.github.mcengine.common.economy.MCEngineEconomyCommon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Command executor for the /economy command family.
 *
 * Supported subcommands:
 * <ul>
 *   <li><code>/economy check</code> - show your default coin balance</li>
 *   <li><code>/economy check &lt;coinType&gt;</code> - show your specific coin balance</li>
 *   <li><code>/economy check &lt;player&gt;</code> - (permission) show another player's default coin</li>
 *   <li><code>/economy check &lt;player&gt; &lt;coinType&gt;</code> - (permission) show another player's specific coin</li>
 *   <li><code>/economy plus &lt;player&gt; &lt;coinType&gt; &lt;amount&gt;</code> - (permission) add coins to a player</li>
 *   <li><code>/economy minus &lt;player&gt; &lt;coinType&gt; &lt;amount&gt;</code> - (permission) remove coins from a player</li>
 * </ul>
 *
 * Permissions:
 * <ul>
 *   <li><code>mcengine.economy.check</code> - check own balance</li>
 *   <li><code>mcengine.economy.check.player</code> - check other players' balances</li>
 *   <li><code>mcengine.economy.plus</code> - add coins to players</li>
 *   <li><code>mcengine.economy.minus</code> - subtract coins from players</li>
 * </ul>
 */
public class MCEngineEconomyCommonCommand implements CommandExecutor {

    /** Economy API for managing and querying player balances. */
    private final MCEngineEconomyCommon currencyApi;

    private static final String PERM_CHECK_SELF = "mcengine.economy.check";
    private static final String PERM_CHECK_OTHER = "mcengine.economy.check.player";
    private static final String PERM_PLUS = "mcengine.economy.plus";
    private static final String PERM_MINUS = "mcengine.economy.minus";

    /**
     * Creates a new command executor.
     *
     * @param currencyApi economy API used to manage player coin balances
     */
    public MCEngineEconomyCommonCommand(MCEngineEconomyCommon currencyApi) {
        this.currencyApi = currencyApi;
    }

    /**
     * Executes the /economy command and subcommands.
     *
     * Supported top-level subcommands: check, plus, minus.
     *
     * @param sender command sender (player or console)
     * @param command command invoked
     * @param label alias used
     * @param args arguments passed
     * @return true if the command was handled; false to show usage (we return true after handling)
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Basic validation
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "check":
                handleCheck(sender, args);
                break;
            case "plus":
                handlePlusMinus(sender, args, true);
                break;
            case "minus":
                handlePlusMinus(sender, args, false);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    // -------------------------
    // Handlers
    // -------------------------

    /**
     * Handle /economy check ...
     *
     * Variants:
     * - /economy check
     * - /economy check <coinType>
     * - /economy check <player>
     * - /economy check <player> <coinType>
     */
    private void handleCheck(CommandSender sender, String[] args) {
        // /economy check
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(color("&cConsole must specify a player: /economy check <player>"));
                return;
            }
            if (!sender.hasPermission(PERM_CHECK_SELF)) {
                sender.sendMessage(color("&cYou do not have permission to check your balance."));
                return;
            }
            Player p = (Player) sender;
            int total = currencyApi.getCoin(p.getUniqueId());
            sender.sendMessage(color("&aYour total coin: &e" + total));
            return;
        }

        // /economy check <arg1>
        if (args.length == 2) {
            String arg1 = args[1];

            // if sender is asking for another player (/economy check <player>) and has permission
            OfflinePlayer target = Bukkit.getOfflinePlayer(arg1);
            if (target != null && (target.hasPlayedBefore() || target.isOnline())) {
                // This branch interprets single argument as player name if it resolves to a known player
                if (!sender.hasPermission(PERM_CHECK_OTHER)) {
                    // If the sender is the player themselves and has self-permission, allow it
                    if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                        int total = currencyApi.getCoin(target.getUniqueId());
                        sender.sendMessage(color("&aYour total coin: &e" + total));
                    } else {
                        sender.sendMessage(color("&cYou do not have permission to check other players' balances."));
                    }
                    return;
                }
                int total = currencyApi.getCoin(target.getUniqueId());
                sender.sendMessage(color("&a" + target.getName() + " total coin: &e" + total));
                return;
            }

            // otherwise treat arg1 as coinType for self-check (e.g., /economy check copper)
            if (sender instanceof Player) {
                if (!sender.hasPermission(PERM_CHECK_SELF)) {
                    sender.sendMessage(color("&cYou do not have permission to check your balance."));
                    return;
                }
                Player p = (Player) sender;
                String coinType = arg1.toLowerCase();
                int amount = currencyApi.getCoin(p.getUniqueId(), coinType);
                sender.sendMessage(color("&aYour " + coinType + ": &e" + amount));
                return;
            } else {
                sender.sendMessage(color("&cConsole must specify a player and coin type: /economy check <player> <coinType>"));
                return;
            }
        }

        // /economy check <player> <coinType>
        if (args.length >= 3) {
            String playerName = args[1];
            String coinType = args[2].toLowerCase();

            if (!sender.hasPermission(PERM_CHECK_OTHER)) {
                sender.sendMessage(color("&cYou do not have permission to check other players' balances."));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                sender.sendMessage(color("&cPlayer not found: " + playerName));
                return;
            }

            int amount = currencyApi.getCoin(target.getUniqueId(), coinType);
            sender.sendMessage(color("&a" + target.getName() + " " + coinType + ": &e" + amount));
            return;
        }
    }

    /**
     * Handle /economy plus and /economy minus commands.
     *
     * Expected form:
     * /economy plus <player> <coinType> <amount>
     * /economy minus <player> <coinType> <amount>
     *
     * @param sender command sender
     * @param args command arguments
     * @param isPlus true for plus, false for minus
     */
    private void handlePlusMinus(CommandSender sender, String[] args, boolean isPlus) {
        String perm = isPlus ? PERM_PLUS : PERM_MINUS;
        String verb = isPlus ? "plus" : "minus";

        if (!sender.hasPermission(perm)) {
            sender.sendMessage(color("&cYou do not have permission to " + verb + " coins."));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(color("&cUsage: /economy " + verb + " <player> <coinType> <amount>"));
            return;
        }

        String playerName = args[1];
        String coinType = args[2].toLowerCase();
        String amountStr = args[3];

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(color("&cPlayer not found: " + playerName));
            return;
        }

        if (!coinType.matches("coin|copper|silver|gold")) {
            sender.sendMessage(color("&cInvalid coin type: " + coinType + ". Valid types: coin, copper, silver, gold"));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(color("&cAmount must be a positive integer."));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(color("&cAmount must be greater than zero."));
            return;
        }

        UUID uuid = target.getUniqueId();

        if (isPlus) {
            currencyApi.plusCoin(uuid, coinType.equals("coin") ? amount : 0); // adjust below
            // Use the more specific overload when coinType is not "coin"
            if (!"coin".equals(coinType)) {
                currencyApi.plusCoin(uuid, coinType, amount);
            } else {
                // already handled above by plusCoin(uuid, amount)
                // (some implementations may prefer only the coin-type overload;
                //  keep both for safety — implementations should handle accordingly)
            }

            sender.sendMessage(color("&aAdded " + amount + " " + coinType + " to " + target.getName()));
        } else {
            currencyApi.minusCoin(uuid, coinType.equals("coin") ? amount : 0);
            if (!"coin".equals(coinType)) {
                currencyApi.minusCoin(uuid, coinType, amount);
            }
            sender.sendMessage(color("&aRemoved " + amount + " " + coinType + " from " + target.getName()));
        }
    }

    // -------------------------
    // Helpers
    // -------------------------

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(color("&6--- Economy Commands ---"));
        sender.sendMessage(color("&e/economy check &7- check your default coin"));
        sender.sendMessage(color("&e/economy check <coinType> &7- check your specific coin"));
        sender.sendMessage(color("&e/economy check <player> &7- check player's default coin (perm)"));
        sender.sendMessage(color("&e/economy check <player> <coinType> &7- check player's specific coin (perm)"));
        sender.sendMessage(color("&e/economy plus <player> <coinType> <amount> &7- add coins to a player (perm)"));
        sender.sendMessage(color("&e/economy minus <player> <coinType> <amount> &7- remove coins from a player (perm)"));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
