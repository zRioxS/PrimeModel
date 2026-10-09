package kr.rioxs.primemodel.api;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Platform-agnostic logging interface for PrimeModel.
 * <p>
 * Provides standardized methods to emit formatted adventure components as info or warning log messages.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PrimeModelLogger logger = PrimeModel.platform().logger();
 * logger.info(Component.text("Model loaded successfully."));
 * logger.warn(Component.text("Failed to resolve bone texture."));
 * }</pre>
 */
public interface PrimeModelLogger {
    /**
     * Logs informational messages to the platform console or log file.
     *
     * @param message one or more Adventure {@link Component} messages to log
     */
    void info(@NotNull Component... message);

    /**
     * Logs warning messages to the platform console or log file.
     *
     * @param message one or more Adventure {@link Component} messages to log
     */
    void warn(@NotNull Component... message);
}
