package io.github.mcengine.common.economy;

import java.util.UUID;

import io.github.mcengine.common.economy.database.IMCEngineEconomyDB;
import io.github.mcengine.common.economy.database.mysql.MCEngineEconomyMySQL;
import io.github.mcengine.common.economy.database.sqlite.MCEngineEconomySQLite;
import io.github.mcengine.common.economy.database.postgresql.MCEngineEconomyPostgreSQL;
import org.bukkit.plugin.Plugin;

/**
 * Provides a high-level Economy API wrapping the selected database backend.
 * <p>
 * Handles initialization, player balance queries, and balance mutations.
 * This class delegates directly to {@link IMCEngineEconomyDB} using player UUID strings.
 */
public class MCEngineEconomyCommon {

    /** Singleton instance of the Economy API. */
    private static MCEngineEconomyCommon instance;

    /** Bukkit plugin providing configuration and logger. */
    private final Plugin plugin;

    /** Selected backend implementing IMCEngineEconomyDB. */
    private final IMCEngineEconomyDB db;

    /**
     * Constructs the Economy manager and selects a database backend based on plugin config.
     *
     * @param plugin Bukkit plugin instance
     */
    public MCEngineEconomyCommon(Plugin plugin) {
        instance = this;
        this.plugin = plugin;

        String sqlType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();

        switch (sqlType) {
            case "mysql":
                this.db = new MCEngineEconomyMySQL(plugin);
                break;

            case "postgresql":
                this.db = new MCEngineEconomyPostgreSQL(plugin);
                break;

            case "sqlite":
                this.db = new MCEngineEconomySQLite(plugin);
                break;

            default:
                plugin.getLogger().severe("Unsupported SQL type: " + sqlType + " — falling back to SQLite");
                this.db = new MCEngineEconomySQLite(plugin); // fallback
                break;
        }
    }

    /**
     * Gets the global Economy API instance.
     *
     * @return singleton instance
     */
    public static MCEngineEconomyCommon getApi() {
        return instance;
    }

    /**
     * Gets the plugin instance associated with this API.
     *
     * @return plugin
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Initializes a player's currency entry with default values.
     *
     * @param uuid player UUID
     */
    public void initPlayerData(UUID uuid) {
        db.insertCurrency(uuid.toString(), 0, 0, 0, 0);
    }

    /**
     * Gets a player's total coin balance.
     *
     * @param uuid player UUID
     * @return total coin balance
     */
    public int getCoin(UUID uuid) {
        return db.getCoin(uuid.toString());
    }

    /**
     * Gets a player's specific coin type.
     *
     * @param uuid     player UUID
     * @param coinType coin type string ("coin", "copper", "silver", "gold")
     * @return amount of the coin type
     */
    public int getCoin(UUID uuid, String coinType) {
        return db.getCoin(uuid.toString(), coinType);
    }

    /**
     * Adds currency to a player's default coin.
     *
     * @param uuid player UUID
     * @param amt  amount to add
     */
    public void plusCoin(UUID uuid, int amt) {
        db.plusCoin(uuid.toString(), amt);
    }

    /**
     * Adds currency to a specific coin type.
     *
     * @param uuid     player UUID
     * @param coinType coin type string
     * @param amt      amount to add
     */
    public void plusCoin(UUID uuid, String coinType, int amt) {
        db.plusCoin(uuid.toString(), coinType, amt);
    }

    /**
     * Subtracts currency from a player's default coin.
     *
     * @param uuid player UUID
     * @param amt  amount to subtract
     */
    public void minusCoin(UUID uuid, int amt) {
        db.minusCoin(uuid.toString(), amt);
    }

    /**
     * Subtracts currency from a specific coin type.
     *
     * @param uuid     player UUID
     * @param coinType coin type string
     * @param amt      amount to subtract
     */
    public void minusCoin(UUID uuid, String coinType, int amt) {
        db.minusCoin(uuid.toString(), coinType, amt);
    }

    /**
     * Checks whether a player has an economy entry.
     *
     * @param uuid player UUID
     * @return true if exists in database
     */
    public boolean checkIfPlayerExists(UUID uuid) {
        return db.playerExists(uuid.toString());
    }
}
