#include "pch.h"
#include "socketmanager.h"
#include "sessioncontext.h"
#include "sessionhandler.h"

SocketManager::SocketManager(const RemoteServerConfig* config)
    : m_BtUuid(config->bluetoothUuid)
    , m_tcpPort(config->tcpPort)
    , m_udpPort(config->udpPort)
    , m_btEnabled(config->enableBluetooth)
{
    LOG_DEBUG("[MANAGER] Created (single client mode)");
}

SocketManager::~SocketManager() {
    stop();
}


void SocketManager::start() {
    if (m_running.exchange(true)) return;

    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        LOG_ERROR("[MANAGER] WSAStartup failed!");
        m_running = false;
        return;
    }

    // 1. KHỞI TẠO SOCKET LẮNG NGHE TCP (Không tạo Thread riêng ở Socket)
    m_tcpSocket = std::make_shared<TCPSocket>(m_tcpPort);
    m_tcpSocket->start(nullptr, "TCP-Server"); // Không truyền callback tuần tự cũ nữa

    // 2. KHỞI TẠO SOCKET LẮNG NGHE BLUETOOTH (NẾU BẬT)
    if (m_btEnabled && m_BtUuid.length() > 10) {
        m_btSocket = std::make_shared<BTSocket>(m_BtUuid);
        m_btSocket->start(nullptr, "BT-Server");
    }

    // 3. KHỞI CHẠY UDP DISCOVERY
    m_udpDiscovery = std::make_unique<UDPDiscovery>();
    m_udpDiscovery->start(m_udpPort);

    // 🚀 GIẢI PHÁP ĐỘT PHÁ: KHỞI CHẠY 1 LUỒNG ACCEPT "MỘT CỬA" DUY NHẤT HƯỚNG SỰ KIỆN
    m_acceptThread = std::thread([this]() {
        // Nâng độ ưu tiên luồng lắng nghe lên cao nhất
        SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST);

        while (m_running) {
            fd_set readSet;
            FD_ZERO(&readSet);

            SOCKET maxFd = 0;

            // Nạp socket TCP vào tập quét
            SOCKET tcpListen = m_tcpSocket ? m_tcpSocket->getListenSocket() : INVALID_SOCKET;
            if (tcpListen != INVALID_SOCKET) {
                FD_SET(tcpListen, &readSet);
                if (tcpListen > maxFd) maxFd = tcpListen;
            }

            // Nạp socket Bluetooth vào tập quét
            SOCKET btListen = m_btSocket ? m_btSocket->getListenSocket() : INVALID_SOCKET;
            if (btListen != INVALID_SOCKET) {
                FD_SET(btListen, &readSet);
                if (btListen > maxFd) maxFd = btListen;
            }

            if (maxFd == 0) {
                std::this_thread::sleep_for(std::chrono::milliseconds(50));
                continue;
            }

            // Hàm select() chặn luồng ngủ sâu ở tầng Kernel (Tốn đúng 0% CPU lúc rảnh)
            // Thời gian chờ đặt 100ms để luồng có thể thoát nhanh khi đóng server
            timeval timeout = { 0, 100000 };
            int selectResult = select(static_cast<int>(maxFd + 1), &readSet, NULL, NULL, &timeout);

            if (selectResult == SOCKET_ERROR || selectResult == 0 || !m_running) {
                continue;
            }

            // 🚀 KIỂM TRA SỰ KIỆN: Ai gõ cửa trước thì tiếp đón trước, tuyệt đối không gối đầu
            if (tcpListen != INVALID_SOCKET && FD_ISSET(tcpListen, &readSet)) {
                // Gọi trực tiếp hàm accept đồng bộ thời gian thực của lớp TCP
                sockaddr_in clientAddr{};
                int addrLen = sizeof(clientAddr);
                SOCKET client = accept(tcpListen, (sockaddr*)&clientAddr, &addrLen);

                if (client != INVALID_SOCKET && m_running) {
                    LOG_INFO("[MANAGER] Welcoming New TCP Connection.");
                    onNewConnection(client, TransportType::TCP, "TCP_CLIENT_CONNECTED");
                }
            }
            else if (btListen != INVALID_SOCKET && FD_ISSET(btListen, &readSet)) {
                // Gọi trực tiếp hàm accept đồng bộ thời gian thực của lớp Bluetooth
                SOCKADDR_BTH clientAddr = { 0 };
                int addrLen = sizeof(clientAddr);
                SOCKET client = accept(btListen, (SOCKADDR*)&clientAddr, &addrLen);

                if (client != INVALID_SOCKET && m_running) {
                    LOG_INFO("[MANAGER] Welcoming New Bluetooth Connection.");
                    onNewConnection(client, TransportType::BLUETOOTH, "BLUETOOTH_CLIENT_CONNECTED");
                }
            }
        }
        });

    g_state.serverRunning = true;
    LOG_INFO("[MANAGER] Smart Single-Thread Accept Engine Deployed (0% Idle CPU, Zero Race Condition).");
}

void SocketManager::stop() {
    if (!m_running.exchange(false)) return;

    LOG_DEBUG("[MANAGER] Shutdown triggered...");

    // 1. Đóng socket thô để giải phóng hàm select() đang kẹt ở luồng accept
    if (m_tcpSocket) m_tcpSocket->stop();
    if (m_btSocket) m_btSocket->stop();
    if (m_udpDiscovery) m_udpDiscovery->stop();

    // 2. Thu hồi luồng Accept duy nhất
    if (m_acceptThread.joinable()) {
        m_acceptThread.join();
    }

    // 3. Dừng bộ xử lý Handler mạng mạng hiện tại
    if (m_handler) {
        m_handler->stop();
        m_handler.reset();
    }

    // 4. Thu hồi sạch sẽ tài nguyên session toàn cục
    g_state.cleanupAllSessions();
    WSACleanup();

    g_state.serverRunning = false;
    LOG_INFO("[MANAGER] Smart Accept Engine Shutdown complete!");
}


//void SocketManager::start() {
//    if (running.exchange(true)) return;
//
//    WSADATA wsaData;
//    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
//        LOG_ERROR("[MANAGER] WSAStartup failed!");
//        running = false;
//        return;
//    }
//
//    // ============================================================
//    // 1. Tạo TCP Socket (KHÔNG THREAD)
//    // ============================================================
//    m_tcpSocket = std::make_shared<TCPSocket>(m_tcpPort);
//    m_tcpSocket->start([this](SOCKET s, const std::string& info) {
//        onNewConnection(s, TransportType::TCP, info);
//        }, "TCP-Server");
//
//    // ============================================================
//    // 2. Tạo Bluetooth Socket (KHÔNG THREAD)
//    // ============================================================
//    if (m_btEnabled && m_BtUuid.length() > 10) {
//        m_btSocket = std::make_shared<BTSocket>(m_BtUuid);
//        m_btSocket->start([this](SOCKET s, const std::string& info) {
//            onNewConnection(s, TransportType::BLUETOOTH, info);
//            }, "BT-Server");
//    }
//
//    // ============================================================
//    // 3. Chạy UDP Discovery
//    // ============================================================
//    m_udpDiscovery = std::make_unique<UDPDiscovery>();
//    m_udpDiscovery->start(m_udpPort);
//
//    // ============================================================
//    // 4. Chạy POLL LOOP (1 THREAD DUY NHẤT)
//    // ============================================================
//    m_polling = true;
//    m_pollThread = std::thread(&SocketManager::pollLoop, this);
//
//    g_state.serverRunning = true;
//    LOG_DEBUG("[MANAGER] All servers started (single client mode)");
//}
//
//void SocketManager::stop() {
//    if (!running.exchange(false)) return;
//
//    LOG_DEBUG("[MANAGER] Stopping...");
//
//    // ============================================================
//    // 1. Dừng POLL LOOP TRƯỚC
//    // ============================================================
//    m_polling = false;
//    if (m_pollThread.joinable()) {
//        m_pollThread.join();
//    }
//
//    // ============================================================
//    // 2. Dừng handler
//    // ============================================================
//    if (m_handler) {
//        m_handler->stop();
//        m_handler.reset();
//    }
//
//    // ============================================================
//    // 3. Dừng transports
//    // ============================================================
//    if (m_tcpSocket) {
//        m_tcpSocket->stop();
//        m_tcpSocket.reset();
//    }
//    if (m_btSocket) {
//        m_btSocket->stop();
//        m_btSocket.reset();
//    }
//    if (m_udpDiscovery) {
//        m_udpDiscovery->stop();
//        m_udpDiscovery.reset();
//    }
//
//    g_state.serverRunning = false;
//
//    // ============================================================
//    // 4. Cleanup session
//    // ============================================================
//    forceCleanupSession(g_state.activeSession->socket);
//
//    WSACleanup();
//    LOG_INFO("[MANAGER] Shutdown complete!");
//}

void SocketManager::restart() {
    LOG_INFO("[MANAGER] Restarting...");
    stop();
    std::this_thread::sleep_for(std::chrono::milliseconds(500));
    start();
}

bool SocketManager::isRunning() const {
    return m_running.load();
}


// ============================================================
// ✅ XỬ LÝ KẾT NỐI MỚI - ĐƯỢC GỌI TỪ POLL LOOP
// ============================================================
void SocketManager::onNewConnection(SOCKET socket, TransportType type, const std::string& clientInfo) {
    if (socket == INVALID_SOCKET) return;

    LOG_INFO("[MANAGER] New connection: %s (transport: %s)",
        clientInfo.c_str(),
        (type == TransportType::TCP) ? "TCP" : "Bluetooth");

    // ✅ replaceSession trả về session
    auto session = g_state.replaceSession(socket, type, clientInfo, m_handler);

    if (!session) {
        LOG_ERROR("[MANAGER] Failed to create session!");
        closesocket(socket);
        return;
    }

    // ✅ Tạo handler mới
    m_handler = std::make_shared<SessionHandler>();
    m_handler->start(session);

    LOG_INFO("[MANAGER] Session created successfully (socket: %d)", socket);
}