package ru.fabit.store

open class BaseState<Event>(private val events: MutableList<Event> = ArrayList()) {

    fun events(): List<Event> {
        return events
    }

    fun addEvent(event: Event) {
        events.add(event)
    }

    fun clearEvent(event: Event) {
        events.remove(event)
    }

    fun clearEvents() {
        events.clear()
    }


    fun compare(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseState<*>

        if (events != other.events) return false

        return true
    }
}

fun Any?.isEventsChange(other: Any?): Boolean {
    val eventPrev = this as? BaseState<*>
    val eventNew = other as? BaseState<*>
    val isEventsEquals = eventPrev?.compare(eventNew) ?: false

    return eventPrev != null && eventNew != null && !isEventsEquals
}