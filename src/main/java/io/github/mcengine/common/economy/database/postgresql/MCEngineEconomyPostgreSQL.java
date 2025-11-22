package io.github.mcengine.common.economy.database.postgresql;

import io.github.mcengine.common.economy.database.IMCEngineEconomyDB;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * PostgreSQL implementation of {@link IMCEngineEconomyDB}.
 * Persists player balances in PostgreSQL.
 */
public class MCEngineEconomyPostgreSQL implements IMCEngineEconomyDB {

    private final Plugin plugin;
    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final String dbSSL;
    private Connection connection;

    /**
     * Create a PostgreSQL economy backend.
     *
     * @param plugin plugin instance
     */
    public MCEngineEconomyPostgreSQL(Plugin plugin) {
        this.plugin = plugin;
        this.dbHost = plugin.getConfig().getString("database.postgresql.host", "localhost");
        this.dbPort = plugin.getConfig().getString("database.postgresql.port", "5432");
        this.dbName = plugin.getConfig().getString("database.postgresql.name", "minecraft");
        this.dbUser = plugin.getConfig().getString("database.postgresql.user", "postgres");
        this.dbPassword = plugin.getConfig().getString("database.postgresql.password", "");
        this.dbSSL = plugin.getConfig().getString("database.postgresql.ssl", "false");
        connect();
        createTable();
    }

    /**
     * Establishes a connection to PostgreSQL.
     */
    public void connect() {
        String dbUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName + "?ssl=" + dbSSL;
        try {
            this.connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            plugin.getLogger().info("Connected to PostgreSQL database");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to PostgreSQL database: " + e.getMessage());
        }
    }

    /**
     * Creates the economy table if it does not exist.
     */
    public void createTable() {
        final String sql = "CREATE TABLE IF NOT EXISTS economy (" +
                "player_uuid VARCHAR(36) PRIMARY KEY NOT NULL," +
                "coin BIGINT DEFAULT 0 NOT NULL," +
                "copper BIGINT DEFAULT 0 NOT NULL," +
                "silver BIGINT DEFAULT 0 NOT NULL," +
                "gold BIGINT DEFAULT 0 NOT NULL" +
                ");";
        if (connection == null) return;
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create economy table (PostgreSQL): " + e.getMessage());
        }
    }

    /**
     * Disconnects from PostgreSQL.
     */
    public void disConnection() {
        if (connection == null) return;
        try {
            connection.close();
            connection = null;
            plugin.getLogger().info("PostgreSQL connection closed.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close PostgreSQL connection: " + e.getMessage());
        }
    }

    /**
     * Get total coin for UUID.
     *
     * @param playerUuid player's UUID string
     * @return total coin or 0
     */
    @Override
    public int getCoin(String playerUuid) {
        if (connection == null) return 0;
        final String sql = "SELECT coin FROM economy WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("coin");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting coin (Postgres) for " + playerUuid + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Get specific coin type for UUID.
     *
     * @param playerUuid player's UUID string
     * @param coinType   "coin","copper","silver" or "gold"
     * @return requested balance or 0
     */
    @Override
    public int getCoin(String playerUuid, String coinType) {
        if (connection == null) return 0;
        if (coinType == null || !coinType.matches("coin|copper|silver|gold")) {
            plugin.getLogger().severe("Invalid coin type: " + coinType);
            return 0;
        }
        final String sql = "SELECT " + coinType + " FROM economy WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting " + coinType + " (Postgres) for " + playerUuid + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Insert or update the player's row using Postgres ON CONFLICT.
     *
     * @param playerUuid player UUID string
     * @param coin       total coin
     * @param copper     copper
     * @param silver     silver
     * @param gold       gold
     */
    public void insertCoin(String playerUuid, int coin, int copper, int silver, int gold) {
        if (connection == null) return;
        final String sql = "INSERT INTO economy(player_uuid, coin, copper, silver, gold) VALUES(?,?,?,?,?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET coin = EXCLUDED.coin, copper = EXCLUDED.copper, silver = EXCLUDED.silver, gold = EXCLUDED.gold";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, coin);
            ps.setLong(3, copper);
            ps.setLong(4, silver);
            ps.setLong(5, gold);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting/updating economy (Postgres) for " + playerUuid + ": " + e.getMessage());
        }
    }

    /**
     * Insert or update a player's currency row. Implements interface contract.
     *
     * @param playerUuid UUID string
     * @param coin       total coin (double in interface; stored as integer)
     * @param copper     copper
     * @param silver     silver
     * @param gold       gold
     */
    @Override
    public void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold) {
        insertCoin(playerUuid, (int) coin, (int) copper, (int) silver, (int) gold);
    }

    /**
     * Checks whether a player exists.
     *
     * @param playerUuid player UUID string
     * @return true if the player has a row
     */
    @Override
    public boolean playerExists(String playerUuid) {
        if (connection == null) return false;
        final String sql = "SELECT 1 FROM economy WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking player existence (Postgres) for " + uuid + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Adds to the player's default total coin.
     *
     * @param playerUuid player's UUID string
     * @param amount     amount to add
     */
    @Override
    public void plusCoin(String playerUuid, int amount) {
        updateCoinValue(playerUuid, "+", "coin", amount);
    }

    /**
     * Adds to a player's specific coin type.
     *
     * @param playerUuid player's UUID string
     * @param coinType   coin type to add to
     * @param amount     amount to add
     */
    @Override
    public void plusCoin(String playerUuid, String coinType, int amount) {
        updateCoinValue(playerUuid, "+", coinType == null ? null : coinType.toLowerCase(), amount);
    }

    /**
     * Subtracts from the player's default total coin.
     *
     * @param playerUuid player's UUID string
     * @param amount     amount to subtract
     */
    @Override
    public void minusCoin(String playerUuid, int amount) {
        updateCoinValue(playerUuid, "-", "coin", amount);
    }

    /**
     * Subtracts from the player's specific coin type.
     *
     * @param playerUuid player's UUID string
     * @param coinType   coin type to subtract from
     * @param amount     amount to subtract
     */
    @Override
    public void minusCoin(String playerUuid, String coinType, int amount) {
        updateCoinValue(playerUuid, "-", coinType == null ? null : coinType.toLowerCase(), amount);
    }

    /**
     * Updates a numeric column by adding or subtracting an amount.
     *
     * @param playerUuid player uuid string
     * @param operator   "+" or "-"
     * @param coinType   column name
     * @param amt        amount
     */
    public void updateCoinValue(String playerUuid, String operator, String coinType, int amt) {
        if (connection == null) return;
        if (coinType == null || !coinType.matches("coin|copper|silver|gold")) {
            plugin.getLogger().severe("Invalid coin type: " + coinType);
            return;
        }
        if (!"+".equals(operator) && !"-".equals(operator)) {
            plugin.getLogger().severe("Invalid operator: " + operator);
            return;
        }
        final String sql = "UPDATE economy SET " + coinType + " = " + coinType + " " + operator + " ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, amt);
            ps.setString(2, playerUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update " + coinType + " (Postgres) for " + playerUuid + ": " + e.getMessage());
        }
    }
}
