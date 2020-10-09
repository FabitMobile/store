package ru.fabit.store

open class BaseState<Event>(private val events: MutableList<Event> = ArrayList()) {

    fun addEvent(event: Event) {
        events.add(event)
    }

    fun clearEvent(event: Event) {
        events.remove(event)
    }

    fun clearEvents() {
        events.clear()
    }
}