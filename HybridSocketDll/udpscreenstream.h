#pragma once

class UDPScreenStream {
public:
    UDPScreenStream(const std::string& destIp, int destPort);
    ~UDPScreenStream();

    bool open();
    void sendPackets(const std::vector<std::vector<uint8_t>>& packets, int pacingMicroseconds = 200);
    void sendPacket(const std::vector<uint8_t>& packet, int pacingMicroseconds = 0);

    // ✅ SỬA: TRẢ VỀ bool ĐỂ BIẾT GỬI THÀNH CÔNG
    bool sendRawBuffer(const uint8_t* data, size_t size, int pacingMicroseconds = 0);

    void close();
    bool isOpen() const { return sock_ >= 0; }

    // ✅ THÊM GETTER CHO SOCKET (DEBUG)
    int getSocket() const { return sock_; }

private:
    std::string destIp_;
    int destPort_;
    int sock_;
    static const size_t MAX_UDP_PAYLOAD = 1400;
};