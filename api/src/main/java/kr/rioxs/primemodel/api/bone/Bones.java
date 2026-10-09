package kr.rioxs.primemodel.api.bone;
import kr.rioxs.primemodel.api.entity.BaseEntity;
import kr.rioxs.primemodel.api.platform.PlatformItemTransform;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.util.Utils.TransformedItemStack;
import kr.rioxs.primemodel.api.util.Utils.InterpolationUtil;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.ApiStatus;
import java.util.Map;
import java.util.function.Function;
import static kr.rioxs.primemodel.api.util.Utils.CollectionUtil.newSequencedAddressingMap;
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
import org.jetbrains.annotations.Unmodifiable;
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

    // ===== BoneItemMapper =====
    /**
 * Item-mapper of bone
 */
public interface BoneItemMapper extends BiFunction<BoneRenderContext, TransformedItemStack, TransformedItemStack> {

    @Override
    @NotNull TransformedItemStack apply(@NotNull BoneRenderContext context, @NotNull TransformedItemStack transformedItemStack);

    /**
     * Empty
     */
    BoneItemMapper EMPTY = new BoneItemMapper() {
        @NotNull
        @Override
        public PlatformItemTransform transform() {
            return PlatformItemTransform.FIXED;
        }

        @Override
        @NotNull
        public TransformedItemStack apply(@NotNull BoneRenderContext context, @NotNull TransformedItemStack transformedItemStack) {
            return transformedItemStack;
        }
    };

    /**
     * Mapped if a render source is player
     * @param transform transformation
     * @param mapper mapper
     * @return bone item mapper
     */
    static @NotNull BoneItemMapper player(@NotNull PlatformItemTransform transform, @NotNull Function<PlatformPlayer, TransformedItemStack> mapper) {
        return new BoneItemMapper() {

            private static final TransformedItemStack AIR = TransformedItemStack.empty();

            @NotNull
            @Override
            public PlatformItemTransform transform() {
                return transform;
            }

            @Override
            public @NotNull TransformedItemStack apply(@NotNull BoneRenderContext context, @NotNull TransformedItemStack transformedItemStack) {
                if (context.source() instanceof RenderSource.Player player) {
                    var get = mapper.apply(player.entity().platform());
                    return get == null ? AIR : get;
                }
                return transformedItemStack;
            }
        };
    }

    /**
     * Mapped if a render source is entity
     * @param transform transformation
     * @param mapper mapper
     * @return bone item mapper
     */
    static @NotNull BoneItemMapper entity(@NotNull PlatformItemTransform transform, @NotNull Function<BaseEntity, TransformedItemStack> mapper) {
        return new BoneItemMapper() {

            private static final TransformedItemStack AIR = TransformedItemStack.empty();

            @NotNull
            @Override
            public PlatformItemTransform transform() {
                return transform;
            }

            @Override
            public @NotNull TransformedItemStack apply(@NotNull BoneRenderContext context, @NotNull TransformedItemStack transformedItemStack) {
                if (context.source() instanceof RenderSource.Entity entity) {
                    var get = mapper.apply(entity.entity());
                    return get == null ? AIR : get;
                }
                return transformedItemStack;
            }
        };
    }

    /**
     * Gets this mapper's display is fixed
     * @return fixed
     */
    default boolean fixed() {
        return transform() == PlatformItemTransform.FIXED;
    }

    /**
     * Gets item display transformation
     * @return transformation
     */
    @NotNull PlatformItemTransform transform();
}


    // ===== BoneIKSolver =====
    /**
 * Bone IK solver
 */
@ApiStatus.Internal
@RequiredArgsConstructor
public static final class BoneIKSolver {

    private static final int MAX_IK_ITERATION = 20;
    private static final Vector3f FROM_VECTOR = new Vector3f(0, -1, 0).normalize();

    private final Map<UUID, RenderedBone> boneMap;
    private final Object2ObjectLinkedOpenHashMap<RenderedBone, IKChain> locators = newSequencedAddressingMap();

    /**
     * Adds some external locator to this solver
     * @param ikSource nullable source
     * @param ikTarget target bone
     * @param locator locator bone
     */
    public void addLocator(@Nullable UUID ikSource, @NotNull UUID ikTarget, @NotNull RenderedBone locator) {
        var target = boneMap.get(ikTarget);
        if (target == null) return;
        var source = ikSource == null ? target.root : boneMap.getOrDefault(ikSource, target.root);
        var chainArray = source.flatten()
            .filter(bone -> !bone.flattenBones().contains(locator) && bone.flattenBones().contains(target))
            .toArray(RenderedBone[]::new);
        if (chainArray.length < 2) return;
        locators.put(locator, new IKChain(chainArray));
    }

    /**
     * Solves ik
     */
    public void solve() {
        solve(null);
    }

    /**
     * Solves ik
     * @param uuid player uuid
     */
    public void solve(@Nullable UUID uuid) {
        if (locators.isEmpty()) return;
        locators.object2ObjectEntrySet().fastForEach(entry -> {
            var locator = entry.getKey();
            var value = entry.getValue();
            fabrik(
                value.movements(uuid),
                value.invertedFirstRotation(uuid),
                value.cache.lengths,
                locator.state(uuid).after().position().get(value.cache.destination)
                    .add(locator.root.group.getPosition())
                    .sub(value.first().root.group.getPosition())
            );
        });
    }

    private record IKChain(@NotNull RenderedBone[] bones, @NotNull IKCache cache) {

        private IKChain(@NotNull RenderedBone[] bones) {
            this(bones, new IKCache(bones.length));
        }

        private @NotNull RenderedBone first() {
            return bones[0];
        }

        private @NotNull Quaternionf invertedFirstRotation(@Nullable UUID uuid) {
            return first().state(uuid).after().rotation().invert(cache.rotation);
        }

        private @NotNull BoneMovement[] movements(@Nullable UUID uuid) {
            var movements = cache.movements;
            for (int i = 0; i < bones.length; i++) {
                movements[i] = bones[i].state(uuid).after();
            }
            return movements;
        }
    }

    private record IKCache(@NotNull BoneMovement[] movements, float[] lengths, @NotNull Vector3f destination, @NotNull Quaternionf rotation) {
        private IKCache(int length) {
            this(new BoneMovement[length], new float[length - 1], new Vector3f(), new Quaternionf());
        }
    }

    private static void fabrik(@NotNull BoneMovement[] bones, @NotNull Quaternionf firstRot, float[] lengths, @NotNull Vector3f target) {
        var first = bones[0].position();
        var last = bones[bones.length - 1].position();

        var vecCache = new Vector3f();
        var rootPos = first.get(vecCache);

        for (int i = 0; i < bones.length - 1; i++) {
            var before = bones[i];
            var after = bones[i + 1];
            lengths[i] = before.position().distance(after.position());
        }
        for (int iter = 0; iter < MAX_IK_ITERATION; iter++) {
            // Forward
            last.set(target);
            for (int i = bones.length - 2; i >= 0; i--) {
                var current = bones[i].position();
                var next = bones[i + 1].position();
                var dist = current.distanceSquared(next);
                if (dist < MathUtil.VECTOR_COMPARISON_EPSILON_SQ) continue;
                InterpolationUtil.lerp(next, current, lengths[i] / (float) Math.sqrt(dist), current);
            }
            // Backward
            first.set(rootPos);
            for (int i = 0; i < bones.length - 1; i++) {
                var current = bones[i].position();
                var next = bones[i + 1].position();
                var dist = current.distanceSquared(next);
                if (dist < MathUtil.VECTOR_COMPARISON_EPSILON_SQ) continue;
                InterpolationUtil.lerp(current, next, lengths[i] / (float) Math.sqrt(dist), next);
            }
            // Check
            if (last.distanceSquared(target) < MathUtil.VECTOR_COMPARISON_EPSILON_SQ) break;
        }
        var rotCache = new Quaternionf();
        for (int i = 0; i < bones.length - 1; i++) {
            var current = bones[i];
            var next = bones[i + 1];

            var dir = next.position().sub(current.position(), vecCache);
            current.rotation().set(rotCache.identity().rotateTo(FROM_VECTOR, dir.normalize()).mul(firstRot).mul(current.rotation()));
        }
    }
}

    // ===== BoneTag =====

    /**
     * A tag of bone.
     *
     * @since 2.0.1
     */
    public interface BoneTag {

        /**
         * The default registry for bone tags.
         * @since 2.0.1
         */
        BoneTagRegistry REGISTRY = new BoneTagRegistry();

        /**
         * Gets tag name.
         * @return tag name
         */
        @NotNull String name();

        /**
         * Gets an item mapper.
         * @return item mapper
         */
        @Nullable BoneItemMapper itemMapper();

        /**
         * Gets a tag list like 'h', 'hi', 'b'.
         * @since 2.0.1
         * @return tags
         */
        @NotNull @Unmodifiable List<String> tags();
    }
}