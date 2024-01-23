package com.algorigo.algorigobleservicelibrary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algorigo.algorigobleservicelibrary.service.BluetoothService
import com.algorigo.algorigobleservicelibrary.ui.theme.AlgorigoBleServiceLibraryTheme
import com.tbruyelle.rxpermissions3.RxPermissions
import kotlinx.coroutines.rx3.asFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Intent(this, BluetoothService::class.java).also {
            startService(it)
        }

        setContent {
            val histories = BluetoothService.bindServiceObservble(this)
                .flatMap { it.connectionHistoryObservable }
                .asFlow()
                .collectAsStateWithLifecycle(initialValue = listOf())

            AlgorigoBleServiceLibraryTheme {
                // A surface container using the 'background' color from the theme
                Column(modifier = Modifier.fillMaxSize()) {
                    FilledTonalButton(onClick = { onStartAdvertisingClick() }) {
                        Text("Start Advertising")
                    }
                    LazyColumn(content = {
                        items(histories.value.size) { index ->
                            Column(Modifier.padding(16.dp)) {
                                Text(histories.value[index].first.toString())
                                Text(histories.value[index].second?.toString() ?: "")
                            }
                        }
                    })
                }
            }
        }
    }

    private fun onStartAdvertisingClick() {
        BluetoothService.bindServiceObservble(this)
            .doOnNext {
                it.toggleAdvertising()
            }
            .firstOrError()
            .ignoreElement()
            .subscribe({
                Log.e(LOG_TAG, "toggle success")
            }, {
                Log.e(LOG_TAG, "toggle error", it)
            })
    }

    companion object {
        private val LOG_TAG = MainActivity::class.java.simpleName
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