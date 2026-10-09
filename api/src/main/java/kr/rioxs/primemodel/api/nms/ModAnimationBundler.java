package kr.rioxs.primemodel.api.nms;

import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * A bundler that sends animation data to a player.
 * @since 2.2.1
 */
public interface ModAnimationBundler {
    /**
     * Sends the bundled animation data to the specified player.
     *
     * @since 2.2.1
     * @param player the player to receive the animation
     */
    void send(@NotNull PlatformPlayer player);
}
