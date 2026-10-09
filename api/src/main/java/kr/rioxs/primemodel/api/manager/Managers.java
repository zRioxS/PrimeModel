package kr.rioxs.primemodel.api.manager;

import kr.rioxs.primemodel.api.animation.Animations.AnimationModifier;
import kr.rioxs.primemodel.api.data.renderer.ModelRenderer;
import kr.rioxs.primemodel.api.nms.PlayerChannelHandler;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSkin;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSupplier;
import kr.rioxs.primemodel.api.script.Scripts.AnimationScript;
import kr.rioxs.primemodel.api.script.Scripts.ScriptBuilder;
import kr.rioxs.primemodel.api.skin.SkinData;
import kr.rioxs.primemodel.api.tracker.EntityTracker;
import kr.rioxs.primemodel.api.tracker.TrackerUtils.TrackerModifier;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Merged manager interfaces.
 */
public final class Managers {

    private Managers() {
        throw new RuntimeException();
    }

    public interface Manager {
    }

    public interface ModelManager extends Manager {

        @Nullable ModelRenderer model(@NotNull String name);

        @Deprecated
        @Nullable
        default ModelRenderer renderer(@NotNull String name) {
            return model(name);
        }

        @NotNull @Unmodifiable
        Collection<ModelRenderer> models();

        @NotNull @Unmodifiable
        Set<String> modelKeys();

        @NotNull @Unmodifiable
        Collection<ModelRenderer> limbs();

        @Nullable ModelRenderer limb(@NotNull String name);

        @NotNull @Unmodifiable
        Set<String> limbKeys();

        default boolean animate(@NotNull PlatformPlayer player, @NotNull String model, @NotNull String animation) {
            return animate(player, model, animation, AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
        }

        default boolean animate(@NotNull PlatformPlayer player, @NotNull String model, @NotNull String animation, @NotNull AnimationModifier modifier) {
            return animate(player, model, animation, modifier, _ -> {});
        }

        default boolean animate(@NotNull PlatformPlayer player, @NotNull String model, @NotNull String animation, @NotNull AnimationModifier modifier, @NotNull Consumer<EntityTracker> consumer) {
            var get = limb(model);
            if (get == null) return false;
            var create = get.getOrCreate(player, TrackerModifier.DEFAULT, consumer);
            if (!create.animate(animation, modifier, create::close)) {
                create.close();
                return false;
            }
            return true;
        }
    }

    public interface PlayerManager extends Manager {

        @Nullable PlayerChannelHandler player(@NotNull UUID uuid);

        @NotNull PlayerChannelHandler player(@NotNull PlatformPlayer player);
    }

    public interface ProfileManager extends Manager {

        @NotNull ModelProfileSupplier supplier();

        @NotNull ModelProfileSkin skin(@NotNull String rawTextures);

        void supplier(@NotNull ModelProfileSupplier supplier);
    }

    public record ReloadInfo(boolean skipConfig, @NotNull Audience sender) {

        public static final ReloadInfo DEFAULT = new ReloadInfo(false, Audience.empty());

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private boolean skipConfig = false;
            private Audience sender = Audience.empty();

            private Builder() {}

            public Builder skipConfig(boolean skipConfig) {
                this.skipConfig = skipConfig;
                return this;
            }

            public Builder sender(Audience sender) {
                this.sender = sender;
                return this;
            }

            public ReloadInfo build() {
                return new ReloadInfo(skipConfig, sender);
            }
        }
    }

    public interface ScriptManager extends Manager {

        @Nullable AnimationScript build(@NotNull String script);

        void addBuilder(@NotNull String name, @NotNull ScriptBuilder script);
    }

    public interface SkinManager extends Manager {

        @NotNull SkinData fallback();

        @NotNull CompletableFuture<? extends SkinData> complete(@NotNull ModelProfile.Uncompleted profile);

        void removeCache(@NotNull ModelProfile profile);
    }
}