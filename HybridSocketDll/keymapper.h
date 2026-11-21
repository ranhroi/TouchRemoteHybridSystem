#pragma once
#include "fasthash.h"

namespace KeyMapper {
    enum ModifierFlags : WORD {
        KEY_NONE = 0x0000,
        KEY_CTRL = 0x0001,
        KEY_SHIFT = 0x0002,
        KEY_ALT = 0x0004,
        KEY_WIN = 0x0008
    };


    // ==================== CHUYỂN ĐỔI TỪ BUFFER SANG VIRTUAL KEY ====================
    static inline bool isWhitespace(uint8_t c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r'; }
    static inline bool isSpace(uint8_t c) { return c <= 32; }
    static inline bool isDigit(char c) { return c >= '0' && c <= '9'; }
    static inline bool isUpper(char c) { return c >= 'A' && c <= 'Z'; }
    static inline bool isLower(char c) { return c >= 'a' && c <= 'z'; }
    static inline char toUpper(char c) { return (c >= 'a' && c <= 'z') ? c - 32 : c; }

    static inline unsigned short toVirtualKey(const unsigned char* buffer, int len) {

        //WORD toVirtualKey(const uint8_t* buffer, int len) {
        if (len <= 0) return 0;

        // Single character
        if (len == 1) {
            char c = static_cast<char>(buffer[0]);
            if (c >= 'a' && c <= 'z') return 'A' + (c - 'a');
            if (c >= 'A' && c <= 'Z') return c;
            if (c >= '0' && c <= '9') return c;
            if (c == ' ') return VK_SPACE;

            // Special characters
            switch (c) {
                // Hàng phím số và các ký tự Shift tương ứng
            case '1': case '!': return 0x31; // Phím 1
            case '2': case '@': return 0x32; // Phím 2
            case '3': case '#': return 0x33; // Phím 3
            case '4': case '$': return 0x34; // Phím 4
            case '5': case '%': return 0x35; // Phím 5
            case '6': case '^': return 0x36; // Phím 6
            case '7': case '&': return 0x37; // Phím 7
            case '8': case '*': return 0x38; // Phím 8
            case '9': case '(': return 0x39; // Phím 9
            case '0': case ')': return 0x30; // Phím 0

                // Các phím ký tự đặc biệt OEM
            case '-': case '_': return VK_OEM_MINUS;  // Phím - và _
            case '=': case '+': return VK_OEM_PLUS;   // Phím = và +
            case '[': case '{': return VK_OEM_4;      // Phím [ và {
            case ']': case '}': return VK_OEM_6;      // Phím ] và }
            case '\\': case '|': return VK_OEM_5;     // Phím \ và |
            case ';': case ':': return VK_OEM_1;      // Phím ; và :
            case '\'': case '"': return VK_OEM_7;     // Phím ' và "
            case ',': case '<': return VK_OEM_COMMA;  // Phím , và <
            case '.': case '>': return VK_OEM_PERIOD; // Phím . và >
            case '/': case '?': return VK_OEM_2;      // Phím / và ?
            case '`': case '~': return VK_OEM_3;      // Phím ` và ~
            default: return 0;
            }

        }

        // Lookup by hash
        uint32_t h = FastHash::HashBytes(buffer, len);

        // System & Control keys
        if (h == HASH_CT("LBUTTON")) return VK_LBUTTON;
        if (h == HASH_CT("RBUTTON")) return VK_RBUTTON;
        if (h == HASH_CT("MBUTTON")) return VK_MBUTTON;
        if (h == HASH_CT("CANCEL")) return VK_CANCEL;
        if (h == HASH_CT("BACK") || h == HASH_CT("BAS")) return VK_BACK;
        if (h == HASH_CT("TAB")) return VK_TAB;
        if (h == HASH_CT("RTN") || h == HASH_CT("ENTER") || h == HASH_CT("RETURN")) return VK_RETURN;
        if (h == HASH_CT("SHIFT")) return VK_SHIFT;
        if (h == HASH_CT("CTRL")) return VK_CONTROL;
        if (h == HASH_CT("ALT")) return VK_MENU;
        if (h == HASH_CT("ESC") || h == HASH_CT("ESCAPE")) return VK_ESCAPE;
        if (h == HASH_CT("SPACE") || h == HASH_CT("SPC")) return VK_SPACE;
        if (h == HASH_CT("PGUP")) return VK_PRIOR;
        if (h == HASH_CT("PGDN")) return VK_NEXT;
        if (h == HASH_CT("END")) return VK_END;
        if (h == HASH_CT("HOME")) return VK_HOME;
        if (h == HASH_CT("LEFT") || h == HASH_CT("LF")|| h == HASH_CT("←")) return VK_LEFT;
        if (h == HASH_CT("UP")|| h == HASH_CT("↑")) return VK_UP;
        if (h == HASH_CT("RIGHT") || h == HASH_CT("RT")|| h == HASH_CT("→")) return VK_RIGHT;
        if (h == HASH_CT("DOWN") || h == HASH_CT("DW")|| h == HASH_CT("↓")) return VK_DOWN;
        if (h == HASH_CT("INS") || h == HASH_CT("INSERT")) return VK_INSERT;
        if (h == HASH_CT("DEL") || h == HASH_CT("DELETE")) return VK_DELETE;
        if (h == HASH_CT("WIN") || h == HASH_CT("LWIN")) return VK_LWIN;
        if (h == HASH_CT("RWIN")) return VK_RWIN;
        if (h == HASH_CT("MENU") || h == HASH_CT("APPS")) return VK_APPS;
        if (h == HASH_CT("PRINT")) return VK_SNAPSHOT;
        if (h == HASH_CT("SLEEP")) return VK_SLEEP;
        if (h == HASH_CT("PAUSE")) return VK_PAUSE;
        if (h == HASH_CT("CAP") || h == HASH_CT("CAPSLOCK")) return VK_CAPITAL;
        if (h == HASH_CT("NUM") || h == HASH_CT("NUMLOCK")) return VK_NUMLOCK;
        if (h == HASH_CT("SCROLL")) return VK_SCROLL;

        // Numpad
        if (h == HASH_CT("NUMPAD0")) return VK_NUMPAD0;
        if (h == HASH_CT("NUMPAD1")) return VK_NUMPAD1;
        if (h == HASH_CT("NUMPAD2")) return VK_NUMPAD2;
        if (h == HASH_CT("NUMPAD3")) return VK_NUMPAD3;
        if (h == HASH_CT("NUMPAD4")) return VK_NUMPAD4;
        if (h == HASH_CT("NUMPAD5")) return VK_NUMPAD5;
        if (h == HASH_CT("NUMPAD6")) return VK_NUMPAD6;
        if (h == HASH_CT("NUMPAD7")) return VK_NUMPAD7;
        if (h == HASH_CT("NUMPAD8")) return VK_NUMPAD8;
        if (h == HASH_CT("NUMPAD9")) return VK_NUMPAD9;
        if (h == HASH_CT("MULTIPLY")) return VK_MULTIPLY;
        if (h == HASH_CT("ADD")) return VK_ADD;
        if (h == HASH_CT("SUBTRACT")) return VK_SUBTRACT;
        if (h == HASH_CT("DECIMAL")) return VK_DECIMAL;
        if (h == HASH_CT("DIVIDE")) return VK_DIVIDE;

        // F-keys
        if (h == HASH_CT("F1")) return VK_F1;
        if (h == HASH_CT("F2")) return VK_F2;
        if (h == HASH_CT("F3")) return VK_F3;
        if (h == HASH_CT("F4")) return VK_F4;
        if (h == HASH_CT("F5")) return VK_F5;
        if (h == HASH_CT("F6")) return VK_F6;
        if (h == HASH_CT("F7")) return VK_F7;
        if (h == HASH_CT("F8")) return VK_F8;
        if (h == HASH_CT("F9")) return VK_F9;
        if (h == HASH_CT("F10")) return VK_F10;
        if (h == HASH_CT("F11")) return VK_F11;
        if (h == HASH_CT("F12")) return VK_F12;

        // Media keys
        if (h == HASH_CT("MUTE")) return VK_VOLUME_MUTE;
        if (h == HASH_CT("VOLUMEUP")) return VK_VOLUME_UP;
        if (h == HASH_CT("VOLUMEDN")) return VK_VOLUME_DOWN;
        if (h == HASH_CT("MEDIA_NEXT") || h == HASH_CT("NEXT")) return VK_MEDIA_NEXT_TRACK;
        if (h == HASH_CT("MEDIA_PREV") || h == HASH_CT("PREV")) return VK_MEDIA_PREV_TRACK;
        if (h == HASH_CT("MEDIA_STOP")) return VK_MEDIA_STOP;
        if (h == HASH_CT("MEDIA_PLAY") || h == HASH_CT("MEDIA_PAUSE")) return VK_MEDIA_PLAY_PAUSE;

        // Browser keys
        if (h == HASH_CT("BROWSER_BACK")) return VK_BROWSER_BACK;
        if (h == HASH_CT("BROWSER_FORWARD")) return VK_BROWSER_FORWARD;
        if (h == HASH_CT("BROWSER_REFRESH")) return VK_BROWSER_REFRESH;
        if (h == HASH_CT("BROWSER_STOP")) return VK_BROWSER_STOP;
        if (h == HASH_CT("BROWSER_SEARCH")) return VK_BROWSER_SEARCH;
        if (h == HASH_CT("BROWSER_FAVORITES")) return VK_BROWSER_FAVORITES;
        if (h == HASH_CT("BROWSER_HOME")) return VK_BROWSER_HOME;

        // Launch keys
        if (h == HASH_CT("LAUNCH_MAIL")) return VK_LAUNCH_MAIL;
        if (h == HASH_CT("LAUNCH_MEDIA")) return VK_LAUNCH_MEDIA_SELECT;
        if (h == HASH_CT("LAUNCH_APP1") || h == HASH_CT("LAUNCH_CALC")) return VK_LAUNCH_APP1;
        if (h == HASH_CT("LAUNCH_APP2")) return VK_LAUNCH_APP2;

        return 0;
    }

    // Overload for std::string
    inline WORD toVirtualKey(const std::string& keyName) {
        return toVirtualKey(reinterpret_cast<const uint8_t*>(keyName.c_str()), (int)keyName.size());
    }

    // ==================== GỬI SỰ KIỆN BÀN PHÍM ====================

    static inline void sendKeyRaw(WORD vk, bool down) {
        INPUT in = {};
        in.type = INPUT_KEYBOARD;
        in.ki.wVk = vk;
        in.ki.dwFlags = down ? 0 : KEYEVENTF_KEYUP;
        SendInput(1, &in, sizeof(INPUT));
    }

    static inline void sendKeyPress(WORD vk) {
        sendKeyRaw(vk, true);
    }

    static inline void sendKeyRelease(WORD vk) {
        sendKeyRaw(vk, false);
    }

    static inline void sendKeyClick(WORD vk) {
        sendKeyPress(vk);
        sendKeyRelease(vk);
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }

    static inline void sendKeyClick(WORD vk, WORD modifiers) {
        // Kiểm tra đúng theo cờ bitmask tự định nghĩa
        if (modifiers & KEY_CTRL)  sendKeyPress(VK_CONTROL);
        if (modifiers & KEY_SHIFT) sendKeyPress(VK_SHIFT);
        if (modifiers & KEY_ALT)   sendKeyPress(VK_MENU);
        if (modifiers & KEY_WIN)   sendKeyPress(VK_LWIN);

        // Press and release main key
        sendKeyClick(vk);

        // Release modifiers (reverse order)
        if (modifiers & KEY_WIN)   sendKeyRelease(VK_LWIN);
        if (modifiers & KEY_ALT)   sendKeyRelease(VK_MENU);
        if (modifiers & KEY_SHIFT) sendKeyRelease(VK_SHIFT);
        if (modifiers & KEY_CTRL)  sendKeyRelease(VK_CONTROL);
    }


    // Send Unicode text
    static inline void sendKeySequence(const std::string& sequence) {
        if (sequence.empty()) return;

        // Chuyển đổi UTF-8 sang UTF-16
        int len = MultiByteToWideChar(CP_UTF8, 0, sequence.c_str(), -1, NULL, 0);
        if (len <= 0) return;

        std::wstring wstr(len, L'\0');
        MultiByteToWideChar(CP_UTF8, 0, sequence.c_str(), -1, &wstr[0], len);

        // Tạo mảng inputs cho toàn bộ chuỗi
        size_t charCount = wstr.length() - 1;  // Bỏ qua null terminator
        std::vector<INPUT> inputs(charCount * 2);

        for (size_t i = 0; i < charCount; i++) {
            wchar_t wch = wstr[i];

            // Key down
            inputs[i * 2].type = INPUT_KEYBOARD;
            inputs[i * 2].ki.wScan = wch;
            inputs[i * 2].ki.dwFlags = KEYEVENTF_UNICODE;

            // Key up
            inputs[i * 2 + 1].type = INPUT_KEYBOARD;
            inputs[i * 2 + 1].ki.wScan = wch;
            inputs[i * 2 + 1].ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;
        }

        // Gửi tất cả cùng lúc
        SendInput((UINT)inputs.size(), inputs.data(), sizeof(INPUT));
    }

    // ==================== GỬI SỰ KIỆN CHUỘT ====================

    static inline void sendMouseMove(int dx, int dy) {
        INPUT input = {};
        input.type = INPUT_MOUSE;
        input.mi.dx = dx;
        input.mi.dy = dy;
        input.mi.dwFlags = MOUSEEVENTF_MOVE;
        SendInput(1, &input, sizeof(INPUT));
    }

    static inline void sendMouseMoveAbs(int x, int y) {
        int screenWidth = GetSystemMetrics(SM_CXSCREEN);
        int screenHeight = GetSystemMetrics(SM_CYSCREEN);

        INPUT input = {};
        input.type = INPUT_MOUSE;
        input.mi.dx = (x * 65535) / screenWidth;
        input.mi.dy = (y * 65535) / screenHeight;
        input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;
        SendInput(1, &input, sizeof(INPUT));
    }

    static inline void sendMouseClick(DWORD buttonDown, DWORD buttonUp) {
        INPUT inputs[2] = {};
        inputs[0].type = INPUT_MOUSE;
        inputs[0].mi.dwFlags = buttonDown;
        inputs[1].type = INPUT_MOUSE;
        inputs[1].mi.dwFlags = buttonUp;
        SendInput(2, inputs, sizeof(INPUT));
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }

    static inline void sendButtonClick(char btn, char state) {
        DWORD flag = 0;
        switch (btn) {
        case 'l': flag = (state == 'd') ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP; break;
        case 'r': flag = (state == 'd') ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP; break;
        case 'm': flag = (state == 'd') ? MOUSEEVENTF_MIDDLEDOWN : MOUSEEVENTF_MIDDLEUP; break;
        default: return;
        }
        INPUT input = {};
        input.type = INPUT_MOUSE;
        input.mi.dwFlags = flag;
        SendInput(1, &input, sizeof(INPUT));
    }

    static inline void sendMouseLeftClick() {
        sendMouseClick(MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP);
    }

    static inline void sendMouseRightClick() {
        sendMouseClick(MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP);
    }

    static inline void sendMouseMiddleClick() {
        sendMouseClick(MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP);
    }

    //static inline void sendMouseScroll(int delta, bool vertical = true) {
    //    INPUT input = {};
    //    input.type = INPUT_MOUSE;
    //    input.mi.dwFlags = vertical ? MOUSEEVENTF_WHEEL : MOUSEEVENTF_HWHEEL;
    //    input.mi.mouseData = delta * WHEEL_DELTA;
    //    SendInput(1, &input, sizeof(INPUT));
    //}

    static inline void sendMouseScroll(int delta, bool vertical = true) {
        if (delta == 0) return;

        // ✅ Chia nhỏ delta để mượt hơn
        int step = std::clamp(delta, -2, 2);
        int count = std::abs(delta) / std::abs(step);

        for (int i = 0; i < count; i++) {
            INPUT input = {};
            input.type = INPUT_MOUSE;
            input.mi.dwFlags = vertical ? MOUSEEVENTF_WHEEL : MOUSEEVENTF_HWHEEL;
            input.mi.mouseData = step * WHEEL_DELTA;
            SendInput(1, &input, sizeof(INPUT));
        }
    }

    static inline void sendMouseDrag(bool start) {
        INPUT input = {};
        input.type = INPUT_MOUSE;
        input.mi.dwFlags = start ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP;
        SendInput(1, &input, sizeof(INPUT));
    }

    static inline void Inject(float ratioX, float ratioY, int flag) {
        // Ép dải dữ liệu an toàn
        if (ratioX < 0.0f) ratioX = 0.0f;
        if (ratioX > 1.0f) ratioX = 1.0f;
        if (ratioY < 0.0f) ratioY = 0.0f;
        if (ratioY > 1.0f) ratioY = 1.0f;

        LONG absoluteX = static_cast<LONG>(ratioX * 65535.0f);
        LONG absoluteY = static_cast<LONG>(ratioY * 65535.0f);

        INPUT input = { 0 };
        input.type = INPUT_MOUSE;
        input.mi.dx = absoluteX;
        input.mi.dy = absoluteY;

        // Chỉ cần flag hợp lệ (kể cả flag nhận đúng bằng 2), SendInput bắt buộc phải chạy
        if (flag == 1) {        // DOWN
            input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTDOWN;
        }
        else if (flag == 2) {   // MOVE
            input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;
        }
        else if (flag == 3) {   // UP
            input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_LEFTUP;
        }
        else {
            // Dự phòng nếu có flag lạ thì vẫn cho di chuyển chuột tuyệt đối
            input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;
        }

        ::SendInput(1, &input, sizeof(INPUT));
    }


    // ==================== TIỆN ÍCH ====================

    static inline std::string keyToString(WORD vk) {
        char buffer[32] = { 0 };
        GetKeyNameTextA(MapVirtualKeyA(vk, 0) << 16, buffer, sizeof(buffer));
        return std::string(buffer);
    }
}

