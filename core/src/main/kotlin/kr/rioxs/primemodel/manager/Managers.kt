package kr.rioxs.primemodel.manager

import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.manager.Managers.Manager
import java.io.File
import java.net.http.HttpResponse
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import kotlin.io.path.createTempFile
import kr.rioxs.primemodel.api.pack.PackObfuscator
import kr.rioxs.primemodel.api.pack.PackZipper
import kr.rioxs.primemodel.library.armormodel.ArmorImage
import kr.rioxs.primemodel.library.armormodel.ArmorModel
import kr.rioxs.primemodel.library.armormodel.ArmorNameMapper
import kr.rioxs.primemodel.library.armormodel.ArmorPaletteImage
import kr.rioxs.primemodel.util.*
import net.kyori.adventure.text.format.NamedTextColor
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kr.rioxs.primemodel.api.bone.Bones
import kr.rioxs.primemodel.api.bone.Bones.BoneItemMapper
import kr.rioxs.primemodel.api.data.DataClasses.ModelAsset
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintElement
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintJson
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.ModelBlueprint
import kr.rioxs.primemodel.api.data.renderer.ModelRenderer
import kr.rioxs.primemodel.api.data.renderer.RendererGroup
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelAssetsEvent
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelImportedEvent
import kr.rioxs.primemodel.api.manager.Managers.ModelManager
import kr.rioxs.primemodel.api.pack.Pack.PackBuilder
import kr.rioxs.primemodel.api.platform.PlatformRegionHolder
import net.kyori.adventure.text.format.NamedTextColor.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kr.rioxs.primemodel.api.manager.Managers.ProfileManager
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSkin
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSupplier
import kr.rioxs.primemodel.profile.DefaultHttpModelProfileSupplier
import kr.rioxs.primemodel.profile.HttpModelProfileSupplier
import kr.rioxs.primemodel.util.PLATFORM
import java.util.*
import kr.rioxs.primemodel.api.event.EventInterfaces.AnimationSignalEvent
import kr.rioxs.primemodel.api.manager.Managers.ScriptManager
import kr.rioxs.primemodel.api.script.Scripts.AnimationScript
import kr.rioxs.primemodel.api.script.Scripts.ScriptBuilder
import kr.rioxs.primemodel.script.*
import kr.rioxs.primemodel.util.boneName
import kr.rioxs.primemodel.util.bonePredicate
import java.util.regex.Matcher
import java.util.regex.Pattern
import kr.rioxs.primemodel.bukkit.util.audience
import kr.rioxs.primemodel.util.componentOf
import kr.rioxs.primemodel.util.emptyComponentOf
import kr.rioxs.primemodel.util.parallelIOThreadPool
import kr.rioxs.primemodel.util.toComponent
import kr.rioxs.primemodel.util.withComma
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// ============================================
// Armor Manager
// ============================================

object ArmorManager : GlobalManager {

    private val ARMOR_PATH = "assets/minecraft/textures/entity/equipment/humanoid" to "assets/minecraft/textures/entity/equipment/humanoid_leggings"
    private val ARMOR_TRIM_PATH = "assets/minecraft/textures/trims/entity/humanoid" to "assets/minecraft/textures/trims/entity/humanoid_leggings"
    private const val ARMOR_PALETTE_PATH = "assets/minecraft/textures/palettes/trim"

    private val armors = setOf(
        "chainmail",
        "copper",
        "diamond",
        "gold",
        "iron",
        "leather",
        "netherite"
    )

    private val palettes = setOf(
        "amethyst",
        "copper",
        "copper_darker",
        "diamond",
        "diamond_darker",
        "emerald",
        "gold",
        "gold_darker",
        "iron",
        "iron_darker",
        "lapis",
        "netherite",
        "netherite_darker",
        "quartz",
        "redstone",
        "resin"
    )

    private val trims = setOf(
        "bolt",
        "coast",
        "dune",
        "eye",
        "flow",
        "host",
        "raiser",
        "rib",
        "sentry",
        "shaper",
        "silence",
        "snout",
        "spire",
        "tide",
        "vex",
        "ward",
        "wayfinder",
        "wild"
    )

    var armor: ArmorModel = ArmorModel.EMPTY
        private set

    private data class VersionManifest(
        val latest: ManifestLatest,
        val versions: List<ManifestVersion>
    ) {
        val manifest get() = versions.associateBy { it.id }[latest.release]!!
    }

    private data class ManifestLatest(
        val release: String
    )

    private data class ManifestVersion(
        val id: String,
        val url: String
    ) {
        fun toURI(): URI = URI.create(url)
    }

    private data class VersionHash(
        val downloads: Map<String, HashDownload>
    ) {
        val client by downloads
    }

    private data class HashDownload(
        val url: String
    ) {
        fun toURI(): URI = URI.create(url)
    }

    private data class MinecraftClient(
        val version: String,
        val file: File
    )

    private fun downloadMinecraftClient(): CompletableFuture<MinecraftClient?> = httpClient {
        val cacheFolder = DATA_FOLDER.getOrCreateDirectory(".cache")
        sendAsync(
            buildHttpRequest {
                GET()
                uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
            },
            HttpResponse.BodyHandlers.ofInputStream()
        ).thenComposeAsync { response1 ->
            val manifest = response1.toJson(VersionManifest::class.java).manifest
            val cache = File(cacheFolder, "${manifest.id}.jar")
            if (cache.exists() && cache.length() > 0) CompletableFuture.supplyAsync { MinecraftClient(manifest.id, cache) }
            else sendAsync(
                buildHttpRequest {
                    GET()
                    uri(manifest.toURI())
                },
                HttpResponse.BodyHandlers.ofInputStream()
            ).thenComposeAsync { response2 ->
                sendAsync(
                    buildHttpRequest {
                        GET()
                        uri(response2.toJson(VersionHash::class.java).client.toURI())
                    },
                    HttpResponse.BodyHandlers.ofInputStream()
                ).thenComposeAsync { response3 ->
                    val temp = createTempFile(cache.parentFile.toPath(), manifest.id, ".tmp").toFile()
                    response3.body().use { input ->
                        temp.outputStream().buffered().use(input::copyTo)
                    }
                    temp.renameTo(cache)
                    CompletableFuture.supplyAsync { MinecraftClient(manifest.id, cache) }
                }
            }
        }
    }.orElse {
        CompletableFuture.completedFuture(null)
    }

    private class ArmorImageCache(
        val name: String,
        val armor: ByteArray,
        val leggings: ByteArray
    ) {
        val size: Long get() = armor.size.toLong() + leggings.size.toLong()

        fun write(target: File) {
            val file = File(target, name).apply { mkdirs() }
            File(file, "armor.png").outputStream().buffered().use { it.write(armor) }
            File(file, "leggings.png").outputStream().buffered().use { it.write(leggings) }
        }
    }

    private fun JarFile.loadArmorImage(name: String, pathPair: Pair<String, String>) = ArmorImageCache(
        name,
        loadImage(pathPair.first, name),
        loadImage(pathPair.second, name)
    )

    private fun JarFile.loadImage(path: String, name: String) = getInputStream(ZipEntry("$path/$name.png")).use { it.readAllBytes() }

    override fun reload(
        pipeline: ReloadPipeline,
        zipper: PackZipper
    ) {
        if (!CONFIG.module().playerAnimation) {
            armor = ArmorModel.EMPTY
            return
        }
        val folder = DATA_FOLDER.getOrCreateDirectory("armors") {
            info("Downloading client jar...".toComponent())
            val armorsFile = File(it, "armors")
            val trimsFile = File(it, "armor_trims")
            val palettesFile = File(it, "palettes").apply { mkdirs() }
            runCatching {
                downloadMinecraftClient().join()?.let { client ->
                    JarFile(client.file).use { jar ->
                        pipeline.forEachParallel(
                            armors.map { name -> jar.loadArmorImage(name, ARMOR_PATH) },
                                ArmorImageCache::size
                        ) { image -> image.write(armorsFile) }
                        pipeline.forEachParallel(
                            trims.map { name -> jar.loadArmorImage(name, ARMOR_TRIM_PATH) },
                            ArmorImageCache::size
                        ) { image -> image.write(trimsFile) }
                        pipeline.forEachParallel(
                            palettes.map { name -> name to jar.loadImage(ARMOR_PALETTE_PATH, name) },
                            { image -> image.second.size.toLong() },
                        ) { image -> File(palettesFile, "${image.first}.png").writeBytes(image.second)}
                    }
                }
                info("Download success!".toComponent(NamedTextColor.LIGHT_PURPLE))
            }.handleFailure {
                "Unable to download default armor assets."
            }
        }
        val textures = PackObfuscator.order()
        val models = PackObfuscator.order()
        armor = ArmorModel.builder()
            .namespace(CONFIG.namespace())
            .itemPath(zipper.assets().obfuscate("armor"))
            .streamLoader { path -> PLATFORM.getResource(path)!! }
            .armors(pipeline
                .mapParallel(File(folder, "armors").subFiles(), File::length) { it.toArmorImage() }
                .sortedBy { it.name }
            )
            .armorTrims(pipeline
                .mapParallel(File(folder, "armor_trims").subFiles(), File::length) { it.toArmorImage() }
                .sortedBy { it.name }
            )
            .palettes(pipeline
                .mapParallel(File(folder, "palettes").subFiles(), File::length) { it.toPaletteImage() }
                .sortedBy { it.name }
            )
            .nameMapper(ArmorNameMapper(
                { textures.obfuscate(it) },
                { models.obfuscate(it) }
            ))
            .flush(false)
            .build()
        armor.builders().forEach {
            zipper.assets().add(
                it.path(),
                256
            ) {
                it.get()
            }
        }
    }

    private fun File.toArmorImage() = runCatching {
        ArmorImage(
            nameWithoutExtension,
            File(this, "armor.png").toImage(),
            File(this, "leggings.png").toImage()
        )
    }.handleFailure {
        "Unable to load this armor image: $path"
    }.getOrNull()

    private fun File.toPaletteImage() = runCatching {
        ArmorPaletteImage(nameWithoutExtension, toImage())
    }.handleFailure {
        "Unable to load this palette image: $path"
    }.getOrNull()
}

interface GlobalManager {
    fun start() {}
    fun reload(pipeline: ReloadPipeline, zipper: PackZipper)
    fun end() {}
}

// ============================================
// Model Manager
// ============================================

object ModelManagerImpl : ModelManager, GlobalManager {

    private val generalModelMap = addressingMapOf<String, ModelRenderer>()
    private val generalModelView = generalModelMap.toImmutableView()
    private val playerModelMap = addressingMapOf<String, ModelRenderer>()
    private val playerModelView = playerModelMap.toImmutableView()
    private val modelExtensions = setOf("bbmodel", "ajmodel")

    private fun importModels(
        type: ModelRenderer.Type,
        pipeline: ReloadPipeline,
        dir: File
    ): Sequence<ImportedModel> {
        val targetAssets = ModelAssetsEvent(type, dir.fileTrees().use { stream ->
            stream.filter { it.extension.lowercase() in modelExtensions }
                .map(ModelAsset::of)
                .toMutableSet()
        }).apply { call() }
            .assets
            .ifEmpty { return emptySequence() }
            .toList()
        val modelFileMap = ConcurrentHashMap<String, Pair<ModelAsset, ModelBlueprint>>(targetAssets.size)
        val typeName = type.name.lowercase()
        pipeline.apply {
            status = "Importing $typeName models..."
            goal = targetAssets.size
        }.forEachParallel(targetAssets, ModelAsset::sizeAssume) {
            val index = pipeline.progress(it.name)
            val load = it.toTexturedModel() ?: return@forEachParallel
            modelFileMap.compute(load.name) { _, v ->
                if (v != null) {
                    // A model with the same name already exists from a different file
                    warn(
                        "Duplicate $typeName model name '${load.name}'.".toComponent(),
                        "Duplicated file: $it".toComponent(RED),
                        "And: ${v.first}".toComponent(RED)
                    )
                    if (v.first < it) return@compute v
                }
                debugPack {
                    componentOf(
                        "$typeName model file successfully loaded: ".toComponent(),
                        it.toString().toComponent(GREEN),
                        " ($index/${pipeline.goal})".toComponent(DARK_GRAY)
                    )
                }
                it to load
            }
        }
        return modelFileMap.values
            .asSequence()
            .sortedBy { it.first }
            .map {
                ImportedModel(
                    it.first.sizeAssume - it.second.textures.sumOf { tex -> tex.image.size },
                    type,
                    it.second
                )
            }
    }

    private fun loadModels(pipeline: ReloadPipeline, zipper: PackZipper) {
        ModelPipeline(zipper).use {
            if (CONFIG.module().model) it.addModelTo(
                generalModelMap,
                importModels(ModelRenderer.Type.GENERAL, pipeline, DATA_FOLDER.getOrCreateDirectory("models") { folder ->
                    File(DATA_FOLDER.parent, "ModelEngine/blueprints")
                        .takeIf(File::isDirectory)
                        ?.run {
                            copyRecursively(folder, overwrite = true)
                            info("ModelEngine's models are successfully migrated.".toComponent(GREEN))
                        } ?: run {
                        folder.addResource("demon_knight.bbmodel")
                        folder.addResource("blue_wizard.bbmodel")
                    }
                })
            )
            if (CONFIG.module().playerAnimation) it.addModelTo(
                playerModelMap,
                importModels(ModelRenderer.Type.PLAYER, pipeline, DATA_FOLDER.getOrCreateDirectory("players") { folder ->
                    folder.addResource("steve.bbmodel")
                })
            )
        }
    }

    private data class ImportedModel(
        val jsonSize: Long,
        val type: ModelRenderer.Type,
        val blueprint: ModelBlueprint
    )

    private class ModelPipeline(
        private val zipper: PackZipper
    ) : AutoCloseable {

        private val textures = zipper.assets().primemodel().textures()

        private val modernModel = ModelBuilder(
            namespace = zipper.assets().obfuscate("model"),
            builder = { zipper.assets().primemodel().models().resolve(namespace) },
            available = true,
            onBuild = { name, blueprints, json, size ->
                items.add(name, size) {
                    jsonObjectOf("model" to blueprints.toModernJson(namespace, json)).toByteArray()
                }
                blueprints.forEach { json ->
                    models.add(json.jsonName(), size / blueprints.size) {
                        json.buildJson().toByteArray()
                    }
                }
            }
        )

        override fun close() {
        }

        fun addModelTo(
            targetMap: MutableMap<String, ModelRenderer>,
            model: Sequence<ImportedModel>
        ) {
            model.forEach { addModelTo(targetMap, it) }
        }

        private fun addModelTo(
            targetMap: MutableMap<String, ModelRenderer>,
            importedModel: ImportedModel
        ) {
            val (size, type, blueprint) = importedModel
            val context = blueprint.context()
            targetMap[blueprint.name] = blueprint.toRenderer(type) render@ { group ->
                if (!context.canBeRendered()) return@render null
                modernModel.ifAvailable {
                    val json = group.buildModernJson(obfuscator, context)
                    val itemModel = group.buildMeshItemModel(context)
                    if (json != null || itemModel != null) {
                        group.jsonName(context)
                            .also { name -> build("$name.json", json ?: emptyList(), itemModel, if (json != null) size / json.size else 0) }
                            .let { "$namespace/$it" }
                    } else null
                }
            }.apply {
                debugPack {
                    componentOf(
                        "This model was successfully imported: ".toComponent(),
                        blueprint.name.toComponent(GREEN)
                    )
                }
                callEvent { ModelImportedEvent(blueprint, this) }
            }
            context.buildImage(textures.obfuscator()).forEach { image ->
                textures.add(image.pngName(), image.estimatedSize()) {
                    image.toByteArray()
                }
                image.mcmeta()?.let { meta ->
                    textures.add(image.mcmetaName(), -1) {
                        meta.toByteArray()
                    }
                }
            }
        }

        inner class ModelBuilder(
            val namespace: String,
            val builder: ModelBuilder.() -> PackBuilder,
            private val available: Boolean,
            private val onBuild: ModelBuilder.(String, List<BlueprintJson>, JsonObject?, Long) -> Unit,
        ) {
            val items = zipper.assets().primemodel().items().resolve(namespace)
            val models = builder()
            val obfuscator = textures.obfuscator().withModels(models.obfuscator())

            inline fun <T> ifAvailable(block: ModelBuilder.() -> T): T? {
                return if (available) block() else null
            }

            fun build(name: String, list: List<BlueprintJson>, json: JsonObject?, size: Long) {
                onBuild(name, list, json, size)
            }
        }

        private fun List<BlueprintJson>.toModernJson(namespace: String, plus: JsonObject?) = if (size == 1) first().toModernJson(namespace) else jsonObjectOf(
            "type" to "composite",
            "models" to fold(JsonArray(size + if (plus != null) 1 else 0).apply {
                plus?.run(::add)
            }) { array, element -> array.apply { add(element.toModernJson(namespace)) } }
        )

        private fun BlueprintJson.toModernJson(namespace: String) = jsonObjectOf(
            "type" to "model",
            "model" to "${CONFIG.namespace()}:$namespace/$name",
            "tints" to jsonArrayOf(
                jsonObjectOf(
                    "type" to "custom_model_data",
                    "default" to 0xFFFFFF
                )
            )
        )

        private fun ModelBlueprint.toRenderer(type: ModelRenderer.Type, builder: (BlueprintElement.Group) -> String?): ModelRenderer {
            fun <T> Collection<BlueprintElement>.toBoneMap(mapper: (BlueprintElement.Bone) -> T) = filterIsInstance<BlueprintElement.Bone>().let { bone ->
                bone.associateTo(sequencedAddressingMapOf(bone.size)) { it.name() to mapper(it) }
            }.toImmutableView()
            fun BlueprintElement.Bone.parse(): RendererGroup {
                if (this !is BlueprintElement.Group) return RendererGroup(1.0F, null, this, emptySequencedMap(), null)
                return RendererGroup(
                    scale(),
                    if (name.toItemMapper() !== Bones.BoneItemMapper.EMPTY) null else builder(this)?.let { itemNamespace ->
                        CONFIG.item().get().itemModel(PlatformRegionHolder.Namespace(CONFIG.namespace(), itemNamespace))
                    },
                    this,
                    children.toBoneMap { it.parse() },
                    hitBox(),
                )
            }
            return ModelRenderer(
                name,
                type,
                elements.toBoneMap { it.parse() },
                animations
            )
        }
    }

    override fun start() {
    }

    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
        generalModelMap.clear()
        playerModelMap.clear()
        loadModels(pipeline, zipper)
    }

    override fun model(name: String): ModelRenderer? = generalModelView[name]
    override fun models(): Collection<ModelRenderer> = generalModelView.values
    override fun modelKeys(): Set<String> = generalModelView.keys
    override fun limb(name: String): ModelRenderer? = playerModelView[name]
    override fun limbs(): Collection<ModelRenderer> = playerModelView.values
    override fun limbKeys(): Set<String> = playerModelView.keys
}

// ============================================
// Profile Manager
// ============================================

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

// ============================================
// Script Manager
// ============================================

object ScriptManagerImpl : ScriptManager, GlobalManager {

    private val scriptMap = hashMapOf<String, ScriptBuilder>()
    private val scriptPattern = Pattern.compile("^(?<name>[a-zA-Z]+)(:(?<argument>([\\w_\\-])+))?(\\{(?<metadata>([\\w\\W])+)})?$")
    private val validatePattern = Pattern.compile("^[a-z]+$")

    init {
        addBuilder("signal") {
            val args = it.args() ?: return@addBuilder AnimationScript.EMPTY
            AnimationScript.of { tracker ->
                tracker.pipeline.allPlayer()
                    .map { channel -> channel.player() }
                    .forEach { player -> AnimationSignalEvent(player, args).call() }
            }
        }

        addBuilder("tint") {
            TintScript(
                it.metadata.bonePredicate,
                it.metadata.asNumber("color")?.toInt() ?: return@addBuilder AnimationScript.EMPTY,
                it.metadata.asBoolean("damage") == true
            )
        }
        addBuilder("partvis") {
            PartVisibilityScript(
                it.metadata.bonePredicate,
                it.metadata.asBoolean("visible") ?: return@addBuilder AnimationScript.EMPTY,
            )
        }
        addBuilder("partbright") {
            BrightnessScript(
                it.metadata.bonePredicate,
                it.metadata().asNumber("block")?.toInt() ?: return@addBuilder AnimationScript.EMPTY,
                it.metadata().asNumber("sky")?.toInt() ?: return@addBuilder AnimationScript.EMPTY,
            )
        }
        addBuilder("enchant") {
            EnchantScript(
                it.metadata.bonePredicate,
                it.metadata.asBoolean("enchant") ?: return@addBuilder AnimationScript.EMPTY,
            )
        }
        addBuilder("changepart") {
            ChangePartScript(
                it.metadata.bonePredicate,
                it.metadata.asString("nmodel") ?: return@addBuilder AnimationScript.EMPTY,
                it.metadata.asString("npart")?.boneName ?: return@addBuilder AnimationScript.EMPTY
            )
        }
        addBuilder("remap") {
            RemapScript(
                it.metadata.asString("model") ?: return@addBuilder AnimationScript.EMPTY,
                it.metadata.asString("map")
            )
        }
    }

    override fun build(script: String): AnimationScript? = script.toScript()

    override fun addBuilder(name: String, script: ScriptBuilder) {
        if (!validatePattern.matcher(name).find()) throw RuntimeException("name must be in [a-z]")
        scriptMap[name] = script
    }

    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
    }

    private fun String.toScript(): AnimationScript? = scriptPattern.matcher(this)
        .takeIf(Matcher::find)
        ?.let {
            scriptMap[it.group("name").lowercase()]?.build(ScriptBuilder.ScriptData(it.group("argument"),
                ScriptMetaDataImpl(it.group("metadata")
                    ?.split(';')
                    ?.associate { pair ->
                        pair.split('=', limit = 2).let { arr -> arr[0] to if (arr.size == 2) arr[1] else "" }
                    }
                    ?: emptyMap()
                )
            ))
        }

    private class ScriptMetaDataImpl(
        private val map: Map<String, String>
    ) : ScriptBuilder.ScriptMetaData {
        override fun toMap(): Map<String, String> = map
    }
}


// ============================================
// Reload Pipeline (from ReloadPipeline.kt)
// ============================================

/**
 * Reload pipeline + debug indicators.
 *
 * Originally split across ReloadPipeline.kt and debug/DebugIndicators.kt.
 * Merged for consistency (single progress-tracking unit).
 */
class ReloadPipeline(
    private val indicators: List<ReloadIndicator>
) : AutoCloseable {

    var status = "Starting..."
    private var target = "Unknown"
    private val pool = parallelIOThreadPool()

    private val current = AtomicInteger()
    var goal = 0
        set(value) {
            field = value
            current.set(0)
        }

    fun progress(target: String): Int {
        this.target = target
        return current.incrementAndGet()
    }

    fun <T> forEachParallel(list: List<T>, sizeAssume: (T) -> Long, block: (T) -> Unit) {
        pool.forEachParallel(list, sizeAssume, block)
    }

    fun <T, R> mapParallel(list: List<T>, sizeAssume: (T) -> Long, block: (T) -> R?): List<R> {
        return CopyOnWriteArrayList<R>().apply {
            forEachParallel(list, sizeAssume) { t: T ->
                block(t)?.let { add(it) }
            }
        }
    }

    private val task = PLATFORM.scheduler().asyncTaskTimer(1, 1) {
        current.get().run {
            Status(
                if (goal > 0) toFloat() / goal.toFloat() else 0F,
                this,
                goal,
                status,
                target
            )
        }.run {
            indicators.forEach {
                it status this
            }
        }
    }

    data class Status(
        val progress: Float,
        val current: Int,
        val goal: Int,
        val status: String,
        val target: String
    )

    override fun close() {
        task.cancel()
        indicators.forEach(ReloadIndicator::close)
        pool.close()
    }
}

/**
 * Merged debug indicators.
 * <p>
 * Originally split across {@code ReloadIndicator} and {@code BossBarIndicator}.
 * </p>
 *
 * @since 3.0.0
 */
interface ReloadIndicator {
    infix fun status(status: ReloadPipeline.Status)
    fun close()
}

class BossBarIndicator(
    private val audience: Audience
) : ReloadIndicator {

    private var showed = false
    private val bossBar by lazy {
        BossBar.bossBar(
            emptyComponentOf(),
            0F,
            BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS
        ).apply {
            showed = true
            audience.showBossBar(this)
        }
    }

    override fun status(status: ReloadPipeline.Status) {
        bossBar.run {
            name(componentOf(status.status) {
                append(" [${status.target}]".toComponent(NamedTextColor.AQUA))
                append(" (${status.current.withComma()} / ${status.goal.withComma()})".toComponent(NamedTextColor.YELLOW))
            })
            progress(status.progress)
        }
    }

    override fun close() {
        if (showed) audience.hideBossBar(bossBar)
    }
}