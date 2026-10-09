package kr.rioxs.primemodel.api.bone;
import kr.rioxs.primemodel.api.manager.Managers.Manager;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.data.renderer.RenderSource;
import kr.rioxs.primemodel.api.manager.Managers.SkinManager;
import kr.rioxs.primemodel.api.skin.SkinData;
import it.unimi.dsi.fastutil.objects.*;
import kr.rioxs.primemodel.api.nms.HitBoxListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static kr.rioxs.primemodel.api.util.Utils.CollectionUtil.newAddressingMap;

/**
 * Merged bone utility classes.
 */
public final class Bones {

    private Bones() {
        throw new RuntimeException();
    }

    public static final class BoneTagRegistry {

        private static final String TAG_SPLITTER = "_";
        private final Object2ObjectMap<String, BoneTag> byName = newAddressingMap();

        BoneTagRegistry() {
            for (BoneTags value : BoneTags.values()) {
                addTag(value);
            }
        }

        public void addTag(@NotNull BoneTag tag) {
            BoneTag checkDuplicate;
            for (String s : tag.tags()) {
                if ((checkDuplicate = byName.put(s, tag)) != null) throw new RuntimeException("Duplicated tags: " + tag.name() + " between " + checkDuplicate.name());
            }
        }

        public @NotNull Optional<BoneTag> byTagName(@NotNull String tag) {
            return Optional.ofNullable(byTagNameOrNull(tag));
        }

        public @Nullable BoneTag byTagNameOrNull(@NotNull String tag) {
            return byName.get(tag);
        }

        public @NotNull BoneName parse(@NotNull String rawName) {
            rawName = rawName.toLowerCase(Locale.ROOT);
            var tagArray = rawName.split(TAG_SPLITTER);
            if (tagArray.length < 2) return new BoneName(ObjectSets.emptySet(), rawName, rawName);
            var tagList = List.of(tagArray);
            var maxSize = tagList.size() - 1;
            ObjectSet<BoneTag> set = maxSize <= 4 ? new ObjectArraySet<>(maxSize) : new ObjectOpenHashSet<>(maxSize);
            for (String s : tagList) {
                var tag = byTagNameOrNull(s);
                if (tag != null && set.size() < maxSize) set.add(tag);
                else return new BoneName(
                    set.isEmpty() ? ObjectSets.emptySet() : ObjectSets.unmodifiable(set),
                    set.isEmpty() ? rawName : String.join(TAG_SPLITTER, tagList.subList(set.size(), tagList.size())),
                    rawName
                );
            }
            return new BoneName(
                ObjectSets.unmodifiable(set),
                String.join(TAG_SPLITTER, tagList.subList(set.size(), tagList.size())),
                rawName
            );
        }
    }

    public static final class BoneEventDispatcher {
        private final EventFunction builder = new EventFunction();
        private EventFunction applier = builder;

        public synchronized void extend(@NotNull BoneEventDispatcher dispatcher) {
            if (dispatcher == this) throw new UnsupportedOperationException("cannot extend self");
            applier = EventFunction.concat(dispatcher.applier, builder);
        }

        public synchronized void handleCreateHitBox(@NotNull BiFunction<RenderedBone, HitBoxListener.Builder, HitBoxListener.Builder> function) {
            var before = builder.createHitBox;
            builder.createHitBox = (b, l) -> function.apply(b, before.apply(b, l));
        }

        public synchronized void handleStateCreate(@NotNull BiConsumer<RenderedBone, UUID> function) {
            builder.stateCreate = builder.stateCreate.andThen(function);
        }

        public synchronized void handleStateRemove(@NotNull BiConsumer<RenderedBone, UUID> function) {
            builder.stateRemove = builder.stateRemove.andThen(function);
        }

        @NotNull HitBoxListener.Builder onCreateHitBox(@NotNull RenderedBone bone, @NotNull HitBoxListener.Builder builder) {
            return applier.createHitBox.apply(bone, builder);
        }

        void onStateCreated(@NotNull RenderedBone bone, @NotNull UUID uuid) {
            applier.stateCreate.accept(bone, uuid);
        }

        void onStateRemoved(@NotNull RenderedBone bone, @NotNull UUID uuid) {
            applier.stateRemove.accept(bone, uuid);
        }

        private static class EventFunction {
            private BiFunction<RenderedBone, HitBoxListener.Builder, HitBoxListener.Builder> createHitBox;
            private BiConsumer<RenderedBone, UUID> stateCreate;
            private BiConsumer<RenderedBone, UUID> stateRemove;

            EventFunction() {
                this((_, l) -> l, (_, _) -> {}, (_, _) -> {});
            }

            EventFunction(BiFunction<RenderedBone, HitBoxListener.Builder, HitBoxListener.Builder> createHitBox,
                          BiConsumer<RenderedBone, UUID> stateCreate,
                          BiConsumer<RenderedBone, UUID> stateRemove) {
                this.createHitBox = createHitBox;
                this.stateCreate = stateCreate;
                this.stateRemove = stateRemove;
            }

            static @NotNull EventFunction concat(@NotNull EventFunction first, @NotNull EventFunction second) {
                return new EventFunction(
                    (b, l) -> second.createHitBox.apply(b, first.createHitBox.apply(b, l)),
                    (b, u) -> { first.stateCreate.accept(b, u); second.stateCreate.accept(b, u); },
                    (b, u) -> { first.stateRemove.accept(b, u); second.stateRemove.accept(b, u); }
                );
            }
        }
    }

    public interface BoneEventHandler {
        @NotNull Bones.BoneEventDispatcher eventDispatcher();

        default void extend(@NotNull BoneEventHandler eventHandler) {
            eventDispatcher().extend(eventHandler.eventDispatcher());
        }
    }

    public record BoneMovement(
        @NotNull Vector3f position,
        @NotNull Vector3f scale,
        @NotNull Quaternionf rotation,
        @NotNull Vector3f rawRotation
    ) {
        public BoneMovement() {
            this(new Vector3f(), new Vector3f(1), new Quaternionf(), new Vector3f());
        }

        public @NotNull BoneMovement set(@NotNull BoneMovement movement) {
            position.set(movement.position);
            scale.set(movement.scale);
            rotation.set(movement.rotation);
            rawRotation.set(movement.rawRotation);
            return this;
        }

        public @NotNull BoneMovement lerp(@NotNull BoneMovement to, float alpha, @NotNull BoneMovement dest) {
            kr.rioxs.primemodel.api.util.Utils.InterpolationUtil.lerp(position, to.position, alpha, dest.position);
            kr.rioxs.primemodel.api.util.Utils.InterpolationUtil.lerp(scale, to.scale, alpha, dest.scale);
            kr.rioxs.primemodel.api.util.Utils.MathUtil.toQuaternion(kr.rioxs.primemodel.api.util.Utils.InterpolationUtil.lerp(rawRotation, to.rawRotation, alpha, dest.rawRotation), dest.rotation);
            return dest;
        }
    }

    public record BonePosition(
        @NotNull Vector3f globalOffset,
        @NotNull Vector3f localOffset,
        @Nullable UUID state
    ) {
    }

    public record BoneRenderContext(@NotNull RenderSource<?> source, @NotNull SkinData skin) {
        public BoneRenderContext(@NotNull RenderSource<?> source) {
            this(source, PrimeModel.platform().manager(SkinManager.class).fallback());
        }
    }
}
