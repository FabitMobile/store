package ru.fabit.store

import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

open class ActionHandler<Action>(
    val query: (Action) -> Boolean,
    private val handler: (Action) -> (Unit),
    private val error: (Throwable) -> Action,
    val handlerScheduler: Scheduler = Schedulers.trampoline()
) {
    open val key: String = this::class.qualifiedName ?: this::class.java.simpleName

    operator fun invoke(action: Action) = handler(action)

    operator fun invoke(error: Throwable) = error(error)
}