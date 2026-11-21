package com.ranhroi.touchremote.ui.screens.remote

import android.content.Context
import org.json.JSONArray

object RemoteCodeMap {

    private var profiles: List<RemoteProfile> = emptyList()
    private var commandMap: Map<String, RemoteCommand> = emptyMap()

    fun initialize(context: Context) {
        if (profiles.isNotEmpty()) return
        try {
            val json = context.assets.open("code.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(json)
            val profileList = mutableListOf<RemoteProfile>()
            val cmdMap = mutableMapOf<String, RemoteCommand>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val modelName = obj.getString("modelname")
                val modelData = obj.getJSONArray("modeldata")

                val codes = mutableListOf<RemoteCode>()
                var fullCount = 0
                var swingCount = 0
                var specialCount = 0

                for (j in 0 until modelData.length()) {
                    val codeObj = modelData.getJSONObject(j)
                    val fragment = codeObj.getString("buttonfragment")
                    val frequency = codeObj.getString("frequency")
                    val mainframe = codeObj.getString("mainframe")
                    val pattern = parseMainframe(mainframe)

                    codes.add(RemoteCode(frequency, fragment, mainframe))
                    cmdMap["${modelName}_$fragment"] = RemoteCommand(
                        profileId = modelName,
                        fragment = fragment,
                        frequency = frequency.toIntOrNull() ?: 38000,
                        pattern = pattern,
                        rawMainframe = mainframe
                    )

                    // Thống kê
                    when {
                        fragment.startsWith("FAN_") || fragment.startsWith("Mode_") -> specialCount++
                        fragment.contains("_S_") -> swingCount++
                        fragment.contains("_F_") -> fullCount++
                    }
                }

                profileList.add(RemoteProfile(modelName, modelName, codes))
            }

            profiles = profileList
            commandMap = cmdMap

        } catch (e: Exception) {
            e.printStackTrace()
            createFallbackProfiles()
        }
    }

    private fun createFallbackProfiles() {
        val fallbackCode = RemoteCode("38000", "PowerOn", "3250, 1586, 416, 390")
        val profile = RemoteProfile("default", "Default Remote", listOf(fallbackCode))
        profiles = listOf(profile)
        commandMap = mapOf("default_PowerOn" to RemoteCommand(
            profileId = "default",
            fragment = "PowerOn",
            frequency = 38000,
            pattern = intArrayOf(3250, 1586, 416, 390),
            rawMainframe = "3250, 1586, 416, 390"
        ))
    }

    private fun parseMainframe(mainframe: String): IntArray {
        return try {
            mainframe.split(",").map { it.trim().toInt() }.toIntArray()
        } catch (e: Exception) {
            intArrayOf(3250, 1586, 416, 390)
        }
    }

    fun getAllProfiles(): List<RemoteProfile> = profiles
    fun getProfile(id: String): RemoteProfile? = profiles.find { it.id == id }
    fun getCommand(profileId: String, fragment: String): RemoteCommand? =
        commandMap["${profileId}_$fragment"]

    /**
     * Tìm lệnh phù hợp với state hiện tại
     * Thứ tự ưu tiên:
     * 1. PowerOn/PowerOff
     * 2. FAN_AUTO, FAN_HIGH, FAN_LOW (nếu có)
     * 3. Mode_Cool, Mode_Heat (nếu có)
     * 4. Full command: {temp}_F_{fan}_{mode}
     * 5. Swing command: {temp}_S_{fan}_{mode} (nếu Swing ON)
     * 6. Fallback: matchesFragment()
     */
    fun findMatchingCommand(profileId: String, state: RemoteState): RemoteCommand? {
        val profile = getProfile(profileId) ?: return null

        // ===== 1. POWER =====
        getCommand(profileId, "PowerOn")?.let { if (state.isOn) return it }
        getCommand(profileId, "PowerOff")?.let { if (!state.isOn) return it }

        // ===== 2. FAN SPEED COMMANDS (AC3) =====
        val fanFragment = when (state.fanSpeed) {
            "AUTO" -> "FAN_AUTO"
            "HIGH" -> "FAN_HIGH"
            "MED" -> "FAN_MED"
            "LOW" -> "FAN_LOW"
            "QUIET" -> "FAN_QUIET"
            else -> null
        }
        fanFragment?.let {
            getCommand(profileId, it)?.let { return it }
        }

        // ===== 3. MODE COMMANDS (AC3) =====
        val modeFragment = when (state.mode) {
            "COOL" -> "Mode_Cool"
            "HEAT" -> "Mode_Heat"
            "DRY" -> "Mode_Dry"
            "FAN" -> "Mode_Fan"
            "AUTO" -> "Mode_Auto"
            else -> null
        }
        modeFragment?.let {
            getCommand(profileId, it)?.let { return it }
        }

        // ===== 4. FULL COMMAND =====
        val fanCode = when (state.fanSpeed) {
            "HIGH" -> "1"
            "MED" -> "2"
            "LOW" -> "3"
            "AUTO" -> "A"
            "QUIET" -> "Q"
            else -> "A"
        }

        val modeCode = when (state.mode) {
            "COOL" -> "C"
            "HEAT" -> "H"
            "DRY" -> "D"
            "FAN" -> "F"
            "AUTO" -> "A"
            else -> "C"
        }

        // Thử {temp}_F_{fan}_{mode}
        val fullFragment = "${state.temp}_F_${fanCode}_${modeCode}"
        getCommand(profileId, fullFragment)?.let { return it }

        // Thử {temp}_F_{fan} (không mode)
        val simpleFragment = "${state.temp}_F_${fanCode}"
        getCommand(profileId, simpleFragment)?.let { return it }

        // ===== 5. SWING COMMAND =====
        if (state.isSwing) {
            val swingFragment = "${state.temp}_S_${fanCode}_${modeCode}"
            getCommand(profileId, swingFragment)?.let { return it }

            val swingSimple = "${state.temp}_S_${fanCode}"
            getCommand(profileId, swingSimple)?.let { return it }
        }

        // ===== 6. FALLBACK =====
        for (code in profile.modelData) {
            if (matchesFragment(code.buttonfragment, state)) {
                return getCommand(profileId, code.buttonfragment)
            }
        }

        return null
    }

    /**
     * Kiểm tra fragment có khớp với state không
     * Hỗ trợ các định dạng:
     * - {temp}_F_{fan}_{mode}  e.g., 25_F_3_H
     * - {temp}_S_{fan}_{mode}  e.g., 24_S_A_C
     * - {temp}_F_{fan}         e.g., 25_F_3
     * - {temp}_S_{fan}         e.g., 24_S_A
     */
    private fun matchesFragment(fragment: String, state: RemoteState): Boolean {
        // Bỏ qua các lệnh đặc biệt
        when (fragment) {
            "PowerOn", "PowerOff", "Delay" -> return false
            "FAN_AUTO", "FAN_HIGH", "FAN_MED", "FAN_LOW", "FAN_QUIET" -> return false
            "Mode_Cool", "Mode_Heat", "Mode_Dry", "Mode_Fan", "Mode_Auto" -> return false
        }

        val parts = fragment.split("_")
        if (parts.size < 2) return false

        // ===== NHIỆT ĐỘ =====
        val tempPart = parts[0].toIntOrNull()
        if (tempPart != null && tempPart != state.temp) return false

        // ===== LOẠI LỆNH =====
        val type = parts.getOrNull(1) ?: return false
        if (type == "S" && !state.isSwing) return false

        // ===== FAN SPEED =====
        val fanPart = parts.getOrNull(2) ?: ""
        val expectedFan = when (state.fanSpeed) {
            "HIGH" -> "1"
            "MED" -> "2"
            "LOW" -> "3"
            "AUTO" -> "A"
            "QUIET" -> "Q"
            else -> "A"
        }

        if (fanPart == "C" || fanPart == "H") {
            // Mode indicator, bỏ qua fan check
        } else if (fanPart.isNotEmpty() && fanPart != expectedFan) {
            return false
        }

        // ===== MODE =====
        val modePart = parts.getOrNull(3) ?: ""
        val expectedMode = when (state.mode) {
            "COOL" -> "C"
            "HEAT" -> "H"
            "DRY" -> "D"
            "FAN" -> "F"
            "AUTO" -> "A"
            else -> "C"
        }

        if (modePart.isNotEmpty() && modePart != expectedMode) return false
        if (fanPart == "C" && state.mode != "COOL") return false
        if (fanPart == "H" && state.mode != "HEAT") return false

        return true
    }

    fun getEncoderType(profileId: String): String {
        return when {
            profileId.contains("AC1", ignoreCase = true) -> "AC1"
            profileId.contains("AC2", ignoreCase = true) -> "AC2"
            profileId.contains("AC3", ignoreCase = true) -> "AC3"
            profileId.contains("AC4", ignoreCase = true) -> "AC4"
            profileId.contains("AC5", ignoreCase = true) -> "AC5"
            profileId.contains("AC6", ignoreCase = true) -> "AC6"
            profileId.contains("AC7", ignoreCase = true) -> "AC7"
            profileId.contains("AC8", ignoreCase = true) -> "AC8"
            profileId.contains("AC9", ignoreCase = true) -> "AC9"
            profileId.contains("AC10", ignoreCase = true) -> "AC10"
            else -> "DEFAULT"
        }
    }
}

data class RemoteCommand(
    val profileId: String,
    val fragment: String,
    val frequency: Int,
    val pattern: IntArray,
    val rawMainframe: String
)

data class RemoteProfile(
    val id: String,
    val name: String,
    val modelData: List<RemoteCode>
)

data class RemoteCode(
    val frequency: String,
    val buttonfragment: String,
    val mainframe: String
)