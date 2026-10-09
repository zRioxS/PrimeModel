package kr.rioxs.primemodel.api.profile;
import kr.rioxs.primemodel.api.manager.Managers.Manager;

import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.manager.Managers.PlayerManager;
import kr.rioxs.primemodel.api.manager.Managers.ProfileManager;
import kr.rioxs.primemodel.api.platform.PlatformOfflinePlayer;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Merged profile classes.
 */
public final class Profiles {

    private Profiles() {
        throw new RuntimeException();
    }

    public record ModelProfileInfo(@NotNull UUID id, @Nullable String name) {
        public static final ModelProfileInfo UNKNOWN = new ModelProfileInfo(
            UUID.fromString("00000000-0000-0000-0000-000000000000"),
            null
        );
    }

    public record ModelProfileSkin(
        @Nullable URI skin,
        @Nullable URI cape,
        boolean slim,
        @NotNull String raw
    ) {
        public static final ModelProfileSkin EMPTY = new ModelProfileSkin(null, null, false, "");
    }

    public interface ModelProfile {

        ModelProfile UNKNOWN = of(ModelProfileInfo.UNKNOWN);

        static @NotNull ModelProfile of(@NotNull ModelProfileInfo info) {
            return new Simple(info, ModelProfileSkin.EMPTY);
        }

        static @NotNull ModelProfile of(@NotNull ModelProfileInfo info, @NotNull ModelProfileSkin skin) {
            return new Simple(info, skin);
        }

        static @NotNull ModelProfile of(@NotNull PlatformPlayer player) {
            var channel = PrimeModel.platform().manager(PlayerManager.class).player(player.uuid());
            return channel != null ? channel.base().profile() : PrimeModel.nms().profile(player);
        }

        static @NotNull Uncompleted of(@NotNull PlatformOfflinePlayer offlinePlayer) {
            return PrimeModel.platform().manager(ProfileManager.class).supplier().supply(offlinePlayer);
        }

        static @NotNull Uncompleted of(@NotNull UUID uuid) {
            return of(PrimeModel.platform().adapter().offlinePlayer(uuid));
        }

        @NotNull ModelProfileInfo info();

        @NotNull ModelProfileSkin skin();

        default @NotNull Uncompleted asUncompleted() {
            return new Uncompleted() {
                @Override
                public @NotNull ModelProfileInfo info() {
                    return ModelProfile.this.info();
                }

                @Override
                public @NotNull CompletableFuture<ModelProfile> complete() {
                    return CompletableFuture.completedFuture(ModelProfile.this);
                }
            };
        }

        default @Nullable PlatformPlayer player() {
            return PrimeModel.platform().adapter().player(info().id());
        }

        record Simple(@NotNull ModelProfileInfo info, @NotNull ModelProfileSkin skin) implements ModelProfile {
        }

        interface Uncompleted {

            @NotNull ModelProfileInfo info();

            @NotNull CompletableFuture<ModelProfile> complete();

            default @NotNull ModelProfile fallback() {
                return of(info());
            }
        }
    }

    public interface ModelProfileSupplier {

        @NotNull ModelProfile.Uncompleted supply(@NotNull ModelProfileInfo info);

        default @NotNull ModelProfile.Uncompleted supply(@NotNull PlatformOfflinePlayer player) {
            return supply(new ModelProfileInfo(player.uuid(), player.name()));
        }
    }
}