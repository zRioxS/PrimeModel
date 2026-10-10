package kr.rioxs.primemodel.api.bukkit.entity;

import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitAdapter;
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitEntity;
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitPlayer;
import kr.rioxs.primemodel.api.entity.BaseEntity;
import kr.rioxs.primemodel.api.entity.BaseEntity.Player;
import kr.rioxs.primemodel.api.util.Utils.TransformedItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a Bukkit-specific entity adapter.
 * <p>
 * This interface extends {@link BaseEntity} and {@link PersistentDataHolder} to provide
 * access to the underlying Bukkit entity and its persistent data container.
 * </p>
 *
 * @since 2.0.0
 */
public interface BaseBukkitEntity extends BaseEntity, PersistentDataHolder {

    /**
     * The namespaced key used for storing tracker data in the entity's persistent data container.
     * @since 2.0.0
     */
    @NotNull
    NamespacedKey TRACKING_ID = Objects.requireNonNull(NamespacedKey.fromString("primemodel_tracker"));

    /**
     * Returns the underlying Bukkit entity.
     *
     * @return the Bukkit entity
     * @since 2.0.0
     */
    default @NotNull Entity entity() {
        return ((BukkitEntity) platform()).getSource();
    }

    /**
     * Returns the item in the entity's main hand.
     *
     * @return the main hand item
     * @since 2.0.0
     */
    @Override
    default @NotNull TransformedItemStack mainHand() {
        if (entity() instanceof LivingEntity livingEntity) {
            var equipment = livingEntity.getEquipment();
            if (equipment != null) return TransformedItemStack.of(BukkitAdapter.adapt(equipment.getItemInMainHand()));
        }
        return TransformedItemStack.empty();
    }

    /**
     * Returns the item in the entity's offhand.
     *
     * @return the offhand item
     * @since 2.0.0
     */
    @Override
    default @NotNull TransformedItemStack offHand() {
        if (entity() instanceof LivingEntity livingEntity) {
            var equipment = livingEntity.getEquipment();
            if (equipment != null) return TransformedItemStack.of(BukkitAdapter.adapt(equipment.getItemInOffHand()));
        }
        return TransformedItemStack.empty();
    }

    /**
     * Retrieves the model data stored in the entity's persistent data container.
     *
     * @return the model data string, or null if not present
     * @since 2.0.0
     */
    default @Nullable String modelData() {
        return getPersistentDataContainer().get(TRACKING_ID, PersistentDataType.STRING);
    }

    /**
     * Stores the model data in the entity's persistent data container.
     *
     * @param modelData the model data string, or null to remove it
     * @since 2.0.0
     */
    default void modelData(@Nullable String modelData) {
        var container = getPersistentDataContainer();
        if (modelData == null) container.remove(TRACKING_ID);
        else container.set(TRACKING_ID, PersistentDataType.STRING, modelData);
    }

    // ===== Nested Types =====

    /**
     * Represents a Bukkit-specific player adapter.
     * <p>
     * This interface extends {@link BaseBukkitEntity} and {@link BaseEntity.Player} to provide
     * access to the underlying Bukkit player.
     * </p>
     *
     * @since 2.0.0
     */
    interface Player extends BaseBukkitEntity, BaseEntity.Player {

        /**
         * Returns the underlying Bukkit player.
         *
         * @return the Bukkit player
         * @since 2.0.0
         */
        @Override
        default @NotNull org.bukkit.entity.Player entity() {
            return ((BukkitPlayer) platform()).getSource();
        }
    }
}