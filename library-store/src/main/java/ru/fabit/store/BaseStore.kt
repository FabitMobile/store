package ru.fabit.store

import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subscribers.DisposableSubscriber
import java.util.concurrent.CopyOnWriteArrayList

abstract class BaseStore<State, Action : Any>(
    currentState: State,
    private var reducer: Reducer<State, Action>,
    bootstrapper: Action?,
    private val errorHandler: ErrorHandler,
    private val sideEffects: Iterable<SideEffect<State, Action>> = CopyOnWriteArrayList(),
    private val actionSources: Iterable<ActionSource<Action>> = CopyOnWriteArrayList(),
    private val bindActionSources: Iterable<BindActionSource<State, Action>> = CopyOnWriteArrayList(),
    private val actionHandlers: Iterable<ActionHandler<State, Action>> = CopyOnWriteArrayList()
) : Store<State, Action> {

    protected val disposable = CompositeDisposable()
    protected val sourceDisposable = SourceDisposable()

    protected val actionSubject = PublishSubject.create<Action>()
    protected val stateSubject = BehaviorSubject.createDefault(currentState)

    init {
        disposable.add(handleActions())
        actionSourceDispatch()
        bootstrapper?.let { dispatchAction(it) }
    }

    /**
     * Отправляет событие на обработку в reducer а так же влияет на запуск сайд-эффектов
     *
     * action - событие
     */
    final override fun dispatchAction(action: Action) {
        actionSubject.onNext(action)
    }

    override fun subscribe(
        observer: Observer<in State>,
        observerOnScheduler: Scheduler,
        subscribeOnScheduler: Scheduler
    ) {
        stateSubject
            .subscribeOn(subscribeOnScheduler)
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

    private fun handleActions(): Disposable {
        //Passage through the reducer should be one at a time
        val countRequestItem = 1L

        val subscriber = object : DisposableSubscriber<Action>() {
            override fun onStart() {
                request(countRequestItem)
            }

            override fun onComplete() {
            }

            override fun onNext(action: Action) {
                val state = reducer.reduce(stateSubject.value!!, action)!!
                stateSubject.onNext(state)
                sideEffectDispatch(state, action)
                actionHandlerDispatch(state, action)
                bindActionSourceDispatch(state, action)
                request(countRequestItem)
            }

            override fun onError(t: Throwable?) {
                t?.let {
                    errorHandler.handleError(t)
                }
            }
        }
        actionSubject
            .toFlowable(BackpressureStrategy.BUFFER)
            .observeOn(Schedulers.computation())
            .subscribe(subscriber)
        return subscriber
    }

    private fun sideEffectDispatch(state: State, action: Action) {
        sideEffects.filter { sideEffect ->
            sideEffect.query(state, action)
        }.forEach { sideEffect ->
            val effect = try {
                sideEffect(state, action)
            } catch (t: Throwable) {
                Single.error(t)
            }
            sourceDisposable.add(
                sideEffect.key,
                effect
                    .doOnError { errorHandler.handleError(it) }
                    .onErrorResumeNext { throwable ->
                        Single.just(sideEffect(throwable))
                    }
                    .subscribe({ action ->
                        actionSubject.onNext(action)
                    }, {
                        errorHandler.handleError(it)
                    })
            )
        }
    }

    private fun actionHandlerDispatch(state: State, action: Action) {
        actionHandlers.filter { actionHandler ->
            actionHandler.query(state, action)
        }.forEach { actionHandler ->
            val handler = try {
                Single.create { it.onSuccess(actionHandler(state, action)) }
            } catch (t: Throwable) {
                Single.error(t)
            }
            sourceDisposable.add(
                actionHandler.key,
                handler
                    .doOnError { errorHandler.handleError(it) }
                    .subscribeOn(actionHandler.handlerScheduler)
                    .subscribe({ }, {
                        errorHandler.handleError(it)
                    })
            )
        }
    }

    private fun actionSourceDispatch() {
        actionSources.forEach { actionSource ->
            val source = try {
                actionSource()
            } catch (t: Throwable) {
                Observable.error(t)
            }
            sourceDisposable.add(
                actionSource.key,
                source
                    .doOnError { errorHandler.handleError(it) }
                    .onErrorResumeNext { throwable: Throwable ->
                        Observable.create { emitter ->
                            emitter.onNext(actionSource(throwable))
                        }
                    }
                    .subscribe({ action ->
                        actionSubject.onNext(action)
                    }, {
                        errorHandler.handleError(it)
                    })
            )
        }
    }

    private fun bindActionSourceDispatch(state: State, action: Action) {
        bindActionSources.filter { actionSource ->
            actionSource.query(state, action)
        }.forEach { actionSource ->
            val source = try {
                actionSource(state, action)
            } catch (t: Throwable) {
                Observable.error(t)
            }
            sourceDisposable.add(
                actionSource.key,
                source
                    .doOnError { errorHandler.handleError(it) }
                    .onErrorResumeNext { throwable: Throwable ->
                        Observable.create { emitter ->
                            emitter.onNext(actionSource(throwable))
                        }
                    }
                    .subscribe({ action ->
                        actionSubject.onNext(action)
                    }, {
                        errorHandler.handleError(it)
                    })
            )
        }
    }
}