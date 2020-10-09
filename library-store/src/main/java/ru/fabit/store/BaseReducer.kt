package ru.fabit.store

abstract class BaseReducer<Event, State : BaseState<Event>, Action> : Reducer<State, Action>() {

    override fun reduce(state: State, action: Action): State {
        return state
    }

    override fun prereduce(state: State, action: Action): State {
        (state as BaseState<Event>).clearEvents()
        return reduce(state, action)
    }
}