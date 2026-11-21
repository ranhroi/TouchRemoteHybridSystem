package com.ranhroi.touchremote.ui.pager.application

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ApplicationViewModel : ViewModel() {
    private val _apps = mutableStateListOf<DockInfoModel>()
    val apps: List<DockInfoModel> get() = _apps

    var isLoading = mutableStateOf(false)
    private var hybridState = HybridConnectionModel()

    private var fetchAppsJob: Job? = null
    fun setSocket(hybridState: HybridConnectionModel){
        this.hybridState = hybridState
    }

    fun loadDockInfo() {
        _apps.clear()
        isLoading.value = true

        fetchAppsJob?.cancel()
        fetchAppsJob = viewModelScope.launch(Dispatchers.Main) {
            val handler = ApplicationHandler(hybridState)
            handler.fetchApplications(
                onListReady = { list ->
                    _apps.addAll(list)
                    isLoading.value = false // Tắt loading ngay khi có text
                },
                onIconReady = { id, icon ->
                    // Tìm app trong danh sách và cập nhật bitmap
                    val index = _apps.indexOfFirst { it.bundleID == id }
                    if (index != -1) {
                        val currentApp = _apps[index]
                        // Cần tạo object mới để Compose nhận diện thay đổi
                        _apps[index] = currentApp.copy(icon = icon)
                    }
                }
            )
        }
    }

    fun openApp(bundleID: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullPath = "launch $bundleID\n"
                val output = hybridState.output ?: return@launch
                output.write(fullPath.toByteArray())
                output.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                delay(3000)
                loadDockInfo()
            }
        }
    }

    fun closeApp(bundleID: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullPath = "closeapp $bundleID\n"
                val output = hybridState.output ?: return@launch
                output.write(fullPath.toByteArray())
                output.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                delay(10000)
                loadDockInfo()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fetchAppsJob?.cancel()
    }
}