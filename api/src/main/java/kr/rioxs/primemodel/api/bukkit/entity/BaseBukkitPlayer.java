package kr.rioxs.primemodel.api.bukkit.entity;

import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitPlayer;
import kr.rioxs.primemodel.api.entity.BasePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Bukkit-specific player adapter.
 * <p>
 * This interface extends {@link BaseBukkitEntity} and {@link BasePlayer} to provide
 * access to the underlying Bukkit player.
 * </p>
 *
 * @since 2.0.0
 */
public interface BaseBukkitPlayer extends BaseBukkitEntity, BasePlayer {
    /**
     * Returns the underlying Bukkit player.
     *
     * @return the Bukkit player
     * @since 2.0.0
     */
    @Override
    default @NotNull Player entity() {
        return ((BukkitPlayer) platform()).getSource();
    }
}
