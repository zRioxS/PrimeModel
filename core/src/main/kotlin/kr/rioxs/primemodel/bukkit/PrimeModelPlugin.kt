package kr.rioxs.primemodel.bukkit
import kr.rioxs.primemodel.api.PrimeModel

import net.kyori.adventure.platform.bukkit.BukkitAudiences

import java.io.File
import java.io.InputStream
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kr.rioxs.primemodel.api.bukkit.PrimeModelBukkit.BukkitModelEventBus
import kr.rioxs.primemodel.api.bukkit.PrimeModelBukkit.PrimeModelBukkitEvent
import kr.rioxs.primemodel.api.bukkit.PrimeModelBukkit.BukkitModelScheduler
import kr.rioxs.primemodel.api.event.EventInterfaces.CancellableEvent
import kr.rioxs.primemodel.api.manager.Managers.Manager
import kr.rioxs.primemodel.api.manager.Managers.ReloadInfo
import kr.rioxs.primemodel.api.nms.NMS
import kr.rioxs.primemodel.api.pack.PackZipper
import kr.rioxs.primemodel.api.PrimeModelConfig
import kr.rioxs.primemodel.api.PrimeModel.Evaluator
import kr.rioxs.primemodel.api.PrimeModelEventBus
import kr.rioxs.primemodel.api.PrimeModel.Logger
import kr.rioxs.primemodel.api.PrimeModelPlatform.ReloadResult
import kr.rioxs.primemodel.api.PrimeModelPlatform.ReloadResult.*
import kr.rioxs.primemodel.api.nms.NMSVersion.MinecraftVersion
import kr.rioxs.primemodel.bukkit.command.startBukkitCommand
import kr.rioxs.primemodel.bukkit.configuration.PluginConfiguration
import kr.rioxs.primemodel.bukkit.util.ADVENTURE_PLATFORM
import kr.rioxs.primemodel.bukkit.util.audience
import kr.rioxs.primemodel.bukkit.util.PLUGIN
import kr.rioxs.primemodel.bukkit.util.registerListener
import kr.rioxs.primemodel.manager.GlobalManager
import kr.rioxs.primemodel.manager.ReloadPipeline
import kr.rioxs.primemodel.PrimeModelEventBusImpl
import kr.rioxs.primemodel.util.*
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.format.NamedTextColor.*
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.ServerLoadEvent
import org.semver4j.Semver

abstract class PrimeModelPlugin : AbstractPrimeModelPlugin() {

    private lateinit var props: PrimeModelProperties

    override fun onLoad() {
        super.onLoad()
        props = runCatching {
            PrimeModelProperties(this)
        }.getOrElse {
            warn(
                "Unable to start PrimeModel.".toComponent(),
                "Reason: ${it.message ?: "Unknown"}".toComponent(RED),
                "Stack trace: ${it.stackTraceToString()}".toComponent(RED),
                "Plugin will be automatically disabled.".toComponent(DARK_RED)
            )
            return Bukkit.getPluginManager().disablePlugin(this)
        }
    }

    override fun onEnable() {
        ADVENTURE_PLATFORM = BukkitAudiences.create(this)
        props.managers.values.forEach(GlobalManager::start)
        ADVENTURE_PLATFORM
        if (isSnapshot) warn(
            "This build is dev version: be careful to use it!".toComponent(),
            "Build number: ${props.snapshot}".toComponent(LIGHT_PURPLE)
        )
        startBukkitCommand()
        registerListener(object : Listener {
            @EventHandler
            fun PlayerJoinEvent.join() {
                if (!player.isOp || !config().versionCheck()) return
                props.scheduler.asyncTask {
                    val result = LATEST_VERSION
                    player.audience().infoNotNull(
                        result.release
                            ?.takeIf { props.semver < it.versionNumber() }
                            ?.let { version -> componentOf("New PrimeModel release found: ") { append(version.toURLComponent()) } },
                        result.snapshot
                            ?.takeIf { props.semver < it.versionNumber() }
                            ?.let { version -> componentOf("New PrimeModel snapshot found: ") { append(version.toURLComponent()) } }
                    )
                }
            }

            @EventHandler
            fun ServerLoadEvent.load() {
                if (skipInitialReload || type != ServerLoadEvent.LoadType.STARTUP) return
                when (val result = reload(ReloadInfo(true, Audience.empty()))) {
                    is Failure -> result.throwable.handleException("Unable to load plugin properly.")
                    is OnReload -> throw RuntimeException("Plugin load failed.")
                    is Success -> info(
                        "Plugin is loaded. (${result.totalTime().withComma()} ms)".toComponent(GREEN),
                        "Minecraft version: ${props.version}, NMS version: ${props.nms.version()}".toComponent(AQUA),
                        "Platform: ${
                            when {
                                IS_FOLIA -> "Folia"
                                IS_PURPUR -> "Purpur"
                                IS_PAPER -> "Paper"
                                else -> "Bukkit"
                            }
                        }".toComponent(AQUA)
                    )
                }
            }
        })
    }

    override fun onDisable() {
        if (!firstLoad.get()) return
        props.managers.values.forEach(GlobalManager::end)
        ADVENTURE_PLATFORM.close()
    }

    override fun reload(info: ReloadInfo): ReloadResult {
        if (!onReload.compareAndSet(false, true)) return OnReload.INSTANCE
        return runCatching {
            if (!info.skipConfig) props.config = PrimeModelConfigImpl(PluginConfiguration.CONFIG.create())
            val zipper = PackZipper.zipper().also(props.reloadStartTask)
            ReloadPipeline(
                config().indicator().options.toIndicator(info)
            ).use { pipeline ->
                val time = System.currentTimeMillis()
                props.managers.values.forEach {
                    it.reload(pipeline, zipper)
                }
                Success(
                    firstLoad.compareAndSet(false, true),
                    System.currentTimeMillis() - time,
                    config().packType().toGenerator().create(zipper, pipeline.apply {
                        status = "Generating files..."
                        goal = zipper.size()
                    })
                )
            }
        }.getOrElse {
            Failure(it)
        }.apply {
            onReload.set(false)
        }.also(props.reloadEndTask)
    }

    override fun loadAssets(pipeline: ReloadPipeline, prefix: String, consumer: BiConsumer<String, InputStream>) {
        JarFile(file).use {
            pipeline.forEachParallel(it.entries()
                .asSequence()
                .filter { entry ->
                    entry.name.startsWith(prefix)
                        && entry.name.length > prefix.length + 1
                        && !entry.isDirectory
                }
                .toList(),
                JarEntry::getSize
            ) { entry ->
                it.getInputStream(entry).use { stream ->
                    consumer.accept(entry.name.substring(prefix.length + 1), stream)
                }
            }
        }
    }

    override fun dataFolder(): File = dataFolder
    override fun logger(): PrimeModel.Logger = logger
    override fun scheduler(): BukkitModelScheduler = props.scheduler
    override fun evaluator(): PrimeModel.Evaluator = props.evaluator
    override fun eventBus(): BukkitModelEventBus = props.eventbus
    override fun <T : Manager> manager(managerClass: Class<T>): T = managerClass.cast(props.managers[managerClass])

    override fun config(): PrimeModelConfig = props.config
    override fun version(): MinecraftVersion = props.version
    override fun semver(): Semver = props.semver
    override fun nms(): NMS = props.nms
    override fun isSnapshot(): Boolean = props.snapshot > 0

    @Synchronized
    override fun addReloadStartHandler(consumer: Consumer<PackZipper>) {
        val previous = props.reloadStartTask
        props.reloadStartTask = {
            previous(it)
            consumer.accept(it)
        }
    }

    @Synchronized
    override fun addReloadEndHandler(consumer: Consumer<ReloadResult>) {
        val previous = props.reloadEndTask
        props.reloadEndTask = {
            previous(it)
            consumer.accept(it)
        }
    }
}

class BukkitModelEventBusImpl : BukkitModelEventBus, PrimeModelEventBus by PrimeModelEventBusImpl({ eventClass, supplier ->
    PrimeModelBukkitEvent(eventClass, supplier).apply {
        Bukkit.getPluginManager().callEvent(this)
    }.source()?.let { event ->
        if (event !is CancellableEvent || !event.isCancelled()) PrimeModelEventBus.Result.SUCCESS else PrimeModelEventBus.Result.FAIL
    } ?: PrimeModelEventBus.Result.NO_EVENT_HANDLER
})

