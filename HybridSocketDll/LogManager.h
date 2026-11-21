#pragma once
// ============================================================
// 1. CẤU HÌNH LOG - TỐI ƯU CHO PERFORMANCE
// ============================================================

// Mặc định tắt debug log trong Release
#ifdef _DEBUG
#define ENABLE_DEBUG_LOG
// #define ENABLE_TRACE_LOG  // Comment để tránh spam
#else
    // Release: chỉ log lỗi và cảnh báo
#undef ENABLE_DEBUG_LOG
#undef ENABLE_TRACE_LOG
#endif

// ============================================================
// 2. MACRO LOG - HỖ TRỢ PRINTF STYLE
// ============================================================

// ----- LUÔN LOG (Không thể tắt) -----
#define LOG_ERROR(fmt, ...)  g_logger.Error(fmt, ##__VA_ARGS__)
#define LOG_WARN(fmt, ...)   g_logger.Warn(fmt, ##__VA_ARGS__)
#define LOG_INFO(fmt, ...)   g_logger.Info(fmt, ##__VA_ARGS__)

// ----- DEBUG LOG (Biến mất khi không define) -----
#ifdef ENABLE_DEBUG_LOG
#define LOG_DEBUG(fmt, ...)  g_logger.Debug(fmt, ##__VA_ARGS__)
#else
#define LOG_DEBUG(fmt, ...)  ((void)0)  // ✅ Zero cost!
#endif

#ifdef ENABLE_TRACE_LOG
#define LOG_TRACE(fmt, ...)  g_logger.Trace(fmt, ##__VA_ARGS__)
#else
#define LOG_TRACE(fmt, ...)  ((void)0)  // ✅ Zero cost!
#endif


// ============================================================
// 3. LOG MANAGER - NON-BLOCKING QUEUE
// ============================================================
class LogManager {
private:
    std::queue<std::string> m_queue;
    std::mutex m_mutex;
    const size_t MAX_LOGS = 200;

    std::string getTime() {
        auto now = std::chrono::system_clock::now();
        auto time_t = std::chrono::system_clock::to_time_t(now);
        std::tm bt{};
        localtime_s(&bt, &time_t);
        std::ostringstream oss;
        oss << std::put_time(&bt, "%H:%M:%S");
        return oss.str();
    }

    // ✅ Hàm format string với va_list
    std::string formatString(const char* fmt, va_list args) {
        if (!fmt) return "";

        // Dùng _vscprintf để tính độ dài cần thiết
        va_list argsCopy;
        va_copy(argsCopy, args);
        int len = _vscprintf(fmt, argsCopy);
        va_end(argsCopy);

        if (len <= 0) return std::string(fmt);

        std::vector<char> buffer(len + 1);
        vsnprintf(buffer.data(), buffer.size(), fmt, args);
        return std::string(buffer.data());
    }

public:
    // ===== UI LOG (Hỗ trợ format) =====
    void Info(const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        std::string msg = formatString(fmt, args);
        va_end(args);
        add("[INFO] " + msg);
    }

    void Warn(const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        std::string msg = formatString(fmt, args);
        va_end(args);
        add("[WARN] " + msg);
    }

    void Error(const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        std::string msg = formatString(fmt, args);
        va_end(args);
        add("[ERROR] " + msg);
    }

    // ===== DEBUG LOG (Nội bộ) =====
    void Debug(const char* fmt, ...) {
#ifdef ENABLE_DEBUG_LOG
        va_list args;
        va_start(args, fmt);
        std::string msg = formatString(fmt, args);
        va_end(args);
        add("[DEBUG] " + msg);
#endif
    }

    void Trace(const char* fmt, ...) {
#ifdef ENABLE_TRACE_LOG
        va_list args;
        va_start(args, fmt);
        std::string msg = formatString(fmt, args);
        va_end(args);
        add("[TRACE] " + msg);
#endif
    }

    // ===== LẤY LOG CHO UI =====
    std::string PopLog() {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_queue.empty()) return "";
        std::string msg = m_queue.front();
        m_queue.pop();
        return msg;
    }

    bool HasLog() const {
        std::lock_guard<std::mutex> lock(const_cast<std::mutex&>(m_mutex));
        return !m_queue.empty();
    }

private:
    void add(const std::string& msg) {
        std::lock_guard<std::mutex> lock(m_mutex);
        std::string formatted = "[" + getTime() + "] " + msg;
        if (m_queue.size() >= MAX_LOGS) m_queue.pop();
        m_queue.push(formatted);
    }
};

extern LogManager g_logger;


// ===== MỨC ĐỘ CHI TIẾT TĂNG DẦN =====
//LOG_ERROR   // 🔴 Lỗi nghiêm trọng, app crash
//LOG_WARN    // 🟡 Cảnh báo, có vấn đề nhưng vẫn chạy
//LOG_INFO    // 🔵 Thông tin quan trọng (kết nối, ngắt kết nối)
//LOG_DEBUG   // 🟢 Thông tin chi tiết (parse command, xử lý)
//LOG_TRACE   // ⚪ Rất chi tiết (từng biến, từng bước nhỏ)