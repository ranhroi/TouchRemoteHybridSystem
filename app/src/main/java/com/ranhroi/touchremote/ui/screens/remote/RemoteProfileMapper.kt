package com.ranhroi.touchremote.ui.screens.remote

object RemoteProfileMapper {

    fun toRemoteModel(profile: RemoteProfile): RemoteModel {
        val fragments = profile.modelData.map { it.buttonfragment }
        val encoderType = RemoteCodeMap.getEncoderType(profile.id)

        // === PHÂN TÍCH DỰA TRÊN FRAGMENT ===
        val hasSwing = fragments.any {
            it.contains("_S_", ignoreCase = true) ||
                    it.startsWith("S_", ignoreCase = true)
        }

        val hasFanSpeedCommands = fragments.any {
            it.startsWith("FAN_", ignoreCase = true)
        }

        val hasModeCommands = fragments.any {
            it.startsWith("Mode_", ignoreCase = true)
        }

        // === XÁC ĐỊNH TÍNH NĂNG DỰA TRÊN MODEL ===
        // AC1: Có Swing, KHÔNG có AirClean/CoilDry/Powerful/Quiet
        // AC2: KHÔNG có Swing, CÓ AirClean/CoilDry/Powerful/Quiet
        // AC3: CÓ Swing, CÓ AirClean/CoilDry/Powerful/Quiet, CÓ FanSpeed/Mode commands
        // AC4-AC10: CÓ Swing, CÓ AirClean/CoilDry/Powerful/Quiet

        val isAC1 = encoderType == "AC1"
        val isAC2 = encoderType == "AC2"
        val isAC3 = encoderType == "AC3"
        val isAC4Plus = encoderType in listOf("AC4", "AC5", "AC6", "AC7", "AC8", "AC9", "AC10")

        // AirClean: Có trên AC2, AC3, AC4-AC10
        val hasAirClean = isAC2 || isAC3 || isAC4Plus

        // CoilDry: Có trên AC2, AC3, AC4-AC10
        val hasCoilDry = isAC2 || isAC3 || isAC4Plus

        // Powerful: Có trên AC2, AC3, AC4-AC10
        val hasPowerful = isAC2 || isAC3 || isAC4Plus

        // Quiet: Có trên AC2, AC3, AC4-AC10
        val hasQuiet = isAC2 || isAC3 || isAC4Plus

        // Timer: Có trên tất cả
        val hasTimer = true

        // Sleep: Có trên tất cả
        val hasSleep = true

        // Swing: Có trên AC1, AC3, AC4-AC10; KHÔNG có trên AC2
        val finalHasSwing = hasSwing || (isAC1 || isAC3 || isAC4Plus)

        // Device Family Byte
        val deviceFamilyByte = when (encoderType) {
            "AC1" -> 0x28  // AR-RAH1E
            "AC2" -> 0x14  // AR-RY12
            else -> 0x14
        }

        return RemoteModel(
            id = profile.id,
            name = profile.name,
            description = "Remote to code.json",
            encoderType = encoderType,
            deviceFamilyByte = deviceFamilyByte.toByte(),
            hasAirClean = hasAirClean,
            hasCoilDry = hasCoilDry,
            hasTimer = hasTimer,
            hasQuiet = hasQuiet,
            hasPowerful = hasPowerful,
            hasSwing = finalHasSwing,
            hasSleep = hasSleep,
            minTemp = 16,
            maxTemp = 30
        )
    }

    fun getAllRemoteModels(): List<RemoteModel> {
        return RemoteCodeMap.getAllProfiles().map { toRemoteModel(it) }
    }

    fun findRemoteModel(id: String): RemoteModel? {
        val profile = RemoteCodeMap.getProfile(id)
        return profile?.let { toRemoteModel(it) }
    }
}