#include "pch.h"
#include "SessionContext.h"
#include "sessionhandler.h"
#include "ziputils.h"
#include "auth.h"

// ==================== DESTRUCTOR ====================
SessionHandler::~SessionHandler() {
    stop();
    if (m_thread.joinable()) {
        m_thread.join();
    }
}

// ==================== START ====================
void SessionHandler::start(std::shared_ptr<ClientSession> session) {
    if (m_running.exchange(true)) return;
    m_session = session;
    m_thread = std::thread(&SessionHandler::run, this);

    HANDLE hThread = m_thread.native_handle();
    if (hThread) {
        // ============================================================
        // ✅ PRIORITY THEO ROLE
        // ============================================================
        switch (session->role) {
        case SessionRole::COMMAND:
            // 🔥 Chuột — mạnh nhất
            SetThreadPriority(hThread, THREAD_PRIORITY_TIME_CRITICAL);
            SetThreadPriorityBoost(hThread, TRUE);
            LOG_INFO("[SESSION-HANDLER] Started socket: %d (COMMAND / TIME_CRITICAL)",
                session->socket);
            break;

        case SessionRole::TRANSFER:
            // 📁 File — bình thường
            SetThreadPriority(hThread, THREAD_PRIORITY_NORMAL);
            LOG_INFO("[SESSION-HANDLER] Started socket: %d (TRANSFER / NORMAL)",
                session->socket);
            break;

        case SessionRole::SCREEN_STREAM:
            // 🖥️ Stream — nhường CPU
            SetThreadPriority(hThread, THREAD_PRIORITY_BELOW_NORMAL);
            LOG_INFO("[SESSION-HANDLER] Started socket: %d (STREAM / BELOW_NORMAL)",
                session->socket);
            break;

        default:
            SetThreadPriority(hThread, THREAD_PRIORITY_NORMAL);
            LOG_INFO("[SESSION-HANDLER] Started socket: %d (UNKNOWN / NORMAL)",
                session->socket);
            break;
        }
    }
}

// ==================== STOP ====================
void SessionHandler::stop() {
    if (!m_running.exchange(false)) return;
    LOG_INFO("[SESSION-HANDLER] Stopped for socket: %d",
        m_session ? m_session->socket : INVALID_SOCKET);
}

// ==================== IS RUNNING ====================
bool SessionHandler::isRunning() const {
    return m_running.load();
}

// ==================== GỬI TRỰC TIẾP (BLOCKING, CÓ TIMEOUT) ====================
bool SessionHandler::sendDataDirect(SOCKET socket, const std::string& data, int timeoutMs) {
    if (socket == INVALID_SOCKET || data.empty()) return false;

    int totalSent = 0;
    const int length = (int)data.length();
    const auto startTime = std::chrono::steady_clock::now();

    while (totalSent < length) {
        // Kiểm tra cờ dừng
        if (!m_running) {
            LOG_WARN("[SEND] Aborted: handler stopped (%d/%d bytes)", totalSent, length);
            return false;
        }

        // Kiểm tra timeout tổng
        const auto now = std::chrono::steady_clock::now();
        const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            now - startTime).count();
        if (elapsed > timeoutMs) {
            LOG_WARN("[SEND] Timeout after %lldms, sent %d/%d bytes",
                (long long)elapsed, totalSent, length);
            return false;   // ✅ luôn false khi chưa gửi đủ
        }

        int sent = send(socket, data.c_str() + totalSent, length - totalSent, 0);
        if (sent == SOCKET_ERROR) {
            const int error = WSAGetLastError();
            if (error == WSAEWOULDBLOCK) {
                // Chờ socket writable — kiểm tra return của select
                fd_set writeSet;
                FD_ZERO(&writeSet);
                FD_SET(socket, &writeSet);
                timeval tv = { 0, 10000 };  // 10ms
                const int sel = select(0, NULL, &writeSet, NULL, &tv);
                if (sel <= 0) {
                    LOG_WARN("[SEND] select() returned %d, aborting", sel);
                    return false;
                }
                continue;   // socket sẵn sàng ghi → retry
            }
            LOG_WARN("[SEND] send() error: %d", error);
            return false;
        }
        if (sent == 0) {
            LOG_WARN("[SEND] send() returned 0, peer closed");
            return false;
        }

        totalSent += sent;
    }

    return true;
}

// ==================== MAIN LOOP ====================
void SessionHandler::run() {
    auto session = m_session;
    if (!session) {
        LOG_ERROR("[SESSION-HANDLER] Session is null");
        m_running = false;
        return;
    }

    // ============================================================
    // ✅ MMCSS THEO ROLE — chỉ COMMAND và TRANSFER cần
    // ============================================================
    if (session->role == SessionRole::COMMAND) {
        m_mmcssHandle = AvSetMmThreadCharacteristicsW(L"Pro Audio", &m_mmcssTaskIndex);
        if (m_mmcssHandle) {
            AvSetMmThreadPriority(m_mmcssHandle, AVRT_PRIORITY_CRITICAL);
            LOG_INFO("[SESSION-HANDLER] MMCSS registered (Pro Audio, CRITICAL) socket: %d",
                session->socket);
        }
        else {
            LOG_WARN("[SESSION-HANDLER] MMCSS failed: %d (continuing)", GetLastError());
        }
    }
    else if (session->role == SessionRole::TRANSFER) {
        m_mmcssHandle = AvSetMmThreadCharacteristicsW(L"Pro Audio", &m_mmcssTaskIndex);
        if (m_mmcssHandle) {
            AvSetMmThreadPriority(m_mmcssHandle, AVRT_PRIORITY_NORMAL);
            LOG_INFO("[SESSION-HANDLER] MMCSS registered (Pro Audio, NORMAL) socket: %d",
                session->socket);
        }
    }
    // 🖥️ SCREEN_STREAM: KHÔNG dùng MMCSS — chạy bình thường

    LOG_INFO("[SESSION-HANDLER] Thread running socket: %d (role: %d)",
        session->socket, (int)session->role);

    int healthCheckCounter = 0;
    const int HEALTH_CHECK_INTERVAL = 50;

    while (m_running) {
        if (!session || session->socket == INVALID_SOCKET ||
            session->state == SessionState::DISCONNECTED) {
            LOG_INFO("[SESSION-HANDLER] Session invalid, stopping");
            break;
        }

        try {
            // ==================== HANDSHAKE ====================
            if (session->state == SessionState::HANDSHAKE) {
                handleHandshake(session);
                if (session->state == SessionState::DISCONNECTED) break;
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
                continue;
            }

            // ==================== AUTH ====================
            if (session->state == SessionState::AUTH_WAIT) {
                handleAuth(session);
                if (session->state == SessionState::DISCONNECTED) break;
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
                continue;
            }

            // ==================== STREAM ====================
            if (session->state == SessionState::STREAM) {
                fd_set readSet;
                FD_ZERO(&readSet);
                FD_SET(session->socket, &readSet);

                // ✅ Timeout theo role
                timeval tv;
                if (session->role == SessionRole::COMMAND) {
                    tv = { 0, 1000 };   // 1ms — chuột cần nhanh
                }
                else if (session->role == SessionRole::TRANSFER) {
                    tv = { 0, 10000 };  // 10ms — file không cần gấp
                }
                else {
                    tv = { 0, 50000 };  // 50ms — stream nhường CPU
                }

                int sel = select(0, &readSet, NULL, NULL, &tv);
                if (sel > 0 && FD_ISSET(session->socket, &readSet)) {
                    readData(session);
                }

                // ✅ processData xử lý bất kỳ bytes nào đang trong buffer
                //    (kể cả bytes đến từ lần recv trước chưa kịp xử lý)
                {
                    bool hasData = false;
                    {
                        std::lock_guard<std::mutex> lock(m_dataMutex);
                        hasData = !m_readBuffer.empty();
                    }
                    if (hasData) {
                        processData(session);
                    }
                }

                if (++healthCheckCounter >= HEALTH_CHECK_INTERVAL) {
                    healthCheckCounter = 0;
                    if (!checkSessionHealth(session)) break;
                }
            }
        }
        catch (const std::exception& e) {
            LOG_ERROR("[SESSION-HANDLER] Exception: %s", e.what());
            if (session) session->state = SessionState::DISCONNECTED;
            break;
        }
        catch (...) {
            LOG_ERROR("[SESSION-HANDLER] Unknown exception");
            if (session) session->state = SessionState::DISCONNECTED;
            break;
        }
    }

    // ============================================================
    // ✅ CLEANUP MMCSS
    // ============================================================
    if (m_mmcssHandle) {
        AvRevertMmThreadCharacteristics(m_mmcssHandle);
        m_mmcssHandle = nullptr;
        LOG_INFO("[SESSION-HANDLER] MMCSS unregistered");
    }

    if (session && session->socket != INVALID_SOCKET) {
        shutdown(session->socket, SD_BOTH);
        closesocket(session->socket);
        session->socket = INVALID_SOCKET;
    }

    m_running = false;
    LOG_INFO("[SESSION-HANDLER] Thread ended socket: %d",
        session ? session->socket : INVALID_SOCKET);
}

// ==================== HANDSHAKE ====================
void SessionHandler::handleHandshake(std::shared_ptr<ClientSession> session) {
    if (!session || session->socket == INVALID_SOCKET) {
        if (session) session->state = SessionState::DISCONNECTED;
        return;
    }

    fd_set readSet;
    FD_ZERO(&readSet);
    FD_SET(session->socket, &readSet);
    timeval tv = { 0, 1000 };
    int sel = select(0, &readSet, NULL, NULL, &tv);
    if (sel <= 0 || !FD_ISSET(session->socket, &readSet)) {
        return;
    }

    char buffer[1] = { 0 };
    int recvResult = recv(session->socket, buffer, 1, 0);
    if (recvResult <= 0) {
        LOG_WARN("[HANDSHAKE] recv failed: %d", WSAGetLastError());
        session->state = SessionState::DISCONNECTED;
        return;
    }

    SessionRole newRole = SessionRole::UNKNOWN;
    switch (buffer[0]) {
    case 0x01: newRole = SessionRole::COMMAND;       break;
    case 0x02: newRole = SessionRole::TRANSFER;      break;
    case 0x03: newRole = SessionRole::SCREEN_STREAM; break;
    default: break;
    }

    if (newRole == SessionRole::UNKNOWN) {
        LOG_WARN("[HANDSHAKE] Unknown role: 0x%02X", (unsigned char)buffer[0]);
        session->state = SessionState::DISCONNECTED;
        return;
    }

    session->role = newRole;
    session->createParserForRole(newRole, session->socket);

    LOG_INFO("[HANDSHAKE] Parsed role: %d", (int)newRole);

    bool skipAuth = session->transport == TransportType::BLUETOOTH;

    std::string response = "system windows\n";
    if (!skipAuth && g_state.needPassword) response += "needpassword\n";
    if (g_state.needBuy) response += "needBuy\n";

    session->sendText(response);
    LOG_INFO("[HANDSHAKE] Response sent successfully");

    if (!skipAuth && g_state.needPassword) {
        session->state = SessionState::AUTH_WAIT;
    }
    else {
        session->state = SessionState::STREAM;
        session->authenticated = true;
    }
    session->lastActivity = std::chrono::steady_clock::now();
}

// ==================== AUTH ====================
void SessionHandler::handleAuth(std::shared_ptr<ClientSession> session) {
    if (!session) return;

    fd_set readSet;
    FD_ZERO(&readSet);
    FD_SET(session->socket, &readSet);
    timeval tv = { 0, 1000 };
    int sel = select(0, &readSet, NULL, NULL, &tv);
    if (sel <= 0 || !FD_ISSET(session->socket, &readSet)) return;

    char buffer[512] = { 0 };
    int bytesReceived = recv(session->socket, buffer, sizeof(buffer) - 1, 0);
    if (bytesReceived <= 0) {
        if (bytesReceived == 0 || WSAGetLastError() != WSAEWOULDBLOCK) {
            session->state = SessionState::DISCONNECTED;
        }
        return;
    }

    m_authBuffer.append(buffer, bytesReceived);

    size_t newlinePos = m_authBuffer.find('\n');
    if (newlinePos == std::string::npos) {
        if (m_authBuffer.size() > 1024) {
            LOG_WARN("[AUTH] Buffer overflow, resetting");
            m_authBuffer.clear();
        }
        return;
    }

    std::string command = m_authBuffer.substr(0, newlinePos);
    m_authBuffer.erase(0, newlinePos + 1);
    if (!command.empty() && command.back() == '\r') command.pop_back();

    if (g_state.needPassword.load() && command.find("verifypassword ") == 0) {
        std::string md5Hash = command.substr(15);
        if (Auth::verify(md5Hash)) {
            std::string response = "verifypassword ok\n";
            session->sendText(response);
            session->state = SessionState::STREAM;
            session->authenticated = true;
            session->lastActivity = std::chrono::steady_clock::now();
            LOG_INFO("[AUTH] Success");
        }
        else {
            session->sendText("verifypassword fail\n");
            LOG_INFO("[AUTH] Failed");
        }
    }
}

// ==================== READ DATA ====================
// ✅ FIX #3: Không drop bytes khi buffer đầy.
//    - Nếu buffer đã đầy: return false → loop gọi processData() ở iteration sau.
//    - Nếu còn chỗ: chỉ recv tối đa phần còn trống → không bao giờ overflow.
bool SessionHandler::readData(std::shared_ptr<ClientSession> session) {
    if (!session || session->socket == INVALID_SOCKET) return false;

    // Đợi processData tiêu thụ bớt buffer trước khi recv tiếp
    size_t space = 0;
    {
        std::lock_guard<std::mutex> lock(m_dataMutex);
        if (m_readBuffer.size() >= MAX_READ_BUFFER) {
            return false;
        }
        space = MAX_READ_BUFFER - m_readBuffer.size();
    }

    fd_set readSet;
    FD_ZERO(&readSet);
    FD_SET(session->socket, &readSet);
    timeval tv = { 0, 1000 };
    int sel = select(0, &readSet, NULL, NULL, &tv);
    if (sel <= 0 || !FD_ISSET(session->socket, &readSet)) return false;

    char buffer[8192];
    const int toRead = static_cast<int>(std::min<int>(space, sizeof(buffer)));

    int bytesRead = recv(session->socket, buffer, toRead, 0);
    if (bytesRead <= 0) {
        if (bytesRead == 0 || WSAGetLastError() != WSAEWOULDBLOCK) {
            session->state = SessionState::DISCONNECTED;
        }
        return false;
    }

    session->lastActivity = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(m_dataMutex);
        m_readBuffer.insert(m_readBuffer.end(), buffer, buffer + bytesRead);
    }

    return true;
}

// ==================== PROCESS DATA ====================
// ✅ FIX #1: FAST PATH cho upload.
//    Khi đang upload (session->uploadFile != nullptr), ghi thẳng bytes vào file
//    — KHÔNG đi qua parser (giống SessionWorker::handleStream cũ).
//    Khi chưa upload, mới đưa cho parser parse lệnh text.
void SessionHandler::processData(std::shared_ptr<ClientSession> session) {
    if (!session || session->role == SessionRole::UNKNOWN) return;

    std::vector<uint8_t> localBuffer;
    {
        std::lock_guard<std::mutex> lock(m_dataMutex);
        if (m_readBuffer.empty()) return;
        localBuffer.swap(m_readBuffer);
    }

    const uint8_t* dataPtr = localBuffer.data();
    const int dataSize = static_cast<int>(localBuffer.size());

    // ============================================================
    // ✅ FAST PATH: TRANSFER đang upload → ghi thẳng vào file
    // ============================================================
    if (session->role == SessionRole::TRANSFER && session->uploadFile) {
        handleUploadData(session, dataPtr, dataSize);
        return;
    }

    // ============================================================
    // ✅ SLOW PATH: parser xử lý lệnh text
    // ============================================================
    switch (session->role) {
    case SessionRole::COMMAND:
    case SessionRole::SCREEN_STREAM:
        if (!session->parseCommand) {
            session->createParserForRole(session->role, session->socket);
        }
        if (session->parseCommand) {
            session->parseCommand->pushData(dataPtr, dataSize);
        }
        break;

    case SessionRole::TRANSFER:
        if (!session->parseFile) {
            session->createParserForRole(SessionRole::TRANSFER, session->socket);
        }
        if (session->parseFile) {
            session->parseFile->pushData(dataPtr, dataSize);
        }
        break;

    default:
        break;
    }
}

// ==================== UPLOAD DATA ====================
void SessionHandler::handleUploadData(std::shared_ptr<ClientSession> session,
    const uint8_t* data, int size) {
    if (!session || !session->uploadFile || size <= 0) return;

    // ============================================================
    // ✅ GHI FILE — không check disk trước.
    //    fwrite sẽ fail nếu đĩa thật sự đầy → xử lý qua nhánh lỗi.
    // ============================================================
    const size_t written = std::fwrite(data, 1, size, session->uploadFile);
    if (written != static_cast<size_t>(size)) {
        LOG_ERROR("[UPLOAD] Write error (disk full?)");
        session->sendText("upload_error disk_full\n");
        session->state = SessionState::DISCONNECTED;
        return;
    }

    session->uploadReceivedSize += written;

    // ✅ Log mỗi 10%
    const int percent = (session->uploadTotalSize > 0)
        ? static_cast<int>((session->uploadReceivedSize * 100) / session->uploadTotalSize)
        : 0;

    static thread_local int lastLoggedPercent = -1;
    if (percent >= lastLoggedPercent + 10 ||
        session->uploadReceivedSize >= session->uploadTotalSize) {
        lastLoggedPercent = percent;
        LOG_INFO("[UPLOAD] Progress: %d%% (%lld / %lld bytes)",
            percent,
            static_cast<long long>(session->uploadReceivedSize),
            static_cast<long long>(session->uploadTotalSize));
    }

    // ✅ Đủ bytes → finish
    if (session->uploadReceivedSize >= session->uploadTotalSize) {
        lastLoggedPercent = -1;
        finishUpload(session);
    }
}

// ==================== FINISH UPLOAD ====================
void SessionHandler::finishUpload(std::shared_ptr<ClientSession> session) {
    if (!session) return;

    if (session->uploadFile) {
        fflush(session->uploadFile);
        fclose(session->uploadFile);
        session->uploadFile = nullptr;
    }

    if (session->uploadFilePath.find("_upload_temp.zip") != std::string::npos) {
        std::string zipPath = session->uploadFilePath;
        std::string targetFolder = zipPath.substr(0, zipPath.find("_upload_temp.zip"));

        session->sendText("unzipping\n");
        session->processingZipPath = "UNZIPPING";

        bool success = ZipUtils::decompressZipToFolder(zipPath, targetFolder, session->socket);
        session->processingZipPath.clear();

        if (success) {
            DeleteFileA(zipPath.c_str());
            session->sendText("upload_complete\n");
            LOG_INFO("[UPLOAD] Complete with unzip");
        }
        else {
            DeleteFileA(zipPath.c_str());
            session->sendText("upload_error unzip_failed\n");
            LOG_ERROR("[UPLOAD] Unzip failed");
        }
    }
    else {
        session->sendText("upload_complete\n");
        LOG_INFO("[UPLOAD] Complete");
    }
    session->isLongTransfer = false;
}

// ==================== CHECK SESSION HEALTH ====================
bool SessionHandler::checkSessionHealth(std::shared_ptr<ClientSession> session) {
    if (!session || session->socket == INVALID_SOCKET) return false;

    auto now = std::chrono::steady_clock::now();
    auto idleSeconds = std::chrono::duration_cast<std::chrono::seconds>(
        now - session->lastActivity).count();

    if (idleSeconds > session->getTimeout()) {
        LOG_WARN("[SESSION-HANDLER] Timeout for socket: %d, idle: %d seconds",
            session->socket, (int)idleSeconds);
        session->state = SessionState::DISCONNECTED;
        return false;
    }

    return true;
}