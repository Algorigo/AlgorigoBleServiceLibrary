package com.algorigo.test_app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.algorigo.test_app.ui.theme.AlgorigoBleServiceLibraryTheme
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo

class TestActivity : ComponentActivity() {

    private var disposable = CompositeDisposable()
    private var title = mutableStateOf("Start Scan")
    private var states = mutableStateListOf<TestBle.State>()
    private val buttonRelay = PublishRelay.create<Any>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSubscribe()
        setContent {
            AlgorigoBleServiceLibraryTheme {
                // A surface container using the 'background' color from the theme
                Column(modifier = Modifier.fillMaxWidth()) {
                    ScanButton(title = title, onClick = {
                        onScanClick()
                    }, modifier = Modifier.fillMaxWidth())
                    DeviceList(states = states, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    private fun initSubscribe() {
        TestService
            .bindServiceObservble(this)
            .flatMapCompletable { service ->
                Completable.merge(listOf(
                    service.scanningRelay
                        .switchMapSingle { isScanning ->
                            buttonRelay.firstOrError()
                                .doOnSuccess {
                                    if (isScanning) {
                                        service.stopScan()
                                    } else {
                                        service.startScan()
                                    }
                                }
                                .map { !isScanning }
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext {
                            if (it) {
                                title.value = "Stop Scan"
                            } else {
                                title.value = "Start Scan"
                            }
                        }
                        .ignoreElements(),
                    service.statesRelay
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext {
                            states.clear()
                            states.addAll(it)
                        }
                        .ignoreElements(),
                ))
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({

            }, {

            })
            .addTo(disposable)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun onScanClick() {
        Log.e("!!!", "onScanClick")
        buttonRelay.accept(1)
    }

    companion object {
        private val LOG_TAG = TestActivity::class.java.simpleName
    }
}

@Composable
fun ScanButton(title: MutableState<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Text(title.value)
    }
}

@Composable
fun DeviceList(states: List<TestBle.State>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, content = {
        items(states.size) { index ->
            Device(state = states[index], modifier = modifier)
        }
    })
}

@Composable
fun Device(state: TestBle.State, modifier: Modifier = Modifier) {
    Column {
        Text(text = state.macAddress, modifier = modifier)
        Text(text = state.connectedTime.toString(), modifier = modifier)
        Text(text = state.notificationCount.toString(), modifier = modifier)
        Text(text = state.disconnectedTime?.toString() ?: "Not Disconnected", modifier = modifier)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AlgorigoBleServiceLibraryTheme {
        Greeting("Android")
    }
}
