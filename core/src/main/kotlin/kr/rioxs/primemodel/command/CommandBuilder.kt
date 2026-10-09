package kr.rioxs.primemodel.command
import kr.rioxs.primemodel.api.manager.Managers.Manager

import kr.rioxs.primemodel.api.data.renderer.ModelRenderer
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.bukkit.util.audience
import kr.rioxs.primemodel.bukkit.util.PLUGIN
import kr.rioxs.primemodel.util.*
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor.*
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.TextComponent
import org.incendo.cloud.Command
import org.incendo.cloud.CommandManager
import org.incendo.cloud.component.CommandComponent
import org.incendo.cloud.component.CommandComponent.ComponentType.*
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.description.Description
import org.incendo.cloud.parser.standard.IntegerParser

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
            append(" Ã¢â€â€ ".toComponent())
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
