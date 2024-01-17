package com.algorigo.algorigobleservicelibrary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.algorigo.algorigobleservicelibrary.service.BluetoothService
import com.algorigo.algorigobleservicelibrary.ui.theme.AlgorigoBleServiceLibraryTheme
import com.tbruyelle.rxpermissions3.RxPermissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Intent(this, BluetoothService::class.java).also {
            startService(it)
        }
        setContent {
            AlgorigoBleServiceLibraryTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row {
                        FilledTonalButton(onClick = { onStartAdvertisingClick() }) {
                            Text("Start Advertising")
                        }
                    }
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