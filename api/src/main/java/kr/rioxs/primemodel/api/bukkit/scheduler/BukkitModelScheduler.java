package kr.rioxs.primemodel.api.bukkit.scheduler;

import kr.rioxs.primemodel.api.scheduler.Schedulers.ModelScheduler;
import kr.rioxs.primemodel.api.scheduler.Schedulers.ModelTask;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Bukkit-specific scheduler for model tasks.
 * <p>
 * This interface extends {@link ModelScheduler} to provide methods for scheduling tasks
 * that are synchronized with specific locations (e.g., for Folia compatibility).
 * </p>
 *
 * @since 2.0.0
 */
public interface BukkitModelScheduler extends ModelScheduler {

    /**
     * Schedules a task to run on the next tick, synchronized with the given location.
     *
     * @param location the location to synchronize with
     * @param runnable the task to run
     * @return the scheduled task, or null if scheduling failed
     * @since 2.0.0
     */
    @Nullable ModelTask task(@NotNull Location location, @NotNull Runnable runnable);

    /**
     * Schedules a task to run after a delay, synchronized with the given location.
     *
     * @param location the location to synchronize with
     * @param delay the delay in ticks
     * @param runnable the task to run
     * @return the scheduled task, or null if scheduling failed
     * @since 2.0.0
     */
    @Nullable ModelTask taskLater(@NotNull Location location, long delay, @NotNull Runnable runnable);
}
