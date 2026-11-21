#include "pch.h"
#include "sessioncontext.h"
#include "sessionhandler.h"

// ==================== GLOBAL ====================
SessionContext g_state;

// ============================================================
// CLIENT SESSION - PARSER CREATION
// ============================================================
void ClientSession::createParserForRole(SessionRole role, SOCKET socket) {
    switch (role) {
    case SessionRole::COMMAND:
        if (!parseCommand) {
            parseCommand = std::make_unique<ParseCommand>(static_cast<int>(socket));
            LOG_DEBUG("[SESSION] Created ParseCommand for role COMMAND (socket: %d)", socket);
        }
        break;
    case SessionRole::SCREEN_STREAM:
        if (!parseCommand) {
            parseCommand = std::make_unique<ParseCommand>(static_cast<int>(socket));
            LOG_DEBUG("[SESSION] Created ParseCommand for role SCREEN_STREAM (socket: %d)", socket);
        }
        break;
    case SessionRole::TRANSFER:
        if (!parseFile) {
            parseFile = std::make_unique<ParseTransferFile>(static_cast<int>(socket));
            LOG_DEBUG("[SESSION] Created ParseTransferFile for role TRANSFER (socket: %d)", socket);
        }
        break;

    default:
        LOG_WARN("[SESSION] Unknown role for parser creation: %d", static_cast<int>(role));
        break;
    }
}

// ============================================================
// CLIENT SESSION - CLEANUP
// ============================================================
void ClientSession::cleanup() {
    LOG_DEBUG("[CLEANUP] Starting cleanup for socket: %d", socket);

    // ===== 1. DỪNG PARSERS =====
    if (parseCommand) {
        parseCommand.reset();
        LOG_DEBUG("[CLEANUP] Destroyed ParseCommand");
    }

    if (parseFile) {
        parseFile.reset();
        LOG_DEBUG("[CLEANUP] Destroyed ParseTransferFile");
    }

    // ===== 2. ĐÓNG SOCKET =====
    if (socket != INVALID_SOCKET) {
        SenderBinary::cancelImmediately(socket);
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
        SenderBinary::setFileCallbacks(socket, nullptr, nullptr, nullptr, nullptr);

        shutdown(socket, SD_BOTH);
        closesocket(socket);
        SenderBinary::remove(socket);
        socket = INVALID_SOCKET;
        LOG_DEBUG("[CLEANUP] Closed socket");
    }

    // ===== 3. DỌN FILE UPLOAD =====
    if (uploadFile) {
        fflush(uploadFile);
        fclose(uploadFile);
        uploadFile = nullptr;

        if (uploadReceivedSize < uploadTotalSize && !uploadFilePath.empty()) {
            if (FileExplorerUtils::FileExists(uploadFilePath)) {
                DeleteFileA(uploadFilePath.c_str());
                LOG_DEBUG("[CLEANUP] Deleted incomplete upload: %s", uploadFilePath.c_str());
            }
        }
        uploadFilePath.clear();
        uploadTotalSize = 0;
        uploadReceivedSize = 0;
    }

    // ===== 4. DỌN TEMP ZIP =====
    if (!processingZipPath.empty() && processingZipPath != "UNZIPPING") {
        if (FileExplorerUtils::FileExists(processingZipPath)) {
            DeleteFileA(processingZipPath.c_str());
            LOG_DEBUG("[CLEANUP] Deleted temp zip: %s", processingZipPath.c_str());
        }
        processingZipPath.clear();
    }

    // ===== 5. DỌN CLIPBOARD =====
    ClipboardState::getInstance().clearClipboard();

    // ===== 6. RESET STATE =====
    role = SessionRole::UNKNOWN;
    state = SessionState::DISCONNECTED;
    isLongTransfer = false;
    currentDir.clear();
    pendingRefresh = false;
    authenticated = false;
    clientInfo.clear();
    clientAddress.clear();
    lastActivity = std::chrono::steady_clock::time_point();
    writeBuffer.clear();

    LOG_DEBUG("[CLEANUP] Cleanup completed");
}

// ============================================================
// SESSION CONTEXT - REPLACE SESSION
// ============================================================
std::shared_ptr<ClientSession> SessionContext::replaceSession(
    SOCKET newSocket,
    TransportType type,
    const std::string& clientInfo,
    std::shared_ptr<SessionHandler>& handler)
{
    std::unique_lock<std::recursive_mutex> lock(sessionsMutex);

    // ===== 1. XÓA SESSION CŨ =====
    if (activeSession) {
        LOG_DEBUG("[SessionContext] Removing old session (socket: %d)", activeSession->socket);

        // 1.1 Dừng handler
        if (handler) {
            handler->stop();
            handler.reset();
        }

        // 1.2 Đóng socket
        if (activeSession->socket != INVALID_SOCKET) {
            SenderBinary::cancelImmediately(activeSession->socket);
            SenderBinary::remove(activeSession->socket);
            shutdown(activeSession->socket, SD_BOTH);
            closesocket(activeSession->socket);
            activeSession->socket = INVALID_SOCKET;
        }

        // 1.3 Cleanup
        activeSession->cleanup();
        activeSession.reset();

        // 1.4 Đợi cleanup
        lock.unlock();
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        lock.lock();
    }

    // ===== 2. TẠO SESSION MỚI =====
    auto session = std::make_shared<ClientSession>();
    session->socket = newSocket;
    session->transport = type;
    session->clientInfo = clientInfo;
    session->clientAddress = getRealClientAddress(newSocket);
    session->setupSocket();
    session->state = SessionState::HANDSHAKE;
    session->lastActivity = std::chrono::steady_clock::now();
    session->currentDir = FileExplorerUtils::GetHomeDirectory();

    // ❌ KHÔNG tạo parser ở đây (sẽ tạo sau khi biết role)

    activeSession = session;

    LOG_INFO("[SessionContext] New session created (socket: %d) - waiting for role", newSocket);

    return session;
}

// ============================================================
// SESSION CONTEXT - REMOVE SESSION
// ============================================================
void SessionContext::removeCurrentSession(std::shared_ptr<SessionHandler>& handler) {
    std::lock_guard<std::recursive_mutex> lock(sessionsMutex);

    if (!activeSession) {
        LOG_WARN("[SessionContext] No active session to remove");
        return;
    }

    LOG_INFO("[SessionContext] Removing current session (socket: %d)", activeSession->socket);

    if (handler) {
        handler->stop();
        handler.reset();
    }

    if (activeSession->socket != INVALID_SOCKET) {
        SenderBinary::cancelImmediately(activeSession->socket);
        SenderBinary::remove(activeSession->socket);
        shutdown(activeSession->socket, SD_BOTH);
        closesocket(activeSession->socket);
        activeSession->socket = INVALID_SOCKET;
    }

    activeSession->cleanup();
    activeSession.reset();

    LOG_INFO("[SessionContext] Session removed");
}

// ============================================================
// SESSION CONTEXT - CLEANUP ALL
// ============================================================
void SessionContext::cleanupAllSessions() {
    std::lock_guard<std::recursive_mutex> lock(sessionsMutex);

    if (activeSession) {
        activeSession->cleanup();
        activeSession.reset();
        LOG_DEBUG("[SessionContext] All sessions cleaned up");
    }
}

// ============================================================
// HELPER FUNCTIONS
// ============================================================

// ----- GET REAL CLIENT ADDRESS -----
std::string getRealClientAddress(SOCKET socket) {
    if (!socket || socket == INVALID_SOCKET) return "";

    sockaddr_storage peerAddr;
    int len = sizeof(peerAddr);

    if (getpeername(socket, (sockaddr*)&peerAddr, &len) == 0) {
        if (peerAddr.ss_family == AF_INET) {
            sockaddr_in* s = (sockaddr_in*)&peerAddr;
            char ipStr[INET_ADDRSTRLEN] = { 0 };
            inet_ntop(AF_INET, &(s->sin_addr), ipStr, sizeof(ipStr));
            return std::string(ipStr);
        }
        else if (peerAddr.ss_family == AF_INET6) {
            sockaddr_in6* s = (sockaddr_in6*)&peerAddr;
            // IPv4 mapped address
            if (s->sin6_addr.s6_addr[0] == 0 && s->sin6_addr.s6_addr[10] == 0xff && s->sin6_addr.s6_addr[11] == 0xff) {
                char ipStr[INET_ADDRSTRLEN] = { 0 };
                in_addr ipv4;
                std::memcpy(&ipv4, &s->sin6_addr.s6_addr[12], 4);
                inet_ntop(AF_INET, &ipv4, ipStr, sizeof(ipStr));
                return std::string(ipStr);
            }
            else {
                char ipStr[INET6_ADDRSTRLEN] = { 0 };
                inet_ntop(AF_INET6, &(s->sin6_addr), ipStr, sizeof(ipStr));
                return std::string(ipStr);
            }
        }
        else if (peerAddr.ss_family == AF_BTH) {
            SOCKADDR_BTH* s = (SOCKADDR_BTH*)&peerAddr;
            char macStr[18];
            unsigned __int64 mac = s->btAddr;
            snprintf(macStr, sizeof(macStr), "%02X:%02X:%02X:%02X:%02X:%02X",
                (int)((mac >> 40) & 0xFF), (int)((mac >> 32) & 0xFF),
                (int)((mac >> 24) & 0xFF), (int)((mac >> 16) & 0xFF),
                (int)((mac >> 8) & 0xFF), (int)(mac & 0xFF));
            return std::string(macStr) + ":" + std::to_string(s->port);
        }
    }

    std::ostringstream oss;
    oss << socket;
    return oss.str();
}

// ----- FIND SESSION BY SOCKET -----
std::shared_ptr<ClientSession> findSessionBySocket(SOCKET socket) {
    if (socket == INVALID_SOCKET) return nullptr;

    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    if (g_state.activeSession && g_state.activeSession->socket == socket) {
        return g_state.activeSession;
    }
    return nullptr;
}

// ----- CHECK SESSION ALIVE -----
bool isSessionAlive(SOCKET socket) {
    if (socket == INVALID_SOCKET) return false;

    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    if (g_state.activeSession && g_state.activeSession->socket == socket) {
        return g_state.activeSession->state != SessionState::DISCONNECTED &&
            g_state.activeSession->socket != INVALID_SOCKET;
    }
    return false;
}

// ----- GET SESSION ROLE -----
SessionRole getSessionRole(SOCKET socket) {
    if (socket == INVALID_SOCKET) return SessionRole::UNKNOWN;

    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    if (g_state.activeSession && g_state.activeSession->socket == socket) {
        return g_state.activeSession->role;
    }
    return SessionRole::UNKNOWN;
}

// ----- GET SESSION TRANSPORT -----
TransportType getSessionTransport(SOCKET socket) {
    if (socket == INVALID_SOCKET) return TransportType::UNKNOWN;

    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    if (g_state.activeSession && g_state.activeSession->socket == socket) {
        return g_state.activeSession->transport;
    }
    return TransportType::UNKNOWN;
}

// ----- CHECK HAS ACTIVE SCREEN STREAM -----
bool hasActiveScreenStream() {
    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    return g_state.activeSession &&
        g_state.activeSession->role == SessionRole::SCREEN_STREAM &&
        g_state.activeSession->state != SessionState::DISCONNECTED;
}

// ----- COUNT ACTIVE SESSIONS BY ROLE -----
int countActiveSessionsByRole(SessionRole role) {
    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    if (g_state.activeSession &&
        g_state.activeSession->role == role &&
        g_state.activeSession->state != SessionState::DISCONNECTED) {
        return 1;
    }
    return 0;
}

// ----- GET SOCKETS BY ROLE -----
std::vector<SOCKET> getSocketsByRole(SessionRole role) {
    std::vector<SOCKET> sockets;
    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    if (g_state.activeSession &&
        g_state.activeSession->role == role &&
        g_state.activeSession->state != SessionState::DISCONNECTED) {
        sockets.push_back(g_state.activeSession->socket);
    }
    return sockets;
}

// ----- FORCE CLEANUP SESSION -----
void forceCleanupSession(SOCKET socket) {
    if (socket == INVALID_SOCKET) return;

    LOG_INFO("[SessionContext] Force cleaning session with socket: %d", socket);

    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);

    if (g_state.activeSession && g_state.activeSession->socket == socket) {
        g_state.activeSession->cleanup();
        g_state.activeSession.reset();
        LOG_INFO("[SessionContext] Session cleaned successfully");
        return;
    }

    shutdown(socket, SD_BOTH);
    closesocket(socket);
    LOG_WARN("[SessionContext] Session not found for socket: %d, closed directly", socket);
}

// ----- CHECK SCREEN STREAM SESSION -----
bool isScreenStreamSession(SOCKET socket) {
    if (socket == INVALID_SOCKET) return false;

    std::lock_guard<std::recursive_mutex> lock(g_state.sessionsMutex);
    if (g_state.activeSession && g_state.activeSession->socket == socket) {
        return g_state.activeSession->role == SessionRole::SCREEN_STREAM &&
            g_state.activeSession->state != SessionState::DISCONNECTED;
    }
    return false;
}