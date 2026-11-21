#include "pch.h"
#include "processcommands.h"
#include "keymapper.h"

// Helper: fast integer parser
namespace MouseUtils {
    static inline int fastParseInt(const uint8_t*& ptr, const uint8_t* end, int& sign) {
        int value = 0;
        sign = 1;

        if (ptr < end && *ptr == '-') {
            sign = -1;
            ptr++;
        }

        while (ptr < end && *ptr >= '0' && *ptr <= '9') {
            value = value * 10 + (*ptr - '0');
            ptr++;
        }

        return value;
    }

    // Helper: trim string
    static inline std::string trim(const std::string& str) {
        size_t start = str.find_first_not_of(" \t\n\r");
        if (start == std::string::npos) return "";
        size_t end = str.find_last_not_of(" \t\n\r");
        return str.substr(start, end - start + 1);
    }
}


// Hàm bổ trợ phân tách nhanh số Float từ con trỏ byte (Theo phong cách fastParse của bạn)
// Giúp bóc tách các số dạng "0.877" hoặc "0.776" trực tiếp từ mảng byte thô
inline float fastParseFloat(const uint8_t*& ptr, const uint8_t* end) {
    while (ptr < end && *ptr <= ' ') ptr++; // Bỏ qua khoảng trắng đầu
    if (ptr >= end) return 0.0f;

    float result = 0.0f;
    // Đọc phần nguyên trước dấu chấm
    while (ptr < end && *ptr >= '0' && *ptr <= '9') {
        result = result * 10.0f + (*ptr - '0');
        ptr++;
    }

    // Nếu gặp dấu chấm thập phân, đọc tiếp phần phân số
    if (ptr < end && *ptr == '.') {
        ptr++;
        float factor = 0.1f;
        while (ptr < end && *ptr >= '0' && *ptr <= '9') {
            result += (*ptr - '0') * factor;
            factor *= 0.1f;
            ptr++;
        }
    }
    return result;
}

void ProcessCommands::mouseHandle(const uint8_t* data, int len, OpCode op) {
    if (len < 1 || op != OpCode::MOS) return;

    const uint8_t* ptr = data;
    const uint8_t* end = data + len;

    // Skip leading whitespace
    while (ptr < end && *ptr <= ' ') ptr++;
    if (ptr >= end) return;

    // Get type character (c, m, R, w, h, a)
    char type = static_cast<char>(*ptr++);

    // Skip to number data
    while (ptr < end && *ptr <= ' ') ptr++;

    switch (type) {
        // =========================================================================
        // NHÁNH TÍCH HỢP MỚI: Xử lý tọa độ cảm ứng tuyệt đối tối giản 3 tham số
        // Chuỗi đầu vào từ Android có dạng: "a 0.6703109 0.5774667 2"
        // =========================================================================
    case 'a': {
        // 1. Lọc lấy ratioX và ratioY từ mảng byte thô
        float ratioX = fastParseFloat(ptr, end);
        float ratioY = fastParseFloat(ptr, end);

        // Đảm bảo con trỏ ptr nhảy qua các khoảng trắng thừa trước khi đọc số nguyên flag
        while (ptr < end && *ptr <= ' ') ptr++;

        // 2. Lọc lấy tham số flag cuối cùng (1=DOWN, 2=MOVE, 3=UP)
        int dummySign = 1;
        int flag = MouseUtils::fastParseInt(ptr, end, dummySign);
        flag *= dummySign;

        // 3. Thực thi bắn trực tiếp 3 tham số sạch vào hàm Inject của bạn
        // LƯU Ý ĐỒNG BỘ: Truyền chính xác 3 tham số: ratioX, ratioY, flag
        KeyMapper::Inject(ratioX, ratioY, flag);
        break;
    }

    // =========================================================================

    case 'm': {  // Move: m x y (Chuột rê tương đối cũ của bạn)
        int x = 0, y = 0;
        int signX = 1, signY = 1;

        x = MouseUtils::fastParseInt(ptr, end, signX);
        x *= signX;

        while (ptr < end && *ptr <= ' ') ptr++;

        y = MouseUtils::fastParseInt(ptr, end, signY);
        y *= signY;

        KeyMapper::sendMouseMove(x, y);
        break;
    }

    case 'c':  // Click
        KeyMapper::sendMouseLeftClick();
        break;

    case 'R': {  // Raw buttons: l d, r u, etc.
        char btn = 0, state = 0;
        while (ptr < end) {
            char c = static_cast<char>(*ptr++);
            if (c == 'l' || c == 'r' || c == 'm') {
                btn = c;
            }
            else if (c == 'd' || c == 'u') {
                state = c;
            }
        }
        if (btn && state) {
            KeyMapper::sendButtonClick(btn, state);
        }
        break;
    }

    case 'w':   // Vertical scroll
    case 'h': { // Horizontal scroll
        int delta = 0;
        int sign = 1;

        delta = MouseUtils::fastParseInt(ptr, end, sign);
        delta *= sign;

        if (delta != 0) {
            KeyMapper::sendMouseScroll(delta, type == 'w');
        }
        break;
    }

    default:
        break;
    }
}


void ProcessCommands::dragHandle(const uint8_t* data, int len, OpCode op) {
    (void)op;
    if (len <= 0) return;

    uint32_t h = FastHash::HashBytes(data, len);

    if (h == HASH_CT("start")) {
        KeyMapper::sendMouseDrag(true);
    }
    else if (h == HASH_CT("end")) {
        KeyMapper::sendMouseDrag(false);
    }
}