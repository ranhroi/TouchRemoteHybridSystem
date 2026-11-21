#pragma once
#include "pch.h"

// ==================== INCLUDES ====================
#include "parsecommand.h"
#include "parsetransferfile.h"
#include "smartsenderbinary.h"
#include "fileexplorerutils.h"
#include "fileexplorerstate.h"

// ==================== TIMEOUT CONSTANTS ====================
namespace TimeoutConfig {   
    // ===== TIMEOUT =====
    constexpr int COMMAND = 120;
    constexpr int SCREEN_STREAM = 1800;
    constexpr int TRANSFER_IDLE = 180;
    constexpr int TRANSFER_ACTIVE = 1200;
    constexpr int BLUETOOTH_TRANSFER_ACTIVE = 2700;
    constexpr int COMPRESS_TIMEOUT = 1800;
    constexpr int BLUETOOTH_SEND_TIMEOUT = 900;
    constexpr int TCP_SEND_TIMEOUT = 600;

    // ===== SIZE LIMITS =====
    constexpr unsigned long long BLUETOOTH_MAX_SIZE = 200ULL * 1024 * 1024;
    constexpr unsigned long long TCP_MAX_SIZE = 10ULL * 1024 * 1024 * 1024;

    // ===== CHUNK SIZE =====
    constexpr int BLUETOOTH_CHUNK_SIZE = 16 * 1024;
    constexpr int TCP_CHUNK_SIZE = 64 * 1024;

    // ===== BUFFER & QUEUE =====
    constexpr int BLUETOOTH_BUFFER_THRESHOLD = 2 * 1024 * 1024;
    constexpr int BUFFER_FULL_WAIT_MS = 50;
    constexpr int UNZIP_TIMEOUT = 300;
    constexpr int FILE_QUEUE_SIZE = 50;
    constexpr int FILE_QUEUE_MEMORY_MB = 50;
    constexpr int FILE_RETRIES = 3;
}

// ==================== FORWARD DECLARATIONS ====================
class SessionHandler;

// ==================== CLIENT SESSION ====================
struct ClientSession {
    // ===== SOCKET & STATE =====
    SOCKET socket = INVALID_SOCKET;
    SessionState state = SessionState::IDLE;
    TransportType transport = TransportType::UNKNOWN;

    // ===== CLIENT INFO =====
    std::string clientInfo;
    std::string clientAddress;
    std::chrono::steady_clock::time_point lastActivity;

    // ===== AUTHENTICATION =====
    bool authenticated = false;
    bool isLongTransfer = false;

    // ===== ROLE & PARSERS =====
    SessionRole role = SessionRole::UNKNOWN;
    std::unique_ptr<ParseCommand> parseCommand;
    std::unique_ptr<ParseTransferFile> parseFile;

    // ===== FILE UPLOAD =====
    std::FILE* uploadFile = nullptr;
    int64_t uploadTotalSize = 0;
    int64_t uploadReceivedSize = 0;
    std::string uploadFilePath = "";
    std::string processingZipPath;

    // ===== FILE EXPLORER =====
    std::string currentDir;
    bool pendingRefresh = false;
    std::vector<uint8_t> writeBuffer;

    // ============================================================
    // HELPER METHODS
    // ============================================================

    // ----- TRANSPORT CHECK -----
    bool isBluetooth() const { return transport == TransportType::BLUETOOTH; }
    bool isTcp() const { return transport == TransportType::TCP; }

    // ----- ROLE CHECK -----
    bool isCommandRole() const { return role == SessionRole::COMMAND; }
    bool isStreamRole() const { return role == SessionRole::SCREEN_STREAM; }
    bool isTransferRole() const { return role == SessionRole::TRANSFER; }

    // ----- ACTIVITY -----
    void updateActivity() {
        lastActivity = std::chrono::steady_clock::now();
    }

    void updateProgress(int64_t current, int64_t total) {
        uploadReceivedSize = current;
        uploadTotalSize = total;
        updateActivity();
    }

    // ----- DIRECTORY -----
    std::string getCurrentDir() const {
        if (currentDir.empty()) {
            return FileExplorerUtils::GetHomeDirectory();
        }
        return currentDir;
    }

    void setCurrentDir(const std::string& dir) {
        currentDir = dir;
        updateActivity();
    }

    // ----- TIMEOUT -----
    int getTimeout() const {
        if (processingZipPath == "UNZIPPING") {
            return 999999;
        }
        if (role == SessionRole::COMMAND) {
            return TimeoutConfig::COMMAND;
        }
        if (role == SessionRole::SCREEN_STREAM) {
            return TimeoutConfig::SCREEN_STREAM;
        }
        if (isLongTransfer) {
            if (isBluetooth()) {
                return TimeoutConfig::BLUETOOTH_TRANSFER_ACTIVE;
            }
            return TimeoutConfig::TRANSFER_ACTIVE;
        }
        return TimeoutConfig::TRANSFER_IDLE;
    }

    // ----- SIZE & CHUNK -----
    int getChunkSize() const {
        if (isBluetooth()) {
            return TimeoutConfig::BLUETOOTH_CHUNK_SIZE;
        }
        return TimeoutConfig::TCP_CHUNK_SIZE;
    }

    uint64_t getMaxAllowedSize() const {
        if (isBluetooth()) {
            return TimeoutConfig::BLUETOOTH_MAX_SIZE;
        }
        return TimeoutConfig::TCP_MAX_SIZE;
    }

    int getSendTimeout() const {
        if (isBluetooth()) {
            return TimeoutConfig::BLUETOOTH_SEND_TIMEOUT;
        }
        return TimeoutConfig::TCP_SEND_TIMEOUT;
    }

    bool isFileSizeAllowed(uint64_t fileSize) const {
        return fileSize <= getMaxAllowedSize();
    }

    // ----- SOCKET SETUP -----
    void setupSocket() {
        if (socket == INVALID_SOCKET) return;

        u_long mode = 1;
        ioctlsocket(socket, FIONBIO, &mode);

        int bufSize = getChunkSize();
        setsockopt(socket, SOL_SOCKET, SO_RCVBUF, (char*)&bufSize, sizeof(bufSize));
        setsockopt(socket, SOL_SOCKET, SO_SNDBUF, (char*)&bufSize, sizeof(bufSize));

        if (isTcp()) {
            int nodelay = 1;
            setsockopt(socket, IPPROTO_TCP, TCP_NODELAY, (char*)&nodelay, sizeof(nodelay));
        }

        int keepalive = 1;
        setsockopt(socket, SOL_SOCKET, SO_KEEPALIVE, (char*)&keepalive, sizeof(keepalive));
    }


    // ==================== SEND HELPERS (DỰA VÀO ROLE) ====================

    // Gửi TEXT - ưu tiên cao nhất, dùng cho COMMAND role
    bool sendText(const std::vector<uint8_t>& data) {
        if (socket == INVALID_SOCKET) return false;
        return SenderBinary::send(socket, data);
    }

    bool sendText(const std::string& data) {
        if (socket == INVALID_SOCKET) return false;
        return SenderBinary::send(socket, data);
    }

    // Gửi FILE DATA - dùng cho TRANSFER role
    bool sendFileData(const std::vector<uint8_t>& data) {
        if (socket == INVALID_SOCKET) return false;
        return SenderBinary::sendFileData(socket, data);
    }

    // Gửi RAW - không qua queue, dùng cho trường hợp đặc biệt
    bool sendRaw(const std::vector<uint8_t>& data) {
        if (socket == INVALID_SOCKET) return false;
        return SenderBinary::sendRaw(socket, data);
    }

    // Gửi FILE (ifstream) - dùng cho TRANSFER role
    bool sendFile(const std::string& filePath) {
        if (socket == INVALID_SOCKET) return false;
        return SenderBinary::sendFile(socket, filePath);
    }

    bool sendFile(std::ifstream* file) {
        if (socket == INVALID_SOCKET || !file) return false;
        return SenderBinary::sendFile(socket, file);
    }

    // ----- PARSER CREATION -----
    void createParserForRole(SessionRole role, SOCKET socket);

    // ============================================================
    // CLEANUP
    // ============================================================
    void cleanup();

    // ============================================================
    // CONSTRUCTORS
    // ============================================================
    ClientSession() = default;
    ~ClientSession() { cleanup(); }

    // ----- MOVE -----
    ClientSession(ClientSession&& other) noexcept
        : socket(std::exchange(other.socket, INVALID_SOCKET))
        , state(std::exchange(other.state, SessionState::DISCONNECTED))
        , transport(std::exchange(other.transport, TransportType::UNKNOWN))
        , lastActivity(std::move(other.lastActivity))
        , clientInfo(std::move(other.clientInfo))
        , clientAddress(std::move(other.clientAddress))
        , authenticated(std::exchange(other.authenticated, false))
        , isLongTransfer(std::exchange(other.isLongTransfer, false))
        , role(std::exchange(other.role, SessionRole::UNKNOWN))
        , parseCommand(std::move(other.parseCommand))
        , parseFile(std::move(other.parseFile))
        , uploadFile(std::exchange(other.uploadFile, nullptr))
        , uploadTotalSize(std::exchange(other.uploadTotalSize, 0))
        , uploadReceivedSize(std::exchange(other.uploadReceivedSize, 0))
        , uploadFilePath(std::move(other.uploadFilePath))
        , processingZipPath(std::move(other.processingZipPath))
        , currentDir(std::move(other.currentDir))
        , pendingRefresh(std::exchange(other.pendingRefresh, false))
        , writeBuffer(std::move(other.writeBuffer))
    {
    }

    ClientSession& operator=(ClientSession&& other) noexcept {
        if (this != &other) {
            cleanup();
            socket = std::exchange(other.socket, INVALID_SOCKET);
            state = std::exchange(other.state, SessionState::DISCONNECTED);
            transport = std::exchange(other.transport, TransportType::UNKNOWN);
            lastActivity = std::move(other.lastActivity);
            clientInfo = std::move(other.clientInfo);
            clientAddress = std::move(other.clientAddress);
            authenticated = std::exchange(other.authenticated, false);
            isLongTransfer = std::exchange(other.isLongTransfer, false);
            role = std::exchange(other.role, SessionRole::UNKNOWN);
            parseCommand = std::move(other.parseCommand);
            parseFile = std::move(other.parseFile);
            uploadFile = std::exchange(other.uploadFile, nullptr);
            uploadTotalSize = std::exchange(other.uploadTotalSize, 0);
            uploadReceivedSize = std::exchange(other.uploadReceivedSize, 0);
            uploadFilePath = std::move(other.uploadFilePath);
            processingZipPath = std::move(other.processingZipPath);
            currentDir = std::move(other.currentDir);
            pendingRefresh = std::exchange(other.pendingRefresh, false);
            writeBuffer = std::move(other.writeBuffer);
        }
        return *this;
    }

    // ----- DISABLE COPY -----
    ClientSession(const ClientSession&) = delete;
    ClientSession& operator=(const ClientSession&) = delete;
};

// ==================== SESSION CONTEXT ====================
struct SessionContext {
    // ===== SESSION =====
    std::shared_ptr<ClientSession> activeSession;
    std::recursive_mutex sessionsMutex;

    // ===== SERVER STATE =====
    std::atomic<bool> serverRunning{ false };
    std::atomic<bool> needPassword{ false };
    std::atomic<bool> needBuy{ false };

    // ===== PORTS =====
    int udpPort = 2027;
    int tcpPort = 2028;

    // ===== CONFIG =====
    std::string passwordMd5;
    std::string btUuid;

    // ============================================================
    // SESSION MANAGEMENT
    // ============================================================
    std::shared_ptr<ClientSession> replaceSession(
        SOCKET newSocket,
        TransportType type,
        const std::string& clientInfo,
        std::shared_ptr<SessionHandler>& handler);

    void removeCurrentSession(std::shared_ptr<SessionHandler>& handler);
    void cleanupAllSessions();

    std::shared_ptr<ClientSession> getActiveSession() {
        std::lock_guard<std::recursive_mutex> lock(sessionsMutex);
        return activeSession;
    }

    void setActiveSession(std::shared_ptr<ClientSession> session) {
        std::lock_guard<std::recursive_mutex> lock(sessionsMutex);
        activeSession = session;
    }
};

// ==================== GLOBAL ====================
extern SessionContext g_state;

// ============================================================
// HELPER FUNCTIONS
// ============================================================
std::string getRealClientAddress(SOCKET socket);
std::shared_ptr<ClientSession> findSessionBySocket(SOCKET socket);
bool isSessionAlive(SOCKET socket);
SessionRole getSessionRole(SOCKET socket);
TransportType getSessionTransport(SOCKET socket);
bool hasActiveScreenStream();
int countActiveSessionsByRole(SessionRole role);
std::vector<SOCKET> getSocketsByRole(SessionRole role);
void forceCleanupSession(SOCKET socket);
bool isScreenStreamSession(SOCKET socket);