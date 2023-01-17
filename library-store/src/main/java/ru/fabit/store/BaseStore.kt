package ru.fabit.store

import io.reactivex.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.observers.DisposableObserver
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subscribers.DisposableSubscriber
import java.util.concurrent.CopyOnWriteArrayList

abstract class BaseStore<State, Action : Any>(
    currentState: State,
    private var reducer: Reducer<State, Action>,
    bootstrapper: () -> Single<Action>,
    private val errorHandler: ErrorHandler,
    private val sideEffects: Iterable<SideEffect<State, Action>> = CopyOnWriteArrayList(),
    private val actionSources: Iterable<ActionSource<Action>> = CopyOnWriteArrayList(),
    private val bindActionSources: Iterable<BindActionSource<Action>> = CopyOnWriteArrayList(),
    private val actionHandlers: Iterable<ActionHandler<Action>> = CopyOnWriteArrayList()
) : Store<State, Action> {

    private val disposable = CompositeDisposable()
    private val sourceDisposable = SourceDisposable()

    private val actionSubject = PublishSubject.create<Action>()
    private val stateSubject = BehaviorSubject.createDefault(currentState)

    init {
        disposable.add(reduce())
        sideEffectDispatch()
        bindActionSourceDispatch()
        disposable.add(bootstrapper().subscribe(Consumer { dispatchAction(it) }))
        actionSourceDispatch()
        actionHandlerDispatch()
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
                val state = reducer.prereduce(stateSubject.value!!, action)
                stateSubject.onNext(state!!)
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

    private fun sideEffectDispatch() {
        sideEffects.map { sideEffect ->
            sourceDisposable.add(
                sideEffect.key,
                actionSubject.map {
                    Pair(stateSubject.value!!, it)
                }
                    .filter {
                        sideEffect.query(it.first, it.second)
                    }
                    .switchMapSingle { stateActionPair ->
                        sideEffect(stateActionPair.first, stateActionPair.second)
                            .map { it }
                            .doOnError { errorHandler.handleError(it) }
                            .onErrorResumeNext { throwable: Throwable ->
                                Single.create { emitter ->
                                    emitter.onSuccess(
                                        sideEffect(throwable)
                                    )
                                }
                            }
                    }
                    .subscribe({ action ->
                        actionSubject.onNext(action)
                    }, { it.printStackTrace() })
            )
        }
    }


    private fun actionHandlerDispatch() {
        actionHandlers.map { handler ->
            sourceDisposable.add(
                handler.key,
                actionSubject
                    .filter { handler.query(it) }
                    .observeOn(handler.handlerScheduler)
                    .subscribe({ action ->
                        handler.invoke(action)
                    },
                        { throwable ->
                            throwable.printStackTrace()
                        }
                    )
            )
        }
    }

    private fun actionSourceDispatch() {
        actionSources.map { actionSource ->
            sourceDisposable.add(
                actionSource.key,
                actionSource()
                    .doOnError { errorHandler.handleError(it) }
                    .onErrorResumeNext { throwable: Throwable ->
                        Observable.create { emitter ->
                            emitter.onNext(
                                actionSource(
                                    throwable
                                )
                            )
                        }
                    }
                    .subscribe { action -> actionSubject.onNext(action) }
            )
        }
    }

    private fun bindActionSourceDispatch() {
        bindActionSources.map { actionSource ->
            sourceDisposable.add(
                actionSource.key,
                actionSubject
                    .filter { actionSource.query(it) }
                    .switchMap { action ->
                        actionSource(action)
                            .doOnError { errorHandler.handleError(it) }
                            .onErrorResumeNext { throwable: Throwable ->
                                Observable.create { emitter ->
                                    emitter.onNext(
                                        actionSource(
                                            throwable
                                        )
                                    )
                                }
                            }
                    }
                    .subscribe({ action ->
                        actionSubject.onNext(
                            action
                        )
                    }, { e -> e.printStackTrace() })

            )
        }
    }
}