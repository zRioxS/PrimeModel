package kr.rioxs.primemodel.api.nms;

import kr.rioxs.primemodel.api.armor.PlayerArmor;
import kr.rioxs.primemodel.api.player.PlayerSkinParts;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

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
}