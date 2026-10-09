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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    boolean IS_FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    boolean IS_PURPUR = classExists("org.purpurmc.purpur.PurpurConfig");
    boolean IS_PAPER = IS_PURPUR || IS_FOLIA || classExists("io.papermc.paper.configuration.PaperConfigurations");

    static @NotNull PrimeModelBukkit platform() {
        return (PrimeModelBukkit) PrimeModel.platform();
    }

    @Override
    @NotNull BukkitModelScheduler scheduler();

    @Override
    @NotNull BukkitAdapter adapter();

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
     *
     * @since 2.0.0
     */
    record BukkitEventApplication(@NotNull String name, @NotNull WeakReference<Plugin> pluginRef) implements ModelEventApplication {

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

    /**
     * A wrapper class that adapts {@link ModelEvent} to Bukkit's {@link Event} system.
     * <p>
     * This allows Bukkit plugins to listen for PrimeModel events using the standard Bukkit event API.
     * The underlying {@link ModelEvent} is lazily initialized when accessed.
     * </p>
     *
     * @since 2.0.0
     */
    final class PrimeModelBukkitEvent extends Event {

        private static final HandlerList HANDLER_LIST = new HandlerList();

        private final Class<? extends ModelEvent> eventClass;
        private final @NotNull Supplier<? extends ModelEvent> supplier;
        private volatile ModelEvent source;

        /**
         * Creates a new PrimeModelBukkitEvent.
         *
         * @param eventClass the class of the model event
         * @param supplier a supplier that creates the model event
         * @since 2.0.0
         */
        @ApiStatus.Internal
        public PrimeModelBukkitEvent(@NotNull Class<? extends ModelEvent> eventClass, @NotNull Supplier<? extends ModelEvent> supplier) {
            super(!Bukkit.isPrimaryThread());
            this.eventClass = eventClass;
            this.supplier = supplier;
        }

        /**
         * Checks if the wrapped event is an instance of the specified class.
         *
         * @param eventClass the class to check against
         * @param <T> the type of the event
         * @return true if the wrapped event is assignable to the class
         * @since 2.0.0
         */
        public <T extends ModelEvent> boolean is(@NotNull Class<T> eventClass) {
            return eventClass.isAssignableFrom(this.eventClass);
        }

        /**
         * Casts the wrapped event to the specified class if possible.
         *
         * @param eventClass the class to cast to
         * @param <T> the type of the event
         * @return the cast event, or null if the cast is not possible
         * @since 2.0.0
         */
        public <T extends ModelEvent> @Nullable T as(@NotNull Class<T> eventClass) {
            if (!is(eventClass)) return null;
            var event = source;
            if (event == null) {
                synchronized (this) {
                    event = source;
                    if (event == null) event = source = supplier.get();
                }
            }
            return eventClass.cast(event);
        }

        /**
         * Executes a consumer if the wrapped event is of the specified type.
         *
         * @param eventClass the class to check against
         * @param consumer the consumer to execute
         * @param <T> the type of the event
         * @since 2.0.0
         */
        public <T extends ModelEvent> void as(@NotNull Class<T> eventClass, @NotNull Consumer<? super T> consumer) {
            var get = as(eventClass);
            if (get != null) consumer.accept(get);
        }

        /**
         * Returns the underlying model event, if initialized.
         *
         * @return the model event, or null if not yet initialized
         * @since 2.0.0
         */
        @ApiStatus.Internal
        public @Nullable ModelEvent source() {
            return source;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLER_LIST;
        }

        /**
         * Returns the handler list for this event.
         *
         * @return the handler list
         * @since 2.0.0
         */
        public static HandlerList getHandlerList() {
            return HANDLER_LIST;
        }
    }
}