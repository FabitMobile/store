package ru.fabit.store

import io.reactivex.Observable

open class BindActionSource<Action>(
    val key: String,
    val query: (Action) -> Boolean,
    private val source: (Action) -> Observable<Action>
) {
    operator fun invoke(action: Action) =
        source(action)
}