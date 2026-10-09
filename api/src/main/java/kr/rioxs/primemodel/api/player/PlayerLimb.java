package kr.rioxs.primemodel.api.player;
import kr.rioxs.primemodel.api.bone.Bones;
import kr.rioxs.primemodel.api.bone.Bones.BoneRenderContext;

import kr.rioxs.primemodel.api.armor.PlayerArmor;
import kr.rioxs.primemodel.api.nms.NMSTypes.Profiled;
import kr.rioxs.primemodel.api.platform.PlatformItemTransform;
import kr.rioxs.primemodel.api.skin.SkinData;
import kr.rioxs.primemodel.api.util.Utils.TransformedItemStack;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Player limb data
 */
@RequiredArgsConstructor
@Getter
public enum PlayerLimb {
    /**
     * Head
     */
    HEAD(
        SkinData::head,
        PlatformItemTransform.FIXED
    ),
    /**
     * Right arm
     */
    RIGHT_ARM(
        SkinData::rightArm,
        PlatformItemTransform.FIXED
    ),
    /**
     * Right forearm
     */
    RIGHT_FOREARM(
        (d, _) -> d.rightForeArm(),
        PlatformItemTransform.FIXED
    ),
    /**
     * Left arm
     */
    LEFT_ARM(
        SkinData::leftArm,
        PlatformItemTransform.FIXED
    ),
    /**
     * Left forearm
     */
    LEFT_FOREARM(
        (d, _) -> d.leftForeArm(),
        PlatformItemTransform.FIXED
    ),
    /**
     * Hip
     */
    HIP(
        SkinData::hip,
        PlatformItemTransform.FIXED
    ),
    /**
     * Waist
     */
    WAIST(
        SkinData::waist,
        PlatformItemTransform.FIXED
    ),
    /**
     * Chest
     */
    CHEST(
        SkinData::chest,
        PlatformItemTransform.FIXED
    ),
    /**
     * Right leg
     */
    RIGHT_LEG(
        SkinData::rightLeg,
        PlatformItemTransform.FIXED
    ),
    /**
     * Right foreleg
     */
    RIGHT_FORELEG(
        SkinData::rightForeLeg,
        PlatformItemTransform.FIXED
    ),
    /**
     * Left leg
     */
    LEFT_LEG(
        SkinData::leftLeg,
        PlatformItemTransform.FIXED
    ),
    /**
     * Left foreleg
     */
    LEFT_FORELEG(
        SkinData::leftForeLeg,
        PlatformItemTransform.FIXED
    ),
    ;

    private final @NotNull BiFunction<SkinData, PlayerArmor, TransformedItemStack> skinMapper;
    private final @NotNull PlatformItemTransform transform;

    @Getter
    private final @NotNull LimbItemMapper itemMapper = new LimbItemMapper(this::createItem);

    /**
     * Generates transformed item from player
     * @param context context
     * @return item
     */
    public @NotNull TransformedItemStack createItem(@NotNull BoneRenderContext context) {
        return skinMapper.apply(context.skin(), context.source() instanceof Profiled profiled ? profiled.armors() : PlayerArmor.EMPTY);
    }

    /**
     * Limb item mapper
     */
    @RequiredArgsConstructor
    public class LimbItemMapper implements Bones.BoneItemMapper {

        private final Function<BoneRenderContext, TransformedItemStack> playerMapper;

        @NotNull
        @Override
        public PlatformItemTransform transform() {
            return transform;
        }

        @Override
        public @NotNull TransformedItemStack apply(@NotNull BoneRenderContext context, @NotNull TransformedItemStack transformedItemStack) {
            return playerMapper.apply(context);
        }
    }
}
