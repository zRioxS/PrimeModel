package kr.rioxs.primemodel.api.animation;
import kr.rioxs.primemodel.api.bone.Bones.BoneMovement;
import com.google.gson.annotations.SerializedName;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.tracker.Tracker;
import kr.rioxs.primemodel.api.util.Utils.Collections2.PriorityMap;
import kr.rioxs.primemodel.api.util.Utils.FunctionUtil;
import kr.rioxs.primemodel.api.util.Utils.Functions.FloatFunction;
import kr.rioxs.primemodel.api.util.Utils.Functions.FloatSupplier;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import kr.rioxs.primemodel.api.util.Utils.VectorInterpolator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import static kr.rioxs.primemodel.api.util.Utils.MathUtil.isNotZero;

public final class Animations {

    private Animations() {
        throw new RuntimeException();
    }

    public sealed interface AnimationIterator<T extends AnimationIterator.Timed> extends Iterator<T> {

        interface Timed extends Comparable<Timed> {
            @Override
            default int compareTo(@NotNull Timed o) {
                return MathUtil.FRAME_COMPARATOR.compare(time(), o.time());
            }
            float time();
        }

        interface TimedStorage<T extends Timed> {
            static <T extends Timed> @NotNull TimedStorage<T> listOf(@NotNull List<T> list) {
                return new ListDelegate<>(list);
            }
            @NotNull T get(int index);
            int size();
            @NotNull T getLast();

            record ListDelegate<T extends Timed>(@NotNull List<T> list) implements TimedStorage<T> {
                @Override public @NonNull T get(int index) { return list.get(index); }
                @Override public int size() { return list.size(); }
                @Override public @NonNull T getLast() { return list.getLast(); }
            }
        }

        interface AnimationProgress extends Timed {
            AnimationProgress EMPTY = empty(0);

            boolean skipInterpolation();
            boolean globalRotation();

            static @NotNull AnimationProgress empty(float time) {
                return new EmptyProgress(time, false, false);
            }

            static @NotNull AnimationProgress empty(float time, boolean skipInterpolation, boolean globalRotation) {
                return new EmptyProgress(time, skipInterpolation, globalRotation);
            }

            default @NotNull AnimationProgress toEmpty() {
                var time = time();
                return time <= 0 ? EMPTY : empty(time, skipInterpolation(), globalRotation());
            }

            static @NotNull TimedStorage<AnimationProgress> emptyStorage(float time) {
                return TimedStorage.listOf(List.of(EMPTY, empty(time)));
            }

            @NotNull BoneMovement animate(@NotNull BoneMovement movement, @NotNull BoneMovement dest);

            record EmptyProgress(float time, boolean skipInterpolation, boolean globalRotation) implements AnimationProgress {
                @Override public @NotNull BoneMovement animate(@NotNull BoneMovement movement, @NotNull BoneMovement dest) {
                    return dest.set(movement);
                }
                @Override public @NotNull AnimationProgress toEmpty() { return this; }
            }
        }

        void clear();
        @NotNull Type type();

        enum Type {
            @SerializedName("once") PLAY_ONCE {
                @Override public @NotNull <T extends Timed> AnimationIterator<T> create(@NotNull TimedStorage<T> keyframes) {
                    return new PlayOnce<>(keyframes);
                }
            },
            @SerializedName("loop") LOOP {
                @Override public @NotNull <T extends Timed> AnimationIterator<T> create(@NotNull TimedStorage<T> keyframes) {
                    return new Loop<>(keyframes);
                }
            },
            @SerializedName("hold") HOLD_ON_LAST {
                @Override public @NotNull <T extends Timed> AnimationIterator<T> create(@NotNull TimedStorage<T> keyframes) {
                    return new HoldOnLast<>(keyframes);
                }
            };
            public abstract <T extends Timed> @NotNull AnimationIterator<T> create(@NotNull TimedStorage<T> keyframes);
        }

        final class PlayOnce<T extends Timed> implements AnimationIterator<T> {
            private final TimedStorage<T> keyframe;
            private int index = 0;
            private PlayOnce(TimedStorage<T> keyframe) { this.keyframe = keyframe; }
            public static <T extends Timed> @NotNull PlayOnce<T> of(TimedStorage<T> keyframe) { return new PlayOnce<>(keyframe); }
            @Override public void clear() { index = Integer.MAX_VALUE; }
            @Override public boolean hasNext() { return index < keyframe.size(); }
            @Override public @NotNull T next() { return keyframe.get(index++); }
            @NotNull @Override public Type type() { return Type.PLAY_ONCE; }
        }

        final class HoldOnLast<T extends Timed> implements AnimationIterator<T> {
            private final TimedStorage<T> keyframe;
            private int index = 0;
            private HoldOnLast(TimedStorage<T> keyframe) { this.keyframe = keyframe; }
            public static <T extends Timed> @NotNull HoldOnLast<T> of(TimedStorage<T> keyframe) { return new HoldOnLast<>(keyframe); }
            @Override public void clear() { index = 0; }
            @Override public boolean hasNext() { return true; }
            @Override public @NotNull T next() {
                if (index >= keyframe.size()) return keyframe.getLast();
                return keyframe.get(index++);
            }
            @NotNull @Override public Type type() { return Type.HOLD_ON_LAST; }
        }

        final class Loop<T extends Timed> implements AnimationIterator<T> {
            private final TimedStorage<T> keyframe;
            private int index = 0;
            private Loop(TimedStorage<T> keyframe) { this.keyframe = keyframe; }
            public static <T extends Timed> @NotNull Loop<T> of(TimedStorage<T> keyframe) { return new Loop<>(keyframe); }
            @Override public void clear() { index = 0; }
            @Override public boolean hasNext() { return true; }
            @Override public @NotNull T next() {
                if (index >= keyframe.size()) index = 0;
                return keyframe.get(index++);
            }
            @NotNull @Override public Type type() { return Type.LOOP; }
        }
    }

    public record AnimationKeyframe(@NotNull AnimationIterator.AnimationProgress[] progresses) implements AnimationIterator.TimedStorage<AnimationIterator.AnimationProgress> {

        public record VectorPoint(
            @NotNull FloatFunction<Vector3f> function,
            float time,
            @NotNull BezierConfig bezier,
            @NotNull VectorInterpolator interpolator
        ) implements AnimationIterator.Timed {
            private static final Vector3f ZERO = new Vector3f();

            public static final VectorPoint EMPTY = new VectorPoint(
                FloatFunction.of(ZERO), 0F,
                new BezierConfig(null, null, null, null),
                VectorInterpolator.LINEAR
            );

            public @NotNull Vector3f vector() { return vector(time); }
            public @NotNull Vector3f vector(float time) { return function.apply(time); }
            public boolean isContinuous() { return interpolator.isContinuous(); }

            public record BezierConfig(
                @Nullable Vector3f leftTime, @Nullable Vector3f leftValue,
                @Nullable Vector3f rightTime, @Nullable Vector3f rightValue
            ) {
                public @NotNull Vector3f leftTime() { return leftTime != null ? leftTime : ZERO; }
                public @NotNull Vector3f leftValue() { return leftValue != null ? leftValue : ZERO; }
                public @NotNull Vector3f rightTime() { return rightTime != null ? rightTime : ZERO; }
                public @NotNull Vector3f rightValue() { return rightValue != null ? rightValue : ZERO; }
            }

            @Override public boolean equals(Object o) {
                if (!(o instanceof VectorPoint that)) return false;
                return Float.compare(time, that.time) == 0;
            }
            @Override public int hashCode() { return Float.hashCode(time); }
        }

        public static @NotNull Builder builder(int size, boolean rotateGlobal) { return new Builder(size, rotateGlobal); }

        private record AnimationArray(
            boolean rotateGlobal, boolean[] skipInterpolation, float[] times,
            float[] position, float[] scale, float[] rotation
        ) {
            AnimationArray(int size, boolean rotateGlobal) {
                this(rotateGlobal, new boolean[size], new float[size],
                    new float[size * 3], new float[size * 3], new float[size * 3]);
            }
        }

        public static final class Builder {
            private final AnimationArray set;
            private final AnimationIterator.AnimationProgress[] progresses;
            private int index = 0;

            private Builder(int size, boolean rotateGlobal) {
                set = new AnimationArray(size, rotateGlobal);
                progresses = new AnimationIterator.AnimationProgress[size];
            }

            public void write(float time, @NotNull Vector3f position, @NotNull Vector3f scale,
                              @NotNull Vector3f rotation, boolean skipInterpolation) {
                var i = index++;
                var x = i * 3;
                var y = x + 1;
                var z = x + 2;
                set.times[i] = time;
                set.position[x] = position.x;
                set.position[y] = position.y;
                set.position[z] = position.z;
                set.scale[x] = scale.x + 1;
                set.scale[y] = scale.y + 1;
                set.scale[z] = scale.z + 1;
                set.rotation[x] = rotation.x;
                set.rotation[y] = rotation.y;
                set.rotation[z] = rotation.z;
                set.skipInterpolation[i] = skipInterpolation;
                this.progresses[i] = isNotZero(position) || isNotZero(scale) || isNotZero(rotation)
                    ? new ArrayProgress(set, i)
                    : AnimationIterator.AnimationProgress.empty(time, skipInterpolation, set.rotateGlobal);
            }

            public @NotNull AnimationKeyframe build() { return new AnimationKeyframe(progresses); }
        }

        private record ArrayProgress(@NotNull AnimationArray array, int index) implements AnimationIterator.AnimationProgress {
            @Override public @NotNull BoneMovement animate(@NotNull BoneMovement movement, @NotNull BoneMovement dest) {
                var destPos = movement.position().get(dest.position());
                var destScl = movement.scale().get(dest.scale());
                var destRot = movement.rotation().get(dest.rotation());
                var destRawRot = movement.rawRotation().get(dest.rawRotation());
                var position = array.position;
                var scale = array.scale;
                var rotation = array.rotation;
                var x = index * 3;
                var y = x + 1;
                var z = x + 2;
                destPos.add(position[x], position[y], position[z]);
                destScl.mul(scale[x], scale[y], scale[z]);
                MathUtil.toQuaternion(destRawRot.add(rotation[x], rotation[y], rotation[z]), destRot);
                return dest;
            }
            @Override public boolean skipInterpolation() { return array.skipInterpolation[index]; }
            @Override public boolean globalRotation() { return array.rotateGlobal; }
            @Override public float time() { return array.times[index]; }
        }

        @Override public @NotNull AnimationIterator.AnimationProgress get(int i) { return progresses[i]; }
        @Override public @NotNull AnimationIterator.AnimationProgress getLast() { return get(progresses.length - 1); }
        @Override public int size() { return progresses.length; }

        public @NotNull AnimationIterator.TimedStorage<AnimationIterator.AnimationProgress> toEmpty() {
            return AnimationIterator.TimedStorage.listOf(Arrays.stream(progresses)
                .map(AnimationIterator.AnimationProgress::toEmpty)
                .toList());
        }
    }

    public record AnimationModifier(
        @Nullable BooleanSupplier predicate,
        int start,
        int end,
        int priority,
        @Nullable AnimationIterator.Type type,
        @Nullable FloatSupplier speed,
        @Nullable Boolean override,
        @Nullable PlatformPlayer player
    ) {
        public enum AnimationOverrideState {
            NOT_MATCHED, MATCHED;
            public boolean shouldSkip() { return this == NOT_MATCHED; }
        }

        public record RunningAnimation(@NotNull String name, @NotNull AnimationIterator.Type type) {}

        public static final AnimationModifier DEFAULT = builder().build();
        public static final AnimationModifier DEFAULT_WITH_PLAY_ONCE = builder().type(AnimationIterator.Type.PLAY_ONCE).build();

        public static @NotNull Builder builder() { return new Builder(); }

        public @NotNull Builder toBuilder() {
            return builder().predicate(predicate).start(start).end(end).type(type).speed(speed).override(override).player(player);
        }

        public static final class Builder {
            private BooleanSupplier predicate = null;
            private int start = 1;
            private int end = 0;
            private int priority = 0;
            private AnimationIterator.Type type = null;
            private FloatSupplier speed = null;
            private Boolean override = null;
            private PlatformPlayer player = null;

            private Builder() {}

            public @NotNull Builder predicate(@Nullable BooleanSupplier predicate) {
                this.predicate = predicate == null ? null : FunctionUtil.throttleTickBoolean(predicate);
                return this;
            }
            public @NotNull Builder start(int start) { this.start = start; return this; }
            public @NotNull Builder end(int end) { this.end = end; return this; }
            public @NotNull Builder priority(int priority) { this.priority = priority; return this; }
            public @NotNull Builder type(@Nullable AnimationIterator.Type type) { this.type = type; return this; }
            public @NotNull Builder speed(float speed) { this.speed = toSupplier(speed); return this; }
            public @NotNull Builder speed(@Nullable FloatSupplier speed) {
                this.speed = speed == null ? null : FunctionUtil.throttleTickFloat(speed);
                return this;
            }
            public @NotNull Builder override(@Nullable Boolean override) { this.override = override; return this; }
            public @NotNull Builder player(@Nullable PlatformPlayer player) { this.player = player; return this; }

            public @NotNull Builder mergeNotDefault(@NotNull AnimationModifier modifier) {
                if (modifier.predicate != null) predicate(modifier.predicate);
                if (modifier.start >= 0) start(modifier.start);
                if (modifier.end >= 0) end(modifier.end);
                if (modifier.type != null) type(modifier.type);
                if (modifier.speed != null) speed(modifier.speed);
                if (modifier.override != null) override(modifier.override);
                if (modifier.player != null) player(modifier.player);
                return this;
            }

            public @NotNull AnimationModifier build() {
                return new AnimationModifier(predicate, start, end, priority, type, speed, override, player);
            }
        }

        public AnimationModifier(int start, int end) { this(start, end, null, null); }
        public AnimationModifier(int start, int end, float speedValue) { this(start, end, null, FloatSupplier.of(speedValue)); }
        public AnimationModifier(int start, int end, @Nullable FloatSupplier supplier) { this(start, end, null, supplier); }
        public AnimationModifier(int start, int end, @Nullable AnimationIterator.Type type) { this(start, end, type, null); }
        public AnimationModifier(int start, int end, @Nullable AnimationIterator.Type type, @Nullable FloatSupplier speed) {
            this(null, start, end, type, speed);
        }
        public AnimationModifier(@Nullable BooleanSupplier predicate, int start, int end, @Nullable AnimationIterator.Type type, @Nullable FloatSupplier speed) {
            this(predicate, start, end, 0, type, speed, null, null);
        }

        public @NotNull AnimationIterator.Type type(@NotNull AnimationIterator.Type defaultType) {
            return type != null ? type : defaultType;
        }
        public float speedValue() { return speed != null ? speed.getAsFloat() : 1F; }
        public boolean predicateValue() { return predicate == null || predicate.getAsBoolean(); }
        public boolean override(boolean original) { return override != null ? override : original; }

        private static @Nullable FloatSupplier toSupplier(float speed) {
            return MathUtil.isSimilar(speed, 1F) ? null : FloatSupplier.of(speed);
        }
    }

    @ApiStatus.Internal
    public static final class AnimationStateHandler<T extends AnimationIterator.Timed> {

        private final T initialValue;
        private final BiConsumer<T, T> setConsumer;
        private final PriorityMap<String, TreeIterator> animators = new PriorityMap<>();
        private volatile boolean forceUpdate;
        private int delay;
        private volatile TreeIterator currentIterator = null;
        private volatile T beforeKeyframe = null, afterKeyframe = null;

        public AnimationStateHandler(T initialValue, BiConsumer<T, T> setConsumer) {
            this.initialValue = initialValue;
            this.setConsumer = setConsumer;
        }

        public boolean keyframeFinished() { return delay <= 0; }
        public T beforeKeyframe() { return beforeKeyframe; }
        public T afterKeyframe() { return afterKeyframe; }
        public @NotNull T beforeKeyframe(@NotNull T defaultValue) {
            var value = beforeKeyframe;
            return value != null ? value : defaultValue;
        }
        public @NotNull T afterKeyframe(@NotNull T defaultValue) {
            var value = afterKeyframe;
            return value != null ? value : defaultValue;
        }
        public @Nullable AnimationModifier.RunningAnimation runningAnimation() {
            var iterator = currentIterator;
            return iterator != null ? iterator.animation : null;
        }
        public int delay() { return delay; }

        public boolean tick() { return tick(() -> {}); }
        public boolean tick(@NotNull Runnable ifEmpty) {
            delay--;
            if (animators.isEmpty()) { ifEmpty.run(); return false; }
            return shouldUpdateAnimation() && updateAnimation();
        }

        public float progress() {
            var frame = frame();
            return frame == 0 ? 0 : Math.clamp((float) delay / frame, 0F, 1F);
        }

        private boolean shouldUpdateAnimation() {
            return (afterKeyframe != null && keyframeFinished()) || delay % Tracker.MINECRAFT_TICK_MULTIPLIER == 0 || forceUpdate;
        }

        private boolean updateAnimation() {
            synchronized (animators) {
                var iterator = animators.valueIterator();
                while (iterator.hasNext()) {
                    var next = iterator.next();
                    if (!next.getAsBoolean()) continue;
                    if (currentIterator == null) {
                        if (updateKeyframe(iterator, next)) { currentIterator = next; return setAfterKeyframe(next.next()); }
                    } else if (currentIterator != next) {
                        if (updateKeyframe(iterator, next)) { currentIterator.clear(); currentIterator = next; return setAfterKeyframe(next.next()); }
                    } else if (keyframeFinished()) {
                        if (updateKeyframe(iterator, next)) { return setAfterKeyframe(next.next()); }
                    } else { return false; }
                }
            }
            return setAfterKeyframe(null);
        }

        private boolean updateKeyframe(@NotNull Iterator<TreeIterator> iterator, @NotNull TreeIterator next) {
            if (!next.hasNext()) { next.removeTask.run(); iterator.remove(); return false; }
            else { return true; }
        }

        private boolean setAfterKeyframe(@Nullable T next) {
            if (afterKeyframe == next) return false;
            setConsumer.accept(beforeKeyframe = afterKeyframe, afterKeyframe = next);
            delay = Math.round(frame());
            return true;
        }

        public void addAnimation(@NotNull String name, @NotNull AnimationIterator<T> iterator, @NotNull AnimationModifier modifier, @NotNull Runnable removeTask) {
            synchronized (animators) {
                animators.put(name, new TreeIterator(name, iterator, modifier, removeTask), modifier.priority());
                forceUpdate = true;
            }
        }

        public void replaceAnimation(@NotNull String name, @NotNull AnimationIterator<T> iterator, @NotNull AnimationModifier modifier) {
            synchronized (animators) {
                animators.replace(name, v -> new TreeIterator(name, iterator, v.modifier.toBuilder()
                    .mergeNotDefault(modifier).build(), v.removeTask));
                forceUpdate = true;
            }
        }

        public boolean stopAnimation(@NotNull String name) {
            synchronized (animators) {
                if (animators.remove(name) != null) { forceUpdate = true; return true; }
            }
            return false;
        }

        public float frame() {
            return afterKeyframe != null ? MathUtil.MINECRAFT_TICKS_PER_SECOND * Tracker.MINECRAFT_TICK_MULTIPLIER * (currentIterator.time + MathUtil.FRAME_EPSILON) : 0F;
        }

        private class TreeIterator implements BooleanSupplier {
            private final AnimationModifier.RunningAnimation animation;
            private final AnimationIterator<T> iterator;
            private final AnimationModifier modifier;
            private final Runnable removeTask;
            private final T previous;
            private boolean started = false;
            private boolean ended = false;
            private float time = 0;

            public TreeIterator(String name, AnimationIterator<T> iterator, AnimationModifier modifier, Runnable removeTask) {
                animation = new AnimationModifier.RunningAnimation(name, iterator.type());
                this.iterator = iterator;
                this.modifier = modifier;
                this.removeTask = removeTask;
                previous = afterKeyframe != null ? afterKeyframe : initialValue;
            }

            @Override public boolean getAsBoolean() { return modifier.predicateValue(); }
            public boolean hasNext() { return iterator.hasNext() || (modifier.end() > 0 && !ended); }
            public @NotNull T next() {
                if (!started) {
                    started = true;
                    time = (float) modifier.start() / MathUtil.MINECRAFT_TICKS_PER_SECOND;
                    return iterator.next();
                }
                if (!iterator.hasNext()) {
                    ended = true;
                    time = (float) modifier.end() / MathUtil.MINECRAFT_TICKS_PER_SECOND;
                    return previous;
                }
                var nxt = iterator.next();
                time = nxt.time() / modifier.speedValue();
                return nxt;
            }
            public void clear() { iterator.clear(); started = ended = !iterator.hasNext(); }
        }
    }
}
