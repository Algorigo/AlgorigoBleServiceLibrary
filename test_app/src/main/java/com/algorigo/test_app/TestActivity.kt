package com.algorigo.test_app

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.algorigo.algorigoble2.BleDevice
import com.algorigo.algorigoble2.BleManager
import com.algorigo.test_app.ui.theme.AlgorigoBleServiceLibraryTheme
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit

class TestActivity : ComponentActivity() {

    private var devices = listOf<BleDevice>()
    private val manager = BleManager(this)
    private var scanDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlgorigoBleServiceLibraryTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row {
                        FilledTonalButton(onClick = { onScanClick() }) {
                            Text("Start Scan")
                        }
                    }
                    LazyColumn(content = {
                        items(devices.size) { index ->
                            Text(text = "${devices[index].deviceId} : ${devices[index].deviceName}")
                        }
                    })
                }
            }
        }
    }

    private fun onScanClick() {
        if (scanDisposable != null) {
            scanDisposable?.dispose()
        } else {
            scanDisposable = manager.scanObservable()
                .take(10, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    scanDisposable = null
                }
                .subscribe({
                    Log.e(LOG_TAG, "devices:$it")
                    this.devices = it
                }, {
                    Log.e(LOG_TAG, "error", it)
                })
        }
    }

    companion object {
        private val LOG_TAG = TestActivity::class.java.simpleName
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