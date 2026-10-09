package kr.rioxs.primemodel.api.bone;
import kr.rioxs.primemodel.api.bone.Bones.BoneRenderContext;

import kr.rioxs.primemodel.api.data.renderer.RenderSource;
import kr.rioxs.primemodel.api.entity.BaseEntity;
import kr.rioxs.primemodel.api.platform.PlatformItemTransform;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.util.Utils.TransformedItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Function;

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
