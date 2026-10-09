package kr.rioxs.primemodel.api;
import kr.rioxs.primemodel.api.manager.Managers.Manager;
import kr.rioxs.primemodel.api.manager.Managers.ModelManager;
import kr.rioxs.primemodel.api.manager.Managers.PlayerManager;
import kr.rioxs.primemodel.api.manager.Managers.ProfileManager;
import kr.rioxs.primemodel.api.manager.Managers.ReloadInfo;
import kr.rioxs.primemodel.api.manager.Managers.ScriptManager;
import kr.rioxs.primemodel.api.manager.Managers.SkinManager;

import com.google.gson.annotations.SerializedName;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEventApplication;
import kr.rioxs.primemodel.api.manager.*;
import kr.rioxs.primemodel.api.nms.NMS;
import kr.rioxs.primemodel.api.pack.Pack.PackResult;
import kr.rioxs.primemodel.api.pack.PackZipper;
import kr.rioxs.primemodel.api.platform.PlatformAdapter;
import kr.rioxs.primemodel.api.scheduler.Schedulers.ModelScheduler;
import kr.rioxs.primemodel.api.version.MinecraftVersion;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.semver4j.Semver;

import java.io.File;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Represents the main platform interface for PrimeModel.
 * <p>
 * This interface provides access to core engine managers, schedulers, adapters, configuration,
 * NMS interfaces, and event bus systems across supported server platforms.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PrimeModelPlatform platform = PrimeModel.platform();
 * ModelScheduler scheduler = platform.scheduler();
 * PrimeModelConfig config = platform.config();
 * }</pre>
 *
 * @see PrimeModel
 * @since 1.15.2
 */
public interface PrimeModelPlatform extends ModelEventApplication {

    /**
     * Returns the data folder for the PrimeModel plugin.
     * This is where configuration files, data files, and other plugin-specific resources are stored.
     *
     * @return the data folder as a {@link File} object.
     * @since 2.0.0
     */
    @NotNull File dataFolder();

    /**
     * Returns the type of JAR file this platform is running on (e.g., SPIGOT, PAPER, FABRIC).
     *
     * @return the {@link JarType} enum representing the platform's JAR type.
     * @since 2.0.0
     */
    @NotNull JarType jarType();

    /**
     * Reloads the platform with default settings (console sender).
     *
     * @return the result of the reload operation
     * @since 2.0.0
     */
    default @NotNull ReloadResult reload() {
        return reload(ReloadInfo.DEFAULT);
    }

    /**
     * Reloads the platform, specifying the command sender who initiated it.
     *
     * @param sender the command sender
     * @return the result of the reload operation
     * @since 1.15.2
     */
    default @NotNull ReloadResult reload(@NotNull Audience sender) {
        return reload(ReloadInfo.builder().sender(sender).build());
    }

    /**
     * Reloads the platform with specific reload information.
     *
     * @param info the reload configuration
     * @return the result of the reload operation
     * @since 1.15.2
     */
    @NotNull ReloadResult reload(@NotNull ReloadInfo info);


    /**
     * Checks if the running version of PrimeModel is a snapshot build.
     *
     * @return true if snapshot, false otherwise
     * @since 1.15.2
     */
    boolean isSnapshot();

    /**
     * Returns the platform's configuration manager.
     *
     * @return the configuration
     * @since 1.15.2
     */
    @NotNull PrimeModelConfig config();

    /**
     * Returns the Minecraft version of the running server.
     *
     * @return the Minecraft version
     * @since 1.15.2
     */
    @NotNull MinecraftVersion version();

    /**
     * Returns the semantic version of the platform.
     *
     * @return the semantic version
     * @since 1.15.2
     */
    @NotNull Semver semver();

    /**
     * Returns the NMS (Net.Minecraft.Server) handler for version-specific operations.
     *
     * @return the NMS handler
     * @since 1.15.2
     */
    @NotNull NMS nms();

    /**
     * Gets the specified manager instance.
     * All separately existing manager getters have been refactored into this single method.
     * Use this method to access any manager.
     *
     * <p>Example:
     * <pre>{@code
     * ModelManager modelManager = platform.manager(ModelManager.class);
     * }</pre>
     *
     * @param managerClass the class of the manager to retrieve
     * @param <T> the type of the manager
     * @return the manager instance
     * @since 3.3.0
     */
    @NotNull <T extends Manager> T manager(@NotNull Class<T> managerClass);

    /**
     * Returns the model manager.
     *
     * @deprecated use PrimeModelPlatform#manager instead.
     * @return the model manager
     * @since 1.15.2
     */
    @Deprecated
    default @NotNull ModelManager modelManager() {
        return manager(ModelManager.class);
    }

    /**
     * Returns the player manager.
     *
     * @deprecated use PrimeModelPlatform#manager instead.
     * @return the player manager
     * @since 1.15.2
     */
    @Deprecated
    default @NotNull PlayerManager playerManager() {
        return manager(PlayerManager.class);
    }

    /**
     * Returns the script manager.
     *
     * @deprecated use PrimeModelPlatform#manager instead.
     * @return the script manager
     * @since 1.15.2
     */
    @Deprecated
    default @NotNull ScriptManager scriptManager() {
        return manager(ScriptManager.class);
    }

    /**
     * Returns the skin manager.
     *
     * @deprecated use PrimeModelPlatform#manager instead.
     * @return the skin manager
     * @since 1.15.2
     */
    @Deprecated
    default @NotNull SkinManager skinManager() {
        return manager(SkinManager.class);
    }

    /**
     * Returns the profile manager.
     *
     * @deprecated use PrimeModelPlatform#manager instead.
     * @return the profile manager
     * @since 1.15.2
     */
    @Deprecated
    default @NotNull ProfileManager profileManager() {
        return manager(ProfileManager.class);
    }

    /**
     * Returns the platform's scheduler.
     *
     * @return the scheduler
     * @since 1.15.2
     */
    @NotNull ModelScheduler scheduler();

    /**
     * Returns the platform-specific entity and component adapter.
     *
     * @return the platform adapter
     */
    @NotNull PlatformAdapter adapter();

    /**
     * Registers a handler to be executed when a reload starts.
     *
     * @param consumer the handler, receiving the {@link PackZipper}
     * @since 1.15.2
     */
    void addReloadStartHandler(@NotNull Consumer<PackZipper> consumer);

    /**
     * Registers a handler to be executed when a reload ends.
     *
     * @param consumer the handler, receiving the {@link ReloadResult}
     * @since 1.15.2
     */
    void addReloadEndHandler(@NotNull Consumer<ReloadResult> consumer);

    /**
     * Returns the platform's logger.
     *
     * @return the logger
     * @since 1.15.2
     */
    @NotNull PrimeModelLogger logger();

    /**
     * Returns the expression evaluator.
     *
     * @return the evaluator
     * @since 1.15.2
     */
    @NotNull PrimeModelEvaluator evaluator();

    /**
     * Returns the event bus.
     *
     * @return the event bus
     * @since 2.0.0
     */
    @NotNull PrimeModelEventBus eventBus();

    /**
     * Retrieves a resource from the platform's JAR file.
     *
     * @param path the path to the resource
     * @return an input stream for the resource, or null if not found
     * @since 1.15.2
     */
    @Nullable InputStream getResource(@NotNull String path);

    /**
     * Represents the outcome of a platform reload operation.
     *
     * @since 1.15.2
     */
    sealed interface ReloadResult {

        /**
         * Indicates a successful reload.
         *
         * @param firstLoad true if this is the first load (startup), false otherwise
         * @param assetsTime the time taken to reload assets in milliseconds
         * @param packResult the result of the resource pack generation
         * @since 1.15.2
         */
        record Success(boolean firstLoad, long assetsTime, @NotNull PackResult packResult) implements ReloadResult {

            /**
             * Returns the time taken to generate the resource pack.
             *
             * @return the packing time in milliseconds
             * @since 1.15.2
             */
            public long packingTime() {
                return packResult().time();
            }

            /**
             * Returns the total time taken for the reload operation.
             *
             * @return the total time in milliseconds
             * @since 1.15.2
             */
            public long totalTime() {
                return assetsTime + packingTime();
            }

            /**
             * Returns the size of the generated resource pack.
             *
             * @return the size in bytes
             * @since 1.15.2
             */
            public long length() {
                var dir = packResult.directory();
                return dir != null && dir.isFile() ? dir.length() : packResult.stream().mapToLong(b -> b.bytes().length).sum();
            }
        }

        /**
         * Indicates that a reload is currently in progress.
         * @since 1.15.2
         */
        enum OnReload implements ReloadResult {
            /**
             * Singleton instance.
             * @since 1.15.2
             */
            INSTANCE
        }

        /**
         * Indicates a failed reload.
         *
         * @param throwable the exception that caused the failure
         * @since 1.15.2
         */
        record Failure(@NotNull Throwable throwable) implements ReloadResult {
        }
    }

    /**
     * Represents the type of JAR file the platform is running on.
     * This enum helps identify the specific server implementation (e.g., Spigot, Paper, Fabric).
     *
     * @since 2.0.0
     */
    enum JarType {
        /**
         * Indicates a Spigot-based server.
         * @since 2.0.0
         */
        @SerializedName("spigot")
        SPIGOT,
        /**
         * Indicates a Paper-based server.
         * @since 2.0.0
         */
        @SerializedName("paper")
        PAPER,
        /**
         * Indicates a Fabric-based server.
         * @since 2.0.0
         */
        @SerializedName("fabric")
        FABRIC
    }
}
