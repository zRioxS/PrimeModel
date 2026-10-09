package kr.rioxs.primemodel.api.data.renderer;
import kr.rioxs.primemodel.api.bone.Bones;
import kr.rioxs.primemodel.api.bone.Bones.BoneMovement;
import kr.rioxs.primemodel.api.bone.Bones.BoneRenderContext;

import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.bone.*;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintElement;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.ModelBoundingBox;
import kr.rioxs.primemodel.api.mount.MountController;
import kr.rioxs.primemodel.api.platform.PlatformItemStack;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import kr.rioxs.primemodel.api.util.Utils.TransformedItemStack;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.joml.Vector3f;

import java.util.SequencedMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A group of models.
 */
@RequiredArgsConstructor
public final class RendererGroup {

    private static final Vector3f DEFAULT_SCALE = new Vector3f(1);
    @Getter
    private final BlueprintElement.Bone parent;
    @Getter
    private final Vector3f position;
    private final Vector3f rotation;
    private final TransformedItemStack itemStack;
    @Getter
    @Unmodifiable
    private final SequencedMap<BoneName, RendererGroup> children;
    @Getter
    private final @Nullable ModelBoundingBox hitBox;
    @Getter
    private final @NotNull Vector3f hitBoxPoint;

    @Getter
    private final @NotNull Bones.BoneItemMapper itemMapper;

    @Getter
    private final @NotNull MountController mountController;

    /**
     * Creates group instance.
     * @param scale scale
     * @param itemStack item
     * @param group parent
     * @param children children
     * @param box hit-box
     */
    public RendererGroup(
        float scale,
        @Nullable PlatformItemStack itemStack,
        @NotNull BlueprintElement.Bone group,
        @NotNull SequencedMap<BoneName, RendererGroup> children,
        @Nullable ModelBoundingBox box
    ) {
        this.parent = group;
        this.children = children;
        this.itemStack = TransformedItemStack.of(
            new Vector3f(),
            new Vector3f(),
            new Vector3f(scale),
            itemStack != null ? itemStack : PrimeModel.platform().adapter().air()
        );
        this.itemMapper = name().toItemMapper();
        this.position = group.origin().toBlockScale().toVector();
        this.hitBox = box;
        this.hitBoxPoint = box == null ? new Vector3f() : box.centerPoint();
        this.rotation = group.rotation().toVector();
        if (name().tagged(BoneTags.SEAT)) {
            mountController = PrimeModel.config().defaultMountController();
        } else if (name().tagged(BoneTags.SUB_SEAT)) {
            mountController = MountController.MountControllers.NONE;
        } else mountController = MountController.MountControllers.INVALID;
    }

    public @NotNull Stream<RendererGroup> flatten() {
        return children.isEmpty() ? Stream.of(this) : Stream.concat(
            Stream.of(this),
            children.values().stream().flatMap(RendererGroup::flatten)
        );
    }

    /**
     * Gets name
     * @return name
     */
    public @NotNull BoneName name() {
        return parent.name();
    }

    /**
     * Gets uuid
     * @return uuid
     */
    public @NotNull UUID uuid() {
        return parent.uuid();
    }

    /**
     * Creates entity.
     * @param context context
     * @return entity
     */
    public @NotNull RenderedBone create(@NotNull BoneRenderContext context) {
        return create(context, null);
    }

    private @NotNull RenderedBone create(@NotNull BoneRenderContext context, @Nullable RenderedBone parentBone) {
        return new RenderedBone(
            this,
            parentBone,
            context,
            new BoneMovement(
                parentBone != null ? position.sub(parentBone.getGroup().position, new Vector3f()) : new Vector3f(),
                DEFAULT_SCALE,
                MathUtil.toQuaternion(rotation),
                rotation
            ),
            parent -> children.values().stream().map(value -> value.create(context, parent)).toArray(RenderedBone[]::new)
        );
    }

    /**
     * Gets display item.
     * @return item
     */
    public @NotNull TransformedItemStack getItemStack() {
        return itemStack.copy();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RendererGroup that)) return false;
        return uuid().equals(that.uuid());
    }

    @Override
    public int hashCode() {
        return uuid().hashCode();
    }
}
