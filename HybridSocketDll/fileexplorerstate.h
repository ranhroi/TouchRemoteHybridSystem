#pragma once

// ==================== GLOBAL CLIPBOARD MANAGER ====================
class ClipboardState {
public:
    static ClipboardState& getInstance() {
        static ClipboardState instance;
        return instance;
    }

    // ===== CLIPBOARD OPERATIONS =====
    void setClipboard(const std::string& path, bool isCut, uint64_t sourceSessionId = 0);
    std::string getClipboardPath();
    bool isClipboardCut();
    void clearClipboard();
    bool hasClipboardContent();
    uint64_t getClipboardSourceSessionId();  
    bool isReferencingSession(uint64_t sessionId);
    void clearIfReferencing(uint64_t sessionId);
    std::string getClipboardInfo();

    // ===== CANCEL TRANSFER OPERATIONS =====
    void setCancelRequested(bool value);
    bool isCancelRequested();
    void resetCancelRequested();

    // ===== INITIALIZATION =====
    void initialize();
    void reset();
    std::string toString() const;

private:
    ClipboardState() = default;
    ~ClipboardState() = default;

    ClipboardState(const ClipboardState&) = delete;
    ClipboardState& operator=(const ClipboardState&) = delete;
    ClipboardState(ClipboardState&&) = delete;
    ClipboardState& operator=(ClipboardState&&) = delete;

    struct ClipboardEntry {
        std::string path;
        bool isCut;
        uint64_t sourceSessionId;
        std::chrono::steady_clock::time_point timestamp;

        ClipboardEntry()
            : path(""), isCut(false), sourceSessionId(0), timestamp(std::chrono::steady_clock::now()) {}

        ClipboardEntry(const std::string& p, bool cut, uint64_t sid)
            : path(p), isCut(cut), sourceSessionId(sid), timestamp(std::chrono::steady_clock::now()) {}

        bool isValid() const { return !path.empty(); }

        int ageInSeconds() const {
            auto now = std::chrono::steady_clock::now();
            return std::chrono::duration_cast<std::chrono::seconds>(now - timestamp).count();
        }
    };

    mutable std::mutex m_mutex;
    ClipboardEntry m_clipboard;
    std::atomic<bool> m_cancelRequested{ false };
};