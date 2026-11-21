#pragma once

#include <avrt.h>          // ✅ Cho MMCSS
#pragma comment(lib, "avrt.lib")

struct ClientSession;

class SessionHandler {
public:
    SessionHandler() = default;
    ~SessionHandler();

    void start(std::shared_ptr<ClientSession> session);
    void stop();
    bool isRunning() const;

private:
    // ==================== HẰNG SỐ ====================
    static constexpr size_t MAX_READ_BUFFER = 1024 * 1024;   // 1MB
    static constexpr int    SEND_TIMEOUT_MS = 500;

    // ==================== GỬI ====================
    bool sendDataDirect(SOCKET socket, const std::string& data, int timeoutMs);

    // ==================== MAIN LOOP ====================
    void run();

    // ==================== HANDSHAKE ====================
    void handleHandshake(std::shared_ptr<ClientSession> session);

    // ==================== AUTH ====================
    void handleAuth(std::shared_ptr<ClientSession> session);

    // ==================== READ DATA ====================
    bool readData(std::shared_ptr<ClientSession> session);

    // ==================== PROCESS DATA ====================
    void processData(std::shared_ptr<ClientSession> session);

    // ==================== UPLOAD DATA ====================
    void handleUploadData(std::shared_ptr<ClientSession> session, const uint8_t* data, int size);

    // ==================== FINISH UPLOAD ====================
    void finishUpload(std::shared_ptr<ClientSession> session);

    // ==================== CHECK SESSION HEALTH ====================
    bool checkSessionHealth(std::shared_ptr<ClientSession> session);

private:
    std::shared_ptr<ClientSession> m_session;
    std::thread                    m_thread;
    std::atomic<bool>              m_running{ false };

    std::mutex           m_dataMutex;
    std::vector<uint8_t> m_readBuffer;

    std::string m_authBuffer;

    // ✅ MMCSS handle — để cleanup đúng cách
    HANDLE m_mmcssHandle = nullptr;
    DWORD  m_mmcssTaskIndex = 0;
};