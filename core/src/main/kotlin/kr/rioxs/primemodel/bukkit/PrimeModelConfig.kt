package kr.rioxs.primemodel.bukkit
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.manager.Managers.Manager

import java.io.File
import java.util.function.Supplier
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitAdapter
import kr.rioxs.primemodel.api.bukkit.PrimeModelBukkit
import kr.rioxs.primemodel.api.config.Configs.DebugConfig
import kr.rioxs.primemodel.api.config.Configs.IndicatorConfig
import kr.rioxs.primemodel.api.config.Configs.ModuleConfig
import kr.rioxs.primemodel.api.config.Configs.PackConfig
import kr.rioxs.primemodel.api.event.EventInterfaces.PluginEndReloadEvent
import kr.rioxs.primemodel.api.event.EventInterfaces.PluginStartReloadEvent
import kr.rioxs.primemodel.api.manager.Managers.ModelManager
import kr.rioxs.primemodel.api.manager.Managers.PlayerManager
import kr.rioxs.primemodel.api.manager.Managers.ProfileManager
import kr.rioxs.primemodel.api.manager.Managers.ScriptManager
import kr.rioxs.primemodel.api.manager.Managers.SkinManager
import kr.rioxs.primemodel.api.mount.MountController
import kr.rioxs.primemodel.api.mount.MountController.MountControllers
import kr.rioxs.primemodel.api.pack.PackZipper
import kr.rioxs.primemodel.api.platform.PlatformItemStack
import kr.rioxs.primemodel.api.PrimeModelConfig
import kr.rioxs.primemodel.api.PrimeModelPlatform.ReloadResult
import kr.rioxs.primemodel.api.util.Utils.EntityUtil
import kr.rioxs.primemodel.api.nms.NMSVersion.MinecraftVersion.*
import kr.rioxs.primemodel.bukkit.manager.CompatibilityManager
import kr.rioxs.primemodel.bukkit.manager.EntityManager
import kr.rioxs.primemodel.bukkit.manager.PlayerManagerImpl
import kr.rioxs.primemodel.bukkit.scheduler.BukkitScheduler
import kr.rioxs.primemodel.bukkit.scheduler.PaperScheduler
import kr.rioxs.primemodel.bukkit.util.PLUGIN
import kr.rioxs.primemodel.manager.*
import kr.rioxs.primemodel.PrimeModelEvaluatorImpl
import kr.rioxs.primemodel.util.*
import kr.rioxs.primemodel.util.ifNull
import org.bstats.bukkit.Metrics
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.semver4j.Semver
import kr.rioxs.primemodel.bukkit.util.toYaml
import kr.rioxs.primemodel.util.DATA_FOLDER
import kr.rioxs.primemodel.util.PLATFORM
import org.bukkit.configuration.file.YamlConfiguration

internal class PrimeModelProperties(
    private val plugin: AbstractPrimeModelPlugin
) {
    private lateinit var _config: PrimeModelConfig
    private var _metrics: Metrics? = null

    val version = parse(Bukkit.getBukkitVersion().substringBefore('-'))
    val nms = when (version) {
        V1_21_11 -> kr.rioxs.primemodel.bukkit.nms.v1_21_R7.NMSImpl()
        else -> {
            warn(
                "Note: this version is officially untested.".toComponent(),
                "So be careful to use!".toComponent()
            )
            kr.rioxs.primemodel.bukkit.nms.v1_21_R7.NMSImpl()
        }
    }
    val scheduler = if (PrimeModelBukkit.IS_FOLIA) PaperScheduler() else BukkitScheduler()
    val evaluator = PrimeModelEvaluatorImpl()
    val eventbus = BukkitModelEventBusImpl()
    @Suppress("DEPRECATION") //To support Spigot :(
    val semver = Semver.coerce(plugin.description.version).ifNull { "Unable to load PrimeModel's sermver." }
    val snapshot = runCatching {
        plugin.attributes().getValue("Dev-Build").toInt()
    }.getOrElse {
        it.handleException("Unable to parse manifest's build data")
        -1
    }
    var config
        get() = _config
        set(value) {
            _config = value.apply {
                if (metrics()) {
                    if (_metrics == null) _metrics = Metrics(plugin, 24237)
                } else {
                    _metrics?.shutdown()
                    _metrics = null
                }
            }
        }
    val managers by lazy {
        mapOf(
            CompatibilityManager::class.java to CompatibilityManager,
            ArmorManager::class.java to ArmorManager,
            ProfileManager::class.java to ProfileManagerImpl,
            SkinManager::class.java to SkinManagerImpl,
            ModelManager::class.java to ModelManagerImpl,
            PlayerManager::class.java to PlayerManagerImpl,
            EntityManager::class.java to EntityManager,
            ScriptManager::class.java to ScriptManagerImpl
        )
    }

    var reloadStartTask: (PackZipper) -> Unit = { callEvent { PluginStartReloadEvent(it) } }
    var reloadEndTask: (ReloadResult) -> Unit = { callEvent { PluginEndReloadEvent(it) } }

    init {
        config = PrimeModelConfigImpl(PluginConfiguration.CONFIG.create())
    }
}

class PrimeModelConfigImpl(yaml: ConfigurationSection) : PrimeModelConfig {

    private val debug = yaml.getConfigurationSection("debug")?.let {
        DebugConfig.from(it::getBoolean)
    } ?: DebugConfig.DEFAULT
    private val indicator = yaml.getConfigurationSection("indicator")?.let {
        IndicatorConfig.from(it::getBoolean)
    } ?: IndicatorConfig.DEFAULT
    private val module = yaml.getConfigurationSection("module")?.let {
        ModuleConfig.from(it::getBoolean)
    } ?: ModuleConfig.DEFAULT
    private val pack = yaml.getConfigurationSection("pack")?.let {
        PackConfig.from(it::getBoolean)
    } ?: PackConfig.DEFAULT
    private val metrics = yaml.getBoolean("metrics", true)
    private val sightTrace = yaml.getBoolean("sight-trace", true)
    private val mergeWithExternalResources = yaml.getBoolean("merge-with-external-resources", true)
    private val itemModel = yaml.getString("item")?.let {
        runCatching {
            Material.getMaterial(it.uppercase()).ifNull { "This item doesn't exist: $it" }
        }.getOrDefault(Material.LEATHER_HORSE_ARMOR)
    } ?: Material.LEATHER_HORSE_ARMOR
    private val item = Supplier { BukkitAdapter.adapt(ItemStack(itemModel)) }
    private val maxSight = yaml.getDouble("max-sight", -1.0).run {
        if (this <= 0.0) EntityUtil.renderDistance() else this
    }
    private val minSight = yaml.getDouble("min-sight", 5.0)
    private val namespace = yaml.getString("namespace") ?: "primemodel"
    private val packType = yaml.getString("pack-type")?.let {
        runCatching {
            PrimeModelConfig.PackType.valueOf(it.uppercase())
        }.getOrNull()
    } ?: PrimeModelConfig.PackType.ZIP
    private val buildFolderLocation = (yaml.getString("build-folder-location") ?: "primemodel/build").replace('/', File.separatorChar)
    private val followMobInvisibility = yaml.getBoolean("follow-mob-invisibility", true)
    private val usePurpurAfk = yaml.getBoolean("use-purpur-afk", true)
    private val versionCheck = yaml.getBoolean("version-check", true)
    private val defaultMountController = when (yaml.getString("default-mount-controller")?.lowercase()) {
        "invalid" -> MountController.MountControllers.INVALID
        "none" -> MountController.MountControllers.NONE
        "fly" -> MountController.MountControllers.FLY
        else -> MountController.MountControllers.WALK
    }
    private val lerpFrameTime = yaml.getInt("lerp-frame-time", 5)
    private val cancelPlayerModelInventory = yaml.getBoolean("cancel-player-model-inventory")
    private val playerHideDelay = yaml.getLong("player-hide-delay", 3L).coerceAtLeast(1L)
    private val packetBundlingSize = yaml.getInt("packet-bundling-size", 16)
    private val enableStrictLoading = yaml.getBoolean("enable-strict-loading")

    override fun debug(): DebugConfig = debug
    override fun indicator(): IndicatorConfig = indicator
    override fun module(): ModuleConfig = module
    override fun pack(): PackConfig = pack
    override fun item(): Supplier<PlatformItemStack> = item
    override fun metrics(): Boolean = metrics
    override fun sightTrace(): Boolean = sightTrace
    override fun mergeWithExternalResources(): Boolean = mergeWithExternalResources
    override fun maxSight(): Double = maxSight
    override fun minSight(): Double = minSight
    override fun namespace(): String = namespace
    override fun packType(): PrimeModelConfig.PackType = packType
    override fun buildFolderLocation(): String = buildFolderLocation
    override fun followMobInvisibility(): Boolean = followMobInvisibility
    override fun usePurpurAfk(): Boolean = usePurpurAfk
    override fun versionCheck(): Boolean = versionCheck
    override fun defaultMountController(): MountController = defaultMountController
    override fun lerpFrameTime(): Int = lerpFrameTime
    override fun cancelPlayerModelInventory(): Boolean = cancelPlayerModelInventory
    override fun playerHideDelay(): Long = playerHideDelay
    override fun packetBundlingSize(): Int = packetBundlingSize
    override fun enableStrictLoading(): Boolean = enableStrictLoading
}


// ============================================
// Plugin Configuration (from PluginConfiguration.kt)
// ============================================

enum class PluginConfiguration(
    private val dir: String
) {
    CONFIG("config.yml"),
    ;

    fun create(): YamlConfiguration {
        val file = File(DATA_FOLDER, dir)
        val exists = file.exists()
        if (!exists) PLATFORM.saveResource(dir)
        val yaml = file.toYaml()
        val newYaml = PLATFORM.getResource(dir).ifNull { "Resource '$dir' not found." }.use {
            it.toYaml()
        }
        yaml.getKeys(true).forEach {
            if (!newYaml.contains(it)) yaml.set(it, null)
        }
        newYaml.getKeys(true).forEach {
            if (!yaml.contains(it)) yaml.set(it, newYaml.get(it))
            yaml.setComments(it ,newYaml.getComments(it))
        }
        return yaml.apply {
            save(file)
        }
    }
}