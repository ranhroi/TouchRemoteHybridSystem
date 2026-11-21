package com.ranhroi.touchremote.ui.pager.mirror

class FrameAssembler {
    private var currentFrameId = -1
    private val packetsMap = mutableMapOf<Int, ByteArray>()
    private var expectedPackets = 0

    fun addPacket(header: UdpHeaderModel, payload: ByteArray): ByteArray? {
        // Nếu có khung hình mới hơn hoàn toàn, dọn dẹp hàng đợi cũ
        if (header.frameId > currentFrameId) {
            currentFrameId = header.frameId
            packetsMap.clear()
            expectedPackets = header.packetCount
        }

        // Chỉ xử lý nếu gói tin thuộc khung hình hiện tại
        if (header.frameId == currentFrameId) {
            packetsMap[header.packetIdx] = payload

            // Khi đã gom đủ toàn bộ số lượng gói tin của khung hình
            if (packetsMap.size == expectedPackets) {
                
                // Tính toán tổng dung lượng cần cấp phát (Mỗi mảnh NAL sẽ được bù thêm 4 bytes Start Code)
                var totalSize = 0
                for (i in 0 until expectedPackets) {
                    val chunk = packetsMap[i] ?: return null
                    totalSize += 4 + chunk.size // 4 bytes cho 0x00000001
                }

                val completeFrame = ByteArray(totalSize)
                var currentOffset = 0

                for (i in 0 until expectedPackets) {
                    val chunk = packetsMap[i] ?: return null
                    
                    // Chèn Start Code 0x00000001 chuẩn quốc tế vào trước mỗi gói NAL nhỏ
                    completeFrame[currentOffset] = 0.toByte()
                    completeFrame[currentOffset + 1] = 0.toByte()
                    completeFrame[currentOffset + 2] = 0.toByte()
                    completeFrame[currentOffset + 3] = 1.toByte()
                    currentOffset += 4

                    // Copy dữ liệu NAL vào sau Start Code
                    System.arraycopy(chunk, 0, completeFrame, currentOffset, chunk.size)
                    currentOffset += chunk.size
                }

                packetsMap.clear()
                return completeFrame // Trả về khung hình hoàn chỉnh có ranh giới NAL cực sạch
            }
        }
        return null
    }

    fun clear() {
        packetsMap.clear()
        currentFrameId = -1
        expectedPackets = 0
    }
}