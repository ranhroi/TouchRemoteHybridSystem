#pragma once
#include "commontypes.h"
#include "logmanager.h"

struct ClientSession;

class SmartSenderBinary {
public:
    explicit SmartSenderBinary(SOCKET socket, std::shared_ptr<ClientSession> session = nullptr);
    ~SmartSenderBinary();

    // Disable copy and move
    SmartSenderBinary(const SmartSenderBinary&) = delete;
    SmartSenderBinary& operator=(const SmartSenderBinary&) = delete;
    SmartSenderBinary(SmartSenderBinary&&) = delete;
    SmartSenderBinary& operator=(SmartSenderBinary&&) = delete;

    // ===== GỬI TEXT/COMMAND (GỬI NGAY, ƯU TIÊN CAO NHẤT) =====
    void sendText(const std::vector<uint8_t>& data);
    void sendText(const std::string& data);
    void sendText(const char* data);

    // ===== GỬI FILE DATA (QUA QUEUE, CÓ RETRY) =====
    void sendFileData(const std::vector<uint8_t>& data);
    void sendRaw(const std::vector<uint8_t>& data);

    // ===== GỬI FILE =====
    void sendFile(const std::string& filePath);
    void sendFile(std::ifstream* file);

    // ===== RESUME =====
    void setResumeOffset(int64_t offset);
    int64_t getResumeOffset() const;

    // ===== ĐIỀU KHIỂN =====
    void stop();
    void pause();
    void resume();
    void remove();
    void cancel();
    void cancelImmediately();

    // ===== CẤU HÌNH CHUNG =====
    void setMaxFileSize(int64_t maxSize);
    void setTimeout(int seconds);
    void setChunkSize(int chunkSize);
    void setAutoCleanup(bool autoCleanup);

    // ===== CẤU HÌNH FILE QUEUE =====
    void setMaxFileQueueSize(size_t maxSize);
    void setMaxFileQueueMemory(size_t maxBytes);
    void setFileRetries(int retries);

    // ===== KIỂM TRA =====
    bool isSending() const;
    bool isPaused() const { return m_isPaused.load(std::memory_order_acquire); }
    bool isCancelled() const { return m_cancelRequested.load(std::memory_order_acquire); }
    int64_t progress() const { return m_filePos.load(std::memory_order_acquire); }
    int64_t total() const { return m_totalBytes.load(std::memory_order_acquire); }
    std::string currentFileName() const;
    SOCKET getSocket() const { return m_socket; }

    size_t getFileQueueSize() const;

    // ===== CALLBACKS =====
    // File transfer callbacks
    std::function<void(int64_t sent, int64_t total)> onFileProgress;
    std::function<void()> onFileFinished;
    std::function<void(const std::string& message)> onFileError;
    std::function<void(const std::string& fileName, int64_t fileSize)> onFileStarted;

    // Common callbacks
    std::function<void()> onTransferPaused;
    std::function<void()> onTransferResumed;

private:
    // ===== CORE PROCESSING =====
    void process();
    void processFileQueue();
    void processFileTransfer();

    // ===== SOCKET OPERATIONS =====
    bool sendToSocket(const uint8_t* data, size_t len);
    bool sendToSocket(const std::vector<uint8_t>& data);

    // ===== SEND WITH RETRY =====
    bool sendTextDirect(const std::vector<uint8_t>& data);
    bool sendFileWithRetry(const std::vector<uint8_t>& data, int retryCount = 0);

    // ===== QUEUE MANAGEMENT =====
    bool canAcceptFileData(size_t dataSize);

    // ===== HELPERS =====
    void resetTimeout();
    bool isTimeoutExpired() const;
    void sendBinaryHeader();
    void cleanup();
    void autoCleanupIfNeeded();
    void resetTransferState();
    double calculateSpeed(int64_t bytesSent);
    std::string formatBytes(int64_t bytes);

    bool isFileQueueEmpty() const;
    bool isFileOpen() const;
    bool isSocketValid() const;
    void setProcessing(bool processing);
    bool tryStartProcessing();

private:
    // ===== SOCKET =====
    SOCKET m_socket;
    std::shared_ptr<ClientSession> m_session;
    mutable std::mutex m_socketMutex;

    // ===== FILE TRANSFER =====
    std::unique_ptr<std::ifstream> m_file;
    std::atomic<int64_t> m_filePos{ 0 };
    std::atomic<int64_t> m_totalBytes{ 0 };
    std::atomic<int64_t> m_resumeOffset{ 0 };

    // ===== FILE DATA QUEUE (Dành cho file chunks) =====
    std::deque<std::vector<uint8_t>> m_fileQueue;
    size_t m_currentFileQueueMemory{ 0 };
    size_t m_maxFileQueueSize{ 50 };
    size_t m_maxFileQueueMemory{ 50 * 1024 * 1024 };  // 50MB
    int m_fileRetries{ 3 };

    // ===== CONTROL FLAGS =====
    std::atomic<bool> m_cancelRequested{ false };
    std::atomic<bool> m_isPaused{ false };
    std::atomic<bool> m_isProcessing{ false };
    std::atomic<bool> m_isSendingFile{ false };

    // ===== SETTINGS =====
    std::atomic<int64_t> m_maxFileSize{ 10LL * 1024 * 1024 * 1024 };
    std::atomic<int> m_timeoutSeconds{ 300 };
    std::atomic<int> m_chunkSize{ 1024 * 1024 };
    bool m_autoCleanup{ true };

    // ===== TIMING =====
    std::chrono::steady_clock::time_point m_lastActivityTime;
    std::chrono::steady_clock::time_point m_transferStartTime;
    bool m_cleanupDone{ false };

    // ===== SPEED TRACKING =====
    std::chrono::steady_clock::time_point m_lastSpeedTime;
    int64_t m_lastSpeedBytes{ 0 };

    // ===== MUTEX =====
    mutable std::mutex m_mutex;
};

// ==================== GLOBAL WRAPPER ====================
namespace SenderBinary {

    void init();
    void cleanup();
    void remove(SOCKET socket);

    // ===== GỬI TEXT/COMMAND (ƯU TIÊN CAO NHẤT) =====
    bool send(SOCKET socket, const std::vector<uint8_t>& data);
    bool send(SOCKET socket, const std::string& data);
    bool send(SOCKET socket, const char* data);

    // ===== GỬI FILE DATA =====
    bool sendFileData(SOCKET socket, const std::vector<uint8_t>& data);

    // ===== GỬI RAW (KHÔNG QUA QUEUE) =====
    bool sendRaw(SOCKET socket, const std::vector<uint8_t>& data);

    // ===== GỬI FILE =====
    bool sendFile(SOCKET socket, const std::string& filePath);
    bool sendFile(SOCKET socket, std::ifstream* file);

    // ===== RESUME =====
    void setResumeOffset(SOCKET socket, int64_t offset);
    int64_t getResumeOffset(SOCKET socket);

    // ===== ĐIỀU KHIỂN =====
    void stop(SOCKET socket);
    void cancel(SOCKET socket);
    void cancelImmediately(SOCKET socket);
    void pause(SOCKET socket);
    void resume(SOCKET socket);

    // ===== CẤU HÌNH =====
    void setMaxFileSize(SOCKET socket, int64_t maxSize);
    void setTimeout(SOCKET socket, int seconds);
    void setChunkSize(SOCKET socket, int chunkSize);
    void setAutoCleanup(SOCKET socket, bool autoCleanup);

    void setMaxFileQueueSize(SOCKET socket, size_t maxSize);
    void setMaxFileQueueMemory(SOCKET socket, size_t maxBytes);
    void setFileRetries(SOCKET socket, int retries);

    // ===== KIỂM TRA =====
    bool isSending(SOCKET socket);
    bool isPaused(SOCKET socket);
    bool isCancelled(SOCKET socket);
    int64_t progress(SOCKET socket);
    int64_t total(SOCKET socket);
    std::string currentFileName(SOCKET socket);
    size_t getFileQueueSize(SOCKET socket);

    // ===== CALLBACKS =====
    void setFileCallbacks(SOCKET socket,
        std::function<void(int64_t, int64_t)> onProgress = nullptr,
        std::function<void()> onFinished = nullptr,
        std::function<void(const std::string&)> onError = nullptr,
        std::function<void(const std::string&, int64_t)> onStarted = nullptr);

    SmartSenderBinary* getSender(SOCKET socket);
}