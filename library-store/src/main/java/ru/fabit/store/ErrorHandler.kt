package ru.fabit.store

interface ErrorHandler {
    fun handleError(t: Throwable)
}