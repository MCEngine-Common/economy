package io.github.mcengine.common.economy.database;

/**
 * Storage contract for the MCEngine Economy system.
 * <p>
 * Implementations encapsulate all persistence concerns for player balances and
 * transactions (e.g., MySQL, SQLite, PostgreSQL, MongoDB).
 * <br>
 * To support both SQL and NoSQL backends uniformly, this interface exposes:
 * <ul>
 *   <li>{@link #executeQuery(String)} for non-return operations (DDL/DML or equivalent)</li>
 *   <li>{@link #getValue(String, Class)} for single-cell/field lookups</li>
 * </ul>
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
     * Executes a non-returning operation on the underlying data store.
     * <p>
     * For SQL engines, this may be raw SQL (e.g., CREATE/INSERT/UPDATE/DELETE).
     * For NoSQL engines (e.g., MongoDB), this may be a JSON command or DSL string
     * that the implementation understands.
     *
     * @param query the backend-specific command to execute
     */
    void executeQuery(String query);

    /**
     * Executes a query and returns a single value of the requested type.
     * <p>
     * For SQL engines, this is typically a {@code SELECT ...} returning one column from one row.
     * For NoSQL engines, this can be a JSON/DSL command that specifies collection, filter, and field.
     *
     * @param query backend-specific query string (SQL or JSON/DSL)
     * @param type  the desired Java type of the resulting value (e.g., {@code String.class})
     * @param <T>   generic type parameter
     * @return the value if found; otherwise {@code null}
     * @throws IllegalArgumentException if {@code type} is unsupported by the implementation
     */
    <T> T getValue(String query, Class<T> type);

    /**
     * Retrieves the balance of a specific coin type for a given player.
     *
     * @param playerUuid the UUID of the player
     * @param coinType   the logical coin bucket (e.g., "coin", "copper", "silver", "gold")
     * @return the current balance for the requested coin bucket
     */
    double getCoin(String playerUuid, String coinType);

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
