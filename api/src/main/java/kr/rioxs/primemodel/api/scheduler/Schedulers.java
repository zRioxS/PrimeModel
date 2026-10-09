package kr.rioxs.primemodel.api.scheduler;

import org.jetbrains.annotations.NotNull;

/**
 * Merged scheduler contracts.
 * <p>
 * Originally split across {@code ModelScheduler} and {@code ModelTask}.
 * </p>
 *
 * @since 3.0.0
 */
public final class Schedulers {

    private Schedulers() {
        throw new RuntimeException();
    }

    /**
     * A scheduler of PrimeModel
     */
    public interface ModelScheduler {

        /**
         * Runs async task
         * @param runnable task
         * @return scheduled task
         */
        @NotNull ModelTask asyncTask(@NotNull Runnable runnable);

        /**
         * Runs async task
         * @param delay delay
         * @param runnable task
         * @return scheduled task
         */
        @NotNull ModelTask asyncTaskLater(long delay, @NotNull Runnable runnable);

        /**
         * Runs async task
         * @param delay delay
         * @param period period
         * @param runnable task
         * @return scheduled task
         */
        @NotNull ModelTask asyncTaskTimer(long delay, long period, @NotNull Runnable runnable);
    }

    /**
     * A scheduled task of PrimeModel
     */
    public interface ModelTask {

        /**
         * Checks this task is canceled
         * @return whether to cancel
         */
        boolean isCancelled();

        /**
         * Cancels this task
         */
        void cancel();
    }
}