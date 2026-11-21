#include "pch.h"
#include "fileexplorerstate.h"

// ==================== ClipboardState Implementation ====================

void ClipboardState::initialize() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_clipboard = ClipboardEntry();
    m_cancelRequested.store(false);
    LOG_DEBUG("[ClipboardState] Initialized");
}

void ClipboardState::reset() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_clipboard = ClipboardEntry();
    m_cancelRequested.store(false);
    LOG_DEBUG("[ClipboardState] Reset");
}

void ClipboardState::setClipboard(const std::string& path, bool isCut, uint64_t sourceSessionId) {
    if (path.empty()) {
        LOG_DEBUG("[ClipboardState] Attempted to set empty path to clipboard");
        return;
    }

    std::lock_guard<std::mutex> lock(m_mutex);
    m_clipboard = ClipboardEntry(path, isCut, sourceSessionId);

    LOG_DEBUG("[ClipboardState] Clipboard set - Path: %s, IsCut: %s, SourceSession: %llu", path.c_str(), isCut ? "true" : "false", static_cast<unsigned long long>(sourceSessionId));
}

std::string ClipboardState::getClipboardPath() {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_clipboard.path;
}

bool ClipboardState::isClipboardCut() {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_clipboard.isCut;
}

void ClipboardState::clearClipboard() {
    std::lock_guard<std::mutex> lock(m_mutex);

    std::string oldPath = m_clipboard.path;
    m_clipboard = ClipboardEntry();

    if (!oldPath.empty()) {
        LOG_DEBUG("[ClipboardState] Clipboard cleared - Old path: %s", oldPath.c_str());
    }
}

bool ClipboardState::hasClipboardContent() {
    std::lock_guard<std::mutex> lock(m_mutex);
    return !m_clipboard.path.empty();
}

// ✅ IMPLEMENT METHOD NÀY
uint64_t ClipboardState::getClipboardSourceSessionId() {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_clipboard.sourceSessionId;
}

bool ClipboardState::isReferencingSession(uint64_t sessionId) {
    if (sessionId == 0) return false;

    std::lock_guard<std::mutex> lock(m_mutex);
    return m_clipboard.sourceSessionId == sessionId;
}

void ClipboardState::clearIfReferencing(uint64_t sessionId) {
    if (sessionId == 0) return;

    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_clipboard.sourceSessionId == sessionId) {
        std::string oldPath = m_clipboard.path;
        m_clipboard = ClipboardEntry();
        LOG_DEBUG("[ClipboardState] Cleared clipboard referencing session %llu (path: %s)", static_cast<unsigned long long>(sessionId), oldPath.c_str());
    }
}

std::string ClipboardState::getClipboardInfo() {
    std::lock_guard<std::mutex> lock(m_mutex);

    std::ostringstream oss;
    oss << "ClipboardState{"
        << "path='" << m_clipboard.path << "', "
        << "isCut=" << (m_clipboard.isCut ? "true" : "false") << ", "
        << "sourceSessionId=" << m_clipboard.sourceSessionId << ", "
        << "ageSeconds=" << m_clipboard.ageInSeconds()
        << "}";
    return oss.str();
}

void ClipboardState::setCancelRequested(bool value) {
    m_cancelRequested.store(value);
    LOG_DEBUG("[ClipboardState] Cancel requested: %s", value ? "true" : "false");
}

bool ClipboardState::isCancelRequested() {
    return m_cancelRequested.load();
}

void ClipboardState::resetCancelRequested() {
    m_cancelRequested.store(false);
    LOG_DEBUG("[ClipboardState] Cancel requested reset");
}

std::string ClipboardState::toString() const {
    std::lock_guard<std::mutex> lock(m_mutex);

    std::ostringstream oss;
    oss << "=== ClipboardState ===\n";
    oss << "  Path: " << m_clipboard.path << "\n";
    oss << "  IsCut: " << (m_clipboard.isCut ? "true" : "false") << "\n";
    oss << "  SourceSessionId: " << m_clipboard.sourceSessionId << "\n";
    oss << "  Age (seconds): " << m_clipboard.ageInSeconds() << "\n";
    oss << "  CancelRequested: " << (m_cancelRequested.load() ? "true" : "false") << "\n";
    return oss.str();
}