package kr.rioxs.primemodel.bukkit.compatibility.skinsrestorer
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.manager.Managers.Manager

import kr.rioxs.primemodel.bukkit.compatibility.Compatibility

import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileInfo
import kr.rioxs.primemodel.bukkit.util.wrap
import kr.rioxs.primemodel.manager.ProfileManagerImpl
import kr.rioxs.primemodel.manager.SkinManagerImpl
import kr.rioxs.primemodel.util.PLATFORM
import net.skinsrestorer.api.SkinsRestorerProvider
import net.skinsrestorer.api.event.SkinApplyEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class SkinsRestorerCompatibility : Compatibility {

    private val manager = SkinsRestorerProvider.get()

    override fun start() {
        manager.eventBus.subscribe(PLATFORM, SkinApplyEvent::class.java) {
            val player = it.getPlayer(Player::class.java)
            SkinManagerImpl.removeCache(ModelProfile.of(player.wrap()))
        }
        ProfileManagerImpl.supplier {
            SkinsRestorerProfile(it)
        }
    }

    private inner class SkinsRestorerProfile(
        private val info: ModelProfileInfo
    ) : ModelProfile.Uncompleted {
        override fun info(): ModelProfileInfo = info

        override fun complete(): CompletableFuture<ModelProfile> = CompletableFuture.supplyAsync {
            manager.playerStorage
                .getSkinForPlayer(
                    info.id,
                    info.name,
                    Bukkit.getOnlineMode()
                ).map { skin ->
                    ModelProfile.of(
                        info,
                        ProfileManagerImpl.skin(skin.value)
                    )
                }.orElse(null)
        }

    }
}

