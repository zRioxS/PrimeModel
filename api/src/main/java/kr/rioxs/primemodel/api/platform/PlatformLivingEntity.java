package kr.rioxs.primemodel.api.platform;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a living entity in the underlying platform.
 * <p>
 * This interface extends {@link PlatformEntity} to provide access to living entity-specific properties,
 * such as eye location.
 * </p>
 *
 * @since 2.0.0
 */
public interface PlatformLivingEntity extends PlatformEntity {

    /**
     * Returns the eye location of the living entity.
     *
     * @return the eye location
     * @since 2.0.0
     */
    @NotNull PlatformLocation eyeLocation();
}
