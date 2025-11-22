package io.github.mcengine.common.economy.database.sqlite;

import io.github.mcengine.common.economy.database.IMCEngineEconomyDB;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * SQLite implementation of {@link IMCEngineEconomyDB}.
 * Stores per-player balances in a local SQLite database file.
 */
public class MCEngineEconomySQLite implements IMCEngineEconomyDB {

    private final Plugin plugin;
    private final String dbPath;
    private Connection connection;

    /**
     * Construct the SQLite storage handler.
     *
     * @param plugin owning plugin (used for config, data folder and logging)
     */
    public MCEngineEconomySQLite(Plugin plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getConfig().getString("database.sqlite.path", "economy.db");
        connect();
        createTable();
    }

    /**
     * Establishes a JDBC connection to the SQLite database file.
     */
    public void connect() {
        try {
            String fullPath = plugin.getDataFolder().getAbsolutePath() + "/" + dbPath;
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + fullPath);
            plugin.getLogger().info("Connected to SQLite database at: " + fullPath);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite database: " + e.getMessage());
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
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create economy table (SQLite): " + e.getMessage());
        }
    }

    /**
     * Disconnects from the SQLite database.
     */
    public void disConnection() {
        if (connection == null) return;
        try {
            connection.close();
            connection = null;
            plugin.getLogger().info("SQLite connection closed.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }

    /**
     * Returns the player's total coin balance by UUID.
     *
     * @param playerUuid player's UUID string
     * @return total coin balance, or 0 if not found or on error
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
            plugin.getLogger().severe("Error getting coin (SQLite) for " + playerUuid + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns the player's specific coin-type balance by UUID.
     *
     * @param playerUuid player's UUID string
     * @param coinType   "coin", "copper", "silver" or "gold"
     * @return requested balance, or 0 if not found/invalid/error
     */
    @Override
    public int getCoin(String playerUuid, String coinType) {
        if (connection == null) return 0;
        if (coinType == null || !coinType.matches("coin|copper|silver|gold")) {
            plugin.getLogger().severe("Invalid coin type requested: " + coinType);
            return 0;
        }
        final String sql = "SELECT " + coinType + " FROM economy WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting " + coinType + " (SQLite) for " + playerUuid + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Inserts or upserts a player's economy row using SQLite upsert semantics.
     *
     * @param playerUuid player UUID
     * @param coin       total coin
     * @param copper     copper coin
     * @param silver     silver coin
     * @param gold       gold coin
     */
    public void insertCoin(String playerUuid, int coin, int copper, int silver, int gold) {
        if (connection == null) return;
        final String sql = "INSERT INTO economy(player_uuid, coin, copper, silver, gold) " +
                "VALUES(?,?,?,?,?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET coin = excluded.coin, copper = excluded.copper, silver = excluded.silver, gold = excluded.gold;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, coin);
            ps.setLong(3, copper);
            ps.setLong(4, silver);
            ps.setLong(5, gold);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting/updating economy (SQLite) for " + playerUuid + ": " + e.getMessage());
        }
    }

    /**
     * Inserts or updates a player's currency row. Implements interface contract.
     *
     * @param playerUuid UUID string
     * @param coin       total coin (double in interface; stored as integer)
     * @param copper     copper (double in interface; stored as integer)
     * @param silver     silver (double in interface; stored as integer)
     * @param gold       gold (double in interface; stored as integer)
     */
    @Override
    public void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold) {
        insertCoin(playerUuid, (int) coin, (int) copper, (int) silver, (int) gold);
    }

    /**
     * Checks whether a player exists in the table.
     *
     * @param playerUuid player UUID string
     * @return true if a row exists for the player; false otherwise
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
            plugin.getLogger().severe("Error checking player existence (SQLite) for " + uuid + ": " + e.getMessage());
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
     * {@inheritDoc}
     *
     * SQLite implementation: uses a transaction to atomically move default "coin".
     */
    @Override
    public void sendCoin(String senderUuid, String receiverUuid, int amount) {
        sendCoin(senderUuid, receiverUuid, "coin", amount);
    }

    /**
     * {@inheritDoc}
     *
     * SQLite implementation: performs an atomic transfer of the specified coin column.
     */
    @Override
    public void sendCoin(String senderUuid, String receiverUuid, String coinType, int amount) {
        if (connection == null) return;
        if (amount <= 0) {
            plugin.getLogger().severe("sendCoin: amount must be positive.");
            return;
        }
        if (coinType == null || !coinType.matches("coin|copper|silver|gold")) {
            plugin.getLogger().severe("sendCoin: invalid coin type: " + coinType);
            return;
        }

        try {
            connection.setAutoCommit(false);

            // Ensure sender exists and lock by selecting within transaction (SQLite locks DB page)
            final String selectSql = "SELECT " + coinType + " FROM economy WHERE player_uuid = ? LIMIT 1";
            int senderBalance = 0;
            try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                ps.setString(1, senderUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        senderBalance = rs.getInt(1);
                    } else {
                        // sender has no row -> cannot send
                        plugin.getLogger().severe("sendCoin: sender does not have an economy row: " + senderUuid);
                        connection.rollback();
                        connection.setAutoCommit(true);
                        return;
                    }
                }
            }

            if (senderBalance < amount) {
                plugin.getLogger().severe("sendCoin: insufficient funds for " + senderUuid + " (" + senderBalance + " < " + amount + ")"); 
                connection.rollback();
                connection.setAutoCommit(true);
                return;
            }

            // Ensure receiver exists; insert default row if missing
            final String insertIfMissing = "INSERT OR IGNORE INTO economy(player_uuid, coin, copper, silver, gold) VALUES(?,0,0,0,0)";
            try (PreparedStatement ps = connection.prepareStatement(insertIfMissing)) {
                ps.setString(1, receiverUuid);
                ps.executeUpdate();
            }

            // Perform updates
            final String decSql = "UPDATE economy SET " + coinType + " = " + coinType + " - ? WHERE player_uuid = ?";
            final String incSql = "UPDATE economy SET " + coinType + " = " + coinType + " + ? WHERE player_uuid = ?";

            try (PreparedStatement dec = connection.prepareStatement(decSql);
                 PreparedStatement inc = connection.prepareStatement(incSql)) {

                dec.setInt(1, amount);
                dec.setString(2, senderUuid);
                dec.executeUpdate();

                inc.setInt(1, amount);
                inc.setString(2, receiverUuid);
                inc.executeUpdate();
            }

            connection.commit();
            connection.setAutoCommit(true);
            plugin.getLogger().info("sendCoin: transferred " + amount + " " + coinType + " from " + senderUuid + " to " + receiverUuid);
        } catch (SQLException e) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ex) { /* ignore */ }
            plugin.getLogger().severe("sendCoin (SQLite) failed: " + e.getMessage());
        }
    }

    /**
     * Updates a numeric column by adding or subtracting an amount.
     *
     * @param playerUuid player UUID string
     * @param operator   "+" or "-"
     * @param coinType   one of "coin","copper","silver","gold"
     * @param amt        amount to apply
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
            plugin.getLogger().severe("Failed to update " + coinType + " (SQLite) for " + playerUuid + ": " + e.getMessage());
        }
    }
}
