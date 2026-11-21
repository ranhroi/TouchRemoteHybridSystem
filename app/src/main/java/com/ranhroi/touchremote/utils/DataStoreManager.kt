package com.ranhroi.touchremote.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.models.QuickKeysModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.pager.files.DownloadProgressModel
import com.ranhroi.touchremote.ui.pager.path.PathModel
import com.ranhroi.touchremote.utils.ConstantsUtils.DEVICE_LIST_KEY
import com.ranhroi.touchremote.utils.ConstantsUtils.DOWNLOAD_PROGRESS_KEY
import com.ranhroi.touchremote.utils.ConstantsUtils.PATH_LIST_KEY
import com.ranhroi.touchremote.utils.ConstantsUtils.QUICK_KEY_LIST_KEY
import com.ranhroi.touchremote.utils.ConstantsUtils.ShortKey_LIST_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "config")


@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    private val gson = Gson()

    val data: Flow<Preferences> =
        dataStore.data.catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    // ---------- HÀM XỬ LÝ GENERIC ----------
    private fun <T> getList(key: Preferences.Key<String>, clazz: Class<T>): Flow<List<T>> {
        return dataStore.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }.map { pref ->
            val json = pref[key] ?: ""
            if (json.isEmpty()) {
                emptyList<T>()
            } else {
                val type = TypeToken.getParameterized(List::class.java, clazz).type
                gson.fromJson(json, type) ?: emptyList()
            }
        }
    }

    private suspend fun <T> saveOrUpdateItem(
        key: Preferences.Key<String>,
        item: T,
        clazz: Class<T>,
        getId: (T) -> Int,
        updateId: (T, Int) -> T
    ) {
        val json = dataStore.data.first()[key] ?: ""
        val list: MutableList<T> = if (json.isEmpty()) {
            mutableListOf()
        } else {
            val type = TypeToken.getParameterized(MutableList::class.java, clazz).type
            gson.fromJson(json, type)
        }

        val itemId = getId(item)

        // ✅ Tìm index dựa trên ID (chỉ khi ID > 0)
        val index = if (itemId > 0) {
            list.indexOfFirst { getId(it) == itemId }
        } else {
            -1
        }

        if (index != -1) {
            // ✅ Cập nhật: giữ nguyên ID cũ, không tạo mới
            list[index] = item
        } else {
            // ✅ Thêm mới: tạo ID mới dựa trên maxId + 1
            val maxId = list.maxOfOrNull { getId(it) } ?: 0
            val newItem = updateId(item, maxId + 1)
            list.add(newItem)
        }

        put(key, gson.toJson(list))
    }

    suspend fun deleteItem(key: Preferences.Key<String>, id: Int) {
        val json = dataStore.data.first()[key] ?: ""
        val type = object : TypeToken<MutableList<MutableMap<String, Any>>>() {}.type
        val rawList: MutableList<MutableMap<String, Any>> =
            gson.fromJson(json, type) ?: mutableListOf()
        if (rawList.removeAll { (it["id"] as? Double)?.toInt() == id }) {
            put(key, gson.toJson(rawList))
        }
    }


    // ==================== PATH ====================
    fun getAllPaths(): Flow<List<PathModel>> = getList(PATH_LIST_KEY, PathModel::class.java)
    suspend fun addPathList(newPaths: List<PathModel>) {
        val currentList = getAllPaths().first().toMutableList()
        var nextId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        newPaths.forEach { pathItem ->
            if (currentList.none { it.path == pathItem.path }) {
                currentList.add(pathItem.copy(id = nextId++))
            }
        }
        put(PATH_LIST_KEY, gson.toJson(currentList))
    }

    suspend fun savePath(item: PathModel) = saveOrUpdateItem(
        PATH_LIST_KEY,
        item,
        PathModel::class.java,
        { it.id },
        { obj, newId -> obj.copy(id = newId) })

    suspend fun deletePath(id: Int) = deleteItem(PATH_LIST_KEY, id)


    // ==================== DEVICE ====================
    fun getAllDevices(): Flow<List<DeviceServerModel>> =
        getList(DEVICE_LIST_KEY, DeviceServerModel::class.java)

    suspend fun saveDevice(item: DeviceServerModel) =
        saveOrUpdateItem(
            DEVICE_LIST_KEY,
            item,
            DeviceServerModel::class.java,
            { it.id },
            { obj, newId -> obj.copy(id = newId) })


    suspend fun setSelectedDevice(deviceId: Int) {
        val currentList = getAllDevices().first()
        val updatedList =
            currentList.map { device -> device.copy(selected = (device.id == deviceId)) }
        put(DEVICE_LIST_KEY, gson.toJson(updatedList))
    }


    fun getSelectedDevice(): Flow<DeviceServerModel?> {
        return getAllDevices().map { list -> list.find { it.selected } }
    }

    // ==================== ĐỒNG BỘ OS INDEX ====================
    /**
     * Cập nhật `os` của TẤT CẢ device trong DEVICE_LIST.
     * Gọi khi user đổi OS trong Settings.
     */
    suspend fun updateDeviceOS(deviceId: Int, newOs: String) {
        val currentList = getAllDevices().first().toMutableList()
        val index = currentList.indexOfFirst { it.id == deviceId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(os = newOs)
            put(DEVICE_LIST_KEY, gson.toJson(currentList))
        }
    }

    suspend fun deleteDevice(id: Int) = deleteItem(DEVICE_LIST_KEY, id)



    // ==================== QUICK KEY ====================
    fun getAllQuickKeys(): Flow<List<QuickKeysModel>> =
        getList(QUICK_KEY_LIST_KEY, QuickKeysModel::class.java)

    suspend fun addQuickKeysList(newQuickKeys: List<QuickKeysModel>) {
        val currentList = getAllQuickKeys().first().toMutableList()
        var nextId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        newQuickKeys.forEach { item ->
            if (currentList.none { it.name == item.name }) {
                currentList.add(item.copy(id = nextId++))
            }
        }
        put(QUICK_KEY_LIST_KEY, gson.toJson(currentList))
    }

    suspend fun saveQuickKeys(item: QuickKeysModel) = saveOrUpdateItem(
        QUICK_KEY_LIST_KEY,
        item,
        QuickKeysModel::class.java,
        { it.id },
        { obj, newId -> obj.copy(id = newId) })

    suspend fun deleteQuickKeys(id: Int) = deleteItem(QUICK_KEY_LIST_KEY, id)



    // ==================== ShortKey ====================
    fun getAllShortKeys(): Flow<List<ShortKeyModel>> =
        getList(ShortKey_LIST_KEY, ShortKeyModel::class.java)

    suspend fun addShortKeyList(newShortKeys: List<ShortKeyModel>) {
        val currentList = getAllShortKeys().first().toMutableList()
        var nextId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        newShortKeys.forEach { item ->
            if (currentList.none { it.type == item.type && it.name == item.name }) {
                currentList.add(item.copy(id = nextId++))
            }
        }
        put(ShortKey_LIST_KEY, gson.toJson(currentList))
    }

    suspend fun saveShortKey(item: ShortKeyModel) = saveOrUpdateItem(
        ShortKey_LIST_KEY,
        item,
        ShortKeyModel::class.java,
        { it.id },
        { obj, newId -> obj.copy(id = newId) })

    suspend fun deleteShortKey(id: Int) = deleteItem(ShortKey_LIST_KEY, id)


    // ==================== DOWNLOAD PROGRESS ====================
    fun getAllDownloadProgress(): Flow<List<DownloadProgressModel>> =
        getList(DOWNLOAD_PROGRESS_KEY, DownloadProgressModel::class.java)

    suspend fun addDownloadProgressList(newProgress: List<DownloadProgressModel>) {
        val currentList = getAllDownloadProgress().first().toMutableList()
        var nextId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        newProgress.forEach { item ->
            if (currentList.none { it.fileName == item.fileName }) {
                currentList.add(item.copy(id = nextId++))
            }
        }
        put(DOWNLOAD_PROGRESS_KEY, gson.toJson(currentList))
    }

    suspend fun saveDownloadProgress(item: DownloadProgressModel) =
        saveOrUpdateItem(
            DOWNLOAD_PROGRESS_KEY,
            item,
            DownloadProgressModel::class.java,
            { it.id },
            { obj, newId -> obj.copy(id = newId) })

    suspend fun deleteDownloadProgress(id: Int) = deleteItem(DOWNLOAD_PROGRESS_KEY, id)
    suspend fun deleteDownloadProgressByFileName(fileName: String) {
        val currentList = getAllDownloadProgress().first().toMutableList()
        currentList.removeAll { it.fileName == fileName }
        put(DOWNLOAD_PROGRESS_KEY, gson.toJson(currentList))
    }

    suspend fun clearAllDownloadProgress() {
        put(DOWNLOAD_PROGRESS_KEY, gson.toJson(emptyList<DownloadProgressModel>()))
    }

    suspend fun getDownloadProgressByFileName(fileName: String): DownloadProgressModel? {
        return getAllDownloadProgress().first().find { it.fileName == fileName }
    }

    // ==================== READ / WRITE CƠ BẢN ====================
    fun <T> get(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return dataStore.data.map { pref -> pref[key] ?: defaultValue }
    }

    suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { pref -> pref[key] = value }
    }

    suspend fun <T> remove(key: Preferences.Key<T>) {
        dataStore.edit { it.remove(key) }
    }


    // ==================== JSON OPERATIONS ====================

    suspend fun <T> putJson(key: String, value: T) {
        val json = gson.toJson(value)
        dataStore.edit { pref ->
            pref[stringPreferencesKey(key)] = json
        }
    }

    suspend fun <T> getJson(key: String, clazz: Class<T>): T? {
        val json = dataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return try {
            gson.fromJson(json, clazz)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun <T> getJson(key: String, typeToken: TypeToken<T>): T? {
        val json = dataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return try {
            gson.fromJson(json, typeToken.type)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun remove(key: String) {
        dataStore.edit { pref ->
            pref.remove(stringPreferencesKey(key))
        }
    }
}
