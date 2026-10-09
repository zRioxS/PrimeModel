package kr.rioxs.primemodel.api.entity;
import kr.rioxs.primemodel.api.manager.Managers.Manager;

import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.manager.Managers.PlayerManager;
import kr.rioxs.primemodel.api.nms.NMSTypes.Identifiable;
import kr.rioxs.primemodel.api.platform.PlatformEntity;
import kr.rioxs.primemodel.api.platform.PlatformLocation;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.tracker.EntityTrackerRegistry;
import kr.rioxs.primemodel.api.util.Utils.TransformedItemStack;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * An adapter of entity
 */
public interface BaseEntity extends Identifiable {

    /**
     * Gets base entity
     * @param entity platform entity
     * @return base entity
     */
    static @NotNull BaseEntity of(@NotNull PlatformEntity entity) {
        if (entity instanceof PlatformPlayer player) {
            var channel = PrimeModel.platform().manager(PlayerManager.class).player(player.uuid());
            return channel != null ? channel.base() : PrimeModel.nms().adapt(player);
        }
        return PrimeModel.nms().adapt(entity);
    }

    /**
     * Gets the platform-specific entity object.
     * @since 2.0.0
     * @return The platform entity.
     */
    @NotNull PlatformEntity platform();

    /**
     * Gets the current location of the entity.
     * @since 2.0.0
     * @return The entity's location.
     */
    default @NotNull PlatformLocation location() {
        return platform().location();
    }

    /**
     * Gets custom name of this entity
     * @return custom name
     */
    @Nullable Component customName();

    /**
     * Gets vanilla entity
     * @return vanilla entity
     */
    @NotNull Object handle();

    /**
     * Gets entity id
     * @return entity id
     */
    int id();

    /**
     * Checks source entity is dead
     * @return dead
     */
    boolean dead();

    /**
     * Checks source entity is on the ground
     * @return on the ground
     */
    boolean ground();

    /**
     * Checks source entity is invisible
     * @return invisible
     */
    boolean invisible();

    /**
     * Check source entity is on a glow
     * @return glow
     */
    boolean glow();

    /**
     * Check source entity is on a walk
     * @return walk
     */
    boolean onWalk();

    /**
     * Check source entity is on the fly
     * @return fly
     */
    boolean fly();

    /**
     * Gets entity's scale
     * @return scale
     */
    double scale();

    /**
     * Gets entity's pitch (x-rot)
     * @return pitch
     */
    float pitch();

    /**
     * Gets entity's body yaw (y-rot)
     * @return body yaw
     */
    float bodyYaw();

    /**
     * Gets entity's yaw (y-rot)
     * @return yaw
     */
    float yaw();

    /**
     * Gets entity's head yaw (y-rot)
     * @return head yaw
     */
    float headYaw();

    /**
     * Gets entity's damage tick
     * @return damage tick
     */
    float damageTick();

    /**
     * Gets entity's walk speed
     * @return walk speed
     */
    float walkSpeed();

    /**
     * Gets entity's passenger point
     * @param dest destination vector
     * @return passenger point
     */
    @NotNull Vector3f passengerPosition(@NotNull Vector3f dest);

    /**
     * Gets tracked player set
     * @return tracked player set
     */
    @NotNull Stream<PlatformPlayer> trackedBy();

    /**
     * Gets main hand item
     * @return main hand
     */
    @NotNull TransformedItemStack mainHand();

    /**
     * Gets offhand item
     * @return offhand
     */
    @NotNull TransformedItemStack offHand();

    /**
     * Gets tracker registry of this adapter
     * @return optional tracker registry
     */
    default @NotNull Optional<EntityTrackerRegistry> registry() {
        return PrimeModel.registry(uuid());
    }

    /**
     * Checks this entity has controlling passenger
     * @return has controlling passenger
     */
    default boolean hasControllingPassenger() {
        var registry = registry().orElse(null);
        return registry != null && registry.hasControllingPassenger();
    }

    /**
     * Checks this entity has model data
     * @return has model data
     */
    default boolean hasModelData() {
        return modelData() != null;
    }

    /**
     * Gets this entity's model data
     * @return model data
     */
    @Nullable String modelData();

    /**
     * Sets this entity's model data
     * @param modelData model data
     */
    void modelData(@Nullable String modelData);
}
