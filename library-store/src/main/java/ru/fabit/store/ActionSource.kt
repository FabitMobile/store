package ru.fabit.store

import io.reactivex.Observable

open class ActionSource<Action>(
        val key: String,
        private val source: () -> Observable<Action>
){
    operator fun invoke() =
            source()
}