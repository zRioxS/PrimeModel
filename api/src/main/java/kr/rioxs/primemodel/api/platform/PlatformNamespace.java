package kr.rioxs.primemodel.api.platform;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a namespaced key (e.g., "minecraft:apple").
 *
 * @param namespace the namespace (e.g., "minecraft")
 * @param path the path (e.g., "apple")
 * @since 2.0.0
 */
public record PlatformNamespace(@NotNull String namespace, @NotNull String path) {
}
