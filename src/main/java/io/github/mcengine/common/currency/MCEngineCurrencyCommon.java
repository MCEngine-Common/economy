package io.github.mcengine.common.currency;

import java.sql.Connection;
import java.util.UUID;
import io.github.mcengine.api.core.util.MCEngineCoreApiDispatcher;
import io.github.mcengine.api.currency.database.MCEngineCurrencyApiDBInterface;
import io.github.mcengine.common.currency.database.mysql.MCEngineCurrencyMySQL;
import io.github.mcengine.common.currency.database.sqlite.MCEngineCurrencySQLite;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

/**
 * The MCEngineCurrencyCommon class provides an interface for managing player currency transactions.
 * It supports multiple database implementations (MySQL, SQLite) and enables operations
 * such as initializing databases, checking player existence, managing currency balances,
 * and recording transactions.
 */
public class MCEngineCurrencyCommon {

    /**
     * Singleton instance of the MCEngineCurrencyCommon API.
     */
    private static MCEngineCurrencyCommon instance;

    /**
     * Reference to the Bukkit plugin instance using this API.
     */
    private Plugin plugin;

    /**
     * Interface for database operations related to currency, using a specific backend (e.g., MySQL or SQLite).
     */
    private MCEngineCurrencyApiDBInterface db;

    /**
     * Internal command dispatcher used for registering command namespaces and subcommands.
     */
    private final MCEngineCoreApiDispatcher dispatcher;

    /**
     * Constructs the currency API instance and initializes the appropriate database connection.
     *
     * @param plugin  The Bukkit plugin instance.
     */
    public MCEngineCurrencyCommon(Plugin plugin) {
        instance = this;
        this.plugin = plugin;
        this.dispatcher = new MCEngineCoreApiDispatcher();

        String sqlType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        switch (sqlType.toLowerCase()) {
            case "mysql":
                this.db = new MCEngineCurrencyMySQL(plugin);
                break;
            case "sqlite":
                this.db = new MCEngineCurrencySQLite(plugin);
                break;
            default:
                plugin.getLogger().severe("Unsupported SQL type: " + sqlType);
        }
    }

    /**
     * Gets the global API singleton instance.
     *
     * @return The {@link MCEngineCurrencyCommon} instance.
     */
    public static MCEngineCurrencyCommon getApi() {
        return instance;
    }

    /**
     * Gets the Bukkit plugin instance linked to this API.
     *
     * @return The plugin instance.
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Retrieves the active database connection used by the AI plugin.
     * <p>
     * This delegates to the underlying database implementation, such as SQLite or other supported types.
     * Useful for executing custom SQL queries or diagnostics externally.
     *
     * @return The {@link Connection} instance for the configured database.
     */
    public Connection getDBConnection() {
        return db.getDBConnection();
    }

    /**
     * Registers a command namespace (e.g. "plugin1") for this plugin's dispatcher.
     *
     * @param namespace unique namespace for commands
     */
    public void registerNamespace(String namespace) {
        dispatcher.registerNamespace(namespace);
    }

    /**
     * Binds a Bukkit command (like /example1) to the internal dispatcher.
     *
     * @param namespace       the command namespace
     * @param commandExecutor fallback executor
     */
    public void bindNamespaceToCommand(String namespace, CommandExecutor commandExecutor) {
        dispatcher.bindNamespaceToCommand(namespace, commandExecutor);
    }

    /**
     * Registers a subcommand under the specified namespace.
     *
     * @param namespace the command namespace
     * @param name      subcommand label
     * @param executor  subcommand logic
     */
    public void registerSubCommand(String namespace, String name, CommandExecutor executor) {
        dispatcher.registerSubCommand(namespace, name, executor);
    }

    /**
     * Registers a tab completer for a subcommand under the specified namespace.
     *
     * @param namespace    the command namespace
     * @param subcommand   subcommand label
     * @param tabCompleter tab completion logic
     */
    public void registerSubTabCompleter(String namespace, String subcommand, TabCompleter tabCompleter) {
        dispatcher.registerSubTabCompleter(namespace, subcommand, tabCompleter);
    }

    /**
     * Gets the dispatcher instance to assign as command executor and tab completer.
     *
     * @param namespace command namespace
     * @return command executor for Bukkit command registration
     */
    public CommandExecutor getDispatcher(String namespace) {
        return dispatcher.getDispatcher(namespace);
    }

    /**
     * Initializes player data in the database with default currency values.
     *
     * @param uuid The unique identifier of the player.
     */
    public void initPlayerData(UUID uuid) {
        db.insertCurrency(uuid.toString(), 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Adds a specified amount of a given type of coin to a player's account.
     *
     * @param uuid The unique identifier of the player.
     * @param coinType The type of coin to add (e.g., "gold", "silver").
     * @param amt The amount of coin to add.
     */
    public void addCoin(UUID uuid, String coinType, double amt) {
        updateCurrency(uuid, "+", coinType, amt);
    }

    /**
     * Checks if a player exists in the database.
     *
     * @param uuid The unique identifier of the player.
     * @return {@code true} if the player exists, {@code false} otherwise.
     * @throws RuntimeException If an error occurs while checking player existence.
     */
    public boolean checkIfPlayerExists(UUID uuid) {
        Object result = db.playerExists(uuid.toString());
        if (result instanceof Boolean) {
            return (Boolean) result;
        } else {
            plugin.getLogger().severe("Error checking if player exists in the database.");
            return false;
        }
    }

    /**
     * Records a transaction between two players in the database.
     *
     * @param playerUuidSender The unique identifier of the sender.
     * @param playerUuidReceiver The unique identifier of the receiver.
     * @param currencyType The type of currency involved in the transaction (e.g., "coin", "copper").
     * @param transactionType The type of transaction (e.g., "pay", "purchase").
     * @param amount The amount of currency involved.
     * @param notes Optional notes for the transaction.
     */
    public void createTransaction(UUID playerUuidSender, UUID playerUuidReceiver, String currencyType, String transactionType, double amount, String notes) {
        db.insertTransaction(playerUuidSender.toString(), playerUuidReceiver.toString(), currencyType, transactionType, amount, notes);
    }

    /**
     * Disconnects from the database.
     */
    public void disConnect() {
        db.disConnection();
    }

    /**
     * Retrieves the balance of a specified coin type for a player.
     *
     * @param uuid The unique identifier of the player.
     * @param coinType The type of coin to retrieve (e.g., "coin", "copper", "silver", "gold").
     * @return The balance of the specified coin type for the player.
     * @throws IllegalArgumentException If the coinType is invalid.
     * @throws RuntimeException If an error occurs while retrieving the balance.
     */
    public double getCoin(UUID uuid, String coinType) {
        if (!coinType.matches("coin|copper|silver|gold")) {
            plugin.getLogger().severe("Invalid coin type: " + coinType);
        }

        Object result = db.getCoin(uuid.toString(), coinType);
        if (result instanceof Double) {
            return (Double) result;
        } else {
            plugin.getLogger().severe("Error retrieving coin balance from the database.");
            return 0.0;
        }
    }

    /**
     * Deducts a specified amount of a given type of coin from a player's account.
     *
     * @param uuid The unique identifier of the player.
     * @param coinType The type of coin to deduct (e.g., "gold", "silver").
     * @param amt The amount of coin to deduct.
     */
    public void minusCoin(UUID uuid, String coinType, double amt) {
        updateCurrency(uuid, "-", coinType, amt);
    }

    /**
     * Updates the currency value for a player with a specific operation.
     *
     * @param uuid The unique identifier of the player.
     * @param operator The operation to perform ("+" to add, "-" to subtract).
     * @param coinType The type of coin to update.
     * @param amt The amount of coin to update.
     */
    private void updateCurrency(UUID uuid, String operator, String coinType, double amt) {
        db.updateCurrencyValue(uuid.toString(), operator, coinType, amt);
    }
}
