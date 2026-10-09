package kr.rioxs.primemodel.api.config;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class Configs {

    private Configs() {
        throw new RuntimeException();
    }

    public record DebugConfig(@NotNull @Unmodifiable Set<DebugOption> options) {

        @RequiredArgsConstructor
        public enum DebugOption {
            EXCEPTION("exception"),
            HITBOX("hitbox"),
            PACK("pack"),
            TRACKER("tracker")
            ;
            private final String config;
        }

        public boolean has(@NotNull DebugOption option) {
            return options.contains(option);
        }

        public static final DebugConfig DEFAULT = new DebugConfig(Collections.emptySet());

        public static @NotNull DebugConfig from(@NotNull Predicate<String> predicate) {
            return new DebugConfig(Collections.unmodifiableSet(Arrays.stream(DebugOption.values())
                .filter(o -> predicate.test(o.config))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DebugOption.class)))));
        }
    }

    public record IndicatorConfig(@NotNull @Unmodifiable Set<IndicatorOption> options) {

        @RequiredArgsConstructor
        public enum IndicatorOption {
            PROGRESS_BAR("progress_bar"),
            ;
            private final String config;
        }

        public static final IndicatorConfig DEFAULT = new IndicatorConfig(Collections.emptySet());

        public static @NotNull IndicatorConfig from(@NotNull Predicate<String> predicate) {
            return new IndicatorConfig(Collections.unmodifiableSet(Arrays.stream(IndicatorOption.values())
                .filter(o -> predicate.test(o.config))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(IndicatorOption.class)))));
        }
    }

    public record ModuleConfig(
        boolean model,
        boolean playerAnimation
    ) {
        public static final ModuleConfig DEFAULT = new ModuleConfig(
            true,
            true
        );

        public static @NotNull ModuleConfig from(@NotNull Predicate<String> predicate) {
            return new ModuleConfig(
                predicate.test("model"),
                predicate.test("player-animation")
            );
        }
    }

    public record PackConfig(
        boolean useObfuscation
    ) {
        public static final PackConfig DEFAULT = new PackConfig(true);

        public static @NotNull PackConfig from(@NotNull Predicate<String> predicate) {
            return new PackConfig(
                predicate.test("use-obfuscation")
            );
        }
    }
}