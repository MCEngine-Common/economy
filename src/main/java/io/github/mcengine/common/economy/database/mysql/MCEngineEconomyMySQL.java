package io.github.mcengine.common.economy.database.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.github.mcengine.common.economy.database.MCEngineEconomyApiDBInterface;
import org.bukkit.plugin.Plugin;

/**
 * MySQL implementation of {@link MCEngineEconomyApiDBInterface}.
 * Persists player balances and transaction history for the Economy system.
 */
public class MCEngineEconomyMySQL implements MCEngineEconomyApiDBInterface {

    /** Owning plugin for configuration and logging. */
    private final Plugin plugin;

    /** Database host (e.g., "localhost"). */
    private final String dbHost;

    /** Database port (e.g., "3306"). */
    private final String dbPort;

    /** Database/schema name. */
    private final String dbName;

    /** Database username. */
    private final String dbUser;

    /** Database password. */
    private final String dbPassword;

    /** Whether to use SSL when connecting. */
    private final String dbSSL;

    /** Active JDBC connection. */
    private Connection connection;

    /**
     * Constructs an instance of the MySQL Economy storage.
     *
     * @param plugin the main plugin instance, used to access configuration
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
    }

    /** Establishes a connection to the MySQL database. */
    @Override
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
     * Creates the required tables in the database if they do not already exist.
     * <p>
     * Tables:
     * <ul>
     *   <li><b>currency</b> — per-player balances for coin/copper/silver/gold</li>
     *   <li><b>currency_transaction</b> — transaction history between players</li>
     * </ul>
     */
    @Override
    public void createTable() {
        String createCurrencyTableSQL = "CREATE TABLE IF NOT EXISTS currency ("
            + "player_uuid CHAR(36) PRIMARY KEY, "
            + "coin DECIMAL(10,2), "
            + "copper DECIMAL(10,2), "
            + "silver DECIMAL(10,2), "
            + "gold DECIMAL(10,2));";

        String createTransactionTableSQL = "CREATE TABLE IF NOT EXISTS currency_transaction ("
            + "transaction_id INT AUTO_INCREMENT PRIMARY KEY, "
            + "player_uuid_sender CHAR(36) NOT NULL, "
            + "player_uuid_receiver CHAR(36) NOT NULL, "
            + "currency_type ENUM('coin', 'copper', 'silver', 'gold') NOT NULL, "
            + "transaction_type ENUM('pay', 'purchase') NOT NULL, "
            + "amount DECIMAL(10,2) NOT NULL, "
            + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "notes VARCHAR(255), "
            + "FOREIGN KEY (player_uuid_sender) REFERENCES currency(player_uuid), "
            + "FOREIGN KEY (player_uuid_receiver) REFERENCES currency(player_uuid));";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createCurrencyTableSQL);
            plugin.getLogger().info("Table 'currency' created successfully in MySQL database.");

            stmt.executeUpdate(createTransactionTableSQL);
            plugin.getLogger().info("Table 'currency_transaction' created successfully in MySQL database.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    /** Closes the current database connection. */
    @Override
    public void disConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from MySQL database.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error while disconnecting from MySQL database: " + e.getMessage());
        }
    }

    /**
     * Executes a non-returning command (DDL/DML) on MySQL.
     *
     * @param query raw SQL to execute
     */
    @Override
    public void executeQuery(String query) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(query);
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL executeQuery error: " + e.getMessage());
        }
    }

    /**
     * Executes a SQL SELECT expected to return a single value (one row, one column).
     * Supported types: String, Integer, Long, Double, Boolean.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getValue(String query, Class<T> type) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                Object value;
                if (type == String.class) value = rs.getString(1);
                else if (type == Integer.class) value = rs.getInt(1);
                else if (type == Long.class) value = rs.getLong(1);
                else if (type == Double.class) value = rs.getDouble(1);
                else if (type == Boolean.class) value = rs.getBoolean(1);
                else throw new IllegalArgumentException("Unsupported return type: " + type);
                return (T) value;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL getValue error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves the amount of a specified coin type for a player from the database.
     *
     * @param playerUuid the player UUID
     * @param coinType   column name: coin|copper|silver|gold
     * @return the balance value; 0.0 if not found or error
     */
    @Override
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

    /**
     * Inserts or updates a player's currency values.
     *
     * @param playerUuid player UUID
     * @param coin       coin balance
     * @param copper     copper balance
     * @param silver     silver balance
     * @param gold       gold balance
     */
    @Override
    public void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold) {
        String query = "INSERT INTO currency (player_uuid, coin, copper, silver, gold) VALUES (?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE player_uuid = player_uuid;";
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
     * @param playerUuidSender   sender UUID
     * @param playerUuidReceiver receiver UUID
     * @param currencyType       coin bucket (coin|copper|silver|gold)
     * @param transactionType    type (pay|purchase)
     * @param amount             amount
     * @param notes              optional notes
     */
    @Override
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
     * @param uuid player UUID
     * @return {@code true} if exists; otherwise {@code false}
     */
    @Override
    public boolean playerExists(String uuid) {
        String query = "SELECT COUNT(*) FROM currency WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking player existence: " + e.getMessage());
        }
        return false;
    }

    /**
     * Updates a specific currency value for a player.
     *
     * @param playerUuid player UUID
     * @param operator   '+' or '-'
     * @param coinType   coin|copper|silver|gold
     * @param amt        amount to apply
     */
    @Override
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
