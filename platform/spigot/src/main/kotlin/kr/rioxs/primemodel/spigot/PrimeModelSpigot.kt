package kr.rioxs.primemodel.spigot

import kr.rioxs.primemodel.api.PrimeModelPlatform
import kr.rioxs.primemodel.bukkit.PrimeModelPlugin
import kr.rioxs.primemodel.util.toComponent
import kr.rioxs.primemodel.util.warn
import org.bukkit.Bukkit

@Suppress("UNUSED")
class PrimeModelSpigot : PrimeModelPlugin() {

    override fun onEnable() {
        if (IS_PAPER) {
            warn(
                "You're using Paper, so you have to use Paper jar!".toComponent(),
                "Please download Paper jar from Modrinth! (https://modrinth.com/plugin/PrimeModel)".toComponent()
            )
            return Bukkit.getPluginManager().disablePlugin(this)
        }
        super.onEnable()
    }

    override fun jarType(): PrimeModelPlatform.JarType {
        return PrimeModelPlatform.JarType.SPIGOT
    }
}
