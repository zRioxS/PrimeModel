package kr.rioxs.primemodel.bukkit.nms.v26_R1

import kr.rioxs.primemodel.api.bukkit.entity.BaseBukkitEntity
import kr.rioxs.primemodel.api.bukkit.entity.BaseBukkitPlayer
import kr.rioxs.primemodel.api.nms.Profiled
import kr.rioxs.primemodel.api.platform.PlatformPlayer
import kr.rioxs.primemodel.api.player.PlayerSkinParts
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile
import net.minecraft.util.Mth
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.stream.Stream

internal data class BasePlayerImpl(
    private val delegate: CraftPlayer,
    private val profile: () -> ModelProfile,
    private val skinParts: () -> PlayerSkinParts
) : BaseBukkitEntity by BaseEntityImpl(delegate), BaseBukkitPlayer, Profiled by ProfiledImpl(PlayerArmorImpl(delegate), profile, skinParts) {

    override fun entity(): Player = delegate

    override fun updateInventory() {
        delegate.handle.containerMenu.sendAllDataToRemote()
    }

    override fun platform(): PlatformPlayer = delegate.wrap()

    override fun trackedBy(): Stream<PlatformPlayer> = Stream.concat(
        Stream.of(delegate),
        delegate.trackedBy.stream()
    ).map {
        it.wrap()
    }

    override fun bodyYaw(): Float {
        val handle = delegate.handle
        var yaw = -45 * handle.xMovement()
        if (handle.zMovement() < 0) yaw *= -1
        return Mth.wrapDegrees(handle.yHeadRot + yaw)
    }
}
