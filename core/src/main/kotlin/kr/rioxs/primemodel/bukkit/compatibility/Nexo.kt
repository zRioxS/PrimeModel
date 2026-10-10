package kr.rioxs.primemodel.bukkit.compatibility
import kr.rioxs.primemodel.api.PrimeModel


import kr.rioxs.primemodel.bukkit.util.PLUGIN
import kr.rioxs.primemodel.bukkit.util.registerListener

import com.nexomc.nexo.api.events.resourcepack.NexoPrePackGenerateEvent
import kr.rioxs.primemodel.api.PrimeModelPlatform
import kr.rioxs.primemodel.util.*
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class NexoCompatibility : Compatibility {
    override fun start() {
        if (CONFIG.mergeWithExternalResources()) PLUGIN.skipInitialReload()
        registerListener(object : Listener {
            @EventHandler
            fun NexoPrePackGenerateEvent.generate() {
                if (!CONFIG.mergeWithExternalResources()) return
                when (val result = PLATFORM.reload()) {
                    is PrimeModelPlatform.ReloadResult.Success -> {
                        result.packResult().directory()?.let {
                            addResourcePack(it)
                            info("Successfully merged with Nexo.".toComponent(NamedTextColor.GREEN))
                        }
                    }

                    is PrimeModelPlatform.ReloadResult.OnReload -> {
                        warn("PrimeModel is still on reload!".toComponent(NamedTextColor.RED))
                    }

                    is PrimeModelPlatform.ReloadResult.Failure -> {
                        result.throwable.handleException("Unable to merge with Nexo.")
                    }
                }
            }
        })
    }
}

