package kr.rioxs.primemodel.manager
import kr.rioxs.primemodel.api.manager.Managers.Manager

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kr.rioxs.primemodel.api.manager.Managers.ProfileManager
import kr.rioxs.primemodel.api.pack.PackZipper
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSkin
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSupplier
import kr.rioxs.primemodel.profile.DefaultHttpModelProfileSupplier
import kr.rioxs.primemodel.profile.HttpModelProfileSupplier
import kr.rioxs.primemodel.util.PLATFORM
import java.net.URI
import java.util.*

object ProfileManagerImpl : ProfileManager, GlobalManager {

    private val gson = GsonBuilder().create()
    private lateinit var supplier: ModelProfileSupplier

    override fun supplier(): ModelProfileSupplier = supplier

    override fun supplier(supplier: ModelProfileSupplier) {
        this.supplier = supplier
    }

    override fun skin(rawTextures: String): ModelProfileSkin {
        return gson.fromJson(Base64.getDecoder().decode(rawTextures).toString(Charsets.UTF_8), Profile::class.java).run {
            ModelProfileSkin(
                textures.skin?.toURI(),
                textures.cape?.toURI(),
                textures.skin?.metadata?.slim == true,
                rawTextures
            )
        }
    }

    private data class Profile(
        val textures: ProfileTextures
    )

    private data class ProfileTextures(
        @SerializedName("SKIN") val skin: ProfileSkin?,
        @SerializedName("CAPE") val cape: ProfileSkin?,
    )

    private data class ProfileSkin(
        val url: String,
        val metadata: ProfileMetadata
    ) {
        fun toURI(): URI = URI.create(url)
    }

    private data class ProfileMetadata(
        val model: String
    ) {
        val slim get() = model == "slim"
    }

    override fun start() {
        supplier = if (PLATFORM.nms().isProxyOnlineMode) DefaultHttpModelProfileSupplier() else HttpModelProfileSupplier()
    }

    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
    }
}
