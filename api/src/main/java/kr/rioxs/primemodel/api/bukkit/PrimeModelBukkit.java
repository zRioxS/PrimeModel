package kr.rioxs.primemodel.api.bukkit;

import kr.rioxs.primemodel.api.util.Utils.ReflectionUtil;

import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.PrimeModelPlatform;
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitAdapter;
import kr.rioxs.primemodel.api.bukkit.scheduler.BukkitModelScheduler;
import org.jetbrains.annotations.NotNull;

import static kr.rioxs.primemodel.api.util.Utils.ReflectionUtil.classExists;

/**
 * Represents the Bukkit-specific platform interface for PrimeModel.
 * <p>
 * This interface extends {@link PrimeModelPlatform} to provide Bukkit-specific implementations
 * for scheduling and entity adaptation.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PrimeModelBukkit platform = PrimeModelBukkit.platform();
 * BukkitModelScheduler scheduler = platform.scheduler();
 * if (PrimeModelBukkit.IS_FOLIA) {
 *     // Folia region-aware scheduling
 * }
 * }</pre>
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
}
