package ru.fabit.corestore

import io.reactivex.disposables.Disposable

class SourceDisposable {
    private val map = hashMapOf<String, Disposable>()

    fun add(key: String, disposable: Disposable) {
        map.put(key, disposable)
    }

    fun dispose(key: String) {
        map[key]?.let { disposable ->
            if (!disposable.isDisposed) {
                disposable.dispose()
                map.remove(key)
            }
        }
    }

    fun dispose() {
        for (disposable in map){
            if (!disposable.value.isDisposed){
                disposable.value.dispose()
            }
        }
        map.clear()
    }
}