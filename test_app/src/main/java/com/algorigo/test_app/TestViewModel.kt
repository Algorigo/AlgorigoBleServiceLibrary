package com.algorigo.test_app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestViewModel : ViewModel() {
    private val _statesFlow = MutableStateFlow(listOf<TestBle.State>())
    val statesFlow: StateFlow<List<TestBle.State>>
        get() = _statesFlow.asStateFlow()

    fun updateStates(list: List<TestBle.State>) {
        _statesFlow.value = list
    }
}
