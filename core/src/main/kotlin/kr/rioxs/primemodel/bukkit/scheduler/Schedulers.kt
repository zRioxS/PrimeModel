package kr.rioxs.primemodel.bukkit.scheduler

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit
import kr.rioxs.primemodel.api.bukkit.scheduler.BukkitModelScheduler
import kr.rioxs.primemodel.api.scheduler.Schedulers.ModelTask
import kr.rioxs.primemodel.bukkit.util.PLUGIN
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask

class BukkitScheduler : BukkitModelScheduler {

    private fun BukkitTask.wrap() = object : ModelTask {
        override fun isCancelled(): Boolean = this@wrap.isCancelled
        override fun cancel() {
            this@wrap.cancel()
        }
    }

    private fun ifEnabled(block: () -> ModelTask?): ModelTask? {
        return if (PLUGIN.isEnabled) block() else null
    }

    override fun task(location: Location, runnable: Runnable) = ifEnabled {
        Bukkit.getScheduler().runTask(PLUGIN, runnable).wrap()
    }
    override fun taskLater(location: Location, delay: Long, runnable: Runnable) = ifEnabled {
        Bukkit.getScheduler().runTaskLater(PLUGIN, runnable, delay).wrap()
    }
    override fun asyncTask(runnable: Runnable) = Bukkit.getScheduler().runTaskAsynchronously(PLUGIN, runnable).wrap()
    override fun asyncTaskLater(delay: Long, runnable: Runnable) = Bukkit.getScheduler().runTaskLaterAsynchronously(PLUGIN, runnable, delay).wrap()
    override fun asyncTaskTimer(delay: Long, period: Long, runnable: Runnable) = Bukkit.getScheduler().runTaskTimerAsynchronously(PLUGIN, runnable, delay, period).wrap()
}

class PaperScheduler : BukkitModelScheduler {

    private fun ScheduledTask.wrap() = object : ModelTask {
        override fun isCancelled(): Boolean = this@wrap.isCancelled
        override fun cancel() {
            this@wrap.cancel()
        }
    }

    private fun ifEnabled(block: () -> ModelTask?): ModelTask? {
        return if (PLUGIN.isEnabled) block() else null
    }

    override fun task(location: Location, runnable: Runnable): ModelTask? = ifEnabled {
        Bukkit.getRegionScheduler().run(PLUGIN, location) {
            runnable.run()
        }.wrap()
    }

    override fun taskLater(location: Location, delay: Long, runnable: Runnable): ModelTask? = ifEnabled {
        Bukkit.getRegionScheduler().runDelayed(PLUGIN, location, {
            runnable.run()
        }, delay).wrap()
    }

    override fun asyncTask(runnable: Runnable) = Bukkit.getAsyncScheduler().runNow(PLUGIN) {
        runnable.run()
    }.wrap()

    override fun asyncTaskLater(delay: Long, runnable: Runnable) = Bukkit.getAsyncScheduler().runDelayed(PLUGIN, {
        runnable.run()
    }, (delay * 50).coerceAtLeast(1), TimeUnit.MILLISECONDS).wrap()

    override fun asyncTaskTimer(delay: Long, period: Long, runnable: Runnable) = Bukkit.getAsyncScheduler().runAtFixedRate(PLUGIN, {
        runnable.run()
    }, (delay * 50).coerceAtLeast(1), (period * 50).coerceAtLeast(1), TimeUnit.MILLISECONDS).wrap()
}
