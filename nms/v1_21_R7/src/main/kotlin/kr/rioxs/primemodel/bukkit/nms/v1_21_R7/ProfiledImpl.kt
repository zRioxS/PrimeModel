package kr.rioxs.primemodel.bukkit.nms.v1_21_R7

import kr.rioxs.primemodel.api.armor.PlayerArmor
import kr.rioxs.primemodel.api.nms.Profiled
import kr.rioxs.primemodel.api.player.PlayerSkinParts
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile

internal class ProfiledImpl(
    private val playerArmor: PlayerArmor,
    private val modelProfile: () -> ModelProfile,
    private val playerSkinParts: () -> PlayerSkinParts
) : Profiled {

    override fun profile(): ModelProfile = modelProfile()
    override fun armors(): PlayerArmor = playerArmor
    override fun skinParts(): PlayerSkinParts = playerSkinParts()
}
