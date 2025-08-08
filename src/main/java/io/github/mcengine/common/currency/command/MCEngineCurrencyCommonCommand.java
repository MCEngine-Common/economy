package io.github.mcengine.common.currency.command;

import io.github.mcengine.common.currency.MCEngineCurrencyCommon;
import io.github.mcengine.api.core.MCEngineCoreApi;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
     * Default number of seconds to display the hologram for wrong usage.
     */
    private static final int DEFAULT_HOLOGRAM_SECONDS = 10;

    /**
     * Vertical spacing between stacked ArmorStand lines for the hologram.
     * Smaller values pack lines closer together.
     */
    private static final double HOLOGRAM_LINE_SPACING = 0.27D;

    /**
     * Suggestion prefix that will be offered to the player after clicking the hologram.
     * This text is sent as a clickable chat component using {@link ClickEvent.Action#SUGGEST_COMMAND}.
     */
    private static final String SUGGEST_PREFIX = "/currency default ";

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

        // Unrecognized usage: show chat usage AND a multi-line hologram with the same usage.
        sendUsageTo(sender);

        if (sender instanceof Player player) {
            List<String> lines = buildUsageHologramLines();
            showTemporaryHologram(player, lines, DEFAULT_HOLOGRAM_SECONDS, SUGGEST_PREFIX);
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
        sender.sendMessage(ChatColor.GRAY + "/currency default check <coinType>");
        sender.sendMessage(ChatColor.GRAY + "/currency default check <player> <coinType> (OP)");
        sender.sendMessage(ChatColor.GRAY + "/currency default add <player> <coinType> <amount> (OP)");
        sender.sendMessage(ChatColor.GRAY + "/currency default addon list");
        sender.sendMessage(ChatColor.GRAY + "/currency default dlc list");
    }

    /**
     * Builds the list of lines for the usage hologram. Lines are colored and ordered top-to-bottom.
     *
     * @return lines to display in the hologram
     */
    private List<String> buildUsageHologramLines() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Invalid command");
        lines.add(ChatColor.GOLD + "Usage:");
        lines.add(ChatColor.GRAY + "/currency default check <coinType>");
        lines.add(ChatColor.GRAY + "/currency default check <player> <coinType> (OP)");
        lines.add(ChatColor.GRAY + "/currency default add <player> <coinType> <amount> (OP)");
        lines.add(ChatColor.GRAY + "/currency default addon list");
        lines.add(ChatColor.GRAY + "/currency default dlc list");
        lines.add(ChatColor.YELLOW + "Right-click to get a command prompt");
        return lines;
    }

    /**
     * Spawns a stacked, multi-line hologram (one invisible marker {@link ArmorStand} per line) in front of
     * the player, listens for the player to right-click any line, and then sends a clickable chat component
     * that suggests the provided command prefix (pre-filling their chat input when they click the chat message).
     * <p>
     * All spawned entities and the listener are automatically removed after the specified duration.
     *
     * @param player          player to show the hologram to and listen for clicks from
     * @param lines           ordered list of text lines to display (top-to-bottom)
     * @param durationSeconds how long to display the hologram before removal
     * @param suggestPrefix   command prefix to suggest to the player's chat (e.g. {@code "/currency default "})
     */
    private void showTemporaryHologram(Player player, List<String> lines, int durationSeconds, String suggestPrefix) {
        // Position ~1.6 blocks in front of the player's eyes, then offset upward to fit all lines
        Location eye = player.getEyeLocation();
        Location base = eye.add(eye.getDirection().normalize().multiply(1.6));

        // Raise base so the "title" isn't at the player's feet when we stack downward
        base.add(0, (lines.size() - 1) * HOLOGRAM_LINE_SPACING * 0.5, 0);

        Set<UUID> standIds = new HashSet<>();
        List<ArmorStand> spawned = new ArrayList<>(lines.size());

        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            Location lineLoc = base.clone().subtract(0, i * HOLOGRAM_LINE_SPACING, 0);

            ArmorStand stand = player.getWorld().spawn(lineLoc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setSmall(true);
                as.setGravity(false);
                as.setCustomNameVisible(true);
                as.setCustomName(line);
                as.setInvulnerable(true);
                as.setCollidable(false);
            });

            spawned.add(stand);
            standIds.add(stand.getUniqueId());
        }

        // Listener: only the creator player can interact; right-click yields a clickable chat suggestion.
        Listener listener = new Listener() {
            @EventHandler
            public void onInteract(PlayerInteractAtEntityEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                if (!standIds.contains(event.getRightClicked().getUniqueId())) return;

                event.setCancelled(true);

                TextComponent tip = new TextComponent(ChatColor.YELLOW + "Click to prefill: " + ChatColor.WHITE + suggestPrefix);
                tip.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestPrefix));
                player.spigot().sendMessage(tip);
            }
        };

        Bukkit.getPluginManager().registerEvents(listener, currencyApi.getPlugin());

        // Cleanup after duration
        Bukkit.getScheduler().runTaskLater(currencyApi.getPlugin(), () -> {
            for (ArmorStand stand : spawned) {
                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
            HandlerList.unregisterAll(listener);
        }, durationSeconds * 20L);
    }
}
