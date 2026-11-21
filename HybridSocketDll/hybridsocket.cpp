
#include "pch.h"
#include "auth.h"
#include "hybridsocket.h"
#include "socketmanager.h"
#include "sessioncontext.h"
#include "networkutils.h"

static std::unique_ptr<SocketManager> g_server;
static std::string g_cachedIP;
static std::string g_cachedName;

bool RemoteServer_Start(const RemoteServerConfig* config) {
    if (!config) return false;

    if (!g_server) {
        g_server = std::make_unique<SocketManager>(config);
    }

    g_state.needPassword = config->needPassword;

    if (config->needPassword && config->password && config->password[0]) {
        Auth::setPassword(config->password);
    }

    g_server->start();

    return g_server->isRunning();
}

void RemoteServer_Stop() {
    if (g_server) {
        g_server->stop();
    }
}

void RemoteServer_Cleanup() {
    LOG_DEBUG("[DLL] RemoteServer_Cleanup called");

    // 1. Dừng server nếu đang chạy
    if (g_server) {
        g_server->stop();
        g_server.reset();
    }

    // 2. Dọn dẹp SenderBinary
    SenderBinary::cleanup();

    // 3. Dọn dẹp Winsock
    WSACleanup();

    // 4. Xóa các biến tĩnh
    g_cachedIP.clear();
    g_cachedName.clear();

	g_state.cleanupAllSessions();   

    // 6. Reset các cờ trạng thái
    g_state.serverRunning = false;
    g_state.needPassword = false;
    g_state.needBuy = false;
    g_state.passwordMd5.clear();

    LOG_DEBUG("[DLL] RemoteServer_Cleanup completed");
}

bool RemoteServer_IsRunning() {
    return g_server && g_server->isRunning();
}

void RemoteServer_Restart() {
    if (g_server) {
        g_server->restart();
    }
}

int RemoteServer_GetConnectionStats(int* tcpCount, int* btCount) {
    if (!tcpCount || !btCount) return -1;
    *tcpCount = 0;
    *btCount = 0;

    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);

    // ✅ SỬA: Kiểm tra session duy nhất
    if (g_state.activeSession && g_state.activeSession->state != SessionState::DISCONNECTED) {
        if (g_state.activeSession->transport == TransportType::TCP) {
            (*tcpCount)++;
        }
        else if (g_state.activeSession->transport == TransportType::BLUETOOTH) {
            (*btCount)++;
        }
    }
    return 0;
}

const char* RemoteServer_PopLog() {
    static std::string currentLog;
    currentLog = g_logger.PopLog();
    if (currentLog.empty()) return nullptr;
    return currentLog.c_str();
}

const char* RemoteServer_GetLocalIP() {
    g_cachedIP = NetworkUtils::getLocalIP();
    return g_cachedIP.c_str();
}

const char* RemoteServer_GetMachineName() {
    g_cachedName = NetworkUtils::getHostName();
    return g_cachedName.c_str();
}