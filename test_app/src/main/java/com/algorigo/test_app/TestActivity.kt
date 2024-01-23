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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algorigo.test_app.ui.theme.AlgorigoBleServiceLibraryTheme
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import kotlinx.coroutines.rx3.asFlow

class TestActivity : ComponentActivity() {

    private var disposable = CompositeDisposable()
    private val buttonRelay = PublishRelay.create<Any>()
    private lateinit var testViewModel: TestViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSubscribe()

        testViewModel = ViewModelProvider(this).get(TestViewModel::class.java)

        setContent {
            val buttonState = scanningObservable()
                .asFlow()
                .collectAsStateWithLifecycle(initialValue = "Start Scan")
            val statesFlow = testViewModel.statesFlow.collectAsStateWithLifecycle()

            AlgorigoBleServiceLibraryTheme { // A surface container using the 'background' color from the theme
                Column(modifier = Modifier.fillMaxWidth()) {
                    ScanButton(buttonState.value, Modifier.fillMaxWidth()) {
                        onScanClick()
                    }
                    DeviceList(states = statesFlow.value, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    private fun initSubscribe() {
        TestService
            .bindServiceObservble(this)
            .flatMap { service ->
                service.statesRelay
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                testViewModel.updateStates(it)
            }, {
                Log.e(LOG_TAG, "states relay", it)
            })
            .addTo(disposable)
    }

    private fun scanningObservable() = TestService
        .bindServiceObservble(this)
        .flatMap { service ->
            service.scanningRelay.switchMap { isScanning ->
                Observable
                    .just(isScanning)
                    .concatWith(buttonRelay
                        .firstOrError()
                        .doOnSuccess {
                            if (isScanning) {
                                service.stopScan()
                            } else {
                                service.startScan()
                            }
                        }
                        .ignoreElement())
            }
        }
        .map {
            if (it) {
                "Stop Scan"
            } else {
                "Start Scan"
            }
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
fun ScanButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Text(title)
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
        text = "Hello $name!", modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AlgorigoBleServiceLibraryTheme {
        Greeting("Android")
    }
}
