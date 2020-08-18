package ru.fabit.corestore

import io.reactivex.BackpressureStrategy
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.observers.DisposableObserver
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subscribers.DisposableSubscriber
import java.util.concurrent.CopyOnWriteArrayList

abstract class BaseStore<State, Action>(
    private var currentState: State,
    private var reducer: Reducer<State, Action>,
    private val bootstrapper: () -> Single<Action>,
    private val sideEffects: Iterable<SideEffect<State, Action>> = CopyOnWriteArrayList(),
    private val actionSources: Iterable<ActionSource<Action>> = CopyOnWriteArrayList(),
    private val bindActionSources: Iterable<BindActionSource<Action>> = CopyOnWriteArrayList()
) : Store<State, Action> {

    private val disposable = CompositeDisposable()
    private val sourceDisposable = SourceDisposable()

    private val actionSubject = PublishSubject.create<Action>()
    private val stateSubject = BehaviorSubject.createDefault(currentState)
    private val sideEffectSubject = PublishSubject.create<Pair<State, Action>>()

    init {
        disposable.add(reduce())
        disposable.add(sideEffectDispatch())
        actionSourceDispatch()
        bindActionSourceDispatch()
        disposable.add(bootstrapper().subscribe(Consumer { dispatchAction(it) }))
    }

    /**
     * Отправляет событие на обработку в reducer а так же влияет на запуск сайд-эффектов
     *
     * action - событие
     */
    override fun dispatchAction(action: Action) {
        actionSubject.onNext(action)
    }

    override fun subscribe(observer: Observer<in State>, observerOnScheduler: Scheduler) {
        stateSubject
            .distinctUntilChanged { state1: State, state2: State ->
                state1 == state2
            }
            .observeOn(observerOnScheduler)
            .subscribe(observer)
    }

    override fun unsubscribe(observer: DisposableObserver<in State>) {
        observer.dispose()
    }

    override fun dispose() {
        disposable.clear()
        sourceDisposable.dispose()
    }

    private fun reduce(): Disposable {
        //Passage through the reducer should be one at a time
        val countRequestItem = 1L

        val subscriber = object : DisposableSubscriber<Action>() {
            override fun onStart() {
                request(countRequestItem)
            }

            override fun onComplete() {
            }

            override fun onNext(action: Action) {
                val state = reducer.reduce(stateSubject.value, action)
                if (state != stateSubject.value) {
                    stateSubject.onNext(state)
                }
                sideEffectSubject.onNext(Pair(state, action))
                request(countRequestItem)
            }

            override fun onError(t: Throwable?) {
                t?.printStackTrace()
            }
        }
        actionSubject
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribe(subscriber)
        return subscriber
    }

    private fun sideEffectDispatch(): Disposable {
        return sideEffectSubject
            .toFlowable(BackpressureStrategy.LATEST)
            .switchMap { pair ->
                Single.merge(sideEffects
                    .filter {
                        it.query(
                            pair.first,
                            pair.second
                        )
                    }
                    .map { sideEffect ->
                        sideEffect(pair.first, pair.second)
                            .map { it }
                            .onErrorReturn { throwable ->
                                sideEffect(throwable)
                            }
                    })
            }.subscribe({ action ->
                actionSubject.onNext(action)
            }, { it.printStackTrace() })
    }


    private fun actionSourceDispatch() {
        actionSources.map { actionSource ->
            sourceDisposable.add(
                actionSource.key,
                actionSource().subscribe { action -> actionSubject.onNext(action) }
            )
        }
    }

    private fun bindActionSourceDispatch() {
        bindActionSources.map { actionSource ->
            sourceDisposable.add(
                actionSource.key,
                actionSubject
                    .filter { actionSource.query(it) }
                    .switchMap { actionSource(it) }
                    .subscribe({ action ->
                        actionSubject.onNext(
                            action
                        )
                    }, { e -> e.printStackTrace() })

            )
        }
    }
}