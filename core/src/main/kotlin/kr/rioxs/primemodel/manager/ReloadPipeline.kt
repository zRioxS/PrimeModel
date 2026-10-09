package kr.rioxs.primemodel.manager
import kr.rioxs.primemodel.api.manager.Managers.Manager

import kr.rioxs.primemodel.bukkit.util.audience
import kr.rioxs.primemodel.util.PLATFORM
import kr.rioxs.primemodel.util.componentOf
import kr.rioxs.primemodel.util.emptyComponentOf
import kr.rioxs.primemodel.util.parallelIOThreadPool
import kr.rioxs.primemodel.util.toComponent
import kr.rioxs.primemodel.util.withComma
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.format.NamedTextColor
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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