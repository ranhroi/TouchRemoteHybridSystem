#include "pch.h"
#include "processcommands.h"
#include "keymapper.h"

namespace KeyboardUtils {
    // Helper: check if buffer contains combo token (ctrl+, alt+, etc.)
    static bool hasComboToken(const uint8_t* data, int len) {
        if (len < 4) return false;
        uint32_t h1 = FastHash::HashBytes(data, (len < 6) ? len : 6);
        uint32_t h2 = FastHash::HashBytes(data, (len < 8) ? len : 8);
        return (h1 == HASH_CT("ctrl+") || h1 == HASH_CT("alt+") ||
            h1 == HASH_CT("win+") || h2 == HASH_CT("shift+"));
    }

    // Helper: open URL with default browser
    static void openUrl(const std::string& url) {
        ShellExecuteA(NULL, "open", url.c_str(), NULL, NULL, SW_SHOWNORMAL);
    }

    // Helper: toggle desktop using COM
    static void toggleDesktop() {
        CoInitialize(NULL);
        IShellDispatch4* pShellDisp = NULL;
        HRESULT hr = CoCreateInstance(CLSID_Shell, NULL, CLSCTX_INPROC_SERVER,
            IID_IShellDispatch4, (void**)&pShellDisp);
        if (SUCCEEDED(hr)) {
            pShellDisp->ToggleDesktop();
            pShellDisp->Release();
        }
        CoUninitialize();
    }
}

// ==================== HANDLE KEY ====================
void ProcessCommands::handleKey(const uint8_t* data, int len, OpCode op) {
    if (len <= 0 || op != OpCode::KEY) return;

    // 1. Handle combo keys (CTRL+C, etc.)
    if (KeyboardUtils::hasComboToken(data, len)) {
        handleComboKey(data, len);
        return;
    }

    // 2. Handle raw key mode: [R] KEY STATE
    if (len >= 3 && data[0] == '[' && data[1] == 'R' && data[2] == ']') {
        int pos = 3;
        while (pos < len && data[pos] == ' ') pos++;
        int keyStart = pos;
        while (pos < len && data[pos] != ' ') pos++;
        int keyLen = pos - keyStart;

        while (pos < len && data[pos] == ' ') pos++;
        if (pos >= len) return;

        char state = static_cast<char>(data[pos]);
        bool isDown = (state == 'd' || state == 'D');

        std::string keyName((const char*)data + keyStart, keyLen);
        WORD vk = KeyMapper::toVirtualKey(keyName);
        if (vk) {
            if (isDown) KeyMapper::sendKeyPress(vk);
            else KeyMapper::sendKeyRelease(vk);
        }
        return;
    }

    // 3. Handle single key
    WORD vk = KeyMapper::toVirtualKey(data, len);
    if (vk) {
        KeyMapper::sendKeyClick(vk);
    }
}

void ProcessCommands::handleComboKey(const uint8_t* data, int len) {
    int pos = 0;
    WORD modifiers = 0;

    // Parse modifiers
    while (pos < len) {
        uint32_t h = FastHash::HashBytes(data + pos, (len - pos < 8) ? len - pos : 8);
        if (h == HASH_CT("ctrl+")) {
            modifiers |= KeyMapper::KEY_CTRL;
            pos += 5;
        }
        else if (h == HASH_CT("shift+")) {
            modifiers |= KeyMapper::KEY_SHIFT;
            pos += 6;
        }
        else if (h == HASH_CT("alt+")) {
            modifiers |= KeyMapper::KEY_ALT;
            pos += 4;
        }
        else if (h == HASH_CT("win+")) {
            modifiers |= KeyMapper::KEY_WIN;
            pos += 4;
        }
        else {
            break;
        }
    }

    // Skip whitespace
    while (pos < len && data[pos] == ' ') pos++;
    if (pos >= len) return;

    // Parse main key
    std::string keyName((const char*)data + pos, len - pos);
    WORD vk = KeyMapper::toVirtualKey(keyName);
    if (vk) {
        KeyMapper::sendKeyClick(vk, modifiers);
    }
}

// ==================== HANDLE UTF-8 ====================
void ProcessCommands::handleUtf8(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len <= 0) return;

    std::string text((const char*)data, len);
    if (!text.empty()) {
        KeyMapper::sendKeySequence(text);
    }
}

// ==================== HANDLE MEDIA ====================
void ProcessCommands::handleMedia(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len <= 0) return;

    uint32_t h = FastHash::HashBytes(data, len);
    if (h == HASH_CT("replay") || h == HASH_CT("prev")) {
        KeyMapper::sendKeyClick(VK_MEDIA_PREV_TRACK);
    }
    else {
        std::string keyName = "MEDIA_" + std::string((const char*)data, len);
        // Convert to uppercase
        for (char& c : keyName) {
            if (c >= 'a' && c <= 'z') c -= 32;
        }
        WORD vk = KeyMapper::toVirtualKey(keyName);
        if (vk == 0) vk = KeyMapper::toVirtualKey(data, len);
        if (vk) KeyMapper::sendKeyClick(vk);
    }
}

// ==================== HANDLE PLAYER ====================
void ProcessCommands::handlePlayer(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len < 4) return;

    uint32_t h = FastHash::HashBytes(data, len);
    if (h == HASH_CT("max")) {
        KeyMapper::sendKeyClick(VK_RETURN, KeyMapper::KEY_ALT);
    }
    else if (h == HASH_CT("play") || h == HASH_CT("pause")) {
        KeyMapper::sendKeyClick(VK_SPACE);
    }
    else if (h == HASH_CT("stop")) {
        KeyMapper::sendKeyClick(VK_MEDIA_STOP);
    }
}

// ==================== HANDLE VOLUME ====================
void ProcessCommands::handleVolum(const uint8_t* data, int len, OpCode op) {
    (void)data; (void)len;
    WORD vk = 0;
    if (op == OpCode::MUTE) vk = VK_VOLUME_MUTE;
    else if (op == OpCode::VOLUMEUP) vk = VK_VOLUME_UP;
    else if (op == OpCode::VOLUMEDN) vk = VK_VOLUME_DOWN;

    if (vk) KeyMapper::sendKeyClick(vk);
}

// ==================== HANDLE BROWSER ====================
void ProcessCommands::handleBrowser(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len <= 0) return;

    int cmdEnd = 0;
    while (cmdEnd < len && data[cmdEnd] > 32) cmdEnd++;
    if (cmdEnd == 0) return;

    uint32_t h = FastHash::HashBytes(data, cmdEnd);

    if (h == HASH_CT("newtab")) {
        KeyMapper::sendKeyClick('T', KeyMapper::KEY_CTRL);
    }
    else if (h == HASH_CT("closetab")) {
        KeyMapper::sendKeyClick('W', KeyMapper::KEY_CTRL);
    }
    else if (h == HASH_CT("openurl")) {
        int urlStart = cmdEnd;
        while (urlStart < len && data[urlStart] <= 32) urlStart++;
        if (urlStart < len) {
            std::string url((const char*)data + urlStart, len - urlStart);
            // Trim URL
            size_t start = url.find_first_not_of(" \t\n\r");
            if (start != std::string::npos) url = url.substr(start);
            size_t end = url.find_last_not_of(" \t\n\r");
            if (end != std::string::npos) url = url.substr(0, end + 1);
            KeyboardUtils::openUrl(url);
        }
    }
    else {
        std::string keyName = "BROWSER_" + std::string((const char*)data, cmdEnd);
        for (char& c : keyName) {
            if (c >= 'a' && c <= 'z') c -= 32;
        }
        WORD vk = KeyMapper::toVirtualKey(keyName);
        if (vk) KeyMapper::sendKeyClick(vk);
    }
}

// ==================== HANDLE ZOOM ====================
void ProcessCommands::handleZoom(const uint8_t* data, int len, OpCode op) {
    (void)data; (void)len;

    switch (op) {
    case OpCode::ZOOMIN:
        // Ctrl + Shift + Phím '='
        KeyMapper::sendKeyClick(VK_OEM_PLUS, KeyMapper::KEY_CTRL | KeyMapper::KEY_SHIFT);
        break;
    case OpCode::ZOOMOUT:
        // Ctrl + Phím '-'
        KeyMapper::sendKeyClick(VK_OEM_MINUS, KeyMapper::KEY_CTRL);
        break;
    default:
        break;
    }
}


// ==================== HANDLE WINDOW ====================
void ProcessCommands::handleWindow(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len <= 0) return;

    HWND hwnd = GetForegroundWindow();
    if (!hwnd) return;

    uint32_t hash = FastHash::HashBytes(data, len);
    switch (hash) {
    case HASH_CT("close"): PostMessage(hwnd, WM_CLOSE, 0, 0); break;
    case HASH_CT("maximize"): ShowWindow(hwnd, SW_MAXIMIZE); break;
    case HASH_CT("minimize"): ShowWindow(hwnd, SW_MINIMIZE); break;
    default: return;
    }
}

// ==================== HANDLE PPT ====================
void ProcessCommands::handlePPT(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len <= 0) return;

    WORD vk = 0;
    uint32_t h = FastHash::HashBytes(data, len);

    if (h == HASH_CT("run") || h == HASH_CT("play")) {
        vk = VK_F5;
    }
    else if (h == HASH_CT("stop") || h == HASH_CT("exit")) {
        vk = VK_ESCAPE;
    }
    else if (h == HASH_CT("pgup")) {
        vk = VK_PRIOR;
    }
    else if (h == HASH_CT("pgdn")) {
        vk = VK_NEXT;
    }
    else if (h == HASH_CT("switch")) {
        KeyMapper::sendKeyClick(VK_TAB, KeyMapper::KEY_ALT);
        return;
    }
    else {
        return;
    }

    if (vk) {
        KeyMapper::sendKeyClick(vk);
    }
}

// ==================== HANDLE WPPT ====================
void ProcessCommands::handleWPPT(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len <= 0) return;

    // Find last space to separate action and typeValue
    int lastSpace = -1;
    for (int i = len - 1; i >= 0; i--) {
        if (data[i] <= 32) {
            lastSpace = i;
            break;
        }
    }

    if (lastSpace < 0) return;

    // Parse typeValue
    int typeValue = 0;
    for (int i = lastSpace + 1; i < len; i++) {
        if (data[i] >= '0' && data[i] <= '9') {
            typeValue = typeValue * 10 + (data[i] - '0');
        }
    }

    // Get action length
    int actionLen = lastSpace;
    while (actionLen > 0 && data[actionLen - 1] <= 32) {
        actionLen--;
    }

    if (actionLen <= 0) return;

    uint32_t h = FastHash::HashBytes(data, actionLen);
    WORD vk = 0;
    WORD modifier = 0;

    if (h == HASH_CT("play")) {
        vk = VK_F5;
        if (typeValue == 0) modifier = KeyMapper::KEY_SHIFT;
    }
    else if (h == HASH_CT("stop")) {
        vk = VK_ESCAPE;
    }
    else if (h == HASH_CT("prev")) {
        vk = VK_PRIOR;
    }
    else if (h == HASH_CT("next")) {
        vk = VK_NEXT;
    }
    else {
        return;
    }

    if (vk) {
        KeyMapper::sendKeyClick(vk, modifier);
    }
}

// ==================== HANDLE SLIDE ====================
void ProcessCommands::handleSlide(const uint8_t* data, int len, OpCode op) {
    WORD vk = 0;

    switch (op) {
    case OpCode::SLIDE: {
        if (len <= 0) return;
        uint32_t h = FastHash::HashBytes(data, len);
        if (h == HASH_CT("begin")) {
            vk = VK_HOME;
        }
        else if (h == HASH_CT("end")) {
            vk = VK_END;
        }
        break;
    }
    case OpCode::SLIDEUP:
    case OpCode::SLIDEDOWN:
        KeyboardUtils::toggleDesktop();
        return;
    case OpCode::SLIDERIGHT:
        vk = VK_RIGHT;
        break;
    case OpCode::SLIDELEFT:
        vk = VK_LEFT;
        break;
    default:
        return;
    }

    if (vk) {
        KeyMapper::sendKeyClick(vk);
    }
}