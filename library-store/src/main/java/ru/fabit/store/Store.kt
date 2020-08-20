package ru.fabit.store

import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.observers.DisposableObserver

interface Store<State, Action> {

    fun dispatchAction(action: Action)

    fun subscribe(observer: Observer<in State>, observerOnScheduler: Scheduler)

    fun unsubscribe(observer: DisposableObserver<in State>)

    fun dispose()
}