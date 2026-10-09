package kr.rioxs.primemodel.api.platform;

/**
 * Container for platform-related enumerations.
 * <p>
 * This class groups small, independent enums that are used throughout the platform layer.
 * </p>
 *
 * @since 2.0.0
 */
public final class PlatformEnums {

    private PlatformEnums() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Defines the billboard constraints for a display entity.
     * <p>
     * Billboard settings control how the display rotates to face the player.
     * </p>
     *
     * @since 2.0.0
     */
    public enum PlatformBillboard {
        /**
         * No rotation (default).
         */
        FIXED,
        /**
         * Can pivot around vertical axis.
         */
        VERTICAL,
        /**
         * Can pivot around horizontal axis.
         */
        HORIZONTAL,
        /**
         * Can pivot around center point.
         */
        CENTER
    }

    /**
     * Defines the display context for an item model.
     * <p>
     * These values correspond to the display settings in a Minecraft item model file.
     * </p>
     *
     * @since 2.0.0
     */
    public enum PlatformItemTransform {
        NONE,
        THIRDPERSON_LEFTHAND,
        THIRDPERSON_RIGHTHAND,
        FIRSTPERSON_LEFTHAND,
        FIRSTPERSON_RIGHTHAND,
        HEAD,
        GUI,
        GROUND,
        FIXED
    }
}