#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"

namespace {
    // Helper function - chỉ dùng trong file này
    std::string GetTimestampFilename() {
        SYSTEMTIME st;
        GetLocalTime(&st);

        char buffer[100];
        sprintf_s(buffer, "ScreenCapture%04d%02d%02d_%02d%02d%02d_%03d.bmp",
            st.wYear, st.wMonth, st.wDay,
            st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);

        return std::string(buffer);
    }
}

// ==================== SCREEN CAPTURE ====================
void ProcessCommands::handleScreenCapture(const uint8_t* payload, int len, OpCode op) {
    if (op != OpCode::SCREENCAPTURE) return;

    // --- BẮT ĐẦU PHẦN XỬ LÝ ẨN CONSOLE ---
    HWND hConsole = GetConsoleWindow();
    ShowWindow(hConsole, SW_HIDE);
    ::Sleep(300);
    // -------------------------------------

    // 1. Lấy đường dẫn Desktop và tạo tên file theo thời gian
    char desktopPath[MAX_PATH];
    if (SHGetSpecialFolderPathA(NULL, desktopPath, CSIDL_DESKTOP, FALSE)) {
        std::string fileName = GetTimestampFilename();
        std::string fullPath = std::string(desktopPath) + "\\" + fileName;

        // 2. Thiết lập thông số màn hình
        int width = GetSystemMetrics(SM_CXSCREEN);
        int height = GetSystemMetrics(SM_CYSCREEN);

        HDC hScreenDC = GetDC(NULL);
        if (hScreenDC) {
            HDC hMemoryDC = CreateCompatibleDC(hScreenDC);
            if (hMemoryDC) {
                HBITMAP hBitmap = CreateCompatibleBitmap(hScreenDC, width, height);
                if (hBitmap) {
                    HGDIOBJ hOldBitmap = SelectObject(hMemoryDC, hBitmap);

                    // 3. Chụp ảnh màn hình
                    BitBlt(hMemoryDC, 0, 0, width, height, hScreenDC, 0, 0, SRCCOPY);

                    // 4. Lưu file BMP
                    BITMAPINFOHEADER bi = { sizeof(BITMAPINFOHEADER), width, height, 1, 32, BI_RGB };
                    BITMAPFILEHEADER bfh = {
                        0x4d42,
                        (DWORD)(sizeof(BITMAPFILEHEADER) + sizeof(BITMAPINFOHEADER) + (width * height * 4)),
                        0, 0,
                        (DWORD)(sizeof(BITMAPFILEHEADER) + sizeof(BITMAPINFOHEADER))
                    };

                    HANDLE hFile = CreateFileA(fullPath.c_str(), GENERIC_WRITE, 0, NULL,
                        CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
                    if (hFile != INVALID_HANDLE_VALUE) {
                        DWORD dwWritten;
                        WriteFile(hFile, &bfh, sizeof(bfh), &dwWritten, NULL);
                        WriteFile(hFile, &bi, sizeof(bi), &dwWritten, NULL);

                        DWORD* pPixels = new DWORD[width * height];
                        if (GetDIBits(hMemoryDC, hBitmap, 0, height, pPixels, (BITMAPINFO*)&bi, DIB_RGB_COLORS)) {
                            WriteFile(hFile, pPixels, width * height * 4, &dwWritten, NULL);
                        }
                        delete[] pPixels;
                        CloseHandle(hFile);

                        LOG_DEBUG("[SUCCESS] Screen captured: %s", fileName.c_str());
                    }

                    SelectObject(hMemoryDC, hOldBitmap);
                    DeleteObject(hBitmap);
                }
                DeleteDC(hMemoryDC);
            }
            ReleaseDC(NULL, hScreenDC);
        }
    }

    // --- HIỆN LẠI CONSOLE ---
    ShowWindow(GetConsoleWindow(), SW_SHOW);
}