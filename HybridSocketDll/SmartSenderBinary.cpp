#include "pch.h"
#include "smartsenderbinary.h"
#include "sessioncontext.h"

using namespace std::chrono_literals;

// ==================== CONSTRUCTOR / DESTRUCTOR ====================

SmartSenderBinary::SmartSenderBinary(SOCKET socket, std::shared_ptr<ClientSession> session)
    : m_socket(socket)
    , m_session(session)
    , m_lastActivityTime(std::chrono::steady_clock::now())
    , m_transferStartTime(std::chrono::steady_clock::now())
    , m_lastSpeedTime(std::chrono::steady_clock::now())
    , m_lastSpeedBytes(0)
{
    if (m_session) {
        m_chunkSize.store(m_session->getChunkSize(), std::memory_order_release);
        m_timeoutSeconds.store(m_session->getSendTimeout(), std::memory_order_release);
        m_maxFileSize.store(m_session->getMaxAllowedSize(), std::memory_order_release);

        // Tùy chỉnh cho từng loại transport
        if (m_session->isBluetooth()) {
            m_maxFileQueueSize = 20;
            m_maxFileQueueMemory = 20 * 1024 * 1024;
            m_fileRetries = 2;
            m_timeoutSeconds.store(TimeoutConfig::BLUETOOTH_SEND_TIMEOUT);
        }
        else {
            // TCP/WiFi
            m_maxFileQueueSize = 50;
            m_maxFileQueueMemory = 50 * 1024 * 1024;
            m_fileRetries = 3;
            m_timeoutSeconds.store(TimeoutConfig::TCP_SEND_TIMEOUT);
        }

        std::string transportType = m_session->isBluetooth() ? "Bluetooth" : "TCP/WiFi";
        LOG_DEBUG("[SmartSenderBinary] Using %s settings: " +
            "ChunkSize=%d, Timeout=%d, MaxFileSize=%d, FileQueue=%d",
            transportType.c_str(),
            m_chunkSize.load(),
            m_timeoutSeconds.load(),
            m_maxFileSize.load(),
            m_maxFileQueueSize);
    }
}

SmartSenderBinary::~SmartSenderBinary() {
    LOG_DEBUG("[SmartSenderBinary] Destructor called for socket: %d", m_socket);
    stop();
}

// ==================== PRIVATE HELPERS ====================

bool SmartSenderBinary::isSocketValid() const {
    std::lock_guard<std::mutex> lock(m_socketMutex);
    return m_socket && m_socket != INVALID_SOCKET;
}

// ==================== SEND TO SOCKET ====================

bool SmartSenderBinary::sendToSocket(const uint8_t* data, size_t len) {
    if (!isSocketValid()) return false;

    size_t totalSent = 0;
    while (totalSent < len) {
        int sent = ::send(m_socket,
            reinterpret_cast<const char*>(data + totalSent),
            static_cast<int>(len - totalSent),
            0);

        if (sent == SOCKET_ERROR) {
            int error = WSAGetLastError();
            if (error == WSAEWOULDBLOCK) {
                if (m_session && m_session->isBluetooth()) {
                    int bufferSize = 0;
                    socklen_t optLen = sizeof(bufferSize);
                    getsockopt(m_socket, SOL_SOCKET, SO_SNDBUF, (char*)&bufferSize, &optLen);

                    if (bufferSize > TimeoutConfig::BLUETOOTH_BUFFER_THRESHOLD) {
                        std::this_thread::sleep_for(std::chrono::milliseconds(TimeoutConfig::BUFFER_FULL_WAIT_MS));
                        continue;
                    }
                }
                std::this_thread::sleep_for(10ms);
                continue;
            }
            LOG_ERROR("[SmartSenderBinary] send() failed with error: %d", error);
            return false;
        }

        if (sent == 0) {
            LOG_ERROR("[SmartSenderBinary] Connection closed by peer");
            return false;
        }

        totalSent += sent;
    }

    resetTimeout();
    return true;
}

bool SmartSenderBinary::sendToSocket(const std::vector<uint8_t>& data) {
    return sendToSocket(data.data(), data.size());
}

// ==================== SEND TEXT (GỬI NGAY, ƯU TIÊN CAO NHẤT) ====================

void SmartSenderBinary::sendText(const std::vector<uint8_t>& data) {
    if (!isSocketValid()) {
        if (onFileError) onFileError("Socket not open");
        return;
    }
    if (data.empty()) return;

    if (m_session) {
        m_session->updateActivity();
    }

    // Text gửi ngay, retry 3 lần
    sendTextDirect(data);
}

void SmartSenderBinary::sendText(const std::string& data) {
    std::vector<uint8_t> vec(data.begin(), data.end());
    sendText(vec);
}

void SmartSenderBinary::sendText(const char* data) {
    if (data) sendText(std::string(data));
}

bool SmartSenderBinary::sendTextDirect(const std::vector<uint8_t>& data) {
    if (!isSocketValid()) return false;

    for (int attempt = 0; attempt < 3; attempt++) {
        if (attempt > 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10 * attempt));
        }

        if (sendToSocket(data)) {
            return true;
        }

        if (!isSocketValid()) {
            return false;
        }
    }

    LOG_DEBUG("[SmartSenderBinary] Text send failed after 3 attempts");
    return false;
}

// ==================== SEND FILE DATA ====================

void SmartSenderBinary::sendFileData(const std::vector<uint8_t>& data) {
    if (!isSocketValid()) {
        if (onFileError) onFileError("Socket not open");
        return;
    }
    if (data.empty()) return;

    if (!canAcceptFileData(data.size())) {
        std::string err = "File queue limit exceeded";
        LOG_ERROR("[SmartSenderBinary] %s", err.c_str());
        if (onFileError) onFileError(err);
        return;
    }

    if (m_session) {
        m_session->updateActivity();
    }

    {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_fileQueue.emplace_back(data);
        m_currentFileQueueMemory += data.size();
    }

    if (tryStartProcessing()) {
        process();
    }
}


// ==================== SEND RAW (KHÔNG QUA QUEUE) ====================

void SmartSenderBinary::sendRaw(const std::vector<uint8_t>& data) {
    if (!isSocketValid()) return;
    if (data.empty()) return;

    if (!sendToSocket(data)) {
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
        sendToSocket(data);
    }
}

// ==================== SEND FILE ====================

void SmartSenderBinary::sendFile(const std::string& filePath) {
    auto file = std::make_unique<std::ifstream>(filePath, std::ios::binary);
    if (!file->is_open()) {
        LOG_ERROR("[SmartSenderBinary] Cannot open file: %s", filePath.c_str());
        if (onFileError) onFileError("Cannot open file: " + filePath);
        return;
    }

    sendFile(file.release());
}

void SmartSenderBinary::sendFile(std::ifstream* file) {
    if (!file || !file->is_open()) {
        LOG_ERROR("[SmartSenderBinary] File not open");
        if (onFileError) onFileError("File not open");
        if (file) delete file;
        return;
    }

    file->seekg(0, std::ios::end);
    int64_t fileSize = static_cast<int64_t>(file->tellg());
    file->seekg(0, std::ios::beg);

    if (m_session && !m_session->isFileSizeAllowed(static_cast<uint64_t>(fileSize))) {
        uint64_t maxSize = m_session->getMaxAllowedSize();
        std::string oss = "File too large for " + std::string(m_session->isBluetooth() ? "Bluetooth" : "TCP/WiFi") + ": " + formatBytes(fileSize) + " (max: " + formatBytes(maxSize) + ")";
        LOG_ERROR("[SmartSenderBinary] %s", oss.c_str());
        if (onFileError) onFileError(oss);
        file->close();
        delete file;
        return;
    }

    int64_t maxSize = m_maxFileSize.load(std::memory_order_acquire);
    if (fileSize > maxSize) {
        std::string oss = "File too large: " + formatBytes(fileSize) + " (max: " + formatBytes(maxSize) + ")";
        LOG_ERROR("[SmartSenderBinary] %s", oss.c_str());
        if (onFileError) onFileError("File too large");
        file->close();
        delete file;
        return;
    }

    cleanup();
    resetTransferState();

    int64_t resumeOffset = m_resumeOffset.load(std::memory_order_acquire);
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_file.reset(file);
        m_filePos.store(resumeOffset, std::memory_order_release);
        m_totalBytes.store(fileSize, std::memory_order_release);
    }

    m_cancelRequested.store(false, std::memory_order_release);
    m_isPaused.store(false, std::memory_order_release);
    m_isSendingFile.store(true, std::memory_order_release);

    if (m_session) {
        m_session->isLongTransfer = true;
    }

    if (resumeOffset > 0 && resumeOffset < fileSize) {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_file->seekg(resumeOffset, std::ios::beg);
        LOG_DEBUG("[SmartSenderBinary] Resuming from offset: %s", formatBytes(resumeOffset).c_str());
    }

    sendBinaryHeader();

    if (onFileStarted) onFileStarted("", fileSize);
    LOG_DEBUG("[SmartSenderBinary] Start sending file: %s", formatBytes(fileSize).c_str());

    resetTimeout();

    if (tryStartProcessing()) {
        process();
    }
}

// ==================== SEND BINARY HEADER ====================

void SmartSenderBinary::sendBinaryHeader() {
    if (!isSocketValid()) return;

    int64_t size = m_totalBytes.load(std::memory_order_acquire);
    uint8_t header[8];
    for (int i = 0; i < 8; ++i) {
        header[i] = static_cast<uint8_t>((size >> (i * 8)) & 0xFF);
    }

    if (!sendToSocket(header, 8)) {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_fileQueue.emplace_front(std::vector<uint8_t>(header, header + 8));
        m_currentFileQueueMemory += 8;
    }
}

// ==================== FILE WITH RETRY ====================
bool SmartSenderBinary::sendFileWithRetry(const std::vector<uint8_t>& data, int retryCount) {
    if (retryCount >= m_fileRetries) {
        LOG_DEBUG("[SmartSenderBinary] File retry limit reached");
        return false;
    }

    LOG_DEBUG("[SmartSenderBinary::sendFileWithRetry] Sending %zu bytes, attempt %d", data.size(), retryCount);

    int baseDelay = (m_session && m_session->isBluetooth()) ? 150 : 50;

    for (int attempt = 0; attempt <= retryCount; attempt++) {
        if (attempt > 0) {
            int delay = baseDelay * (attempt);
            std::this_thread::sleep_for(std::chrono::milliseconds(delay));
        }

        if (sendToSocket(data)) {
            LOG_DEBUG("[SmartSenderBinary::sendFileWithRetry] Send successful");
            return true;
        }

        LOG_DEBUG("[SmartSenderBinary::sendFileWithRetry] Send failed, attempt %d", attempt + 1);

        if (!isSocketValid()) {
            return false;
        }
    }

    return false;
}

// ==================== QUEUE MANAGEMENT ====================

bool SmartSenderBinary::canAcceptFileData(size_t dataSize) {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (m_fileQueue.size() >= m_maxFileQueueSize) {
        LOG_DEBUG("[SmartSenderBinary] File queue size limit reached");
        return false;
    }

    if (m_currentFileQueueMemory + dataSize > m_maxFileQueueMemory) {
        LOG_DEBUG("[SmartSenderBinary] File queue memory limit reached");
        return false;
    }

    return true;
}


// ==================== TIMEOUT ====================

void SmartSenderBinary::resetTimeout() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_lastActivityTime = std::chrono::steady_clock::now();

    if (m_session) {
        m_session->updateActivity();
    }
}

bool SmartSenderBinary::isTimeoutExpired() const {
    int timeout = m_timeoutSeconds.load(std::memory_order_acquire);
    if (timeout <= 0) return false;

    if (m_session && m_session->isLongTransfer) {
        timeout = m_session->getTimeout();
    }

    std::lock_guard<std::mutex> lock(m_mutex);
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - m_lastActivityTime);
    return elapsed.count() >= timeout;
}

// ==================== CLEANUP ====================

void SmartSenderBinary::cleanup() {
    std::lock_guard<std::mutex> lock(m_mutex);

    m_cancelRequested.store(true, std::memory_order_release);
    m_fileQueue.clear();
    m_currentFileQueueMemory = 0;

    if (m_file) {
        m_file->close();
        m_file.reset();
    }

    m_isProcessing.store(false, std::memory_order_release);
    m_isSendingFile.store(false, std::memory_order_release);
}

void SmartSenderBinary::resetTransferState() {
    std::lock_guard<std::mutex> lock(m_mutex);

    m_transferStartTime = std::chrono::steady_clock::now();
    m_cleanupDone = false;
    m_fileQueue.clear();
    m_currentFileQueueMemory = 0;

    if (m_file) {
        m_file->close();
        m_file.reset();
    }

    m_filePos.store(0, std::memory_order_release);
    m_totalBytes.store(0, std::memory_order_release);
    m_isSendingFile.store(false, std::memory_order_release);

    LOG_DEBUG("[SmartSenderBinary] Transfer state reset");
}

void SmartSenderBinary::autoCleanupIfNeeded() {
    if (!m_autoCleanup) return;

    std::lock_guard<std::mutex> lock(m_mutex);

    bool isTransferComplete = !m_file && m_fileQueue.empty() && !m_isSendingFile.load();

    if (isTransferComplete && !m_cleanupDone) {
        auto duration = std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::steady_clock::now() - m_transferStartTime);

        LOG_DEBUG("[SmartSenderBinary] Transfer completed in %ld seconds", duration.count());

        m_filePos.store(0, std::memory_order_release);
        m_totalBytes.store(0, std::memory_order_release);
        m_resumeOffset.store(0, std::memory_order_release);
        m_isSendingFile.store(false, std::memory_order_release);
        m_cancelRequested.store(false, std::memory_order_release);
        m_isPaused.store(false, std::memory_order_release);
        m_currentFileQueueMemory = 0;
        m_cleanupDone = true;

        LOG_DEBUG("[SmartSenderBinary] Auto cleanup completed");
    }
}

// ==================== SPEED ====================

double SmartSenderBinary::calculateSpeed(int64_t bytesSent) {
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - m_lastSpeedTime);

    double speed = 0;
    if (elapsed.count() > 0) {
        int64_t bytesDiff = bytesSent - m_lastSpeedBytes;
        speed = (bytesDiff / 1024.0 / 1024.0) / (elapsed.count() / 1000.0);
    }

    if (elapsed.count() >= 1000) {
        m_lastSpeedTime = now;
        m_lastSpeedBytes = bytesSent;
    }

    return speed;
}

std::string SmartSenderBinary::formatBytes(int64_t bytes) {
    const char* units[] = { "B", "KB", "MB", "GB", "TB" };
    int unitIndex = 0;
    double size = static_cast<double>(bytes);

    while (size >= 1024.0 && unitIndex < 4) {
        size /= 1024.0;
        unitIndex++;
    }

    std::ostringstream oss;
    oss << std::fixed << std::setprecision(2) << size << " " << units[unitIndex];
    return oss.str();
}

// ==================== CONFIGURATION ====================

void SmartSenderBinary::setMaxFileSize(int64_t maxSize) {
    m_maxFileSize.store(maxSize, std::memory_order_release);
    LOG_DEBUG("[SmartSenderBinary] Max file size set to: %s", formatBytes(maxSize).c_str());
}

void SmartSenderBinary::setTimeout(int seconds) {
    m_timeoutSeconds.store(seconds, std::memory_order_release);
    LOG_DEBUG("[SmartSenderBinary] Timeout set to: %d seconds", seconds);
}

void SmartSenderBinary::setChunkSize(int chunkSize) {
    if (chunkSize >= 1024 && chunkSize <= 32 * 1024 * 1024) {
        m_chunkSize.store(chunkSize, std::memory_order_release);
        LOG_DEBUG("[SmartSenderBinary] Chunk size set to: %s", formatBytes(chunkSize).c_str());
    }
}

void SmartSenderBinary::setAutoCleanup(bool autoCleanup) {
    m_autoCleanup = autoCleanup;
    LOG_DEBUG("[SmartSenderBinary] Auto cleanup: %s", autoCleanup ? "ON" : "OFF");
}

void SmartSenderBinary::setMaxFileQueueSize(size_t maxSize) {
    m_maxFileQueueSize = maxSize;
    LOG_DEBUG("[SmartSenderBinary] Max file queue size: %zu", maxSize);
}

void SmartSenderBinary::setMaxFileQueueMemory(size_t maxBytes) {
    m_maxFileQueueMemory = maxBytes;
    LOG_DEBUG("[SmartSenderBinary] Max file queue memory: %s", formatBytes(maxBytes).c_str());
}

void SmartSenderBinary::setFileRetries(int retries) {
    if (retries >= 0 && retries <= 10) {
        m_fileRetries = retries;
        LOG_DEBUG("[SmartSenderBinary] File retries: %d", retries);
    }
}


void SmartSenderBinary::setResumeOffset(int64_t offset) {
    if (offset >= 0) {
        m_resumeOffset.store(offset, std::memory_order_release);
        LOG_DEBUG("[SmartSenderBinary] Resume offset set to: %s", formatBytes(offset).c_str());
    }
}

int64_t SmartSenderBinary::getResumeOffset() const {
    return m_resumeOffset.load(std::memory_order_acquire);
}

// ==================== PROCESS ====================
void SmartSenderBinary::process() {
    LOG_DEBUG("[SmartSenderBinary::process] Start processing");

    // ===== 1. KIỂM TRA TRẠNG THÁI =====
    if (m_cancelRequested.load(std::memory_order_acquire)) {
        LOG_DEBUG("[SmartSenderBinary::process] Cancelled");
        cleanup();
        m_isProcessing.store(false, std::memory_order_release);
        autoCleanupIfNeeded();
        return;
    }

    if (m_isPaused.load(std::memory_order_acquire)) {
        LOG_DEBUG("[SmartSenderBinary::process] Paused");
        m_isProcessing.store(false, std::memory_order_release);
        return;
    }

    if (!isSocketValid()) {
        LOG_DEBUG("[SmartSenderBinary::process] Socket invalid");
        cleanup();
        if (m_session) m_session->isLongTransfer = false;
        m_isProcessing.store(false, std::memory_order_release);
        autoCleanupIfNeeded();
        return;
    }

    if (isTimeoutExpired()) {
        LOG_DEBUG("[SmartSenderBinary::process] Timeout expired");
        cleanup();
        if (m_session) m_session->isLongTransfer = false;
        if (onFileError) onFileError("Transfer timeout");
        m_isProcessing.store(false, std::memory_order_release);
        autoCleanupIfNeeded();
        return;
    }

    if (m_session) {
        m_session->updateActivity();
    }
    resetTimeout();

    // ===== 2. KIỂM TRA FILE TRANSFER =====
    bool hasFile = false;
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        hasFile = m_file && m_file->is_open() && !m_file->eof();
    }

    if (!hasFile && m_fileQueue.empty()) {
        LOG_DEBUG("[SmartSenderBinary::process] No data to send");
        m_isProcessing.store(false, std::memory_order_release);
        return;
    }

    // ===== 3. VÒNG LẶP XỬ LÝ =====
    int loopCount = 0;
    const int MAX_LOOP = 10000;

    while (!m_cancelRequested.load(std::memory_order_acquire) &&
        !m_isPaused.load(std::memory_order_acquire)) {

        bool hasProgress = false;

        if (isTimeoutExpired() || !isSocketValid()) {
            LOG_DEBUG("[SmartSenderBinary::process] Timeout or socket lost");
            break;
        }

        // ===== 3.2 FILE QUEUE =====
        {
            std::unique_lock<std::mutex> lock(m_mutex);

            while (!m_fileQueue.empty() &&
                !m_cancelRequested.load(std::memory_order_acquire) &&
                !m_isPaused.load(std::memory_order_acquire)) {

                auto& data = m_fileQueue.front();
                size_t dataSize = data.size();

                lock.unlock();
                bool success = sendFileWithRetry(data);
                lock.lock();

                if (!success) {
                    LOG_DEBUG("[SmartSenderBinary] File data send failed");
                    if (onFileError) onFileError("File send error");
                    m_fileQueue.clear();
                    m_currentFileQueueMemory = 0;
                    cleanup();
                    m_isProcessing.store(false, std::memory_order_release);
                    return;
                }

                m_fileQueue.pop_front();
                m_currentFileQueueMemory -= dataSize;
                hasProgress = true;
                resetTimeout();
            }
        }

        // ===== 3.3 FILE TRANSFER (ifstream) =====
        {
            bool fileProgress = false;
            processFileTransfer();

            // Kiểm tra xem có tiến triển không
            std::lock_guard<std::mutex> lock(m_mutex);
            if (m_file && m_file->is_open() && !m_file->eof()) {
                fileProgress = true;
            }
            hasProgress = hasProgress || fileProgress;
        }

        // ===== 3.4 KIỂM TRA HOÀN TẤT =====
        bool allDone = false;
        {
            std::lock_guard<std::mutex> lock(m_mutex);
            allDone = (!m_file || !m_file->is_open() || m_file->eof()) && m_fileQueue.empty();
        }

        if (allDone) {
            LOG_DEBUG("[SmartSenderBinary::process] All data sent, finishing");
            break;
        }

        if (!hasProgress) {
            LOG_DEBUG("[SmartSenderBinary::process] No progress, breaking loop");
            break;
        }

        loopCount++;
        if (loopCount > MAX_LOOP) {
            LOG_DEBUG("[SmartSenderBinary::process] Max loop reached");
            break;
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }

    // ===== 4. DỌN DẸP =====
    if (m_session && m_isSendingFile.load(std::memory_order_acquire)) {
        m_session->isLongTransfer = false;
    }

    m_isProcessing.store(false, std::memory_order_release);
    autoCleanupIfNeeded();

    LOG_DEBUG("[SmartSenderBinary::process] Processing finished");
}

// ==================== PROCESS FILE TRANSFER ====================

void SmartSenderBinary::processFileTransfer() {
    std::unique_lock<std::mutex> lock(m_mutex);

    if (!m_file || !m_file->is_open()) {
        return;
    }

    if (m_file->eof()) {
        LOG_DEBUG("[SmartSenderBinary::processFileTransfer] File EOF reached");
        return;
    }

    int64_t filePos = m_filePos.load(std::memory_order_acquire);
    int64_t totalBytes = m_totalBytes.load(std::memory_order_acquire);

    LOG_DEBUG("[SmartSenderBinary::processFileTransfer] FilePos: %ld, Total: %ld", filePos, totalBytes);

    if (filePos >= totalBytes) {
        lock.unlock();

        LOG_DEBUG("[SmartSenderBinary] File sent successfully: %s/%s", formatBytes(filePos).c_str(), formatBytes(totalBytes).c_str());

        if (onFileProgress) onFileProgress(filePos, totalBytes);
        if (onFileFinished) onFileFinished();

        {
            std::lock_guard<std::mutex> l(m_mutex);
            if (m_file) {
                m_file->close();
                m_file.reset();
            }
            m_filePos.store(0, std::memory_order_release);
            m_totalBytes.store(0, std::memory_order_release);
            m_isSendingFile.store(false, std::memory_order_release);
            m_currentFileQueueMemory = 0;
        }

        if (m_session) m_session->isLongTransfer = false;
        autoCleanupIfNeeded();
        return;
    }

    int chunkSize = m_chunkSize.load(std::memory_order_acquire);
    int64_t remaining = totalBytes - filePos;
    int toRead = static_cast<int>(std::min<int64_t>(chunkSize, remaining));

    LOG_DEBUG("[SmartSenderBinary::processFileTransfer] Reading chunk: %d bytes", toRead);

    std::vector<uint8_t> chunk(toRead);
    m_file->read(reinterpret_cast<char*>(chunk.data()), toRead);
    std::streamsize actuallyRead = m_file->gcount();

    if (actuallyRead <= 0) {
        LOG_DEBUG("[SmartSenderBinary] Read file error at position: %s", formatBytes(filePos).c_str());
        lock.unlock();
        if (onFileError) onFileError("Read file error");
        cleanup();
        if (m_session) m_session->isLongTransfer = false;
        m_isProcessing.store(false, std::memory_order_release);
        return;
    }

    chunk.resize(static_cast<size_t>(actuallyRead));
    LOG_DEBUG("[SmartSenderBinary::processFileTransfer] Actually read: %ld bytes", actuallyRead);

    lock.unlock();

    if (!sendFileWithRetry(chunk)) {
        LOG_DEBUG("[SmartSenderBinary] Send file error after retries");
        if (onFileError) onFileError("Send file error");
        cleanup();
        if (m_session) m_session->isLongTransfer = false;
        m_isProcessing.store(false, std::memory_order_release);
        return;
    }

    int64_t newPos = filePos + static_cast<int64_t>(chunk.size());
    m_filePos.store(newPos, std::memory_order_release);

    if (m_session) {
        m_session->updateProgress(newPos, totalBytes);
    }

    // Report progress
    static int64_t lastReportPos = 0;
    if (newPos - lastReportPos >= 10 * 1024 * 1024 || newPos == totalBytes) {
        lastReportPos = newPos;
        int percent = (totalBytes > 0) ? static_cast<int>((newPos * 100) / totalBytes) : 0;
        double speedMBps = calculateSpeed(newPos);

        LOG_DEBUG("[SmartSenderBinary] File Progress: %d%% (%s/%s) - Speed: %.2f MB/s", percent, formatBytes(newPos).c_str(), formatBytes(totalBytes).c_str(), speedMBps);

        if (onFileProgress) onFileProgress(newPos, totalBytes);
    }
}

// ==================== CHECK ====================

bool SmartSenderBinary::isSending() const {
    if (m_isProcessing.load(std::memory_order_acquire)) return true;
    if (m_isSendingFile.load(std::memory_order_acquire)) return true;
    if (!isFileQueueEmpty()) return true;
    if (isFileOpen()) return true;
    return false;
}

bool SmartSenderBinary::isFileQueueEmpty() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_fileQueue.empty();
}

bool SmartSenderBinary::isFileOpen() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_file && m_file->is_open();
}

size_t SmartSenderBinary::getFileQueueSize() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_fileQueue.size();
}

void SmartSenderBinary::setProcessing(bool processing) {
    m_isProcessing.store(processing, std::memory_order_release);
}

bool SmartSenderBinary::tryStartProcessing() {
    bool expected = false;
    return m_isProcessing.compare_exchange_strong(expected, true, std::memory_order_acq_rel);
}

std::string SmartSenderBinary::currentFileName() const {
    return std::string();
}

// ==================== CONTROL ====================

void SmartSenderBinary::stop() {
    resetTransferState();

    if (m_session) {
        m_session->isLongTransfer = false;
        m_session->updateActivity();
    }

    autoCleanupIfNeeded();
    LOG_DEBUG("[SmartSenderBinary] Stop completed");
}

void SmartSenderBinary::pause() {
    if (!m_isPaused.load(std::memory_order_acquire) &&
        (isFileOpen() || !isFileQueueEmpty())) {
        m_isPaused.store(true, std::memory_order_release);
        if (onTransferPaused) onTransferPaused();
        LOG_DEBUG("[SmartSenderBinary] Transfer paused");
    }
}

void SmartSenderBinary::resume() {
    if (m_isPaused.load(std::memory_order_acquire)) {
        m_isPaused.store(false, std::memory_order_release);
        resetTimeout();
        if (onTransferResumed) onTransferResumed();

        if (tryStartProcessing()) {
            process();
        }
        LOG_DEBUG("[SmartSenderBinary] Transfer resumed");
    }
}

void SmartSenderBinary::remove() {
    SenderBinary::remove(m_socket);
}

void SmartSenderBinary::cancel() {
    m_cancelRequested.store(true, std::memory_order_release);
    cleanup();
    if (m_session) m_session->isLongTransfer = false;
    if (onFileError) onFileError("Transfer cancelled by user");
    autoCleanupIfNeeded();
}

void SmartSenderBinary::cancelImmediately() {
    m_cancelRequested.store(true, std::memory_order_release);

    {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_fileQueue.clear();
        if (m_file) {
            m_file->close();
            m_file.reset();
        }
        m_filePos.store(0, std::memory_order_release);
        m_totalBytes.store(0, std::memory_order_release);
        m_resumeOffset.store(0, std::memory_order_release);
        m_currentFileQueueMemory = 0;
    }

    m_isProcessing.store(false, std::memory_order_release);
    m_isSendingFile.store(false, std::memory_order_release);
    m_isPaused.store(false, std::memory_order_release);

    if (m_session) m_session->isLongTransfer = false;

    if (onFileError) onFileError("Transfer cancelled immediately by user");
    LOG_DEBUG("[SmartSenderBinary] Transfer cancelled immediately - cleaned up \n");
}

// ==================== GLOBAL WRAPPER ====================

static std::unordered_map<SOCKET, std::unique_ptr<SmartSenderBinary>> g_senders;
static std::mutex g_sendersMutex;

static SmartSenderBinary* getOrCreateSender(SOCKET socket, std::shared_ptr<ClientSession> session = nullptr) {
    if (!socket || socket == INVALID_SOCKET) return nullptr;

    std::lock_guard<std::mutex> lock(g_sendersMutex);
    auto it = g_senders.find(socket);
    if (it != g_senders.end()) {
        return it->second.get();
    }

    auto sender = std::make_unique<SmartSenderBinary>(socket, session);
    SmartSenderBinary* ptr = sender.get();
    g_senders[socket] = std::move(sender);
    return ptr;
}

static SmartSenderBinary* getSender(SOCKET socket) {
    if (!socket || socket == INVALID_SOCKET) return nullptr;

    std::lock_guard<std::mutex> lock(g_sendersMutex);
    auto it = g_senders.find(socket);
    if (it != g_senders.end()) {
        return it->second.get();
    }
    return nullptr;
}

namespace SenderBinary {

    void init() {
        LOG_INFO("[SenderBinary] Initialized");
    }

    void cleanup() {
        std::lock_guard<std::mutex> lock(g_sendersMutex);
        g_senders.clear();
        LOG_INFO("[SenderBinary] Cleanup completed");
    }

    void remove(SOCKET socket) {
        if (!socket || socket == INVALID_SOCKET) return;

        std::lock_guard<std::mutex> lock(g_sendersMutex);
        auto it = g_senders.find(socket);
        if (it != g_senders.end()) {
            g_senders.erase(it);
            LOG_DEBUG("[SenderBinary] Removed sender for socket: %d", socket);
        }
    }

    // ===== TEXT =====
    bool send(SOCKET socket, const std::vector<uint8_t>& data) {
        auto session = findSessionBySocket(socket);
        auto* sender = getOrCreateSender(socket, session);
        if (!sender) return false;
        sender->sendText(data);
        return true;
    }

    bool send(SOCKET socket, const std::string& data) {
        std::vector<uint8_t> v(data.begin(), data.end());
        return send(socket, v);
    }

    bool send(SOCKET socket, const char* data) {
        return send(socket, std::string(data ? data : ""));
    }

    // ===== FILE DATA =====
    bool sendFileData(SOCKET socket, const std::vector<uint8_t>& data) {
        auto session = findSessionBySocket(socket);
        auto* sender = getOrCreateSender(socket, session);
        if (!sender) return false;
        sender->sendFileData(data);
        return true;
    }


    // ===== RAW =====
    bool sendRaw(SOCKET socket, const std::vector<uint8_t>& data) {
        auto* sender = getOrCreateSender(socket);
        if (!sender) return false;
        sender->sendRaw(data);
        return true;
    }

    // ===== FILE =====
    bool sendFile(SOCKET socket, const std::string& filePath) {
        auto session = findSessionBySocket(socket);
        auto* sender = getOrCreateSender(socket, session);
        if (!sender) return false;
        sender->sendFile(filePath);
        return true;
    }

    bool sendFile(SOCKET socket, std::ifstream* file) {
        if (!file) return false;
        auto session = findSessionBySocket(socket);
        auto* sender = getOrCreateSender(socket, session);
        if (!sender) {
            delete file;
            return false;
        }
        sender->sendFile(file);
        return true;
    }

    // ===== RESUME =====
    void setResumeOffset(SOCKET socket, int64_t offset) {
        if (auto* sender = getSender(socket)) {
            sender->setResumeOffset(offset);
        }
    }

    int64_t getResumeOffset(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->getResumeOffset();
        }
        return 0;
    }

    // ===== CONTROL =====
    void stop(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            sender->stop();
        }
    }

    void cancel(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            sender->cancel();
        }
    }

    void cancelImmediately(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            sender->cancelImmediately();
        }
    }

    void pause(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            sender->pause();
        }
    }

    void resume(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            sender->resume();
        }
    }

    // ===== CONFIG =====
    void setMaxFileSize(SOCKET socket, int64_t maxSize) {
        if (auto* sender = getSender(socket)) {
            sender->setMaxFileSize(maxSize);
        }
    }

    void setTimeout(SOCKET socket, int seconds) {
        if (auto* sender = getSender(socket)) {
            sender->setTimeout(seconds);
        }
    }

    void setChunkSize(SOCKET socket, int chunkSize) {
        if (auto* sender = getSender(socket)) {
            sender->setChunkSize(chunkSize);
        }
    }

    void setAutoCleanup(SOCKET socket, bool autoCleanup) {
        if (auto* sender = getSender(socket)) {
            sender->setAutoCleanup(autoCleanup);
        }
    }

    void setMaxFileQueueSize(SOCKET socket, size_t maxSize) {
        if (auto* sender = getSender(socket)) {
            sender->setMaxFileQueueSize(maxSize);
        }
    }

    void setMaxFileQueueMemory(SOCKET socket, size_t maxBytes) {
        if (auto* sender = getSender(socket)) {
            sender->setMaxFileQueueMemory(maxBytes);
        }
    }

    void setFileRetries(SOCKET socket, int retries) {
        if (auto* sender = getSender(socket)) {
            sender->setFileRetries(retries);
        }
    }

    // ===== CHECK =====
    bool isSending(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->isSending();
        }
        return false;
    }

    bool isPaused(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->isPaused();
        }
        return false;
    }

    bool isCancelled(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->isCancelled();
        }
        return false;
    }

    int64_t progress(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->progress();
        }
        return 0;
    }

    int64_t total(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->total();
        }
        return 0;
    }

    std::string currentFileName(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->currentFileName();
        }
        return std::string();
    }

    size_t getFileQueueSize(SOCKET socket) {
        if (auto* sender = getSender(socket)) {
            return sender->getFileQueueSize();
        }
        return 0;
    }


    // ===== CALLBACKS =====
    void setFileCallbacks(SOCKET socket,
        std::function<void(int64_t, int64_t)> onProgress,
        std::function<void()> onFinished,
        std::function<void(const std::string&)> onError,
        std::function<void(const std::string&, int64_t)> onStarted) {

        auto session = findSessionBySocket(socket);
        auto* sender = getOrCreateSender(socket, session);
        if (!sender) return;

        if (onProgress) sender->onFileProgress = std::move(onProgress);
        if (onFinished) sender->onFileFinished = std::move(onFinished);
        if (onError) sender->onFileError = std::move(onError);
        if (onStarted) sender->onFileStarted = std::move(onStarted);
    }

    SmartSenderBinary* getSender(SOCKET socket) {
        return ::getSender(socket);
    }
}