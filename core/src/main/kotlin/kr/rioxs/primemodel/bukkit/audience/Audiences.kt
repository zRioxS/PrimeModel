package kr.rioxs.primemodel.bukkit.audience
import kr.rioxs.primemodel.api.PrimeModel

import kr.rioxs.primemodel.bukkit.util.audience
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

interface BukkitAudience : Audience {
    val sender: CommandSender
}

class AudienceSender(
    override val sender: CommandSender
) : BukkitAudience {

    private val audience = sender.audience()

    override fun sendMessage(message: Component) {
        audience.sendMessage(message)
    }

    override fun showBossBar(bar: BossBar) {
        audience.showBossBar(bar)
    }

    override fun hideBossBar(bar: BossBar) {
        audience.hideBossBar(bar)
    }
}

class AudiencePlayer(
    override val sender: Player
) : BukkitAudience {

    private val audience = sender.audience()

    override fun sendMessage(message: Component) {
        audience.sendMessage(message)
    }

    override fun showBossBar(bar: BossBar) {
        audience.showBossBar(bar)
    }

    override fun hideBossBar(bar: BossBar) {
        audience.hideBossBar(bar)
    }
}
