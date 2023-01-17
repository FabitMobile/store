package ru.fabit.store

import io.reactivex.Observable

open class BindActionSource<Action>(
    val query: (Action) -> Boolean,
    private val source: (Action) -> Observable<Action>,
    private val error: (Throwable) -> Action
) {
    open val key: String = this::class.qualifiedName ?: this::class.java.simpleName

    operator fun invoke(action: Action) = source(action)

    operator fun invoke(throwable: Throwable): Action = error(throwable)
}