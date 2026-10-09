package kr.rioxs.primemodel
import kr.rioxs.primemodel.api.manager.Managers.Manager

import gg.moonflower.molangcompiler.api.MolangCompiler
import gg.moonflower.molangcompiler.api.MolangRuntime
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Supplier
import kr.rioxs.primemodel.api.event.EventInterfaces.CancellableEvent
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEvent
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEventApplication
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEventListener
import kr.rioxs.primemodel.api.PrimeModelEvaluator
import kr.rioxs.primemodel.api.PrimeModelEventBus
import kr.rioxs.primemodel.api.util.Utils.Functions.Float2FloatFunction
import kr.rioxs.primemodel.api.util.Utils.Locks.DuplexLock
import kr.rioxs.primemodel.util.handleFailure

class PrimeModelEvaluatorImpl : PrimeModelEvaluator {

    private val molang = MolangCompiler.create(MolangCompiler.DEFAULT_FLAGS, javaClass.classLoader)

    private fun Float.query() = MolangRuntime.runtime()
        .setQuery("life_time", this)
        .setQuery("anim_time", this)
        .create()

    override fun compile(expression: String): Float2FloatFunction {
        val compiled = molang.compile(expression)
        return Float2FloatFunction {
            it.query().safeResolve(compiled)
        }
    }
}

class PrimeModelEventBusImpl(
    private val externalCallback: (Class<out ModelEvent>, Supplier<out ModelEvent>) -> PrimeModelEventBus.Result = { _, _ -> PrimeModelEventBus.Result.NO_EVENT_HANDLER }
) : PrimeModelEventBus {

    private val subscribers = ConcurrentHashMap<Class<*>, BusManager>()

    override fun <T : ModelEvent> subscribe(application: ModelEventApplication, eventClass: Class<T>, consumer: Consumer<T>): ModelEventListener {
        return subscribers.computeIfAbsent(eventClass) { BusManager(it) }
            .register(application, consumer)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ModelEvent> call(eventClass: Class<out T>, eventSupplier: Supplier<T>): PrimeModelEventBus.Result {
        return subscribers[eventClass]?.let { manager ->
            eventSupplier
                .get()
                .also(manager)
                .also { externalCallback(eventClass) { it } }
                .let { event ->
                    if (event !is CancellableEvent || !event.isCancelled()) PrimeModelEventBus.Result.SUCCESS else PrimeModelEventBus.Result.FAIL
                }
        } ?: run {
            externalCallback(eventClass) { eventSupplier.get() }
        }
    }

    private inner class BusManager(
        private val clazz: Class<*>
    ) : (ModelEvent) -> Unit {
        private val map = ConcurrentHashMap<ModelEventApplication, ListenerRegistry>()

        fun <T : ModelEvent> register(application: ModelEventApplication, consumer: Consumer<T>): ModelEventListener {
            if (!application.isEnabled) return ModelEventListener.NONE
            return map.compute(application) { k, v -> v?.takeIf { k.isEnabled } ?: ListenerRegistry() }?.add(consumer) ?: ModelEventListener.NONE
        }

        override fun invoke(p1: ModelEvent) {
            synchronized(this) {
                map.entries.removeIf { (application, registry) ->
                    !application.isEnabled || runCatching {
                        registry(p1)
                    }.handleFailure {
                        "Unable to pass this event: ${clazz.simpleName}"
                    }.isFailure
                }
                if (map.isEmpty()) subscribers.remove(clazz, this)
            }
        }
    }

    private class ListenerRegistry : (ModelEvent) -> Unit {
        val map = IdentityHashMap<ModelEventListener, Consumer<*>>()
        val lock = DuplexLock()

        fun add(consumer: Consumer<*>): ModelEventListener = ListenerImpl(this, consumer)

        @Suppress("UNCHECKED_CAST")
        override fun invoke(p1: ModelEvent) {
            lock.accessToReadLock {
                map.values.forEach { consumer ->
                    (consumer as Consumer<ModelEvent>).accept(p1)
                }
            }
        }
    }

    private class ListenerImpl(
        registry: ListenerRegistry,
        consumer: Consumer<*>
    ) : ModelEventListener {

        private val ref = WeakReference(registry)

        init {
            registry.lock.accessToWriteLock {
                registry.map[this] = consumer
            }
        }

        override fun unregister() {
            ref.get()?.let {
                it.lock.accessToWriteLock { it.map.remove(this) }
            }
        }
    }
}
