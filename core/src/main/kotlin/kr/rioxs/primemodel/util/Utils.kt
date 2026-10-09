package kr.rioxs.primemodel.util

import kr.rioxs.primemodel.api.bone.BoneTags
import kr.rioxs.primemodel.api.manager.Managers.Manager

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import it.unimi.dsi.fastutil.objects.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.imageio.ImageIO
import kr.rioxs.primemodel.api.bone.BoneTags.BoneName
import kr.rioxs.primemodel.api.config.Configs.DebugConfig
import kr.rioxs.primemodel.api.config.Configs.IndicatorConfig
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintImage
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.ModelBlueprint
import kr.rioxs.primemodel.api.data.DataClasses.ModelAsset
import kr.rioxs.primemodel.api.data.raw.RawData.ModelData
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEvent
import kr.rioxs.primemodel.api.manager.Managers.ReloadInfo
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.script.Scripts.ScriptBuilder
import kr.rioxs.primemodel.api.util.Utils.CollectionUtil
import kr.rioxs.primemodel.api.util.Utils.BonePredicate
import kr.rioxs.primemodel.api.util.Utils.HttpUtil
import kr.rioxs.primemodel.api.util.Utils.LogUtil
import kr.rioxs.primemodel.api.util.Utils.EventUtil
import kr.rioxs.primemodel.api.util.Utils.PackUtil
import kr.rioxs.primemodel.bukkit.util.audience
import kr.rioxs.primemodel.manager.BossBarIndicator
import kr.rioxs.primemodel.manager.ReloadIndicator
import kr.rioxs.primemodel.PrimeModelPlatformImpl
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.TextComponent


private val IO_BUFFER = ThreadLocal.withInitial { ByteArrayOutputStream(1024) }

fun BlueprintImage.toByteArray(): ByteArray {
    return image
}

fun JsonElement.toByteArray(): ByteArray {
    return IO_BUFFER.get().let { buffer ->
        buffer.reset()
        OutputStreamWriter(buffer, StandardCharsets.UTF_8).use {
            ModelData.GSON.toJson(this, it)
        }
        buffer.toByteArray()
    }
}


fun <K, V> addressingMapOf() = CollectionUtil.newAddressingMap<K, V>()
fun <K, V> sequencedAddressingMapOf() = CollectionUtil.newSequencedAddressingMap<K, V>()
fun <K, V> addressingMapOf(capacity: Int) = CollectionUtil.newAddressingMap<K, V>(capacity)
fun <K, V> sequencedAddressingMapOf(capacity: Int) = CollectionUtil.newSequencedAddressingMap<K, V>(capacity)

fun <K, V> emptySequencedMap(): SequencedMap<K, V> = Collections.emptyNavigableMap()

fun <K, V> MutableMap<K, V>.toImmutableView(): Map<K, V> = when (this) {
    is Object2ObjectMap<K, V> -> Object2ObjectMaps.unmodifiable(this)
    is Object2ReferenceMap<K, V> -> Object2ReferenceMaps.unmodifiable(this)
    is Reference2ObjectMap<K, V> -> Reference2ObjectMaps.unmodifiable(this)
    is Reference2ReferenceMap<K, V> -> Reference2ReferenceMaps.unmodifiable(this)
    else -> Collections.unmodifiableMap(this)
}

fun <K, V> SequencedMap<K, V>.toImmutableView(): SequencedMap<K, V> = when (this) {
    is Object2ObjectSortedMap<K, V> -> Object2ObjectSortedMaps.unmodifiable(this)
    is Object2ReferenceSortedMap<K, V> -> Object2ReferenceSortedMaps.unmodifiable(this)
    is Reference2ObjectSortedMap<K, V> -> Reference2ObjectSortedMaps.unmodifiable(this)
    is Reference2ReferenceSortedMap<K, V> -> Reference2ReferenceSortedMaps.unmodifiable(this)
    else -> Collections.unmodifiableSequencedMap(this)
}

fun <T> Stream<T>.toSet(): Set<T> = collect(Collectors.toUnmodifiableSet())
fun <T> Stream<T>.toMutableSet(): MutableSet<T> = collect(Collectors.toSet())

fun parallelIOThreadPool() = try {
    ParallelIOThreadPool()
} catch (error: OutOfMemoryError) {
    throw RuntimeException("You have to set your Linux max thread limit!", error)
}

class ParallelIOThreadPool : AutoCloseable {
    private val available = Runtime.getRuntime().availableProcessors() * 2
    private val integer = AtomicInteger()
    private val pool = Executors.newFixedThreadPool(available) {
        Thread(it).apply {
            isDaemon = true
            name = "PrimeModel-IO-Worker-${integer.andIncrement}"
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, exception ->
                exception.handleException("A error has been occurred in ${thread.name}")
            }
        }
    }

    override fun close() {
        pool.close()
    }

    fun <T> forEachParallel(list: List<T>, sizeAssume: (T) -> Long, block: (T) -> Unit) {
        if (list.isEmpty()) return
        val size = list.size
        val lastIndex = list.lastIndex
        val tasks = if (available >= size) {
            list.map {
                Runnable {
                    block(it)
                }
            }
        } else {
            val sorted = list.sortedBy(sizeAssume)
            val queue = arrayListOf<Runnable>()
            var i = 0
            val add = (size.toDouble() / available).toInt()
            while (i <= size) {
                val list = ArrayList<T>(add)
                for (t in i..<(i + add).coerceAtMost(size)) {
                    val ht = t / 2
                    list += sorted[if (t % 2 == 0) ht else lastIndex - ht]
                }
                queue += Runnable {
                    list.forEach(block)
                }
                i += add
            }
            queue
        }
        CompletableFuture.allOf(
            *tasks.map {
                CompletableFuture.runAsync(it, pool)
            }.toTypedArray()
        ).join()
    }
}


inline fun File.getOrCreateDirectory(name: String, initialConsumer: (File) -> Unit = {}) = File(this, name).also { target ->
    if (!target.exists()) {
        target.mkdirs()
        initialConsumer(target)
    }
}

fun File.subFiles(): List<File> = listFiles()?.toList() ?: emptyList()

inline fun copyResourceAs(name: String, block: (InputStream) -> Unit) {
    PLATFORM.getResource(name)?.use(block)
}

fun File.toImage(): BufferedImage = ImageIO.read(this)

fun File.fileTrees(): Stream<Path> = Files.find(
    toPath(),
    Int.MAX_VALUE,
    { _, attr ->
        !attr.isDirectory
    }
)

fun File.addResource(name: String) {
    copyResourceAs(name) { input ->
        File(this, name).outputStream().use {
            it.buffered().use { output -> input.copyTo(output) }
        }
    }
}


fun ModelAsset.toTexturedModel(): ModelBlueprint? = runCatching {
    toResult().let { result ->
        if (result.errors.isNotEmpty()) warn(
            *buildList {
                add("Error has been occurred while parsing this model: ${result.blueprint.name}")
                addAll(result.errors)
            }.map { error -> error.toComponent() }.toTypedArray()
        )
        result.blueprint
    }
}.handleFailure {
    "Unable to load this model: $name"
}.getOrNull()

fun buildJsonArray(capacity: Int = 10, block: JsonArray.() -> Unit) = JsonArray(capacity).apply(block)
fun buildJsonObject(block: JsonObject.() -> Unit) = JsonObject().apply(block)

fun jsonArrayOf(vararg element: Any?) = buildJsonArray {
    element.filterNotNull().forEach {
        add(it.toJsonElement())
    }
}

fun jsonObjectOf(vararg element: Pair<String, Any>) = buildJsonObject {
    element.forEach {
        add(it.first, it.second.toJsonElement())
    }
}

operator fun JsonArray.plusAssign(other: JsonElement) {
    add(other)
}

fun Any.toJsonElement(): JsonElement = when (this) {
    is String -> JsonPrimitive(this)
    is Char -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is JsonElement -> this
    is List<*> -> run {
        val map = mapNotNull {
            it?.toJsonElement()
        }
        buildJsonArray(map.size) {
            map.forEach {
                add(it)
            }
        }
    }
    is Map<*, *> -> buildJsonObject {
        forEach {
            add(it.key?.toString() ?: return@forEach, it.value?.toJsonElement() ?: return@forEach)
        }
    }
    else -> throw RuntimeException("Unsupported type: ${javaClass.name}")
}


val BYTE_UNIT = BigDecimal("1024.000")
val COMMA_FORMAT = DecimalFormat("#,###")
val COMMA_DECIMAL_FORMAT = DecimalFormat("#,###.000")

inline fun <T> T?.ifNull(lazyMessage: () -> String): T & Any = this ?: throw RuntimeException(lazyMessage())

fun Number.withComma(): String = COMMA_FORMAT.format(this)
val String.boneName get() = BoneTags.BoneName.of(this)

fun Long.toByteFormat(): String {
    var value = BigDecimal("$this.000")
    for (format in LengthFormat.entries) {
        if (value < BYTE_UNIT) return "${COMMA_DECIMAL_FORMAT.format(value)} ${format.name}"
        value /= BYTE_UNIT
    }
    return "${COMMA_DECIMAL_FORMAT.format(value)} ${LengthFormat.entries.last().name}"
}

enum class LengthFormat {
    B,
    KB,
    MB,
    GB
}

inline fun <reified T : ModelEvent> callEvent(noinline block: () -> T): Boolean = EventUtil.call(T::class.java) { block() }.triggered()

private typealias Type = IndicatorConfig.IndicatorOption

private val INDICATOR_MAP = EnumMap<Type, (ReloadInfo) -> ReloadIndicator?>(Type::class.java).apply {
    put(Type.PROGRESS_BAR) {
        BossBarIndicator(it.sender)
    }
}

fun Type.toIndicator(info: ReloadInfo) = INDICATOR_MAP[this]?.invoke(info)
fun Iterable<Type>.toIndicator(info: ReloadInfo) = mapNotNull {
    it.toIndicator(info)
}

val PLATFORM
    get() = PrimeModel.platform() as PrimeModelPlatformImpl
val CONFIG
    get() = PrimeModel.config()
val DATA_FOLDER
    get() = PLATFORM.dataFolder()

private val LATEST_VERSION_CACHE = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build<Any, HttpUtil.LatestVersion> { HttpUtil.latest() }

private val GSON = GsonBuilder().disableHtmlEscaping().create()

val LATEST_VERSION: HttpUtil.LatestVersion get() = LATEST_VERSION_CACHE.get(Unit)

fun info(vararg message: Component) = PLATFORM.logger().info(*message)
fun warn(vararg message: Component) = PLATFORM.logger().warn(*message)
inline fun debugPack(lazyMessage: () -> Component) {
    if (CONFIG.debug().has(DebugConfig.DebugOption.PACK)) info(componentOf(
        "[${Thread.currentThread().name}] ".toComponent(NamedTextColor.YELLOW),
        lazyMessage()
    ))
}

fun Throwable.handleException(message: String) = LogUtil.handleException(message, this)

inline fun <T> Result<T>.handleFailure(lazyMessage: () -> String) = onFailure {
    it.handleException(lazyMessage())
}

fun String.toPackName() = PackUtil.toPackName(this)

fun <T : Any> httpClient(block: HttpClient.() -> T): HttpUtil.Result<T> = HttpUtil.client {
    it.block()
}

fun buildHttpRequest(builder: HttpRequest.Builder.() -> Unit): HttpRequest = HttpRequest.newBuilder().apply(builder).build()


fun <T> HttpResponse<InputStream>.toJson(clazz: Class<T>): T = body().use {
    InputStreamReader(it, StandardCharsets.UTF_8).use { reader -> GSON.fromJson(reader, clazz) }
}

private val INFO = " [!] ".toComponent {
    decorate(TextDecoration.BOLD).color(NamedTextColor.GREEN)
}
private val WARN = " [!] ".toComponent {
    decorate(TextDecoration.BOLD).color(NamedTextColor.RED)
}

fun String.toComponent(builder: TextComponent.Builder.() -> Unit = {}) = componentOf(this, builder)
fun String.toComponent(color: TextColor) = componentOf(this) {
    color(color)
}

fun spaceComponentOf() = Component.space()
fun emptyComponentOf() = Component.empty()
fun lineComponentOf() = Component.newline()
fun componentOf(vararg like: ComponentLike) = componentOf {
    append(*like)
}
fun componentWithLineOf(vararg like: ComponentLike) = componentOf {
    like.forEachIndexed { i, l ->
        append(l)
        if (i < like.lastIndex) append(lineComponentOf())
    }
}
fun componentOf(content: String, builder: TextComponent.Builder.() -> Unit) = componentOf {
    content(content)
    builder()
}
fun componentOf(builder: TextComponent.Builder.() -> Unit) = Component.text { it.builder() }
fun ComponentLike.toHoverEvent() = HoverEvent.showText(this)

fun Audience.info(message: String) = info(message.toComponent())
fun Audience.warn(message: String) = warn(message.toComponent())
fun Audience.infoNotNull(vararg messages: ComponentLike?): Unit = info(*messages.filterNotNull().ifEmpty {
    return
}.toTypedArray())
fun Audience.info(vararg messages: ComponentLike) = sendMessage(componentWithLineOf(*messages.map { componentOf(INFO, it) }.toTypedArray()))
fun Audience.warn(vararg messages: ComponentLike) = sendMessage(componentWithLineOf(*messages.map { componentOf(WARN, it) }.toTypedArray()))
fun Audience.info(message: ComponentLike) = sendMessage(componentOf(INFO, message))
fun Audience.warn(message: ComponentLike) = sendMessage(componentOf(WARN, message))

val ScriptBuilder.ScriptMetaData.bonePredicate get(): BonePredicate {
    val match = asBoolean("exact") != false
    val children = asBoolean("children") == true
    val part = asString("part")?.boneName?.name
    return if (part == null) BonePredicate.TRUE else {
        BonePredicate.of(if (children) BonePredicate.State.TRUE else BonePredicate.State.FALSE, if (match) {
            { b ->
                b.name().name == part
            }
        } else {
            { b ->
                b.name().name.contains(part, ignoreCase = true)
            }
        })
    }
}


