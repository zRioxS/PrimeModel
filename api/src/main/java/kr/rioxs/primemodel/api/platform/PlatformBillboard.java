package kr.rioxs.primemodel.api.platform;

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
