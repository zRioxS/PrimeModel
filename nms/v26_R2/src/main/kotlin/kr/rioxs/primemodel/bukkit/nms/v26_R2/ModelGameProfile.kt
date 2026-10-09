package kr.rioxs.primemodel.bukkit.nms.v26_R2
import kr.rioxs.primemodel.api.manager.Managers.Manager

import com.mojang.authlib.GameProfile
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.manager.Managers.ProfileManager
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileInfo
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSkin

internal data class ModelGameProfile(
    private val gameProfile: GameProfile
) : ModelProfile {

    private val info = ModelProfileInfo(gameProfile.id, gameProfile.name)
    private val skin by lazy {
        gameProfile.properties["textures"].firstOrNull()?.let {
            PrimeModel.platform().manager(ProfileManager::class.java).skin(it.value)
        } ?: ModelProfileSkin.EMPTY
    }

    override fun info(): ModelProfileInfo = info

    override fun skin(): ModelProfileSkin = skin
}
