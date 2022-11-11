package ru.fabit.store

import io.reactivex.Single

open class SideEffect<State, Action>(
    val query: (State, Action) -> Boolean,
    private val effect: (State, Action) -> Single<out Action>,
    private val error: (Throwable) -> Action
) {

    operator fun invoke(state: State, action: Action) =
        effect(state, action)

    operator fun invoke(throwable: Throwable): Action =
        error(throwable)
}