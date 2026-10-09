package kr.rioxs.primemodel.api.bukkit;

import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.PrimeModelEventBus;
import kr.rioxs.primemodel.api.PrimeModelPlatform;
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitAdapter;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEvent;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEventApplication;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEventListener;
import kr.rioxs.primemodel.api.scheduler.Schedulers.ModelScheduler;
import kr.rioxs.primemodel.api.scheduler.Schedulers.ModelTask;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import static kr.rioxs.primemodel.api.util.Utils.ReflectionUtil.classExists;

/**
 * Represents the Bukkit-specific platform interface for PrimeModel.
 * <p>
 * This interface extends {@link PrimeModelPlatform} to provide Bukkit-specific implementations
 * for scheduling and entity adaptation.
 * </p>
 *
 * @since 2.0.0
 */
public interface PrimeModelBukkit extends PrimeModelPlatform {

    /**
     * Checks if the server is running on the Folia platform.
     * @since 2.0.0
     */
    boolean IS_FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    /**
     * Checks if the server is running on the Purpur platform.
     * @since 2.0.0
     */
    boolean IS_PURPUR = classExists("org.purpurmc.purpur.PurpurConfig");

    /**
     * Checks if the server is running on the Paper platform (or a fork like Purpur/Folia).
     * @since 2.0.0
     */
    boolean IS_PAPER = IS_PURPUR || IS_FOLIA || classExists("io.papermc.paper.configuration.PaperConfigurations");

    /**
     * Returns the current {@link PrimeModelBukkit} instance.
     *
     * @return the current platform instance
     * @since 2.0.0
     */
    static @NotNull PrimeModelBukkit platform() {
        return (PrimeModelBukkit) PrimeModel.platform();
    }

    /**
     * Returns the Bukkit-specific scheduler.
     *
     * @return the scheduler
     * @since 2.0.0
     */
    @Override
    @NotNull BukkitModelScheduler scheduler();

    /**
     * Returns the Bukkit-specific adapter.
     *
     * @return the adapter
     * @since 2.0.0
     */
    @Override
    @NotNull BukkitAdapter adapter();

    /**
     * Returns the Bukkit-specific event bus.
     *
     * @return the event bus
     * @since 2.0.0
     */
    @Override
    @NotNull BukkitModelEventBus eventBus();

    // ===== Nested Types =====

    /**
     * Represents a Bukkit-specific scheduler for model tasks.
     *
     * @since 2.0.0
     */
    interface BukkitModelScheduler extends ModelScheduler {

        @Nullable ModelTask task(@NotNull Location location, @NotNull Runnable runnable);

        @Nullable ModelTask taskLater(@NotNull Location location, long delay, @NotNull Runnable runnable);
    }

    /**
     * An implementation of {@link ModelEventApplication} for Bukkit plugins.
     * <p>
     * This record holds a weak reference to a Bukkit plugin to prevent memory leaks
     * and checks if the plugin is enabled.
     * </p>
     *
     * @param name the name of the plugin
     * @param pluginRef a weak reference to the plugin instance
     * @since 2.0.0
     */
    record BukkitEventApplication(@NotNull String name, @NotNull WeakReference<Plugin> pluginRef) implements ModelEventApplication {

        /**
         * Creates a new BukkitEventApplication for the given plugin.
         *
         * @param plugin the Bukkit plugin
         * @return the event application wrapper
         * @since 2.0.0
         */
        public static @NotNull BukkitEventApplication of(@NotNull Plugin plugin) {
            return new BukkitEventApplication(plugin.getName(), new WeakReference<>(plugin));
        }

        @Override
        public boolean isEnabled() {
            var get = pluginRef().get();
            return get != null && get.isEnabled();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BukkitEventApplication that)) return false;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    /**
     * A Bukkit-specific extension of the {@link PrimeModelEventBus}.
     *
     * @since 2.0.0
     */
    interface BukkitModelEventBus extends PrimeModelEventBus {

        @NotNull
        default <T extends ModelEvent> ModelEventListener subscribe(@NotNull Plugin plugin, @NotNull Class<T> eventClass, @NotNull Consumer<T> consumer) {
            return subscribe(BukkitEventApplication.of(plugin), eventClass, consumer);
        }
    }
}