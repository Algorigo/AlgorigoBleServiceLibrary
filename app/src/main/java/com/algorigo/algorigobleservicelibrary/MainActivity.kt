package com.algorigo.algorigobleservicelibrary

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algorigo.algorigobleservicelibrary.service.BluetoothService
import com.algorigo.algorigobleservicelibrary.ui.theme.AlgorigoBleServiceLibraryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.rx3.asFlow

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Intent(this, BluetoothService::class.java).also {
            startService(it)
        }

        setContent {
            val advertisePermissionState = rememberPermissionState(Manifest.permission.BLUETOOTH_ADVERTISE)
            val connectPermissionState = rememberPermissionState(Manifest.permission.BLUETOOTH_CONNECT)
            val buttonState = advertisingObservable()
                .asFlow()
                .collectAsStateWithLifecycle(initialValue = "Start Advertising")
            val histories = BluetoothService.bindServiceObservble(this)
                .flatMap { it.connectionHistoryObservable }
                .asFlow()
                .collectAsStateWithLifecycle(initialValue = listOf())

            AlgorigoBleServiceLibraryTheme {
                // A surface container using the 'background' color from the theme
                Column(modifier = Modifier.fillMaxSize()) {
                    advertisePermissionState.status.also {
                        when (it) {
                            is PermissionStatus.Granted -> {
                                Text("${advertisePermissionState.permission} Granted")
                            }
                            is PermissionStatus.Denied -> {
                                if (!it.shouldShowRationale) {
                                    Text("${advertisePermissionState.permission} Permission Rational")
                                }
                                FilledTonalButton(onClick = { advertisePermissionState.launchPermissionRequest() }) {
                                    Text("Request Permission ${advertisePermissionState.permission}")
                                }
                            }
                        }
                    }
                    connectPermissionState.status.also {
                        when (it) {
                            is PermissionStatus.Granted -> {
                                Text("${connectPermissionState.permission} Granted")
                            }
                            is PermissionStatus.Denied -> {
                                if (!it.shouldShowRationale) {
                                    Text("${connectPermissionState.permission} Permission Rational")
                                }
                                FilledTonalButton(onClick = { connectPermissionState.launchPermissionRequest() }) {
                                    Text("Request Permission ${connectPermissionState.permission}")
                                }
                            }
                        }
                    }
                    FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        checkPermission(advertisePermissionState) {
                            checkPermission(connectPermissionState) {
                                onStartAdvertisingClick()
                            }
                        }
                    }) {
                        Text(buttonState.value)
                    }
                    LazyColumn(content = {
                        items(histories.value.size) { index ->
                            Column(Modifier.padding(16.dp)) {
                                Text(histories.value[index].first)
                                Text(histories.value[index].second.toString())
                                Text(histories.value[index].third?.toString() ?: "")
                            }
                        }
                    })
                }
            }
        }
    }

    private fun checkPermission(permissionState: PermissionState, callback: () -> Unit) {
        permissionState.status.also {
            when (it) {
                is PermissionStatus.Granted -> {
                    callback()
                }

                is PermissionStatus.Denied -> {
                    if (!it.shouldShowRationale) {
                        permissionState.launchPermissionRequest()
                    } else {
                        Toast
                            .makeText(this@MainActivity, "Permission ${permissionState.permission} Denied", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    private fun advertisingObservable() = BluetoothService.bindServiceObservble(this)
        .flatMap { it.advertisingObservable }
        .map {
            if (it) {
                "Stop Advertising"
            } else {
                "Start Advertising"
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