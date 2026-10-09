package kr.rioxs.primemodel.api.armor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Armor item
 * @param tint tint value
 * @param type armor type
 * @param trim trim
 * @param palette palette
 */
public record ArmorItem(int tint, @NotNull String type, @Nullable String trim, @Nullable String palette) {
}
