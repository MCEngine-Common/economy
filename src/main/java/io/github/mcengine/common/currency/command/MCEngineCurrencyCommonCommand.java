package io.github.mcengine.common.currency.command;

import io.github.mcengine.common.currency.MCEngineCurrencyCommon;
import io.github.mcengine.api.core.MCEngineCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /currency default command with subcommands for checking and adding player balances.
 */
public class MCEngineCurrencyCommonCommand implements CommandExecutor {

    /**
     * The currency API instance used for managing player balances.
     */
    private final MCEngineCurrencyCommon currencyApi;

    /**
     * Constructs the command executor with a reference to the currency API.
     *
     * @param currencyApi The currency API instance.
     */
    public MCEngineCurrencyCommonCommand(MCEngineCurrencyCommon currencyApi) {
        this.currencyApi = currencyApi;
    }

    /**
     * Executes the /currency default command and its subcommands.
     *
     * @param sender  The source of the command.
     * @param command The command object.
     * @param label   The alias of the command.
     * @param args    The command arguments.
     * @return true if the command executed successfully, false otherwise.
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

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
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

        sender.sendMessage(ChatColor.RED + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "/currency default check <coinType>");
        sender.sendMessage(ChatColor.GRAY + "/currency default check <player> <coinType> (OP)");
        sender.sendMessage(ChatColor.GRAY + "/currency default add <player> <coinType> <amount> (OP)");
        sender.sendMessage(ChatColor.GRAY + "/currency default addon list");
        sender.sendMessage(ChatColor.GRAY + "/currency default dlc list");
        return true;
    }

    /**
     * Checks if the coin type is valid.
     *
     * @param type The coin type.
     * @return True if valid, false otherwise.
     */
    private boolean isValidCoinType(String type) {
        return type.matches("coin|copper|silver|gold");
    }
}
