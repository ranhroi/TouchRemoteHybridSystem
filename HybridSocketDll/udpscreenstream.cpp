#include "pch.h"
#include "UDPScreenStream.h"

UDPScreenStream::UDPScreenStream(const std::string& destIp, int destPort)
    : destIp_(destIp), destPort_(destPort), sock_(-1) {}

UDPScreenStream::~UDPScreenStream() {
    close();
}

bool UDPScreenStream::open() {
    WSADATA w;
    if (WSAStartup(MAKEWORD(2, 2), &w) != 0) {
        LOG_ERROR("[UDPScreenStream] WSAStartup failed");
        return false;
    }
    sock_ = (int)socket(AF_INET, SOCK_DGRAM, 0);
    if (sock_ < 0) {
        LOG_ERROR("[UDPScreenStream] Socket creation failed");
        WSACleanup();
        return false;
    }

    // ✅ SET NON-BLOCKING ĐỂ KHÔNG BỊ BLOCK
    u_long mode = 1;
    ioctlsocket((SOCKET)sock_, FIONBIO, &mode);

    // ✅ TĂNG BUFFER SIZE
    int bufferSize = 256 * 1024; // 256KB
    setsockopt((SOCKET)sock_, SOL_SOCKET, SO_SNDBUF,
        (const char*)&bufferSize, sizeof(bufferSize));

    return true;
}

void UDPScreenStream::sendPackets(const std::vector<std::vector<uint8_t>>& packets, int pacingMicroseconds) {
    if (sock_ < 0) return;
    sockaddr_in dst{};
    dst.sin_family = AF_INET;
    dst.sin_port = htons(destPort_);
    inet_pton(AF_INET, destIp_.c_str(), &dst.sin_addr);

    for (const auto& p : packets) {
        int sent = (int)sendto((SOCKET)sock_, (const char*)p.data(), (int)p.size(), 0, (sockaddr*)&dst, sizeof(dst));
        if (sent == SOCKET_ERROR) {
            int err = WSAGetLastError();
            if (err != WSAEWOULDBLOCK) {
                LOG_ERROR("[UDPScreenStream] sendto failed: %d", err);
            }
        }
        if (pacingMicroseconds > 0) {
            std::this_thread::sleep_for(std::chrono::microseconds(pacingMicroseconds));
        }
    }
}

void UDPScreenStream::sendPacket(const std::vector<uint8_t>& packet, int pacingMicroseconds) {
    if (sock_ < 0) return;
    sockaddr_in dst{};
    dst.sin_family = AF_INET;
    dst.sin_port = htons(destPort_);
    inet_pton(AF_INET, destIp_.c_str(), &dst.sin_addr);

    int sent = (int)sendto((SOCKET)sock_, (const char*)packet.data(), (int)packet.size(), 0, (sockaddr*)&dst, sizeof(dst));
    if (sent == SOCKET_ERROR) {
        int err = WSAGetLastError();
        if (err != WSAEWOULDBLOCK) {
            LOG_ERROR("[UDPScreenStream] sendto failed: %d", err);
        }
    }
    if (pacingMicroseconds > 0) {
        std::this_thread::sleep_for(std::chrono::microseconds(pacingMicroseconds));
    }
}

// ===== SỬA: TRẢ VỀ bool =====
bool UDPScreenStream::sendRawBuffer(const uint8_t* data, size_t size, int pacingMicroseconds) {
    if (sock_ < 0 || data == nullptr || size == 0) {
        return false;
    }

    // ✅ KIỂM TRA KÍCH THƯỚC > MTU
    if (size > MAX_UDP_PAYLOAD) {
        LOG_DEBUG("[UDPScreenStream] Warning: Packet size %d > MTU %d", (int)size, MAX_UDP_PAYLOAD);
        // Vẫn gửi nhưng có thể bị fragmentation
    }

    sockaddr_in dst{};
    dst.sin_family = AF_INET;
    dst.sin_port = htons(destPort_);
    inet_pton(AF_INET, destIp_.c_str(), &dst.sin_addr);

    // ✅ GỬI VÀ KIỂM TRA KẾT QUẢ
    int sent = (int)sendto((SOCKET)sock_,
        (const char*)data,
        (int)size,
        0,
        (sockaddr*)&dst,
        sizeof(dst));

    if (sent == SOCKET_ERROR) {
        int err = WSAGetLastError();
        if (err != WSAEWOULDBLOCK) {
            LOG_ERROR("[UDPScreenStream] sendRawBuffer failed: %d, size=%d", err, (int)size);
        }
        return false;
    }

    if (sent != (int)size) {
        LOG_ERROR("[UDPScreenStream] Partial send: %d/%d", sent, (int)size);
        return false;
    }

    if (pacingMicroseconds > 0) {
        std::this_thread::sleep_for(std::chrono::microseconds(pacingMicroseconds));
    }

    return true;
}

void UDPScreenStream::close() {
    if (sock_ >= 0) {
        closesocket((SOCKET)sock_);
        WSACleanup();
        sock_ = -1;
    }
}