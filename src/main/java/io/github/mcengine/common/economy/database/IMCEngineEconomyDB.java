package io.github.mcengine.common.economy.database;

/**
 * Contract for economy storage backends.
 * <p>
 * All methods use the player's UUID (string) as the identity key so storage
 * implementations do not depend on Bukkit Player objects.
 */
public interface IMCEngineEconomyDB {

    /**
     * Returns the player's total coin balance.
     *
     * @param playerUuid the player's UUID string (36-char format)
     * @return total coin balance (0 if missing or on error)
     */
    int getCoin(String playerUuid);

    /**
     * Returns the player's balance of a specific coin type.
     *
     * @param playerUuid the player's UUID string
     * @param coinType   coin column name: "coin", "copper", "silver", or "gold"
     * @return coin amount (0 if missing, invalid type, or on error)
     */
    int getCoin(String playerUuid, String coinType);

    /**
     * Inserts or upserts the player's currency row.
     * Implementations should create the row if missing or replace/update values if exists.
     *
     * @param playerUuid player's UUID string
     * @param coin       total coin balance
     * @param copper     copper balance
     * @param silver     silver balance
     * @param gold       gold balance
     */
    void insertCurrency(String playerUuid, double coin, double copper, double silver, double gold);

    /**
     * Returns whether a player row exists in storage.
     *
     * @param playerUuid the player's UUID string
     * @return true when a row exists for the UUID, false otherwise
     */
    boolean playerExists(String playerUuid);

    /**
     * Adds an amount to the player's default total coin column.
     *
     * @param playerUuid player's UUID string
     * @param amount     positive amount to add (method may accept negative but callers shouldn't)
     */
    void plusCoin(String playerUuid, int amount);

    /**
     * Adds an amount to a specific coin type column for the player.
     *
     * @param playerUuid player's UUID string
     * @param coinType   column name: "coin", "copper", "silver", or "gold"
     * @param amount     amount to add
     */
    void plusCoin(String playerUuid, String coinType, int amount);

    /**
     * Subtracts an amount from the player's default total coin column.
     *
     * @param playerUuid player's UUID string
     * @param amount     amount to subtract
     */
    void minusCoin(String playerUuid, int amount);

    /**
     * Subtracts an amount from a specific coin type column for the player.
     *
     * @param playerUuid player's UUID string
     * @param coinType   column name: "coin", "copper", "silver", or "gold"
     * @param amount     amount to subtract
     */
    void minusCoin(String playerUuid, String coinType, int amount);
}
