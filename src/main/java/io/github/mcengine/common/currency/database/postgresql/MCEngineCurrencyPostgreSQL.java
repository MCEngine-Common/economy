package io.github.mcengine.common.currency.database.postgresql;

import io.github.mcengine.api.currency.database.MCEngineCurrencyApiDBInterface;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.UUID;

/**
 * PostgreSQL implementation of the MCEngineCurrencyApiDBInterface.
 * Handles currency data storage and transactions using a PostgreSQL database.
 */
public class MCEngineCurrencyPostgreSQL implements MCEngineCurrencyApiDBInterface {

    private final Plugin plugin;
    private final String dbHost, dbPort, dbName, dbUser, dbPassword, dbSSL;
    private Connection connection;

    /**
     * Constructs a PostgreSQL currency manager.
     *
     * @param plugin the main Bukkit plugin instance used to fetch configuration and log information
     */
    public MCEngineCurrencyPostgreSQL(Plugin plugin) {
        this.plugin = plugin;
        this.dbHost = plugin.getConfig().getString("database.postgresql.host", "localhost");
        this.dbPort = plugin.getConfig().getString("database.postgresql.port", "5432");
        this.dbName = plugin.getConfig().getString("database.postgresql.name", "minecraft");
        this.dbUser = plugin.getConfig().getString("database.postgresql.user", "postgres");
        this.dbPassword = plugin.getConfig().getString("database.postgresql.password", "");
        this.dbSSL = plugin.getConfig().getString("database.postgresql.ssl", "false");
        connect();
    }

    /**
     * Establishes a connection to the PostgreSQL database using configuration values.
     */
    @Override
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
     * Creates the necessary tables (`currency` and `currency_transaction`) in the PostgreSQL database.
     */
    @Override
    public void createTable() {
        String createCurrencyTableSQL = "CREATE TABLE IF NOT EXISTS currency (" +
                "player_uuid UUID PRIMARY KEY," +
                "coin NUMERIC(10,2)," +
                "copper NUMERIC(10,2)," +
                "silver NUMERIC(10,2)," +
                "gold NUMERIC(10,2)" +
                ");";

        String createTransactionTableSQL = "CREATE TABLE IF NOT EXISTS currency_transaction (" +
                "transaction_id SERIAL PRIMARY KEY," +
                "player_uuid_sender UUID NOT NULL," +
                "player_uuid_receiver UUID NOT NULL," +
                "currency_type VARCHAR(10) NOT NULL CHECK (currency_type IN ('coin', 'copper', 'silver', 'gold'))," +
                "transaction_type VARCHAR(10) NOT NULL CHECK (transaction_type IN ('pay', 'purchase'))," +
                "amount NUMERIC(10,2) NOT NULL," +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "notes VARCHAR(255)," +
                "FOREIGN KEY (player_uuid_sender) REFERENCES currency(player_uuid)," +
                "FOREIGN KEY (player_uuid_receiver) REFERENCES currency(player_uuid)" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createCurrencyTableSQL);
            plugin.getLogger().info("Table 'currency' created in PostgreSQL.");

            stmt.executeUpdate(createTransactionTableSQL);
            plugin.getLogger().info("Table 'currency_transaction' created in PostgreSQL.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    /**
     * Closes the active database connection, if open.
     */
    @Override
    public void disConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from PostgreSQL database.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error while disconnecting from PostgreSQL: " + e.getMessage());
        }
    }

    /**
     * Retrieves a specific coin type balance for the given player.
     *
     * @param playerUuid UUID of the player
     * @param coinType   Column name (coin, copper, silver, gold)
     * @return the balance value, or 0.0 if not found
     */
    @Override
    public double getCoin(String playerUuid, String coinType) {
        String query = "SELECT " + coinType + " FROM currency WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setObject(1, UUID.fromString(playerUuid));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving " + coinType + " for player: " + playerUuid + " - " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Returns the current database connection object.
     *
     * @return the JDBC connection instance
     */
    @Override
    public Connection getDBConnection() {
        return connection;
    }

    /**
     * Inserts a currency record for a player if it does not already exist.
     *
     * @param playerUuid the player's UUID
     * @param coin       general coin value
     * @param copper     copper coin value
     * @param silver     silver coin value
     * @param gold       gold coin value
     */
    @Override
    public void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold) {
        String query = "INSERT INTO currency (player_uuid, coin, copper, silver, gold) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO NOTHING;";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setObject(1, UUID.fromString(playerUuid));
            pstmt.setDouble(2, coin);
            pstmt.setDouble(3, copper);
            pstmt.setDouble(4, silver);
            pstmt.setDouble(5, gold);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting currency for player: " + playerUuid + " - " + e.getMessage());
        }
    }

    /**
     * Inserts a new currency transaction between two players.
     *
     * @param playerUuidSender   sender UUID
     * @param playerUuidReceiver receiver UUID
     * @param currencyType       type of currency (coin, copper, silver, gold)
     * @param transactionType    transaction type (pay, purchase)
     * @param amount             amount of currency
     * @param notes              optional notes for the transaction
     */
    @Override
    public void insertTransaction(String playerUuidSender, String playerUuidReceiver, String currencyType,
                                  String transactionType, double amount, String notes) {
        if (!currencyType.matches("coin|copper|silver|gold") || !transactionType.matches("pay|purchase")) {
            plugin.getLogger().severe("Invalid currency or transaction type.");
            return;
        }

        String query = "INSERT INTO currency_transaction (player_uuid_sender, player_uuid_receiver, currency_type, transaction_type, amount, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setObject(1, UUID.fromString(playerUuidSender));
            pstmt.setObject(2, UUID.fromString(playerUuidReceiver));
            pstmt.setString(3, currencyType);
            pstmt.setString(4, transactionType);
            pstmt.setDouble(5, amount);
            pstmt.setString(6, notes);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting transaction: " + e.getMessage());
        }
    }

    /**
     * Checks if a player exists in the `currency` table.
     *
     * @param uuid the player's UUID
     * @return true if the player record exists, false otherwise
     */
    @Override
    public boolean playerExists(String uuid) {
        String query = "SELECT COUNT(*) FROM currency WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, UUID.fromString(uuid));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking player existence: " + e.getMessage());
        }
        return false;
    }

    /**
     * Updates a player's coin balance using an arithmetic operator.
     *
     * @param playerUuid UUID of the player
     * @param operator   '+' to increase, '-' to decrease
     * @param coinType   which coin field to update
     * @param amt        amount to modify
     */
    @Override
    public void updateCurrencyValue(String playerUuid, String operator, String coinType, double amt) {
        if (!coinType.matches("coin|copper|silver|gold") || !operator.matches("[+-]")) {
            plugin.getLogger().severe("Invalid operator or coin type.");
            return;
        }

        String query = "UPDATE currency SET " + coinType + " = " + coinType + " " + operator + " ? WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setDouble(1, amt);
            pstmt.setObject(2, UUID.fromString(playerUuid));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating currency for player: " + playerUuid + " - " + e.getMessage());
        }
    }
}
