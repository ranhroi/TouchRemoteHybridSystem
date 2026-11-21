#include "pch.h"
#include "btsocket.h"

// ===== ĐỊNH NGHĨA WSAESERVICE NẾU CHƯA CÓ =====
#ifndef WSAESERVICE
#define WSAESERVICE 10110
#endif

// UUID từ Android client
inline bool uuidStringToGuid(const char* uuidString, GUID& outGuid) {
    wchar_t wideUuid[64];
    MultiByteToWideChar(CP_UTF8, 0, uuidString, -1, wideUuid, 64);

    wchar_t formatted[64];
    if (wideUuid[0] != L'{') {
        swprintf(formatted, 64, L"{%s}", wideUuid);
    }
    else {
        wcscpy_s(formatted, wideUuid);
    }

    return SUCCEEDED(CLSIDFromString(formatted, &outGuid));
}

std::wstring stringToWideString(const std::string& str) {
    if (str.empty()) return L"";
    int size_needed = MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), NULL, 0);
    std::wstring wstr(size_needed, 0);
    MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), &wstr[0], size_needed);
    return wstr;
}

std::string wideStringToString(const std::wstring& wstr) {
    if (wstr.empty()) return "";
    int size_needed = WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), NULL, 0, NULL, NULL);
    std::string str(size_needed, 0);
    WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), &str[0], size_needed, NULL, NULL);
    return str;
}

BTSocket::BTSocket(const std::string& uuid) {
    if (!uuid.empty()) {
        m_uuidString = uuid;
        uuidStringToGuid(uuid.c_str(), m_uuidGuid);
    }
    else {
        m_uuidString = "c05efa2b-5e9f-4a39-9705-72ccf47d2eb8";
        uuidStringToGuid(m_uuidString.c_str(), m_uuidGuid);
    }
    transportName = "Bluetooth";
}

BTSocket::~BTSocket() {
    cleanup();
}

void BTSocket::initSocket() {
    LOG_INFO("[BT] Initializing Bluetooth server...");

    unregisterBluetoothService(m_uuidGuid);
    std::this_thread::sleep_for(std::chrono::milliseconds(200)); // Rút ngắn thời gian chờ nhả SDP

    listenSocket = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
    if (listenSocket == INVALID_SOCKET) {
        LOG_ERROR("[BT] Failed to create socket: %d", WSAGetLastError());
        return;
    }

    int reuse = 1;
    setsockopt(listenSocket, SOL_SOCKET, SO_REUSEADDR, (char*)&reuse, sizeof(reuse));

    SOCKADDR_BTH addr = { 0 };
    addr.addressFamily = AF_BTH;
    addr.btAddr = 0;
    addr.port = BT_PORT_ANY;

    int bindRetry = 0;
    const int MAX_BIND_RETRY = 3; // Rút ngắn số lần retry tránh block luồng khởi động quá lâu

    while (bindRetry < MAX_BIND_RETRY) {
        if (bind(listenSocket, (SOCKADDR*)&addr, sizeof(addr)) == SOCKET_ERROR) {
            int error = WSAGetLastError();
            if (error == WSAEADDRINUSE) {
                LOG_WARN("[BT] Bind failed (address in use), retry %d", bindRetry + 1);
                std::this_thread::sleep_for(std::chrono::milliseconds(500 * (bindRetry + 1)));
                bindRetry++;
                continue;
            }
            LOG_ERROR("[BT] Bind failed: %d", error);
            closesocket(listenSocket);
            listenSocket = INVALID_SOCKET;
            return;
        }
        break;
    }

    if (bindRetry >= MAX_BIND_RETRY) {
        LOG_ERROR("[BT] Bind failed after %d retries", MAX_BIND_RETRY);
        closesocket(listenSocket);
        listenSocket = INVALID_SOCKET;
        return;
    }

    int len = sizeof(addr);
    if (getsockname(listenSocket, (SOCKADDR*)&addr, &len) != 0) {
        LOG_ERROR("[BT] Failed to get port: %d", WSAGetLastError());
        closesocket(listenSocket);
        listenSocket = INVALID_SOCKET;
        return;
    }

    int channel = addr.port;
    LOG_INFO("[BT] Bound to RFCOMM channel: %d", channel);

    if (registerBluetoothService(channel, m_uuidGuid)) {
        LOG_INFO("[BT] Bluetooth service registered with UUID: %s", m_uuidString.c_str());
    }
    else {
        LOG_WARN("[BT] Failed to register SDP");
    }

    if (listen(listenSocket, SOMAXCONN) == SOCKET_ERROR) {
        LOG_ERROR("[BT] Listen failed: %d", WSAGetLastError());
        unregisterBluetoothService(m_uuidGuid);
        closesocket(listenSocket);
        listenSocket = INVALID_SOCKET;
        return;
    }

    // 🚀 TỐI ƯU CỐT LÕI 1: KHÔNG set non-blocking cho listenSocket nữa!
    // Giữ nó ở chế độ BLOCKING để luồng Bluetooth Accept ngủ sâu ở Kernel (0% CPU).
    // Khi điện thoại Android bấm kết nối, phần cứng chip Bluetooth phát tín hiệu là luồng thức giấc ngay.

    LOG_INFO("[BT] Event-Driven Bluetooth Server listening...");
}

void BTSocket::runAccept(OnAccepted cb) {
}

void BTSocket::cleanup() {
    LOG_DEBUG("[BT] Cleaning up...");
    unregisterBluetoothService(m_uuidGuid);

    if (listenSocket != INVALID_SOCKET) {
        SOCKET s = listenSocket;
        listenSocket = INVALID_SOCKET;
        shutdown(s, SD_BOTH);
        closesocket(s); // Ép hàm accept() đang chặn ở luồng Bluetooth phải thức giấc và thoát lập tức
    }
    LOG_DEBUG("[BT] Cleanup completed");
}

void BTSocket::setupSocketForAndroid(SOCKET client) {
    if (client == INVALID_SOCKET) return;

    // Hàm này hiện tại đã được bốc sang cấu hình tự động bên trong `ClientSession::setupSocket()`
    // Chúng ta giữ hàm trống ở đây nếu giao diện cũ bắt buộc gọi, tránh lỗi build link.
}

// ============================================================
// BTSocket::registerBluetoothService
// ============================================================
bool BTSocket::registerBluetoothService(int channel, GUID myUuid) {
    CSADDR_INFO addrInfo = { 0 };
    SOCKADDR_BTH localAddr = { 0 };

    localAddr.addressFamily = AF_BTH;
    localAddr.port = (ULONG)channel;

    addrInfo.LocalAddr.lpSockaddr = (LPSOCKADDR)&localAddr;
    addrInfo.LocalAddr.iSockaddrLength = sizeof(SOCKADDR_BTH);
    addrInfo.iProtocol = BTHPROTO_RFCOMM;
    addrInfo.iSocketType = SOCK_STREAM;

    WSAQUERYSETW service = { 0 };
    service.dwSize = sizeof(service);
    service.lpszServiceInstanceName = (LPWSTR)L"Windows TouchRemoteServer";
    service.lpServiceClassId = (LPGUID)&myUuid;
    service.dwNameSpace = NS_BTH;
    service.dwNumberOfCsAddrs = 1;
    service.lpcsaBuffer = &addrInfo;

    if (WSASetServiceW(&service, RNRSERVICE_REGISTER, 0) == SOCKET_ERROR) {
        return false;
    }
    return true;
}

// ============================================================
// BTSocket::unregisterBluetoothService
// ============================================================
void BTSocket::unregisterBluetoothService(GUID myUuid) {
    WSAQUERYSETW service = { 0 };
    service.dwSize = sizeof(service);
    service.lpServiceClassId = (LPGUID)&myUuid;
    service.dwNameSpace = NS_BTH;

    WSASetServiceW(&service, RNRSERVICE_DEREGISTER, 0);
}


// ============================================================
// BTSocket::formatBluetoothAddress
// ============================================================
std::string BTSocket::formatBluetoothAddress(unsigned long long btAddr) {
    std::stringstream ss;
    for (int i = 5; i >= 0; i--) {
        ss << std::hex << std::setfill('0') << std::setw(2)
            << ((btAddr >> (i * 8)) & 0xFF);
        if (i > 0) ss << ":";
    }
    return ss.str();
}