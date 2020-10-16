package ru.fabit.store

import io.reactivex.Observable

open class ActionSource<Action>(
    val key: String,
    private val source: () -> Observable<Action>,
    private val error: (Throwable) -> Action = {t: Throwable -> throw t}
){
    operator fun invoke() =
        source()

    operator fun invoke(throwable: Throwable): Action =
        error(throwable)
}