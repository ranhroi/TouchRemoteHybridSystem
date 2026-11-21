package com.ranhroi.touchremote.ui.screens.server

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class PortsViewModel @Inject constructor(
    private val dataStore: DataStoreManager
) : ViewModel() {

    val addressPortsModelResult: StateFlow<AddressPortsModel> =
        combine(
            dataStore.get(
                ConstantsUtils.KEY_PORT_SCAN,
                ConstantsUtils.PORT_SCAN
            ),
            dataStore.get(
                ConstantsUtils.KEY_PORT_CONNECTION,
                ConstantsUtils.PORT_CONNECTION
            )
        ) { portScan, portConnection ->

            AddressPortsModel(
                portScan = portScan,
                portConnection = portConnection
            )
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                AddressPortsModel()
            )

    fun updatePorts(new: AddressPortsModel) {
        viewModelScope.launch {
            runCatching {
                dataStore.apply {
                    put(ConstantsUtils.KEY_PORT_SCAN, new.portScan)
                    put(ConstantsUtils.KEY_PORT_CONNECTION, new.portConnection)
                }
            }.onFailure {
                Log.e("PortsViewModel", "Lỗi khi cập nhật ports", it)
            }
        }
    }
}
