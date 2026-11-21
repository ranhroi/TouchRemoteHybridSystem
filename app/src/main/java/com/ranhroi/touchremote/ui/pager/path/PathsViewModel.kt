package com.ranhroi.touchremote.ui.pager.path

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PathsViewModel @Inject constructor(
    private val dataStore: DataStoreManager
) : ViewModel() {

    // 1. Luôn lắng nghe danh sách từ DataStore
    val allPaths: StateFlow<List<PathModel>> = dataStore.getAllPaths()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkAndInitDefaultPaths()
    }

    /**
     * Kiểm tra nếu chưa có dữ liệu thì nạp danh sách mặc định
     */
    private fun checkAndInitDefaultPaths() {
        viewModelScope.launch {
            // Lấy danh sách hiện tại (chỉ lấy 1 lần đầu tiên)
            val currentPaths = dataStore.getAllPaths().first()

            if (currentPaths.isEmpty()) {
                dataStore.addPathList(PathRepository.listPathWeb)
                Log.d("PathsViewModel", "Đã khởi tạo danh sách mặc định thành công")
            }
        }
    }

    // ============= Path Management =============

    fun insertPath(path: PathModel) {
        viewModelScope.launch {
            try {
                dataStore.savePath(path)
            } catch (e: Exception) {
                Log.e("PathsViewModel", "Lỗi thêm path", e)
            }
        }
    }

    fun updatePath(path: PathModel) {
        viewModelScope.launch {
            try {
                dataStore.savePath(path)
            } catch (e: Exception) {
                Log.e("PathsViewModel", "Lỗi cập nhật path", e)
            }
        }
    }

    fun removePath(path: PathModel) {
        viewModelScope.launch {
            try {
                dataStore.deletePath(path.id)
            } catch (e: Exception) {
                Log.e("PathsViewModel", "Lỗi xóa path", e)
            }
        }
    }
}
