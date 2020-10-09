package ru.fabit.store

abstract class Reducer<State, Action> {
    abstract fun reduce(state: State, action: Action): State

    open fun prereduce(state: State, action: Action): State {
        return reduce(state, action)
    }
}