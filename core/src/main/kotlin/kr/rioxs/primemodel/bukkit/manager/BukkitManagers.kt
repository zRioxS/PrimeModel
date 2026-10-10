package kr.rioxs.primemodel.bukkit.manager
import kr.rioxs.primemodel.api.manager.Managers.Manager


import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityJumpEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import it.unimi.dsi.fastutil.objects.ReferenceSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kr.rioxs.primemodel.api.bukkit.PrimeModelBukkit
import kr.rioxs.primemodel.api.manager.Managers.PlayerManager
import kr.rioxs.primemodel.api.nms.HitBox
import kr.rioxs.primemodel.api.nms.PlayerChannelHandler
import kr.rioxs.primemodel.api.pack.PackZipper
import kr.rioxs.primemodel.api.platform.PlatformPlayer
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.tracker.EntityTracker
import kr.rioxs.primemodel.api.tracker.EntityTrackerRegistry
import kr.rioxs.primemodel.api.tracker.Tracker
import kr.rioxs.primemodel.api.tracker.Tracker.TrackerAnimations
import kr.rioxs.primemodel.bukkit.compatibility.citizens.CitizensCompatibility
import kr.rioxs.primemodel.bukkit.compatibility.NexoCompatibility
import kr.rioxs.primemodel.bukkit.compatibility.skinsrestorer.SkinsRestorerCompatibility
import kr.rioxs.primemodel.bukkit.util.PLUGIN
import kr.rioxs.primemodel.bukkit.util.registerListener
import kr.rioxs.primemodel.bukkit.util.wrap
import kr.rioxs.primemodel.manager.GlobalManager
import kr.rioxs.primemodel.manager.ReloadPipeline
import kr.rioxs.primemodel.manager.SkinManagerImpl
import kr.rioxs.primemodel.util.handleFailure
import kr.rioxs.primemodel.util.info
import kr.rioxs.primemodel.util.PLATFORM
import kr.rioxs.primemodel.util.toComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.world.EntitiesUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffectType

object CompatibilityManager : GlobalManager {

    private val compatibilities = mutableMapOf(
        "Citizens" to {
            CitizensCompatibility()
        },
        "SkinsRestorer" to {
            SkinsRestorerCompatibility()
        },
        "Nexo" to {
            NexoCompatibility()
        }
    )

    override fun start() {
        Bukkit.getPluginManager().run {
            compatibilities.entries.removeIf { (k, v) ->
                if (isPluginEnabled(k)) {
                    v().start()
                    k.hookMessage()
                    true
                } else false
            }
        }
        registerListener(object : Listener {
            @EventHandler
            fun PluginEnableEvent.enable() {
                val name = plugin.name
                compatibilities.remove(name)?.let {
                    it().start()
                    name.hookMessage()
                }
            }
        })
    }

    private fun String.hookMessage() = info("Plugin hooks $this".toComponent(NamedTextColor.AQUA))

    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
    }
}

object EntityManager : GlobalManager {

    private val effectSet = ReferenceSet.of(
        PotionEffectType.GLOWING,
        PotionEffectType.INVISIBILITY
    )

    private class PaperListener : Listener { //More accurate world change event for Paper
        @EventHandler(priority = EventPriority.MONITOR)
        fun EntityRemoveFromWorldEvent.remove() {
            PrimeModel.registryOrNull(entity.uniqueId)?.despawn()
        }
        @EventHandler(priority = EventPriority.MONITOR)
        fun EntityAddToWorldEvent.add() {
            PrimeModel.registryOrNull(entity.wrap())?.refresh()
        }
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun EntityJumpEvent.jump() {
            entity.forEachTracker { it.animate(TrackerAnimations.JUMP) }
        }
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun PlayerJumpEvent.jump() {
            player.forEachTracker { it.animate(TrackerAnimations.JUMP) }
        }
    }

    private class SpigotListener : Listener { //Portal event for Spigot
        @EventHandler(priority = EventPriority.MONITOR)
        fun EntityRemoveEvent.remove() {
            PrimeModel.registryOrNull(entity.uniqueId)?.despawn()
        }
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun EntitySpawnEvent.spawn() {
            PrimeModel.registryOrNull(entity.wrap())?.refresh()
        }
        @EventHandler(priority = EventPriority.MONITOR)
        fun PlayerChangedWorldEvent.change() {
            PrimeModel.registryOrNull(player.uniqueId)?.let {
                it.despawn()
                it.refresh()
            }
        }
    }

    private val standardListener = object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun EntityPotionEffectEvent.potion() { //Apply potion effect
            if (action == EntityPotionEffectEvent.Action.CHANGED) return
            if (oldEffect?.let { it.type in effectSet } == true || newEffect?.let { it.type in effectSet } == true) {
                // For NoSuchMethodError: EntityPotionEffectEvent#getEntity() in some server implementation
                (this as EntityEvent).entity.forEachTracker { it.updateBaseEntity() }
            }
        }
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun EntityDismountEvent.dismount() { //Dismount
            val e = dismounted
            isCancelled = e is HitBox && (e.mountController().canFly() || !e.mountController().canDismountBySelf()) && !e.forceDismount()
        }
        @EventHandler(priority = EventPriority.MONITOR)
        fun PlayerQuitEvent.quit() { //Quit
            val wrap = player.wrap()
            PrimeModel.registryOrNull(wrap.uuid())?.close()
            PLATFORM.scheduler().asyncTask {
                EntityTrackerRegistry.registries { registry -> registry.remove(wrap) }
            }
            (player.vehicle as? HitBox)?.dismount(wrap)
        }
        @EventHandler(priority = EventPriority.MONITOR)
        fun PlayerDeathEvent.death() {
            PrimeModel.registryOrNull(entity.uniqueId)?.despawn()
        }
        @EventHandler(priority = EventPriority.MONITOR)
        fun EntitiesUnloadEvent.unload() { //Chunk unload
            entities.forEach { entity ->
                PrimeModel.registryOrNull(entity.uniqueId)?.despawn()
            }
        }
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun EntityDeathEvent.death() { //Death
            entity.forEachTracker {
                it.animate(TrackerAnimations.DEATH)
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun PlayerInteractEntityEvent.interact() { //Interact base entity based on interaction entity
            (rightClicked as? HitBox)?.let {
                if (!isCancelled && hand == EquipmentSlot.HAND && !player.triggerDismount(rightClicked)) player.triggerMount(it)
                isCancelled = false
            }
        }
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun EntityDamageEvent.damage() { //Damage
            if (this is EntityDamageByEntityEvent) {
                val victim = entity.run {
                    if (this is HitBox) source().uuid() else uniqueId
                }
                val v = damager.vehicle
                if (v is HitBox && !v.mountController().canBeDamagedByRider() && v.source().uuid() == victim) {
                    isCancelled = true
                    return
                }
//                    if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
//                        EntityTracker.tracker(damager)?.animate("attack", AnimationModifier.DEFAULT_WITH_PLAY_ONCE)
//                    }
            }
            entity.forEachTracker {
                it.animate(TrackerAnimations.DAMAGE)
                it.damageTint()
            }
        }
    }
    private val platformListener = if (PrimeModelBukkit.IS_PAPER) PaperListener() else SpigotListener()

    override fun start() {
        registerListener(standardListener)
        registerListener(platformListener)
    }

    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
        EntityTrackerRegistry.registries(EntityTrackerRegistry::reload)
    }

    override fun end() {
        EntityTrackerRegistry.registries {
            it.save()
            it.close(Tracker.CloseReason.PLUGIN_DISABLE)
        }
    }

    private fun Entity.forEachTracker(block: (EntityTracker) -> Unit) {
        PrimeModel.registryOrNull(uniqueId)?.trackers()?.forEach(block)
    }

    private fun Player.triggerDismount(e: Entity): Boolean {
        val previous = vehicle
        if (previous !is HitBox) return false
        val uuid = if (e is HitBox) e.source().uuid() else e.uniqueId
        if (previous.source().uuid() == uuid && previous.mountController().canDismountBySelf()) {
            previous.dismount(wrap())
            return true
        }
        return false
    }

    private fun Player.triggerMount(hitBox: HitBox) {
        if (hitBox.mountController().canMount()) hitBox.mount(wrap())
    }
}

object PlayerManagerImpl : PlayerManager, GlobalManager {

    private val playerMap = ConcurrentHashMap<UUID, PlayerChannelHandler>()

    override fun start() {
        registerListener(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST)
            fun PlayerJoinEvent.join() {
                if (player.isOnline) runCatching { //For fake player
                    player.wrap().register()
                }.handleFailure {
                    "Unable to load ${player.name}'s data."
                }
            }
            @EventHandler(priority = EventPriority.MONITOR)
            fun PlayerQuitEvent.quit() {
                playerMap.remove(player.uniqueId)?.use {
                    SkinManagerImpl.removeCache(it.base().profile())
                }
            }
        })
    }

    private fun PlatformPlayer.register() = playerMap.computeIfAbsent(uuid()) {
        PLATFORM.nms().inject(this)
    }.apply {
        SkinManagerImpl.complete(base().profile().asUncompleted())
    }

    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
    }

    override fun end() {
        playerMap.values.removeIf {
            it.use { used -> SkinManagerImpl.removeCache(used.base().profile()) }
            true
        }
    }

    override fun player(uuid: UUID): PlayerChannelHandler? = playerMap[uuid]
    override fun player(player: PlatformPlayer): PlayerChannelHandler = player.register()
}

