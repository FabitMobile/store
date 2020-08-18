package ru.fabit.corestore

abstract class Reducer<State, Action> {
    abstract fun reduce(state: State, action: Action): State
}