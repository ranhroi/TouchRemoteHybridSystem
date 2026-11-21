#pragma once
#include "pch.h"


class MouseTracker {
private:
    inline static int lastX = -1;
    inline static int lastY = -1;
    inline static uint8_t lastShape = 255;
    inline static int lastScreenW = 0;
    inline static int lastScreenH = 0;

    // Hàm bổ trợ chuyển đổi float sang định dạng mạng Big Endian
    static uint32_t htonf(float value) {
        uint32_t temp;
        std::memcpy(&temp, &value, 4);
        return htonl(temp);
    }

public:
    static std::vector<uint8_t> captureStateAndPack() {
        std::vector<uint8_t> packedData;

        // ===== 1. Lấy hình dáng con trỏ chuột (Phương pháp so sánh an toàn bằng ID) =====
        uint8_t currentShape = 0;
        CURSORINFO ci = { 0 };
        ci.cbSize = sizeof(CURSORINFO);

        if (GetCursorInfo(&ci) && (ci.flags & CURSOR_SHOWING)) {
            // Lấy thông tin chi tiết của Cursor để lấy ID thay vì Handle vùng nhớ
            ICONINFO iconInfo = { 0 };
            if (GetIconInfo(ci.hCursor, &iconInfo)) {
                // Xóa các bitmap tạm để tránh rò rỉ bộ nhớ (Memory Leak) của Windows
                if (iconInfo.hbmColor) DeleteObject(iconInfo.hbmColor);
                if (iconInfo.hbmMask) DeleteObject(iconInfo.hbmMask);
            }

            // Giải pháp chuẩn: Kiểm tra và so sánh trực tiếp Handle tĩnh từ hệ thống
            static HCURSOR hArrow = LoadCursor(NULL, IDC_ARROW);
            static HCURSOR hIBeam = LoadCursor(NULL, IDC_IBEAM);
            static HCURSOR hHand = LoadCursor(NULL, IDC_HAND);
            static HCURSOR hWait = LoadCursor(NULL, IDC_WAIT);
            static HCURSOR hCross = LoadCursor(NULL, IDC_CROSS);
            static HCURSOR hSizeAll = LoadCursor(NULL, IDC_SIZEALL);
            static HCURSOR hSizeNWSE = LoadCursor(NULL, IDC_SIZENWSE);
            static HCURSOR hSizeNESW = LoadCursor(NULL, IDC_SIZENESW);
            static HCURSOR hSizeWE = LoadCursor(NULL, IDC_SIZEWE);
            static HCURSOR hSizeNS = LoadCursor(NULL, IDC_SIZENS);
            static HCURSOR hUpArrow = LoadCursor(NULL, IDC_UPARROW);

            if (ci.hCursor == hArrow) currentShape = 0;
            else if (ci.hCursor == hIBeam) currentShape = 4;
            else if (ci.hCursor == hHand) currentShape = 2;
            else if (ci.hCursor == hWait) currentShape = 3;
            else if (ci.hCursor == hCross) currentShape = 6;
            else if (ci.hCursor == hSizeAll) currentShape = 1;
            else if (ci.hCursor == hSizeNWSE || ci.hCursor == hSizeNESW) currentShape = 5;
            else if (ci.hCursor == hSizeWE) currentShape = 7;
            else if (ci.hCursor == hSizeNS) currentShape = 8;
            else if (ci.hCursor == hUpArrow) currentShape = 13;
        }


        // ===== 2. Lấy tọa độ chuột và kích thước màn hình =====
        POINT pt;
        GetCursorPos(&pt);
        int currentX = pt.x;
        int currentY = pt.y;

        int screenWidth = GetSystemMetrics(SM_CXSCREEN);
        int screenHeight = GetSystemMetrics(SM_CYSCREEN);

        // Chuyển sang tỷ lệ 0-1
        float floatX = static_cast<float>(currentX) / screenWidth;
        float floatY = static_cast<float>(currentY) / screenHeight;

        // Clamp để tránh sai số tràn biên
        if (floatX < 0.0f) floatX = 0.0f;
        if (floatX > 1.0f) floatX = 1.0f;
        if (floatY < 0.0f) floatY = 0.0f;
        if (floatY > 1.0f) floatY = 1.0f;

        // ===== 3. Kiểm tra thay đổi =====
        bool positionChanged = (currentX != lastX || currentY != lastY);
        bool shapeChanged = (currentShape != lastShape);
        bool screenChanged = (screenWidth != lastScreenW || screenHeight != lastScreenH);

        if (positionChanged || shapeChanged || screenChanged) {

            // ===== THÊM LOG =====
            //std::cout << "[MOUSE] Sending: x=" << floatX
            //    << ", y=" << floatY
            //    << ", shape=" << (int)currentShape
            //    << ", screen=" << screenWidth << "x" << screenHeight << "\n";

            // Gói tin: [1 byte header] + [4 bytes screenW] + [4 bytes screenH] + [4 bytes x] + [4 bytes y] + [1 byte shape]
            // Tổng = 18 bytes
            packedData.resize(18);
            packedData[0] = 0x09; // ID gói tin chuột

            // Ghi kích thước màn hình (Big Endian)
            uint32_t swBE = htonl(static_cast<uint32_t>(screenWidth));
            uint32_t shBE = htonl(static_cast<uint32_t>(screenHeight));
            memcpy(packedData.data() + 1, &swBE, 4);
            memcpy(packedData.data() + 5, &shBE, 4);

            // Ghi tọa độ tỷ lệ (Big Endian)
            uint32_t xBE = htonf(floatX);
            uint32_t yBE = htonf(floatY);
            memcpy(packedData.data() + 9, &xBE, 4);
            memcpy(packedData.data() + 13, &yBE, 4);

            // Ghi shape
            packedData[17] = currentShape;

            // Cập nhật last values
            lastX = currentX;
            lastY = currentY;
            lastShape = currentShape;
            lastScreenW = screenWidth;
            lastScreenH = screenHeight;
        }

        return packedData;
    }
};