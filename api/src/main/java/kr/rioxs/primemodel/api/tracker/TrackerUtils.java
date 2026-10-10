package kr.rioxs.primemodel.api.tracker;

import com.google.gson.JsonArray;
import kr.rioxs.primemodel.api.animation.Animations.AnimationModifier;
import kr.rioxs.primemodel.api.data.renderer.RenderPipeline;
import kr.rioxs.primemodel.api.event.EventInterfaces.CreateDummyTrackerEvent;
import kr.rioxs.primemodel.api.nms.PlayerChannelHandler;
import kr.rioxs.primemodel.api.platform.PlatformLocation;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.util.Utils.EventUtil;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Merged tracker utility classes.
 */
public final class TrackerUtils {

    private TrackerUtils() {
        throw new RuntimeException();
    }

    public static final class DummyTracker extends Tracker {

        private volatile PlatformLocation location;

        public DummyTracker(@NotNull PlatformLocation location, @NotNull RenderPipeline pipeline, @NotNull TrackerModifier modifier, @NotNull Consumer<DummyTracker> preUpdateConsumer) {
            super(pipeline, modifier);
            this.location = location;
            animate("spawn", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
            pipeline.scale(() -> scaler().scale(this));
            rotation(() -> new ModelRotation(this.location.pitch(), this.location.yaw()));
            preUpdateConsumer.accept(this);
            EventUtil.call(CreateDummyTrackerEvent.class, () -> new CreateDummyTrackerEvent(this));
        }

        public void location(@NotNull PlatformLocation location) {
            Objects.requireNonNull(location, "location");
            if (this.location.equals(location)) return;
            synchronized (this) {
                this.location = location;
                var bundler = pipeline.createBundler();
                pipeline.forEach(b -> b.teleport(location, bundler));
                if (bundler.isNotEmpty()) pipeline.allPlayer().map(PlayerChannelHandler::player).forEach(bundler::send);
            }
        }

        @Override
        public @NotNull PlatformLocation location() {
            return location;
        }

        public void spawn(@NotNull PlatformPlayer player) {
            var bundler = pipeline.createBundler();
            spawn(player, bundler);
            bundler.send(player);
        }
    }

    public record ModelRotation(float x, float y) {

        public static final ModelRotation EMPTY = new ModelRotation(0, 0);
        public static final ModelRotation INVALID = new ModelRotation(Float.MAX_VALUE, Float.MAX_VALUE);

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            return o instanceof ModelRotation other && packedX() == other.packedX() && packedY() == other.packedY();
        }

        @Override
        public int hashCode() {
            return ((Byte.hashCode(packedX()) & 0xFF) << 8) | (Byte.hashCode(packedY()) & 0xFF);
        }

        public @NotNull ModelRotation pitch() {
            return new ModelRotation(x, 0);
        }

        public @NotNull ModelRotation yaw() {
            return new ModelRotation(0, y);
        }

        public float radianX() {
            return x * MathUtil.DEGREES_TO_RADIANS;
        }

        public float radianY() {
            return y * MathUtil.DEGREES_TO_RADIANS;
        }

        public byte packedX() {
            return (byte) (x * MathUtil.DEGREES_TO_PACKED_BYTE);
        }

        public byte packedY() {
            return (byte) (y * MathUtil.DEGREES_TO_PACKED_BYTE);
        }
    }

    public record EntityHideOption(boolean equipment, boolean fire, boolean visibility, boolean glowing) {

        public static final EntityHideOption DEFAULT = new EntityHideOption(true, true, true, true);
        public static final EntityHideOption FALSE = builder().build();

        public static @NotNull EntityHideOption composite(@NotNull Stream<EntityHideOption> options) {
            return builder().composite(options).build();
        }

        public static @NotNull EntityHideOption deserialize(@NotNull JsonArray array) {
            return new EntityHideOption(
                array.get(0).getAsBoolean(),
                array.get(1).getAsBoolean(),
                array.get(2).getAsBoolean(),
                array.get(3).getAsBoolean()
            );
        }

        public @NotNull JsonArray serialize() {
            var array = new JsonArray(4);
            array.add(equipment);
            array.add(fire);
            array.add(visibility);
            array.add(glowing);
            return array;
        }

        public static @NotNull Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private boolean equipment;
            private boolean fire;
            private boolean visibility;
            private boolean glowing;

            private Builder() {}

            public @NotNull Builder composite(@NotNull Stream<EntityHideOption> options) {
                options.forEach(this::or);
                return this;
            }

            public @NotNull Builder or(@NotNull EntityHideOption option) {
                equipment |= option.equipment;
                fire |= option.fire;
                visibility |= option.visibility;
                glowing |= option.glowing;
                return this;
            }

            public @NotNull EntityHideOption build() {
                return new EntityHideOption(equipment, fire, visibility, glowing);
            }
        }
    }

    public record TrackerModifier(
        boolean sightTrace,
        boolean damageAnimation,
        boolean damageTint
    ) {
        public static final TrackerModifier DEFAULT = new TrackerModifier(true, true, true);

        public static @NotNull Builder builder() {
            return DEFAULT.toBuilder();
        }

        public @NotNull Builder toBuilder() {
            return new Builder(this);
        }

        public static final class Builder {
            private boolean sightTrace;
            private boolean damageAnimation;
            private boolean damageTint;

            private Builder(@NotNull TrackerModifier modifier) {
                this.sightTrace = modifier.sightTrace;
                this.damageAnimation = modifier.damageAnimation;
                this.damageTint = modifier.damageTint;
            }

            public @NotNull Builder sightTrace(boolean sightTrace) {
                this.sightTrace = sightTrace;
                return this;
            }

            public @NotNull Builder damageAnimation(boolean damageAnimation) {
                this.damageAnimation = damageAnimation;
                return this;
            }

            public @NotNull Builder damageTint(boolean damageTint) {
                this.damageTint = damageTint;
                return this;
            }

            public @NotNull TrackerModifier build() {
                return new TrackerModifier(sightTrace, damageAnimation, damageTint);
            }
        }
    }

    public record TrackerAnimation<T extends Tracker>(
        @NotNull String name,
        int priority,
        @NotNull Class<T> targetClass,
        @NotNull Predicate<? super T> applyCondition,
        @NotNull Function<? super T, AnimationModifier> modifierBuilder,
        @NotNull Consumer<? super T> removeTask,
        @NotNull Consumer<? super T> successTask,
        @NotNull Consumer<? super T> fallbackTask
    ) implements Comparable<TrackerAnimation<T>> {

        private static final Comparator<TrackerAnimation<?>> COMPARATOR = Comparator.comparing((TrackerAnimation<?> animation) -> animation.priority)
            .thenComparing(animation -> animation.name);

        public static @NotNull Builder<Tracker> builder(@NotNull String name) {
            return new Builder<>(name, Tracker.class);
        }

        @ApiStatus.Internal
        public TrackerAnimation {
        }

        @Override
        public int compareTo(@NonNull TrackerAnimation<T> o) {
            return COMPARATOR.compare(this, o);
        }

        boolean play(@NotNull Tracker tracker) {
            return play(tracker, () -> {});
        }

        boolean play(@NotNull Tracker tracker, @NotNull Runnable removeTask) {
            if (!targetClass.isInstance(tracker)) return false;
            var cast = targetClass.cast(tracker);
            if (!applyCondition.test(cast)) return false;
            var result = cast.animate(
                name,
                modifierBuilder.apply(cast),
                () -> {
                    this.removeTask.accept(cast);
                    removeTask.run();
                }
            );
            if (result) successTask.accept(cast);
            else fallbackTask.accept(cast);
            return result;
        }

        public static final class Builder<T extends Tracker> {
            private final String name;
            private final Class<T> targetClass;

            private int priority = 0;
            private @NotNull Predicate<? super T> applyCondition = _ -> true;
            private @NotNull Function<? super T, AnimationModifier> modifierBuilder = _ -> AnimationModifier.DEFAULT;
            private @NotNull Consumer<? super T> removeTask = _ -> {};
            private @NotNull Consumer<? super T> successTask = _ -> {};
            private @NotNull Consumer<? super T> fallbackTask = _ -> {};

            Builder(@NotNull String name, @NotNull Class<T> targetClass) {
                this.name = name;
                this.targetClass = targetClass;
            }

            public @NotNull <R extends T> Builder<R> type(@NotNull Class<R> newTargetClass) {
                return new Builder<>(name, newTargetClass)
                    .priority(priority)
                    .check(applyCondition)
                    .modifier(modifierBuilder)
                    .onRemove(removeTask)
                    .onSuccess(successTask)
                    .onFallback(fallbackTask);
            }

            public @NotNull Builder<T> priority(int priority) {
                this.priority = priority;
                return this;
            }

            public @NotNull Builder<T> check(@NotNull Predicate<? super T> applyCondition) {
                this.applyCondition = Objects.requireNonNull(applyCondition, "applyCondition cannot be null");
                return this;
            }

            public @NotNull Builder<T> modifier(@NotNull Function<? super T, AnimationModifier> modifierBuilder) {
                this.modifierBuilder = Objects.requireNonNull(modifierBuilder, "modifierBuilder cannot be null");
                return this;
            }

            public @NotNull Builder<T> onRemove(@NotNull Consumer<? super T> removeTask) {
                this.removeTask = Objects.requireNonNull(removeTask, "removeTask cannot be null");
                return this;
            }

            public @NotNull Builder<T> onSuccess(@NotNull Consumer<? super T> successTask) {
                this.successTask = Objects.requireNonNull(successTask, "successTask cannot be null");
                return this;
            }

            public @NotNull Builder<T> onFallback(@NotNull Consumer<? super T> fallbackTask) {
                this.fallbackTask = Objects.requireNonNull(fallbackTask, "fallbackTask cannot be null");
                return this;
            }

            public @NotNull TrackerAnimation<T> build() {
                return new TrackerAnimation<>(
                    name, priority, targetClass, applyCondition, modifierBuilder, removeTask, successTask, fallbackTask
                );
            }
        }
    }
}