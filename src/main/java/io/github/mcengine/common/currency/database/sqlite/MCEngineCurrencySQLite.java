package io.github.mcengine.common.currency.database.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import io.github.mcengine.api.currency.database.MCEngineCurrencyApiDBInterface;
import org.bukkit.plugin.Plugin;

public class MCEngineCurrencySQLite implements MCEngineCurrencyApiDBInterface {
    private final Plugin plugin;
    private final String dbPath;
    private Connection connection;

    /**
     * Constructor to initialize the SQLite API with a database path.
     * @param plugin the plugin instance
     */
    public MCEngineCurrencySQLite(Plugin plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getConfig().getString("database.sqlite.path", "currency.db");
        connect();
        createTable();
    }

    /**
     * Establishes a connection to the SQLite database.
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
     * Creates the 'currency' and 'currency_transaction' tables in the database if they do not exist.
     * 
     * @param connection The active database connection used to execute SQL statements.
     * 
     * The 'currency' table stores player-specific balances for different currency types:
     * - player_uuid: Unique identifier for the player (primary key).
     * - coin, copper, silver, gold: Decimal values representing the player's balance for each currency type.
     * 
     * The 'currency_transaction' table records individual transactions involving currency:
     * - transaction_id: Unique identifier for each transaction (primary key, auto-incremented).
     * - player_uuid: Identifier linking the transaction to a player (foreign key referencing 'currency.player_uuid').
     * - currency_type: Specifies the type of currency involved ('coin', 'copper', 'silver', or 'gold').
     * - transaction_type: Indicates the type of transaction ('credit' or 'debit').
     * - amount: The amount of currency involved in the transaction.
     * - timestamp: Automatically records the time of the transaction.
     * - notes: Optional field for additional transaction details.
     */
    public void createTable() {
        // SQL for creating the 'currency' table
        String createCurrencyTableSQL = "CREATE TABLE IF NOT EXISTS currency ("
            + "player_uuid CHAR(36) PRIMARY KEY, "
            + "coin DECIMAL(10,2), "
            + "copper DECIMAL(10,2), "
            + "silver DECIMAL(10,2), "
            + "gold DECIMAL(10,2));";

        // SQL for creating the 'currency_transaction' table
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
            // Execute the SQL to create the 'currency' table
            stmt.executeUpdate(createCurrencyTableSQL);
            plugin.getLogger().info("Table 'currency' created successfully in SQLite database.");

            // Execute the SQL to create the 'currency_transaction' table
            stmt.executeUpdate(createTransactionTableSQL);
            plugin.getLogger().info("Table 'currency_transaction' created successfully in SQLite database.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    /**
     * Disconnects from the SQLite database.
     */
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
     * Retrieves the amount of a specified coin type for a player from the database.
     *
     * @param playerUuid the UUID of the player whose coin balance is to be retrieved
     * @param coinType   the type of coin to retrieve (e.g., "Copper", "Silver", "Gold")
     * @return the amount of the specified coin type the player has; returns 0.0 if no record is found or if an error occurs
     * @throws IllegalArgumentException if the coinType parameter is invalid or null
     * @implNote This method queries the database table "currency" for the coin balance.
     *           Ensure that the `connection` object is properly initialized and connected to the database.
     * @implSpec The `coinType` parameter should match a valid column name in the "currency" table.
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
        return 0.0; // Default value if no record is found
    }

    /**
     * Returns the current connection to the SQLite database.
     * @return the current {@link Connection}.
     */
    public Connection getDBConnection() {
        return connection;
    }

    /**
     * Inserts currency information for a player into the database.
     * @param playerUuid the unique identifier for the player.
     * @param coin the amount of coin currency.
     * @param copper the amount of copper currency.
     * @param silver the amount of silver currency.
     * @param gold the amount of gold currency.
     */
    public void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold) {
        String query = "INSERT INTO currency (player_uuid, coin, copper, silver, gold) VALUES (?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO NOTHING;";
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
     * 
     * @param playerUuidSender the unique identifier of the sender.
     * @param playerUuidReceiver the unique identifier of the receiver.
     * @param currencyType the type of currency involved in the transaction (coin, copper, silver, gold).
     * @param transactionType the type of transaction (pay, purchase).
     * @param amount the amount of currency involved in the transaction.
     * @param notes optional notes about the transaction.
     */
    public void insertTransaction(String playerUuidSender, String playerUuidReceiver, String currencyType, 
        String transactionType, double amount, String notes) {

        // Validate currencyType and transactionType
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
     * Checks if a player with the specified UUID exists in the database.
     *
     * @param uuid the UUID of the player to check
     * @return {@code true} if a player with the specified UUID exists, {@code false} otherwise
     * @throws SQLException if a database access error occurs
     *
     * <p>This method executes a SQL query to count the number of players with the given UUID
     * in the 'players' table. If the query result is greater than 0, the player exists.</p>
     *
     * <p>Note: Exceptions are caught and printed to the standard error stream. Ensure proper
     * exception handling and logging in production code.</p>
     */
    public boolean playerExists(String uuid) {
        String query = "SELECT COUNT(*) FROM currency WHERE player_uuid = ?"; // Corrected column name
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
     * @param playerUuid the unique identifier for the player.
     * @param operator the SQL operator to apply (+, -, etc.).
     * @param coinType the type of currency to update (coin, copper, silver, or gold).
     * @param amt the amount by which to update the currency.
     * @throws IllegalArgumentException if the coinType is invalid.
     */
    public void updateCurrencyValue(String playerUuid, String operator, String coinType, double amt) {
        // Validate coinType against allowed columns
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
