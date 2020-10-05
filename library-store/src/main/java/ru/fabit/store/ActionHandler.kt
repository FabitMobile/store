package ru.fabit.store

import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

open class ActionHandler<Action>(
    val key: String,
    val query: (Action) -> Boolean,
    private val handler: (Action) -> (Unit),
    val handlerScheduler: Scheduler = Schedulers.trampoline()
) {
    operator fun invoke(action: Action) =
        handler(action)
}