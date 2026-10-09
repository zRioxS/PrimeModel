package kr.rioxs.primemodel.api.platform;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a player in the underlying platform.
 * <p>
 * This interface combines the properties of a living entity and an offline player,
 * providing access to player-specific data like name and online status.
 * </p>
 *
 * @since 2.0.0
 */
public interface PlatformPlayer extends PlatformLivingEntity, PlatformOfflinePlayer {

    /**
     * Returns the name of the player.
     *
     * @return the player name
     * @since 2.0.0
     */
    @Override
    @NotNull String name();
}
