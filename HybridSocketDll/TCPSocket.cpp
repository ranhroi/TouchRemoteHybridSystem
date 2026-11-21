#include "pch.h"
#include "tcpsocket.h"

void TCPSocket::initSocket() {
    listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket == INVALID_SOCKET) {
        LOG_ERROR("[%s] Failed to create socket: %d", transportName.c_str(), WSAGetLastError());
        return;
    }

    int opt = 1;
    setsockopt(listenSocket, SOL_SOCKET, SO_REUSEADDR, (char*)&opt, sizeof(opt));
    setsockopt(listenSocket, IPPROTO_TCP, TCP_NODELAY, (char*)&opt, sizeof(opt));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (bind(listenSocket, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
        LOG_ERROR("[%s] Bind failed on port %d: %d", transportName.c_str(), port, WSAGetLastError());
        closesocket(listenSocket);
        listenSocket = INVALID_SOCKET;
        return;
    }

    // 🚀 TỐI ƯU GỐC RỄ 1: Đẩy backlog lên mức tối đa hệ điều hành hỗ trợ
    if (listen(listenSocket, SOMAXCONN) == SOCKET_ERROR) {
        LOG_ERROR("[%s] Listen failed: %d", transportName.c_str(), WSAGetLastError());
        closesocket(listenSocket);
        listenSocket = INVALID_SOCKET;
        return;
    }

    // 🚀 TỐI ƯU GỐC RỄ 2: Giữ listenSocket ở chế độ BLOCKING 
    // Luồng sẽ ngủ sâu ở Kernel, có kết nối là Windows thức tỉnh ngay lập tức, không tốn 1% CPU quét dạo.
    LOG_INFO("[%s] Event-Driven Server Listening on port %d", transportName.c_str(), port);
}

void TCPSocket::runAccept(OnAccepted cb) {
}

void TCPSocket::cleanup() {
    if (listenSocket != INVALID_SOCKET) {
        SOCKET s = listenSocket;
        listenSocket = INVALID_SOCKET;
        shutdown(s, SD_BOTH);
        closesocket(s); // Ép hàm accept() đang chặn ở luồng kia phải thoát ngay lập tức
    }
}
