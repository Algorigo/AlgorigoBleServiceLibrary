package com.algorigo.algorigobleservicelibrary.util

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.internal.disposables.DisposableHelper
import io.reactivex.rxjava3.observers.SerializedObserver
import io.reactivex.rxjava3.plugins.RxJavaPlugins

fun <T : Any> Observable<T>.throttle(throttleObservable: Observable<Any>): Observable<T> {
    return RxJavaPlugins.onAssembly(ObservableThrottle(this, throttleObservable))
}

class ObservableThrottle<T : Any>(
    private val source: ObservableSource<T>,
    private val throttleObservable: Observable<Any>
) : Observable<T>() {
    override fun subscribeActual(t: Observer<in T>) {
        source.subscribe(
            DebounceObserver(
                SerializedObserver(t),
                throttleObservable,
            )
        )
    }

    internal class DebounceObserver<T : Any>(
        val downstream: Observer<in T>,
        val throttleObservable: Observable<Any>
    ) : Observer<T>, Disposable {
        var upstream: Disposable? = null
        var throttle: Disposable? = null

        @Volatile
        var gate = false

        override fun onSubscribe(d: Disposable) {
            if (DisposableHelper.validate(upstream, d)) {
                upstream = d
                downstream.onSubscribe(this)
                throttle = throttleObservable
                    .doFinally {
                        upstream?.dispose()
                    }
                    .subscribe({
                        gate = false
                    }, {
                        downstream.onError(it)
                    }, {
                        downstream.onComplete()
                    })
            }
        }

        override fun onNext(t: T) {
            if (!gate) {
                gate = true
                downstream.onNext(t)
            }
        }

        override fun onError(t: Throwable) {
            downstream.onError(t)
        }

        override fun onComplete() {
            downstream.onComplete()
        }

        override fun dispose() {
            upstream!!.dispose()
        }

        override fun isDisposed(): Boolean {
            return upstream!!.isDisposed
        }
    }
}
