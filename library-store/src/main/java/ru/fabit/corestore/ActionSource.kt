package ru.fabit.corestore

import io.reactivex.Observable

open class ActionSource<Action>(
        val key: String,
        private val source: () -> Observable<Action>
){
    operator fun invoke() =
            source()
}