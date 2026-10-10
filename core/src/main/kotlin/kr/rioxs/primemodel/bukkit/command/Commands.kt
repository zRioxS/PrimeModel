package kr.rioxs.primemodel.bukkit.command

import kr.rioxs.primemodel.bukkit.util.audience
import kr.rioxs.primemodel.bukkit.util.PLUGIN
import kr.rioxs.primemodel.bukkit.util.toTracker
import kr.rioxs.primemodel.bukkit.util.toRegistry

import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.PrimeModelPlatform.ReloadResult.*
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator
import kr.rioxs.primemodel.api.animation.Animations.AnimationModifier
import kr.rioxs.primemodel.api.tracker.TrackerUtils.EntityHideOption
import kr.rioxs.primemodel.api.tracker.ModelTransform.ModelScaler
import kr.rioxs.primemodel.api.tracker.Tracker
import kr.rioxs.primemodel.api.tracker.TrackerUtils.TrackerModifier
import kr.rioxs.primemodel.bukkit.audience.AudiencePlayer
import kr.rioxs.primemodel.bukkit.audience.AudienceSender
import kr.rioxs.primemodel.bukkit.audience.BukkitAudience
import kr.rioxs.primemodel.bukkit.util.wrap
import kr.rioxs.primemodel.util.*
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.format.NamedTextColor.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.bukkit.BukkitCommandMeta
import org.incendo.cloud.bukkit.CloudBukkitCapabilities
import org.incendo.cloud.bukkit.data.MultipleEntitySelector
import org.incendo.cloud.bukkit.parser.PlayerParser.playerParser
import org.incendo.cloud.bukkit.parser.location.LocationParser.locationParser
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser.multipleEntitySelectorParser
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.incendo.cloud.parser.standard.BooleanParser.booleanParser
import org.incendo.cloud.parser.standard.DoubleParser.doubleParser
import org.incendo.cloud.parser.standard.EnumParser.enumParser
import org.incendo.cloud.parser.standard.StringParser.stringParser
import org.incendo.cloud.suggestion.SuggestionProvider.blockingStrings
import kr.rioxs.primemodel.api.manager.Managers.Manager
import kr.rioxs.primemodel.api.data.renderer.ModelRenderer
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.TextComponent
import org.incendo.cloud.Command
import org.incendo.cloud.CommandManager
import org.incendo.cloud.component.CommandComponent
import org.incendo.cloud.component.CommandComponent.ComponentType.*
import org.incendo.cloud.description.Description
import org.incendo.cloud.parser.standard.IntegerParser

private val MODEL_SUGGESTION = blockingStrings<Audience> { _, _ -> PrimeModel.modelKeys() }
private val LIMB_SUGGESTION = blockingStrings<Audience> { _, _ -> PrimeModel.limbKeys() }

fun startBukkitCommand() {
    LegacyPaperCommandManager(
        PLUGIN,
        ExecutionCoordinator.simpleCoordinator(),
        SenderMapper.create<CommandSender, Audience>(
            { sender -> if (sender is Player) AudiencePlayer(sender) else AudienceSender(sender) },
            { audience -> (audience as BukkitAudience).sender }
        )
    ).apply {
        if (hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            registerBrigadier()
            brigadierManager().setNativeNumberSuggestions(true)
        } else if (hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) registerAsynchronousCompletions()
    }.register(
        "PrimeModel",
        "All-related command.",
        { it.meta(BukkitCommandMeta.BUKKIT_DESCRIPTION, info.description.textDescription()) },
        "bm", "model"
    ) {
        create(
            "reload",
            "Reloads PrimeModel.",
            "re", "rl"
        ) {
            handler(::reload)
        }
        create(
            "spawn",
            "Summons some model to given type",
            "s"
        ) {
            required("model", stringParser(), MODEL_SUGGESTION)
                .optional("type", enumParser(EntityType::class.java))
                .optional("scale", doubleParser(0.0625, 16.0))
                .optional("location", locationParser())
                .senderType(AudiencePlayer::class.java)
                .handler(::spawn)
        }
        create(
            "test",
            "Tests some model's animation to specific player",
            "t"
        ) {
            required("model", stringParser(), MODEL_SUGGESTION)
                .required(
                    "animation",
                    stringParser(),
                    blockingStrings { ctx, _ -> ctx.nullableString("model") { PrimeModel.modelOrNull(it)?.animations()?.keys } ?: emptySet()  }
                )
                .optional("player", playerParser())
                .optional("location", locationParser())
                .handler(::test)
        }
        create(
            "disguise",
            "Disguises self.",
            "d"
        ) {
            required("model", stringParser(), MODEL_SUGGESTION)
                .optional("scaling", booleanParser())
                .senderType(AudiencePlayer::class.java)
                .handler(::disguise)
        }
        create(
            "undisguise",
            "Undisguises self.",
            "ud"
        ) {
            senderType(AudiencePlayer::class.java)
                .optional("model", stringParser(), blockingStrings { ctx, _ -> ctx.sender().sender.toRegistry()?.trackers()?.map(Tracker::name) ?: emptyList() })
                .handler(::undisguise)
        }
        create(
            "play",
            "Plays player animation",
            "p"
        ) {
            required("limb", stringParser(), LIMB_SUGGESTION)
                .required(
                    "animation",
                    stringParser(),
                    blockingStrings { ctx, _ -> ctx.nullableString("limb") { PrimeModel.limbOrNull(it)?.animations()?.keys } ?: emptySet()  }
                )
                .optional("loop_type", enumParser(AnimationIterator.Type::class.java))
                .optional("hide", booleanParser())
                .senderType(AudiencePlayer::class.java)
                .handler(::play)
        }
        create(
            "hide",
            "Hides some entities from target player."
        ) {
            required("model", stringParser(), MODEL_SUGGESTION)
                .required("player", playerParser())
                .required("entities", multipleEntitySelectorParser())
                .handler(::hide)
        }
        create(
            "show",
            "Shows some entities to target player."
        ) {
            required("model", stringParser(), MODEL_SUGGESTION)
                .required("player", playerParser())
                .required("entities", multipleEntitySelectorParser())
                .handler(::show)
        }
        create(
            "version",
            "Checks PrimeModel's version",
            "v"
        ) {
            handler(::version)
        }
    }
}

private fun hide(context: CommandContext<Audience>) {
    val sender = context.sender()
    val model = context.get<String>("model")
    val player = context.get<Player>("player").wrap()
    var success = false
    context.get<MultipleEntitySelector>("entities").values().forEach {
        if (it.toRegistry()?.tracker(model)?.hide(player) == true) success = true
    }
    if (!success) sender.warn("Failed to hide any of provided entities.")
}

private fun show(context: CommandContext<Audience>) {
    val sender = context.sender()
    val model = context.get<String>("model")
    val player = context.get<Player>("player").wrap()
    var success = false
    context.get<MultipleEntitySelector>("entities").values().forEach {
        if (it.toRegistry()?.tracker(model)?.show(player) == true) success = true
    }
    if (!success) sender.warn("Failed to show any of provided entities.")
}

private fun disguise(context: CommandContext<AudiencePlayer>) {
    val audience = context.sender()
    val player = audience.sender
    val scaling = if (context.getOrDefault("scaling", true)) ModelScaler.entity() else ModelScaler.defaultScaler()
    context.model("model") { return audience.warn("Unable to find this model: $it") }.getOrCreate(player.wrap(), TrackerModifier.DEFAULT) {
        it.scaler(scaling)
    }
}

private fun undisguise(context: CommandContext<AudiencePlayer>) {
    val audience = context.sender()
    val player = audience.sender
    val model = context.nullable<String>("model")
    if (model != null) {
        player.toTracker(model)?.close() ?: audience.warn("Cannot find this model to undisguise: $model")
    } else player.toRegistry()?.close() ?: audience.warn("Cannot find any model to undisguise")
}

private fun spawn(context: CommandContext<AudiencePlayer>) {
    val audience = context.sender()
    val player = audience.sender
    val model = context.model("model") { return audience.warn("Unable to find this model: $it") }
    val type = context.nullable("type", EntityType.HUSK)
    val scale = context.nullable("scale", 1.0)
    val loc = context.nullable("location") { player.location }
    loc.run {
        (world ?: player.world).spawnEntity(
            this,
            type
        )
    }.takeIf {
        it.isValid
    }?.let { entity ->
        model.create(entity.wrap(), TrackerModifier.DEFAULT) { tracker -> tracker.scaler(ModelScaler.entity().multiply(scale.toFloat())) }
    } ?: audience.warn("Entity spawning has been blocked.")
}

private fun version(context: CommandContext<Audience>) {
    val sender = context.sender()
    sender.info("Searching version, please wait...")
    PLATFORM.scheduler().asyncTask {
        val version = LATEST_VERSION
        sender.infoNotNull(
            emptyComponentOf(),
            "Current: ${PLATFORM.semver()}".toComponent(),
            version.release?.let { version -> componentOf("Latest release: ") { append(version.toURLComponent()) } },
            version.snapshot?.let { version -> componentOf("Latest snapshot: ") { append(version.toURLComponent()) } }
        )
    }
}

private fun reload(context: CommandContext<Audience>) {
    val audience = context.sender()
    PLATFORM.scheduler().asyncTask {
        audience.info("Start reloading. please wait...")
        when (val result = PLATFORM.reload(audience)) {
            is OnReload -> audience.warn("PrimeModel is still on reload!")
            is Success -> {
                audience.info(
                    emptyComponentOf(),
                    "Reload completed. (${result.totalTime().withComma()}ms)".toComponent(GREEN),
                    "Assets reload time - ${result.assetsTime().withComma()}ms".toComponent {
                        color(GRAY)
                        hoverEvent("Reading all config and model.".toComponent().toHoverEvent())
                    },
                    "Packing time - ${result.packingTime().withComma()}ms".toComponent {
                        color(GRAY)
                        hoverEvent("Packing all model to resource pack.".toComponent().toHoverEvent())
                    },
                    "${PrimeModel.models().size.withComma()} of models are loaded successfully. (${result.length().toByteFormat()})".toComponent(YELLOW),
                    (if (result.packResult.changed()) "${result.packResult.size().withComma()} of files are zipped." else "Zipping is skipped due to the same result.").toComponent(YELLOW),
                    emptyComponentOf()
                )
            }
            is Failure -> {
                audience.warn(
                    emptyComponentOf(),
                    "Reload failed.".toComponent(),
                    "Please read the log to find the problem.".toComponent(),
                    emptyComponentOf()
                )
                audience.warn()
                result.throwable.handleException("Reload failed.")
            }
        }
    }
}

private fun play(context: CommandContext<AudiencePlayer>) {
    val audience = context.sender()
    val player = audience.sender
    val limb = context.limb("limb") { return audience.warn("Unable to find this limb: $it") }
    val animation = context.string("animation") { limb.animation(it).orElse(null) ?: return audience.warn("Unable to find this animation: $it") }
    val loopType = context.nullable("loop_type", AnimationIterator.Type.PLAY_ONCE)
    val hide = context.nullable<Boolean>("hide") != false
    limb.getOrCreate(player.wrap(), TrackerModifier.DEFAULT) {
        it.hideOption(if (hide) EntityHideOption.DEFAULT else EntityHideOption.FALSE)
    }.run {
        if (!animate(animation, AnimationModifier(0, 0, loopType), ::close)) close()
    }
}

private fun test(context: CommandContext<Audience>) {
    val audience = context.sender()
    val model = context.model("model") { return audience.warn("Unable to find this model: $it") }
    val animation = context.string("animation") { str -> model.animation(str).orElse(null) ?: return audience.warn("Unable to find this animation: $str") }
    val player = context.nullable("player") { (audience as? AudiencePlayer)?.sender ?: return audience.warn("Unable to find target player.") }
    val location = context.nullable("location") {
        player.location.apply {
            add(Vector(0, 0, 10).rotateAroundY(-Math.toRadians(yaw.toDouble())))
            yaw += 180
        }
    }
    model.create(location.wrap()).run {
        spawn(player.wrap())
        animate(animation, AnimationModifier(0, 0, AnimationIterator.Type.PLAY_ONCE), ::close)
    }
}


// ============================================
// Command Builder (from CommandBuilder.kt)
// ============================================

class CommandBuildContext(
    val manager: CommandManager<Audience>,
    val commandMapper: CommandBuilder.(Command.Builder<Audience>) -> Command.Builder<Audience>,
    name: String,
    description: String,
    vararg aliases: String,
) {
    val root = CommandBuilder(
        null,
        this,
        CommandBuilder.Info(name, Description.description(description), aliases.toList())
    )

    fun build() {
        root.build().forEach {
            manager.command(it)
        }
    }
}

fun CommandManager<Audience>.register(
    name: String,
    description: String,
    commandMapper: CommandBuilder.(Command.Builder<Audience>) -> Command.Builder<Audience>,
    vararg aliases: String,
    block: CommandBuilder.() -> Unit
) = CommandBuildContext(this, commandMapper, name, description, *aliases).run {
    root.block()
    build()
}

inline fun CommandContext<*>.limb(key: String, notFound: (String) -> ModelRenderer) = optional<String>(key).flatMap {
    PrimeModel.limb(it)
}.orElse(null) ?: notFound(key)

inline fun CommandContext<*>.model(key: String, notFound: (String) -> ModelRenderer) = optional<String>(key).flatMap {
    PrimeModel.model(it)
}.orElse(null) ?: notFound(key)

inline fun <T> CommandContext<*>.string(key: String, mapper: (String) -> T) = mapper(get(key))

fun <T> CommandContext<*>.nullableString(key: String, mapper: (String) -> T): T? = optional<String>(key).map { mapper(it) }.orElse(null)

inline fun <reified T : Any> CommandContext<*>.nullable(key: String): T? = optional<T>(key).orElse(null)
inline fun <reified T : Any> CommandContext<*>.nullable(key: String, ifNotFound: T): T = optional<T>(key).orElse(null) ?: ifNotFound
inline fun <reified T : Any> CommandContext<*>.nullable(key: String, ifNotFound: () -> T): T = optional<T>(key).orElse(null) ?: ifNotFound()

class CommandBuilder(
    val parent: CommandBuilder?,
    val context: CommandBuildContext,
    val info: Info
) : CommandLike {

    private companion object {
        const val PAGE_SPLIT_INDEX = 5

        val prefix = listOf(
            emptyComponentOf(),
            "------ PrimeModel ${PLATFORM.semver()} ------".toComponent(GRAY),
            emptyComponentOf()
        )

        val fullPrefix = listOf(
            prefix,
            listOf(
                componentOf {
                    decorate(TextDecoration.BOLD)
                    append(spaceComponentOf())
                    append("[Wiki]".toComponent {
                        color(AQUA)
                    })
                    append(spaceComponentOf())
                    append("[Download]".toComponent {
                        color(GREEN)
                        toURLComponent("https://modrinth.com/plugin/PrimeModel/versions")
                    })
                    append(spaceComponentOf())
                    append("[Discord]".toComponent {
                        color(BLUE)
                        toURLComponent("https://discord.com/invite/rePyFESDbk")
                    })
                },
                emptyComponentOf()
            )
        ).flatten()

        fun TextComponent.Builder.toURLComponent(url: String) = hoverEvent(componentOf(
            url.toComponent(DARK_AQUA),
            lineComponentOf(),
            lineComponentOf(),
            "Click to open link.".toComponent()
        ).toHoverEvent()).clickEvent(ClickEvent.openUrl(url))
    }

    private val root: CommandBuilder = parent?.root ?: this
    private val suggest: String = parent?.let { "${it.suggest} ${info.name}" } ?: info.simpleName
    private val permission: String = parent?.let { "${it.permission}.${info.name}" } ?: info.name
    private val children = mutableListOf<CommandLike>()
    private val helpCommand by lazy {
        val maxPage = children.size / PAGE_SPLIT_INDEX + 1
        val helpComponents = (1..maxPage).map { index ->
            (if (index == 1) fullPrefix else prefix).toMutableList()
                .also { list ->
                    children.subList(PAGE_SPLIT_INDEX * (index - 1), (PAGE_SPLIT_INDEX * index).coerceAtMost(children.size)).forEach {
                        list += it.toComponent()
                    }
                    list += "/$suggest [help] [page] - help command.".toComponent(LIGHT_PURPLE)
                    list += emptyComponentOf()
                    list += "---------< Page $index / $maxPage >---------".toComponent(GRAY)
                }.toTypedArray()
        }
        val builder = createBuilder()
            .permission("$permission.help")
            .handler { ctx ->
                val page = ctx.getOrDefault("page", 1)
                    .coerceAtLeast(1)
                    .coerceAtMost(maxPage)
                ctx.sender().info(*helpComponents[page - 1])
            }
        listOf(
            builder
                .optional("page", IntegerParser.integerParser(1, maxPage))
                .build(),
            builder.literal("help", "h")
                .optional("page", IntegerParser.integerParser(1, maxPage))
                .build()
        )
    }

    fun create(
        name: String,
        description: String,
        vararg aliases: String,
        builder: Command.Builder<Audience>.() -> Command.Builder<out Audience>
    ) {
        children += CommandLike.Cloud(createBuilder()
            .mapInfo(Info(name, Description.description(description), aliases.toList()))
            .run(builder)
            .build())
    }

    data class Info(
        val name: String,
        val description: Description,
        val aliases: List<String>
    ) {
        val simpleName get() = if (aliases.isNotEmpty()) aliases.minBy { it.length } else name
    }

    override fun toComponent(): TextComponent {
        TODO("Not yet implemented")
    }

    override fun build(): List<Command<out Audience>> = buildList {
        children.flatMapTo(this) { it.build() }
        addAll(helpCommand)
    }

    private fun Command.Builder<Audience>.mapInfo(info: Info) = literal(info.name, *info.aliases.toTypedArray())
        .commandDescription(info.description)
        .permission("$permission.${info.name}")

    private fun createBuilder(): Command.Builder<Audience> = parent?.createBuilder()?.mapInfo(info) ?: context.commandMapper(this, context.manager.commandBuilder(
        info.name,
        info.description,
        *info.aliases.toTypedArray()
    ))
}

interface CommandLike {

    fun toComponent(): TextComponent

    fun build(): List<Command<out Audience>>

    data class Cloud(
        private val command: Command<out Audience>
    ) : CommandLike {

        override fun toComponent(): TextComponent = command.toComponent()

        private fun Command<out Audience>.toComponent() = componentOf {
            append("/".toComponent())
            components().forEachIndexed { i, comp ->
                append(comp.toComponent(i == 0))
                if (i < components().size) append(spaceComponentOf())
            }
            append(lineComponentOf())
            append("  |  ".toComponent { color(GREEN).decorate(TextDecoration.BOLD) })
            append(" ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Â ".toComponent())
            append(commandDescription().description().textDescription().toComponent(GRAY))
            hoverEvent(componentOf(
                "Permission:".toComponent(DARK_AQUA),
                lineComponentOf(),
                commandPermission().permissionString().toComponent(),
                lineComponentOf(),
                lineComponentOf(),
                "Click to suggest command.".toComponent()
            ).toHoverEvent())
            clickEvent(ClickEvent.suggestCommand("/" + components().filter {
                it.type() == LITERAL
            }.joinToString(" ") {
                it.name()
            }))
        }

        private fun CommandComponent<out Audience>.toComponent(root: Boolean): TextComponent = componentOf {
            val n = if (root) aliases().minBy { it.length } else name()
            when (type()) {
                LITERAL -> content(n).color(YELLOW)
                REQUIRED_VARIABLE -> content("<$n>").color(RED)
                OPTIONAL_VARIABLE -> content("[$n]").color(DARK_AQUA)
                FLAG -> content("-$n").color(LIGHT_PURPLE)
            }
            hoverEvent(componentOf {
                if (aliases().isNotEmpty()) {
                    append(componentOf(
                        "Aliases:".toComponent(DARK_AQUA),
                        lineComponentOf(),
                        componentWithLineOf(*aliases().map(String::toComponent).toTypedArray()),
                        lineComponentOf(),
                        lineComponentOf()
                    ))
                }
                append("Click to suggest command.".toComponent())
            }.toHoverEvent())
        }

        override fun build(): List<Command<out Audience>> = listOf(command)
    }
}