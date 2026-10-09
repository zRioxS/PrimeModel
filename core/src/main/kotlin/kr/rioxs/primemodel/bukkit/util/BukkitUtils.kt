package kr.rioxs.primemodel.bukkit.util
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitEntity
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitItemStack
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitLivingEntity
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitLocation
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitOfflinePlayer
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitPlayer
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitWorld

import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kr.rioxs.primemodel.api.bukkit.platform.*
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitAdapter.adapt
import kr.rioxs.primemodel.api.platform.*
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.bukkit.PrimeModelPlugin
import kr.rioxs.primemodel.bukkit.util.ADVENTURE_PLATFORM
import kr.rioxs.primemodel.bukkit.util.audience
import kr.rioxs.primemodel.bukkit.util.PLUGIN
import kr.rioxs.primemodel.bukkit.util.registerListener
import kr.rioxs.primemodel.bukkit.util.toRegistry
import kr.rioxs.primemodel.bukkit.util.toTracker
import kr.rioxs.primemodel.util.PLATFORM
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World

fun Entity.wrap() = adapt(this)
fun LivingEntity.wrap() = adapt(this)
fun OfflinePlayer.wrap() = adapt(this)
fun Player.wrap() = adapt(this)
fun Location.wrap() = adapt(this)
fun World.wrap() = adapt(this)
fun ItemStack.wrap() = adapt(this)

fun PlatformEntity.unwarp(): Entity = (this as BukkitEntity).source
fun PlatformLivingEntity.unwarp(): LivingEntity = (this as BukkitLivingEntity).source
fun PlatformOfflinePlayer.unwarp(): OfflinePlayer = (this as BukkitOfflinePlayer).source
fun PlatformPlayer.unwarp(): Player = (this as BukkitPlayer).source
fun PlatformLocation.unwarp(): Location = (this as BukkitLocation).source
fun PlatformWorld.unwarp(): World = (this as BukkitWorld).source
fun PlatformItemStack.unwarp(): ItemStack = (this as BukkitItemStack).source

val PLUGIN get() = PLATFORM as PrimeModelPlugin

fun Entity.toTracker(model: String?) = toRegistry()?.tracker(model)
fun Entity.toRegistry() = PrimeModel.registryOrNull(uniqueId)

fun registerListener(listener: Listener) {
    Bukkit.getPluginManager().registerEvents(listener, PLUGIN)
}

lateinit var ADVENTURE_PLATFORM: BukkitAudiences
fun CommandSender.audience() = ADVENTURE_PLATFORM.sender(this)

fun File.toYaml() = YamlConfiguration.loadConfiguration(this)
fun InputStream.toYaml() = InputStreamReader(this, StandardCharsets.UTF_8).use { reader ->
    reader.buffered().use(YamlConfiguration::loadConfiguration)
}
