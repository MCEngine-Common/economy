package io.github.mcengine.common.economy.database;

import java.sql.Connection;

/**
 * Storage contract for the MCEngine Economy system.
 * <p>
 * Implementations encapsulate all persistence concerns for player balances and
 * transactions (e.g., MySQL, SQLite, PostgreSQL, MongoDB).
 */
public interface MCEngineEconomyApiDBInterface {

    /**
     * Establishes a connection to the underlying data store.
     * Implementations should be idempotent and safe to call multiple times.
     */
    void connect();

    /**
     * Creates (or migrates) the necessary tables/collections used by the Economy system.
     * This method should be safe to call even if tables already exist.
     */
    void createTable();

    /**
     * Closes and cleans up the active database connection.
     * Implementations should ignore repeated close attempts.
     */
    void disConnection();

    /**
     * Retrieves the balance of a specific coin type for a given player.
     *
     * @param playerUuid the UUID of the player
     * @param coinType   the logical coin bucket (e.g., "COIN", "COPPER", "SILVER", "GOLD")
     * @return the current balance for the requested coin bucket
     */
    double getCoin(String playerUuid, String coinType);

    /**
     * Returns the active database connection, if available.
     *
     * @return the active {@link Connection}; may be {@code null} if not connected
     */
    Connection getDBConnection();

    /**
     * Inserts an initial (or upserted) Economy row for the specified player.
     *
     * @param playerUuid the UUID of the player
     * @param coin       total coin balance (aggregate)
     * @param copper     copper coin balance
     * @param silver     silver coin balance
     * @param gold       gold coin balance
     */
    void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold);

    /**
     * Records a currency transaction between two parties.
     *
     * @param playerUuidSender   UUID of the sender (may be the same as receiver for system grants)
     * @param playerUuidReceiver UUID of the receiver
     * @param currencyType       the currency bucket affected
     * @param transactionType    semantic type (e.g., "SEND", "RECEIVE", "GRANT", "WITHDRAW")
     * @param amount             the amount transferred (positive values)
     * @param notes              free-form notes or metadata for auditing
     */
    void insertTransaction(
            String playerUuidSender,
            String playerUuidReceiver,
            String currencyType,
            String transactionType,
            double amount,
            String notes
    );

    /**
     * Checks if a player already has an Economy record in storage.
     *
     * @param uuid the player's UUID
     * @return {@code true} if a record exists; {@code false} otherwise
     */
    boolean playerExists(String uuid);

    /**
     * Mutates a player's balance using the provided arithmetic operator.
     * Implementations should validate supported operators and ensure atomicity.
     *
     * @param playerUuid the player's UUID
     * @param operator   the arithmetic operator (e.g., "+", "-")
     * @param coinType   the currency bucket to update
     * @param amt        the amount to apply (must be non-negative)
     */
    void updateCurrencyValue(String playerUuid, String operator, String coinType, double amt);
}
