//  VERSION 3.1 (FIX VANE DIRECTION)
package com.ranhroi.touchremote.ui.screens.remote

import kotlin.experimental.or

/**
 * Fujitsu IR Encoder
 * Hỗ trợ đầy đủ các model: AR-RY12, AR-RAH1E, ASTA18LCC, và các model từ code.json
 *
 * Tham khảo từ:
 * - https://github.com/ToniA/arduino-heatpump-ir
 * - https://github.com/r45635/HVAC-IR-Control
 */
object FujitsuIrEncoder {

    // ==================== CONSTANTS ====================
    private const val HEADER_PULSE = 3300
    private const val HEADER_SPACE = 1600
    private const val BIT_PULSE = 400
    private const val BIT_ONE_SPACE = 1200
    private const val BIT_ZERO_SPACE = 400
    private const val STOP_PULSE = 400
    private const val STOP_SPACE = 8000
    private const val FREQUENCY = 38000

    // ==================== MAIN FUNCTION ====================

    /**
     * Tạo lệnh IR đầy đủ cho Fujitsu AC
     * @param remoteModel Model remote đang dùng
     * @param isOn Trạng thái bật/tắt
     * @param temp Nhiệt độ (16-30°C)
     * @param mode Chế độ: COOL, HEAT, DRY, FAN, AUTO
     * @param fan Tốc độ quạt: AUTO, HIGH, MED, LOW, QUIET
     * @param swingActive Bật/tắt đảo gió
     * @param vaneDirection Vị trí cánh gió (0=AUTO, 1-5=Manual)
     * @param isCoilDry Bật/tắt Coil Dry
     * @param isAirClean Bật/tắt Air Clean
     * @param isSleep Bật/tắt Sleep
     * @param timerMode Chế độ hẹn giờ: NONE, ON, OFF
     * @param timerHour Số giờ hẹn (0-12)
     * @param isQuiet Bật/tắt chế độ yên tĩnh (nếu có)
     * @param isPowerful Bật/tắt chế độ mạnh mẽ (nếu có)
     * @return IntArray - Pattern IR để phát
     */
    fun generateFujitsuCommand(
        remoteModel: RemoteModel,
        isOn: Boolean,
        temp: Int,
        mode: String,
        fan: String,
        swingActive: Boolean,
        isVerticalStep: Boolean,  // Thêm
        isCoilDry: Boolean,
        isAirClean: Boolean,
        isSleep: Boolean,
        timerMode: String = "NONE",
        timerHour: Int = 0,
        isQuiet: Boolean = false,
        isPowerful: Boolean = false
    ): IntArray {
        val bytes = ByteArray(16)

        // ======== 1. HEADER BYTES (0-7) ========
        bytes[0] = remoteModel.deviceFamilyByte
        bytes[1] = 0x63.toByte()
        bytes[2] = 0x00.toByte()
        bytes[3] = 0x10.toByte()
        bytes[4] = 0x10.toByte()
        bytes[5] = 0xFE.toByte()
        bytes[6] = 0x09.toByte()
        bytes[7] = 0x30.toByte()

        // ======== 2. POWER (BYTE 8) ========
        bytes[8] = if (isVerticalStep) {
            0x04.toByte()
        } else {
            if (!isOn) 0x02.toByte() else 0x01.toByte()
        }

        // ======== 3. TEMPERATURE (BYTE 9) ========
        val clampedTemp = temp.coerceIn(remoteModel.minTemp, remoteModel.maxTemp)
        bytes[9] = (clampedTemp - 16).toByte()

        // ======== 4. MODE (BYTE 10) ========
        bytes[10] = encodeMode(mode, remoteModel)

        // ======== 5. FAN SPEED (BYTE 11) ========
        bytes[11] = encodeFanSpeed(fan, isQuiet, isPowerful, remoteModel)

        // ======== 6. SPECIAL FEATURES (BYTE 12) - CÓ VANE DIRECTION ========
        bytes[12] = encodeSpecialByte(
            remoteModel = remoteModel,
            swingActive = swingActive,
            isVerticalStep = isVerticalStep,
            isCoilDry = isCoilDry,
            isAirClean = isAirClean
        )

        // ======== 7. SLEEP & TIMER (BYTE 13-14) ========
        bytes[13] = encodeSleepAndTimer(
            remoteModel = remoteModel,
            isSleep = isSleep,
            timerMode = timerMode,
            timerHour = timerHour
        )
        bytes[14] = encodeExtendedTimer(
            remoteModel = remoteModel,
            timerMode = timerMode,
            timerHour = timerHour
        )

        // ======== 8. CHECKSUM (BYTE 15) ========
        bytes[15] = calculateChecksum(bytes)

        // ======== 9. GENERATE IR PATTERN ========
        return buildIrPattern(bytes)
    }

    // ==================== ENCODING FUNCTIONS ====================

    private fun encodeMode(mode: String, remoteModel: RemoteModel): Byte {
        return when (mode) {
            "AUTO" -> 0x00.toByte()
            "COOL" -> 0x01.toByte()
            "DRY"  -> 0x02.toByte()
            "FAN"  -> 0x03.toByte()
            "HEAT" -> 0x04.toByte()
            else   -> {
                when (remoteModel.id) {
                    "AR-RAH1E" -> 0x01.toByte()
                    "ASTA18LCC" -> 0x00.toByte()
                    else -> 0x01.toByte()
                }
            }
        }
    }

    private fun encodeFanSpeed(fan: String, isQuiet: Boolean, isPowerful: Boolean, remoteModel: RemoteModel): Byte {
        return when {
            isPowerful -> {
                when (remoteModel.encoderType) {
                    "RY" -> 0x05.toByte()
                    "RAH" -> 0x06.toByte()
                    else -> 0x05.toByte()
                }
            }
            isQuiet -> 0x04.toByte()
            else -> when (fan) {
                "AUTO" -> 0x00.toByte()
                "HIGH" -> 0x01.toByte()
                "MED" -> 0x02.toByte()
                "LOW" -> 0x03.toByte()
                "QUIET" -> 0x04.toByte()
                else -> 0x00.toByte()
            }
        }
    }

    private fun encodeSpecialByte(
        remoteModel: RemoteModel,
        swingActive: Boolean,
        isVerticalStep: Boolean, // Biến xung bước thay thế hoàn toàn cho vaneDirection
        isCoilDry: Boolean,
        isAirClean: Boolean
    ): Byte {
        var specialByte = 0x00

        // ====== MODEL-SPECIFIC MAPPING ======
        when (remoteModel.id) {
            "AR-RY12" -> {
                if (isVerticalStep) {
                    specialByte = 0x41 // Mã xung bước dọc cho dòng RY
                } else {
                    if (swingActive) specialByte = specialByte or 0x08
                    if (isCoilDry) specialByte = specialByte or 0x02
                    if (isAirClean) specialByte = specialByte or 0x01
                }
            }

            "AR-RAH1E" -> {
                if (isVerticalStep) {
                    specialByte = 0x42 // Mã xung bước ngang/dọc tùy dòng RAH
                } else {
                    if (swingActive) specialByte = specialByte or 0x10
                    if (isCoilDry) specialByte = specialByte or 0x04
                }
            }

            "ASTA18LCC" -> {
                if (isVerticalStep) {
                    specialByte = 0x41
                } else {
                    if (swingActive) specialByte = specialByte or 0x08
                    if (isAirClean) specialByte = specialByte or 0x01
                }
            }

            else -> {
                // Model từ JSON: dựa trên encoderType của bạn
                when {
                    remoteModel.id.contains("RY", ignoreCase = true) -> {
                        if (isVerticalStep) {
                            specialByte = 0x41
                        } else {
                            if (swingActive) specialByte = specialByte or 0x08
                            if (isCoilDry) specialByte = specialByte or 0x02
                            if (isAirClean) specialByte = specialByte or 0x01
                        }
                    }
                    remoteModel.id.contains("RAH", ignoreCase = true) -> {
                        if (isVerticalStep) {
                            specialByte = 0x42
                        } else {
                            if (swingActive) specialByte = specialByte or 0x10
                            if (isCoilDry) specialByte = specialByte or 0x04
                        }
                    }
                    remoteModel.id.contains("ASTA", ignoreCase = true) -> {
                        if (isVerticalStep) {
                            specialByte = 0x41
                        } else {
                            if (swingActive) specialByte = specialByte or 0x08
                            if (isAirClean) specialByte = specialByte or 0x01
                        }
                    }
                    else -> {
                        // Default mapping an toàn cho các dòng lạ từ JSON
                        if (isVerticalStep) {
                            specialByte = 0x41
                        } else {
                            if (swingActive) specialByte = specialByte or 0x08
                            if (isCoilDry && remoteModel.hasCoilDry) specialByte = specialByte or 0x02
                            if (isAirClean && remoteModel.hasAirClean) specialByte = specialByte or 0x01
                        }
                    }
                }
            }
        }

        return specialByte.toByte()
    }


    private fun encodeSleepAndTimer(
        remoteModel: RemoteModel,
        isSleep: Boolean,
        timerMode: String,
        timerHour: Int
    ): Byte {
        var sleepByte = 0x00

        if (isSleep) {
            sleepByte = sleepByte or 0x01
        }

        if (timerMode != "NONE" && timerHour in 1..12) {
            when (remoteModel.id) {
                "AR-RY12" -> {
                    sleepByte = sleepByte or ((timerHour and 0x0F) shl 2)
                    if (timerMode == "ON") {
                        sleepByte = sleepByte or 0x40
                    }
                    sleepByte = sleepByte or 0x80
                }
                "AR-RAH1E" -> {
                    // Timer only in byte 14, sleep only in byte 13
                }
                else -> {
                    sleepByte = sleepByte or ((timerHour and 0x0F) shl 2)
                    if (timerMode == "ON") {
                        sleepByte = sleepByte or 0x40
                    }
                    sleepByte = sleepByte or 0x80
                }
            }
        }

        return sleepByte.toByte()
    }

    private fun encodeExtendedTimer(
        remoteModel: RemoteModel,
        timerMode: String,
        timerHour: Int
    ): Byte {
        var timerByte = 0x00.toByte()

        if (timerMode != "NONE" && timerHour in 1..12) {
            when (remoteModel.id) {
                "AR-RAH1E" -> {
                    timerByte = timerHour.toByte()
                    if (timerMode == "ON") {
                        timerByte = timerByte or 0x10.toByte()
                    }
                    timerByte = timerByte or 0x80.toByte()
                }
                "AR-RY12" -> {
                    // Timer handled in byte 13
                }
                else -> {
                    if (remoteModel.id.contains("RAH", ignoreCase = true)) {
                        timerByte = timerHour.toByte()
                        if (timerMode == "ON") {
                            timerByte = timerByte or 0x10.toByte()
                        }
                        timerByte = timerByte or 0x80.toByte()
                    }
                }
            }
        }

        return timerByte.toByte()
    }

    private fun calculateChecksum(bytes: ByteArray): Byte {
        var checksum = 0
        for (i in 0..14) {
            checksum += bytes[i].toInt() and 0xFF
        }
        return ((256 - (checksum % 256)) % 256).toByte()
    }

    // ==================== IR PATTERN GENERATION ====================

    private fun buildIrPattern(bytes: ByteArray): IntArray {
        val list = ArrayList<Int>()

        list.add(HEADER_PULSE)
        list.add(HEADER_SPACE)

        for (b in bytes) {
            var value = b.toInt() and 0xFF
            for (i in 0..7) {
                list.add(BIT_PULSE)
                if ((value and 1) == 1) {
                    list.add(BIT_ONE_SPACE)
                } else {
                    list.add(BIT_ZERO_SPACE)
                }
                value = value shr 1
            }
        }

        list.add(STOP_PULSE)
        list.add(STOP_SPACE)

        return list.toIntArray()
    }
}