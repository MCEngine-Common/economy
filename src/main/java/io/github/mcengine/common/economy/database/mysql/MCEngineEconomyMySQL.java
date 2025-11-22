package io.github.mcengine.common.economy.database.mysql;

import io.github.mcengine.common.economy.database.IMCEngineEconomyDB;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * MySQL implementation of {@link IMCEngineEconomyDB}.
 * Persists player balances in a MySQL-compatible server.
 */
public class MCEngineEconomyMySQL implements IMCEngineEconomyDB {

    private final Plugin plugin;
    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final String dbSSL;
    private Connection connection;

    /**
     * Create MySQL economy manager.
     *
     * @param plugin plugin instance for config and logging
     */
    public MCEngineEconomyMySQL(Plugin plugin) {
        this.plugin = plugin;
        this.dbHost = plugin.getConfig().getString("database.mysql.host", "localhost");
        this.dbPort = plugin.getConfig().getString("database.mysql.port", "3306");
        this.dbName = plugin.getConfig().getString("database.mysql.name", "minecraft");
        this.dbUser = plugin.getConfig().getString("database.mysql.user", "root");
        this.dbPassword = plugin.getConfig().getString("database.mysql.password", "");
        this.dbSSL = plugin.getConfig().getString("database.mysql.ssl", "false");
        connect();
        createTable();
    }

    /**
     * Establishes a connection to the MySQL database.
     */
    public void connect() {
        String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?useSSL=" + dbSSL + "&serverTimezone=UTC";
        try {
            this.connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            plugin.getLogger().info("Connected to MySQL database");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to MySQL database: " + e.getMessage());
        }
    }

    /**
     * Create the economy table if missing.
     */
    public void createTable() {
        final String sql = "CREATE TABLE IF NOT EXISTS economy (" +
                "player_uuid VARCHAR(36) PRIMARY KEY NOT NULL," +
                "coin BIGINT DEFAULT 0 NOT NULL," +
                "copper BIGINT DEFAULT 0 NOT NULL," +
                "silver BIGINT DEFAULT 0 NOT NULL," +
                "gold BIGINT DEFAULT 0 NOT NULL" +
                ") ENGINE=InnoDB;";
        if (connection == null) return;
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create economy table (MySQL): " + e.getMessage());
        }
    }

    /**
     * Close MySQL connection if open.
     */
    public void disConnection() {
        if (connection == null) return;
        try {
            connection.close();
            connection = null;
            plugin.getLogger().info("MySQL connection closed.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close MySQL connection: " + e.getMessage());
        }
    }

    /**
     * Get total coin for UUID.
     *
     * @param playerUuid player's UUID string
     * @return total coin or 0 on error/not found
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
            plugin.getLogger().severe("Error getting coin (MySQL) for " + playerUuid + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Get specific coin type for UUID.
     *
     * @param playerUuid player's UUID string
     * @param coinType   one of "coin","copper","silver","gold"
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
            plugin.getLogger().severe("Error getting " + coinType + " (MySQL) for " + playerUuid + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Insert or update a player's row using MySQL ON DUPLICATE KEY.
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
                "ON DUPLICATE KEY UPDATE coin = VALUES(coin), copper = VALUES(copper), silver = VALUES(silver), gold = VALUES(gold)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, coin);
            ps.setLong(3, copper);
            ps.setLong(4, silver);
            ps.setLong(5, gold);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting/updating economy (MySQL) for " + playerUuid + ": " + e.getMessage());
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
     * Checks if a player row exists.
     *
     * @param playerUuid player's UUID string
     * @return true if exists
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
            plugin.getLogger().severe("Error checking player existence (MySQL) for " + uuid + ": " + e.getMessage());
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
     * MySQL implementation: delegates to typed transfer.
     */
    @Override
    public void sendCoin(String senderUuid, String receiverUuid, int amount) {
        sendCoin(senderUuid, receiverUuid, "coin", amount);
    }

    /**
     * {@inheritDoc}
     *
     * MySQL implementation: uses transaction + SELECT ... FOR UPDATE to perform atomic transfer.
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

            // lock sender row
            final String selectForUpdate = "SELECT " + coinType + " FROM economy WHERE player_uuid = ? FOR UPDATE";
            int senderBalance = 0;
            try (PreparedStatement ps = connection.prepareStatement(selectForUpdate)) {
                ps.setString(1, senderUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        senderBalance = rs.getInt(1);
                    } else {
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

            // ensure receiver exists (insert if missing)
            final String insertIfMissing = "INSERT IGNORE INTO economy(player_uuid, coin, copper, silver, gold) VALUES(?,0,0,0,0)";
            try (PreparedStatement ps = connection.prepareStatement(insertIfMissing)) {
                ps.setString(1, receiverUuid);
                ps.executeUpdate();
            }

            // lock receiver row
            try (PreparedStatement ps = connection.prepareStatement(selectForUpdate)) {
                ps.setString(1, receiverUuid);
                ps.executeQuery(); // lock it
            }

            // perform updates
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
            plugin.getLogger().severe("sendCoin (MySQL) failed: " + e.getMessage());
        }
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
            plugin.getLogger().severe("Failed to update " + coinType + " (MySQL) for " + playerUuid + ": " + e.getMessage());
        }
    }
}
