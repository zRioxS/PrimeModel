package kr.rioxs.primemodel.api.tracker;
import kr.rioxs.primemodel.api.tracker.TrackerUtils.TrackerAnimation;

import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator;
import kr.rioxs.primemodel.api.animation.Animations.AnimationModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import static kr.rioxs.primemodel.api.util.Utils.CollectionUtil.newAddressingMap;

/**
 * Merged utility class for tracker animations.
 * Originally split across {@code TrackerExtraAnimation} and {@code TrackerBuiltInAnimation}.
 */
public final class TrackerAnimations {

    private static final Map<String, TrackerAnimation<?>> BY_NAME = newAddressingMap();
    private static final SortedSet<TrackerAnimation<?>> BY_PRIORITY = new TreeSet<>();

    public static <T extends Tracker> @NotNull TrackerAnimation<T> register(@NotNull String name, @NotNull Function<TrackerAnimation.Builder<Tracker>, TrackerAnimation.Builder<T>> builderFunction) {
        var animation = builderFunction.apply(TrackerAnimation.builder(name)).build();
        register(animation);
        return animation;
    }

    private static void register(@NotNull TrackerAnimation<?> animation) {
        synchronized (BY_NAME) {
            if (BY_NAME.put(animation.name(), animation) != null) throw new IllegalStateException("Duplicate animation name: " + animation.name());
            BY_PRIORITY.add(animation);
        }
    }

    static void play(@NotNull Tracker tracker) {
        BY_PRIORITY.forEach(animation -> animation.play(tracker));
    }

    // ===== Built-in animations (from TrackerBuiltInAnimation) =====

    public static final TrackerAnimation<Tracker> IDLE = register("idle", b -> b.modifier(_ -> AnimationModifier.builder()
        .start(6)
        .type(AnimationIterator.Type.LOOP)
        .build()
    ));

    public static final TrackerAnimation<EntityTracker> WALK = register("walk", b -> b.type(EntityTracker.class)
        .modifier(tracker -> {
            var property = tracker.registry().animationProperty;
            return AnimationModifier.builder()
                .start(6)
                .predicate(property.onWalk)
                .speed(tracker.modifier.damageAnimation() ? property.walkSpeed : null)
                .type(AnimationIterator.Type.LOOP)
                .build();
        }));

    public static final TrackerAnimation<EntityTracker> IDLE_FLY = register("idle_fly", b -> b.type(EntityTracker.class)
        .modifier(tracker -> {
            var property = tracker.registry().animationProperty;
            return AnimationModifier.builder()
                .start(6)
                .predicate(property.onFly)
                .type(AnimationIterator.Type.LOOP)
                .build();
        }));

    public static final TrackerAnimation<EntityTracker> WALK_FLY = register("walk_fly", b -> b.type(EntityTracker.class)
        .modifier(tracker -> {
            var property = tracker.registry().animationProperty;
            return AnimationModifier.builder()
                .start(6)
                .predicate(() -> property.onFly.getAsBoolean() && property.onWalk.getAsBoolean())
                .type(AnimationIterator.Type.LOOP)
                .build();
        }));

    public static final TrackerAnimation<EntityTracker> SPAWN = register("spawn", b -> b.type(EntityTracker.class)
        .modifier(_ -> AnimationModifier.DEFAULT_WITH_PLAY_ONCE));

    // ===== Extra animations (from TrackerExtraAnimation) =====

    public static final TrackerAnimation<EntityTracker> DEATH = TrackerAnimation.builder("death")
        .type(EntityTracker.class)
        .modifier(_ -> AnimationModifier.DEFAULT_WITH_PLAY_ONCE)
        .onRemove(Tracker::close)
        .onSuccess(tracker -> tracker.forRemoval(true))
        .build();

    public static final TrackerAnimation<EntityTracker> DAMAGE = TrackerAnimation.builder("damage")
        .type(EntityTracker.class)
        .modifier(_ -> AnimationModifier.DEFAULT_WITH_PLAY_ONCE)
        .build();

    public static final TrackerAnimation<EntityTracker> JUMP = TrackerAnimation.builder("jump")
        .type(EntityTracker.class)
        .modifier(_ -> AnimationModifier.DEFAULT_WITH_PLAY_ONCE)
        .build();

    private TrackerAnimations() {
        throw new IllegalStateException("Utility class");
    }
}