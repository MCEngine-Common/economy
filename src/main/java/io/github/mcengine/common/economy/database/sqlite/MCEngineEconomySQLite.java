package io.github.mcengine.common.economy.database.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.github.mcengine.common.economy.database.MCEngineEconomyApiDBInterface;
import org.bukkit.plugin.Plugin;

/**
 * SQLite implementation of {@link MCEngineEconomyApiDBInterface}.
 * Stores Economy balances and transactions in a local SQLite database.
 */
public class MCEngineEconomySQLite implements MCEngineEconomyApiDBInterface {

    /** Owning plugin for configuration and logging. */
    private final Plugin plugin;

    /** Relative/absolute DB path under the plugin's data folder. */
    private final String dbPath;

    /** Active JDBC connection. */
    private Connection connection;

    /**
     * Initializes the SQLite storage with the configured database path.
     *
     * @param plugin the plugin instance
     */
    public MCEngineEconomySQLite(Plugin plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getConfig().getString("database.sqlite.path", "economy.db");
        connect();
        createTable();
    }

    /** Establishes a connection to the SQLite database. */
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
     * Creates the 'currency' and 'currency_transaction' tables if they do not exist.
     * <p>
     * Tables:
     * <ul>
     *   <li><b>currency</b> — per-player balances</li>
     *   <li><b>currency_transaction</b> — transaction history</li>
     * </ul>
     */
    public void createTable() {
        String createCurrencyTableSQL = "CREATE TABLE IF NOT EXISTS currency ("
            + "player_uuid CHAR(36) PRIMARY KEY, "
            + "coin DECIMAL(10,2), "
            + "copper DECIMAL(10,2), "
            + "silver DECIMAL(10,2), "
            + "gold DECIMAL(10,2));";

        String createTransactionTableSQL = "CREATE TABLE IF NOT EXISTS currency_transaction ("
            + "transaction_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "player_uuid_sender CHAR(36) NOT NULL, "
            + "player_uuid_receiver CHAR(36) NOT NULL, "
            + "currency_type TEXT CHECK(currency_type IN ('coin', 'copper', 'silver', 'gold')) NOT NULL, "
            + "transaction_type TEXT CHECK(transaction_type IN ('pay', 'purchase')) NOT NULL, "
            + "amount DECIMAL(10,2) NOT NULL, "
            + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "notes VARCHAR(255), "
            + "FOREIGN KEY (player_uuid_sender) REFERENCES currency(player_uuid), "
            + "FOREIGN KEY (player_uuid_receiver) REFERENCES currency(player_uuid));";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createCurrencyTableSQL);
            plugin.getLogger().info("Table 'currency' created successfully in SQLite database.");

            stmt.executeUpdate(createTransactionTableSQL);
            plugin.getLogger().info("Table 'currency_transaction' created successfully in SQLite database.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    /** Disconnects from the SQLite database. */
    public void disConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from SQLite database.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to disconnect from SQLite database: " + e.getMessage());
        }
    }

    /**
     * Retrieves the amount of a specified coin type for a player.
     *
     * @param playerUuid player UUID
     * @param coinType   column name: coin|copper|silver|gold
     * @return the balance value (0.0 if not found/error)
     */
    public double getCoin(String playerUuid, String coinType) {
        String query = "SELECT " + coinType + " FROM currency WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, playerUuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving " + coinType + " for player uuid: " + playerUuid + " - " + e.getMessage());
        }
        return 0.0;
    }

    /** @return the current {@link Connection} to SQLite */
    public Connection getDBConnection() {
        return connection;
    }

    /**
     * Inserts currency information for a player (no-op if player exists).
     */
    public void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold) {
        String query = "INSERT INTO currency (player_uuid, coin, copper, silver, gold) VALUES (?, ?, ?, ?, ?) " +
                       "ON CONFLICT(player_uuid) DO NOTHING;";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, playerUuid);
            pstmt.setDouble(2, coin);
            pstmt.setDouble(3, copper);
            pstmt.setDouble(4, silver);
            pstmt.setDouble(5, gold);
            pstmt.executeUpdate();
            plugin.getLogger().info("Currency information added for player uuid: " + playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting currency for player uuid: " + playerUuid + " - " + e.getMessage());
        }
    }

    /**
     * Inserts a transaction record into the currency_transaction table.
     */
    public void insertTransaction(String playerUuidSender, String playerUuidReceiver, String currencyType,
                                  String transactionType, double amount, String notes) {

        if (!currencyType.matches("coin|copper|silver|gold")) {
            plugin.getLogger().severe("Invalid currency type: " + currencyType);
        }
        if (!transactionType.matches("pay|purchase")) {
            plugin.getLogger().severe("Invalid transaction type: " + transactionType);
        }

        String query = "INSERT INTO currency_transaction (player_uuid_sender, player_uuid_receiver, currency_type, "
        + "transaction_type, amount, notes) VALUES (?, ?, ?, ?, ?, ?);";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, playerUuidSender);
            pstmt.setString(2, playerUuidReceiver);
            pstmt.setString(3, currencyType);
            pstmt.setString(4, transactionType);
            pstmt.setDouble(5, amount);
            pstmt.setString(6, notes);

            pstmt.executeUpdate();
            plugin.getLogger().info("Transaction successfully recorded between "
            + playerUuidSender + " and " + playerUuidReceiver);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting transaction: " + e.getMessage());
        }
    }

    /**
     * Checks if a player with the specified UUID exists in the currency table.
     *
     * @param uuid the UUID of the player to check
     * @return {@code true} if a player with the specified UUID exists; {@code false} otherwise
     */
    public boolean playerExists(String uuid) {
        String query = "SELECT COUNT(*) FROM currency WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Updates a specific type of currency for a player.
     *
     * @param playerUuid player UUID
     * @param operator   '+' or '-'
     * @param coinType   coin|copper|silver|gold
     * @param amt        amount to apply
     */
    public void updateCurrencyValue(String playerUuid, String operator, String coinType, double amt) {
        if (!coinType.matches("coin|copper|silver|gold")) {
            plugin.getLogger().severe("Invalid coin type: " + coinType);
        }

        String query = "UPDATE currency SET " + coinType + " = " + coinType + " " + operator
        + " ? WHERE player_uuid = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setDouble(1, amt);
            pstmt.setString(2, playerUuid);
            pstmt.executeUpdate();
            plugin.getLogger().info("Updated " + coinType + " for player uuid: " + playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating " + coinType + " for player uuid: " + playerUuid + " - " + e.getMessage());
        }
    }
}
