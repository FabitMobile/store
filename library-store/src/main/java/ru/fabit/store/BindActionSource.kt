package ru.fabit.store

import io.reactivex.Observable

open class BindActionSource<Action>(
    val key: String,
    val query: (Action) -> Boolean,
    private val source: (Action) -> Observable<Action>,
    private val error: (Throwable) -> Action = {t: Throwable -> throw t}
) {
    operator fun invoke(action: Action) =
        source(action)

    operator fun invoke(throwable: Throwable): Action =
        error(throwable)
}