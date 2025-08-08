package io.github.mcengine.common.currency.command;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.hologram.MCEngineHologramApi;
import io.github.mcengine.common.currency.MCEngineCurrencyCommon;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the <code>/currency default</code> command with subcommands for checking balances,
 * checking another player's balance (with permission), adding coins (with permission),
 * and listing addons/DLC.
 */
public class MCEngineCurrencyCommonCommand implements CommandExecutor {

    /**
     * Currency API for managing and querying player balances.
     */
    private final MCEngineCurrencyCommon currencyApi;

    /**
     * The command prefix suggested to the user when they interact with the usage hologram.
     * This is sent as a clickable chat component using {@link ClickEvent.Action#SUGGEST_COMMAND}.
     */
    private static final String SUGGEST_PREFIX = "/currency default ";

    /**
     * Default number of seconds the usage hologram should remain visible.
     */
    private static final int DEFAULT_HOLOGRAM_SECONDS = 10;

    /**
     * Canonical usage lines (without the leading "Usage:" label). These lines are rendered
     * both in chat and inside the usage hologram to ensure consistency.
     */
    private static final String[] USAGE_LINES = new String[]{
            "/currency default check <coinType>",
            "/currency default check <player> <coinType> (OP)",
            "/currency default add <player> <coinType> <amount> (OP)",
            "/currency default addon list",
            "/currency default dlc list"
    };

    /**
     * Creates a new command executor.
     *
     * @param currencyApi currency API used to manage player coin balances
     */
    public MCEngineCurrencyCommonCommand(MCEngineCurrencyCommon currencyApi) {
        this.currencyApi = currencyApi;
    }

    /**
     * Executes the <code>/currency default</code> command and its subcommands.
     *
     * Expected syntaxes:
     * <ul>
     *     <li><code>/currency default check &lt;coinType&gt;</code></li>
     *     <li><code>/currency default check &lt;player&gt; &lt;coinType&gt;</code> (requires <code>mcengine.currency.check.player</code>)</li>
     *     <li><code>/currency default add &lt;player&gt; &lt;coinType&gt; &lt;amount&gt;</code> (requires <code>mcengine.currency.add</code>)</li>
     *     <li><code>/currency default addon list</code></li>
     *     <li><code>/currency default dlc list</code></li>
     * </ul>
     *
     * @param sender  source of the command
     * @param command command object
     * @param label   alias of the command
     * @param args    command arguments
     * @return {@code true} if the command handled the input, otherwise {@code false}
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /currency addon list or /currency dlc list
        if (args.length == 3 && ("addon".equalsIgnoreCase(args[1]) || "dlc".equalsIgnoreCase(args[1]))
                && "list".equalsIgnoreCase(args[2])) {
            if (sender instanceof Player player) {
                return MCEngineCoreApi.handleExtensionList(player, currencyApi.getPlugin(), args[1]);
            } else {
                sender.sendMessage(ChatColor.RED + "Only players can run this command.");
                return true;
            }
        }

        // Player checking their own balance
        if (args.length == 3 && args[1].equalsIgnoreCase("check") && sender instanceof Player player) {
            String coinType = args[2].toLowerCase();
            if (!isValidCoinType(coinType)) {
                player.sendMessage(ChatColor.RED + "Invalid coin type. Use coin, copper, silver, or gold.");
                return true;
            }

            if (!currencyApi.checkIfPlayerExists(player.getUniqueId())) {
                currencyApi.initPlayerData(player.getUniqueId());
            }

            double balance = currencyApi.getCoin(player.getUniqueId(), coinType);
            player.sendMessage(ChatColor.GREEN + "Your balance for " + coinType + ": " + ChatColor.GOLD + balance);
            return true;
        }

        // Admin checking another player's balance
        if (args.length == 4 && args[1].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("mcengine.currency.check.player")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String coinType = args[3].toLowerCase();
            if (!isValidCoinType(coinType)) {
                sender.sendMessage(ChatColor.RED + "Invalid coin type.");
                return true;
            }

            if (!currencyApi.checkIfPlayerExists(target.getUniqueId())) {
                currencyApi.initPlayerData(target.getUniqueId());
            }

            double balance = currencyApi.getCoin(target.getUniqueId(), coinType);
            sender.sendMessage(ChatColor.GREEN + target.getName() + "'s " + coinType + " balance: " + ChatColor.GOLD + balance);
            return true;
        }

        // Admin adding coins
        if (args.length == 5 && args[1].equalsIgnoreCase("add")) {
            if (!sender.hasPermission("mcengine.currency.add")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String coinType = args[3].toLowerCase();
            String amountStr = args[4];

            if (!isValidCoinType(coinType)) {
                sender.sendMessage(ChatColor.RED + "Invalid coin type.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                return true;
            }

            if (!currencyApi.checkIfPlayerExists(target.getUniqueId())) {
                currencyApi.initPlayerData(target.getUniqueId());
            }

            currencyApi.addCoin(target.getUniqueId(), coinType, amount);
            sender.sendMessage(ChatColor.GREEN + "Added " + amount + " " + coinType + " to " + target.getName() + ".");
            return true;
        }

        // Unrecognized usage: show chat usage AND a hologram with the same usage via the Hologram API.
        sendUsageTo(sender);

        if (sender instanceof Player player) {
            new MCEngineHologramApi()
                    .getUsageHologram(player, SUGGEST_PREFIX, USAGE_LINES);
        }

        return true;
    }

    /**
     * Validates supported coin types.
     *
     * @param type coin type string (case-insensitive)
     * @return {@code true} if the type is one of coin, copper, silver, or gold; otherwise {@code false}
     */
    private boolean isValidCoinType(String type) {
        return type.matches("coin|copper|silver|gold");
    }

    /**
     * Sends textual usage information to a {@link CommandSender}. This mirrors what is shown in the hologram.
     *
     * @param sender recipient of the usage text
     */
    private void sendUsageTo(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage:");
        for (String line : USAGE_LINES) {
            sender.sendMessage(ChatColor.GRAY + line);
        }
    }
}
