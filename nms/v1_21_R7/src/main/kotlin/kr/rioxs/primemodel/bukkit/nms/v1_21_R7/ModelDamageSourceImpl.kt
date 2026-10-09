package kr.rioxs.primemodel.bukkit.nms.v1_21_R7

import kr.rioxs.primemodel.api.event.EventInterfaces.ModelDamageSource
import kr.rioxs.primemodel.api.platform.PlatformEntity
import kr.rioxs.primemodel.api.platform.PlatformLocation
import net.minecraft.world.damagesource.DamageSource
import org.bukkit.craftbukkit.util.CraftLocation

internal class ModelDamageSourceImpl(
    private val source: DamageSource
) : ModelDamageSource {
    override fun getCausingEntity(): PlatformEntity? = source.entity?.bukkitEntity?.wrap()
    override fun getDirectEntity(): PlatformEntity? = source.directEntity?.bukkitEntity?.wrap()
    override fun getDamageLocation(): PlatformLocation? = source.sourcePositionRaw()?.let {
        CraftLocation.toBukkit(it, causingEntity?.unwarp()?.world).wrap()
    }
    override fun getSourceLocation(): PlatformLocation? = source.sourcePosition?.let {
        CraftLocation.toBukkit(it, causingEntity?.unwarp()?.world).wrap()
    }
    override fun isIndirect(): Boolean = !source.isDirect
    override fun getFoodExhaustion(): Float = source.foodExhaustion
    override fun scalesWithDifficulty(): Boolean = source.scalesWithDifficulty()
}
