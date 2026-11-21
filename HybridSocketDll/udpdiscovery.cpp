#include "pch.h"
#include "udpdiscovery.h"

UDPDiscovery::UDPDiscovery() {}

UDPDiscovery::~UDPDiscovery() { stop(); }

void UDPDiscovery::start(int portScan) {
    if (m_running.exchange(true)) return;
    m_multicastPort = portScan;
    m_worker = std::thread(&UDPDiscovery::broadcastThread, this);
}

void UDPDiscovery::stop() {
    if (!m_running.exchange(false)) return;
    if (m_socket != INVALID_SOCKET) {
        closesocket(m_socket);
        m_socket = INVALID_SOCKET;
    }
    if (m_worker.joinable()) m_worker.join();
}

void UDPDiscovery::broadcastThread() {
    m_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (m_socket == INVALID_SOCKET) return;

    // 1. Cấu hình TTL cho Multicast (số bước nhảy mạng)
    int ttl = 5;
    setsockopt(m_socket, IPPROTO_IP, IP_MULTICAST_TTL, (char*)&ttl, sizeof(ttl));

    // 2. Địa chỉ gửi tới (Multicast Group)
    sockaddr_in groupAddr{};
    groupAddr.sin_family = AF_INET;
    groupAddr.sin_port = htons(m_multicastPort);
    // groupAddr.sin_addr.s_addr = INADDR_BROADCAST; // Gửi đến 255.255.255.255
    inet_pton(AF_INET, MULTICAST_GROUP, &groupAddr.sin_addr); // Gửi đến 224.3.2.1

    LOG_INFO("[UDP] Multicast Discovery started on :%d", m_multicastPort);

    while (m_running) {
        // --- CHUẨN BỊ GÓI TIN THEO FORMAT ANDROID ---
        // Byte 0-1: "BC"
        // Byte 2-4: Độ dài (VD: 15 -> " 15")
        // Byte 5+: Tên máy

        std::string name = NetworkUtils::getHostInfo();
        if (name.length() > 250) name = name.substr(0, 250); // Giới hạn độ dài

        int len = (int)name.length();
        std::vector<char> packet;
        packet.push_back('B'); // data[0]
        packet.push_back('C'); // data[1]

        // Mã hóa độ dài thành 3 byte theo logic (data[i] and 207) của Android
        // Android dùng 'and 207' để ép từ ký tự ASCII '0'-'9' về số 0-9
        // Nếu là dấu cách (32), nó bỏ qua.

        // Hàng trăm
        int h = len / 100;
        packet.push_back(h > 0 ? (h + '0') : 32); // ' ' nếu là 0

        // Hàng chục
        int t = (len % 100) / 10;
        packet.push_back((h > 0 || t > 0) ? (t + '0') : 32);

        // Hàng đơn vị
        int u = len % 10;
        packet.push_back(u + '0');

        // Thêm tên máy tính (UTF-8)
        packet.insert(packet.end(), name.begin(), name.end());

        // Gửi gói tin
        int result = sendto(m_socket, packet.data(), (int)packet.size(), 0,
            (sockaddr*)&groupAddr, sizeof(groupAddr));

        if (result == SOCKET_ERROR) {
            int err = WSAGetLastError();
            if (err != WSAEINTR && m_running) {
                std::this_thread::sleep_for(std::chrono::seconds(5));
            }
        }

        // Phát mỗi 3 giây để máy khách cập nhật
        std::this_thread::sleep_for(std::chrono::seconds(3));
    }
}
