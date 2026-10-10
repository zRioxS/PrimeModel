package kr.rioxs.primemodel.api.nms;

import kr.rioxs.primemodel.api.armor.PlayerArmor;
import kr.rioxs.primemodel.api.player.PlayerSkinParts;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import kr.rioxs.primemodel.api.platform.PlatformLocation;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Core NMS type definitions used across the model engine.
 *
 * @since 1.15.2
 */
public final class NMSTypes {

    private NMSTypes() {
        throw new UnsupportedOperationException("Utility class");
    }

    public interface Identifiable {
        int id();
        @NotNull UUID uuid();
    }

    public interface Profiled {
        @NotNull ModelProfile profile();
        @NotNull PlayerArmor armors();
        @NotNull PlayerSkinParts skinParts();
    }

    public interface DisplayTransformer {
        void transform(int duration, @NotNull Vector3f position, @NotNull Vector3f scale, @NotNull Quaternionf rotation, @NotNull AnimationBundler bundler);
        void sendTransformation(@NotNull PacketBundler bundler);
    }

    public record AnimationBundler(@NotNull PacketBundler standard, @NotNull NMS.ModAnimationBundler mod) {
        public boolean isNotEmpty() { return standard.isNotEmpty(); }
        public void send(@NotNull PlayerChannelHandler handler) {
            if (handler.isModEnabled()) mod.send(handler.player());
            else standard.send(handler.player());
        }
    }

    public enum ModelInteractionHand { LEFT, RIGHT }

    /**
     * Represents a nametag associated with a model part.
     *
     * @since 1.15.2
     */
    public interface ModelNametag {

        /**
         * Sets whether the nametag should always be visible (even through blocks).
         *
         * @param alwaysVisible true for always visible, false otherwise
         * @since 1.15.2
         */
        void alwaysVisible(boolean alwaysVisible);

        /**
         * Sets the text component of the nametag.
         *
         * @param component the text component, or null to clear
         * @since 1.15.2
         */
        void component(@Nullable Component component);

        /**
         * Teleports the nametag to a new location.
         *
         * @param location the target location
         * @since 1.15.2
         */
        void teleport(@NotNull PlatformLocation location);

        /**
         * Sends the nametag packet to a specific player.
         *
         * @param player the target player
         * @since 1.15.2
         */
        void send(@NotNull PlatformPlayer player);

        /**
         * Removes the nametag.
         *
         * @param bundler the packet bundler to use
         * @since 1.15.2
         */
        void remove(@NotNull PacketBundler bundler);
    }
}