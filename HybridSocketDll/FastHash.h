#pragma once

class FastHash {
public:
    // Hằng số FNV-1a
    static constexpr uint32_t FNV_OFFSET_BASIS = 0x811c9dc5;
    static constexpr uint32_t FNV_PRIME = 0x01000193;

    // === CORE HASH FUNCTIONS (GIỮ NGUYÊN BẢN CHẤT) ===

    static inline uint32_t HashBytes(const uint8_t* data, int len) {
        uint32_t h = FNV_OFFSET_BASIS;
        for (int i = 0; i < len; ++i) {
            uint8_t c = data[i];
            if (c >= 'A' && c <= 'Z') c += 32;
            h = (h ^ c) * FNV_PRIME;
        }
        return h;
    }

    static inline uint32_t HashBuffer(const uint8_t* buffer, int start, int len, int bufSize) {
        uint32_t h = FNV_OFFSET_BASIS;
        for (int i = 0; i < len; ++i) {
            uint8_t c = buffer[(start + i) % bufSize];
            if (c >= 'A' && c <= 'Z') c += 32;
            h = (h ^ c) * FNV_PRIME;
        }
        return h;
    }

    // === NEW: HASH KÈM AUTO-TRIM (ĐỂ XỬ LÝ LỖI KHOẢNG TRẮNG) ===
    static inline uint32_t HashBufferTrimmed(const uint8_t* buffer, int start, int len, int bufSize) {
        // Loại bỏ ký tự <= 32 (space, \r, \n, \t) ở đầu và cuối
        while (len > 0 && buffer[start % bufSize] <= 32) { start++; len--; }
        while (len > 0 && buffer[(start + len - 1) % bufSize] <= 32) { len--; }

        if (len <= 0) return 0;

        uint32_t h = FNV_OFFSET_BASIS;
        for (int i = 0; i < len; ++i) {
            uint8_t c = buffer[(start + i) % bufSize];
            if (c >= 'A' && c <= 'Z') c += 32;
            h = (h ^ c) * FNV_PRIME;
        }
        return h;
    }

    // === COMPILE-TIME HASH (GIỮ NGUYÊN) ===
    template<size_t N>
    static constexpr uint32_t CT_Hash(const char(&str)[N]) {
        uint32_t h = FNV_OFFSET_BASIS;
        for (size_t i = 0; i < N - 1; ++i) {
            uint8_t c = (uint8_t)str[i];
            if (c >= 'A' && c <= 'Z') c += 32;
            h = (h ^ c) * FNV_PRIME;
        }
        return h;
    }

    static constexpr uint32_t CT_Hash(const char* str, int len) {
        uint32_t h = FNV_OFFSET_BASIS;
        for (int i = 0; i < len; ++i) {
            uint8_t c = (uint8_t)str[i];
            if (c >= 'A' && c <= 'Z') c += 32;
            h = (h ^ c) * FNV_PRIME;
        }
        return h;
    }

    // === RUNTIME HASH (GIỮ NGUYÊN VÀ CẬP NHẬT OVERLOAD) ===
    static inline uint32_t Hash(const char* str) {
        uint32_t h = FNV_OFFSET_BASIS;
        for (int i = 0; str[i]; ++i) {
            uint8_t c = (uint8_t)str[i];
            if (c >= 'A' && c <= 'Z') c += 32;
            h = (h ^ c) * FNV_PRIME;
        }
        return h;
    }

    static inline uint32_t Hash(const char* str, int len) {
        return HashBytes((const uint8_t*)str, len);
    }

    static inline uint32_t Hash(const uint8_t* buffer, int start, int len) {
        return HashBytes(buffer + start, len);
    }

    // Cập nhật hàm 4 tham số để mặc định sử dụng Trimmed (Tránh lỗi dự án)
    static inline uint32_t Hash(const uint8_t* buffer, int start, int len, int bufSize) {
        return HashBufferTrimmed(buffer, start, len, bufSize);
    }

    // === TIỆN ÍCH BỔ SUNG (GIỮ NGUYÊN) ===
    static inline uint32_t Hash(const std::string& str) {
        return HashBytes((const uint8_t*)str.c_str(), (int)str.length());
    }

    static inline bool Equals(const uint8_t* buffer, int start, int len, int bufSize, const char* str) {
        // Cập nhật Equals để cũng tự động Trim khi so sánh
        while (len > 0 && buffer[start % bufSize] <= 32) { start++; len--; }
        while (len > 0 && buffer[(start + len - 1) % bufSize] <= 32) { len--; }

        if (len != (int)strlen(str)) return false;
        for (int i = 0; i < len; ++i) {
            uint8_t c1 = buffer[(start + i) % bufSize];
            uint8_t c2 = (uint8_t)str[i];
            if (c1 >= 'A' && c1 <= 'Z') c1 += 32;
            if (c2 >= 'A' && c2 <= 'Z') c2 += 32;
            if (c1 != c2) return false;
        }
        return true;
    }
};

#define HASH_CT(str) FastHash::CT_Hash(str)
#define HASH(...) FastHash::Hash(__VA_ARGS__)
