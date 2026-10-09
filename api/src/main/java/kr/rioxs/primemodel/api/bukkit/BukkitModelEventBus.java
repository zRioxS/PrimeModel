package kr.rioxs.primemodel.api.bukkit;

import kr.rioxs.primemodel.api.PrimeModelEventBus;
import kr.rioxs.primemodel.api.bukkit.event.BukkitEventApplication;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEvent;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEventListener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A Bukkit-specific extension of the {@link PrimeModelEventBus}.
 * <p>
 * This interface provides convenience methods for subscribing to events using a Bukkit {@link Plugin} instance.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * BukkitModelEventBus eventBus = PrimeModelBukkit.platform().eventBus();
 * eventBus.subscribe(plugin, ModelEvent.class, event -> {
 *     // Handle Bukkit event
 * });
 * }</pre>
 *
 * @since 2.0.0
 */
public interface BukkitModelEventBus extends PrimeModelEventBus {

    /**
     * Subscribes a consumer to a specific event type, associated with a Bukkit plugin.
     *
     * @param plugin the plugin that subscribes to the event
     * @param eventClass the class of the event to subscribe to
     * @param consumer the consumer to handle the event
     * @param <T> the type of the event
     * @return a listener handle that can be used to unregister the subscription
     * @since 2.0.0
     */
    @NotNull
    default <T extends ModelEvent> ModelEventListener subscribe(@NotNull Plugin plugin, @NotNull Class<T> eventClass, @NotNull Consumer<T> consumer) {
        return subscribe(BukkitEventApplication.of(plugin), eventClass, consumer);
    }
}
