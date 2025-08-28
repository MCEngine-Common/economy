package io.github.mcengine.common.economy;

import java.sql.Connection;
import java.util.UUID;

import io.github.mcengine.api.core.util.MCEngineCoreApiDispatcher;
import io.github.mcengine.api.economy.database.MCEngineEconomyApiDBInterface;
import io.github.mcengine.common.economy.database.mysql.MCEngineEconomyMySQL;
import io.github.mcengine.common.economy.database.sqlite.MCEngineEconomySQLite;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

/**
 * Provides common Economy operations for player balances and transactions.
 * <p>
 * Supports multiple storage backends (MySQL, SQLite), exposes helpers for
 * initializing player data, checking existence, mutating balances, and
 * recording transactions.
 */
public class MCEngineEconomyCommon {

    /** Singleton instance of the Economy common API. */
    private static MCEngineEconomyCommon instance;

    /** The Bukkit plugin that owns and configures this API. */
    private Plugin plugin;

    /**
     * Database contract implementation (e.g., MySQL/SQLite) that persists
     * balances and transactions for the Economy system.
     */
    private MCEngineEconomyApiDBInterface db;

    /** Internal command dispatcher for namespaced commands and tabs. */
    private final MCEngineCoreApiDispatcher dispatcher;

    /**
     * Constructs the Economy API instance and initializes the configured database.
     *
     * @param plugin  the Bukkit plugin instance
     */
    public MCEngineEconomyCommon(Plugin plugin) {
        instance = this;
        this.plugin = plugin;
        this.dispatcher = new MCEngineCoreApiDispatcher();

        String sqlType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        switch (sqlType.toLowerCase()) {
            case "mysql":
                this.db = new MCEngineEconomyMySQL(plugin);
                break;
            case "sqlite":
                this.db = new MCEngineEconomySQLite(plugin);
                break;
            default:
                plugin.getLogger().severe("Unsupported SQL type: " + sqlType);
        }
    }

    /**
     * Gets the global API singleton instance.
     *
     * @return the {@link MCEngineEconomyCommon} instance
     */
    public static MCEngineEconomyCommon getApi() {
        return instance;
        }

    /**
     * Gets the Bukkit plugin instance linked to this API.
     *
     * @return the plugin instance
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Retrieves the active database connection used by the plugin.
     * <p>Useful for custom queries or diagnostics.</p>
     *
     * @return the active {@link Connection}
     */
    public Connection getDBConnection() {
        return db.getDBConnection();
    }

    /**
     * Registers a command namespace (e.g., "economy") for this plugin's dispatcher.
     *
     * @param namespace unique namespace for commands
     */
    public void registerNamespace(String namespace) {
        dispatcher.registerNamespace(namespace);
    }

    /**
     * Binds a Bukkit command (like /economy) to the internal dispatcher.
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
     * @param uuid the unique identifier of the player
     */
    public void initPlayerData(UUID uuid) {
        db.insertCurrency(uuid.toString(), 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Adds a specified amount of a given type of coin to a player's account.
     *
     * @param uuid the unique identifier of the player
     * @param coinType the coin bucket (coin, copper, silver, gold)
     * @param amt the amount to add
     */
    public void addCoin(UUID uuid, String coinType, double amt) {
        updateCurrency(uuid, "+", coinType, amt);
    }

    /**
     * Checks if a player exists in the database.
     *
     * @param uuid the unique identifier of the player
     * @return {@code true} if the player exists; otherwise {@code false}
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
     * @param playerUuidSender   sender UUID
     * @param playerUuidReceiver receiver UUID
     * @param currencyType       coin bucket (coin|copper|silver|gold)
     * @param transactionType    transaction type (e.g., "pay", "purchase")
     * @param amount             amount transferred
     * @param notes              optional notes for auditing
     */
    public void createTransaction(UUID playerUuidSender, UUID playerUuidReceiver, String currencyType, String transactionType, double amount, String notes) {
        db.insertTransaction(playerUuidSender.toString(), playerUuidReceiver.toString(), currencyType, transactionType, amount, notes);
    }

    /** Disconnects from the database. */
    public void disConnect() {
        db.disConnection();
    }

    /**
     * Retrieves the balance of a specified coin type for a player.
     *
     * @param uuid the player UUID
     * @param coinType one of coin|copper|silver|gold
     * @return the balance value (0.0 if not found or error)
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
     * @param uuid the player UUID
     * @param coinType the coin bucket
     * @param amt amount to deduct
     */
    public void minusCoin(UUID uuid, String coinType, double amt) {
        updateCurrency(uuid, "-", coinType, amt);
    }

    /**
     * Updates the currency value for a player with a specific operation.
     *
     * @param uuid the player UUID
     * @param operator "+" to add, "-" to subtract
     * @param coinType the coin bucket to mutate
     * @param amt the amount to apply
     */
    private void updateCurrency(UUID uuid, String operator, String coinType, double amt) {
        db.updateCurrencyValue(uuid.toString(), operator, coinType, amt);
    }
}
