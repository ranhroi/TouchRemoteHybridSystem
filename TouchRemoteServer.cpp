#include <iostream>
#include <thread>
#include <chrono>
#include <string>
#include <cstring>
#include <atomic>
#include <csignal>
#include <iomanip>
#include <windows.h>
#include "HybridSocket.h"
#include "RemoteServerConfig.h"
#include "resource.h" 
#pragma execution_character_set("utf-8")

HWND hConsoleWnd = NULL;

    static std::atomic<bool> g_running{ true };
    static std::chrono::steady_clock::time_point g_startTime;

    void signalHandler(int signal) {
        if (signal == SIGINT || signal == SIGTERM) {
            std::cout << "\n[SIGNAL] Đang nhận tín hiệu Ctrl+C, tiến hành dừng server..." << std::endl;
            g_running = false;
            RemoteServer_Stop();
            RemoteServer_Cleanup();
        }
    }


    // ==================== MAIN ====================

    int main() {

        // Load custom app icon
        HINSTANCE hInstance = GetModuleHandle(NULL);
        HICON hCustomIcon = LoadIcon(hInstance, MAKEINTRESOURCE(IDI_ICON1));
        if (hCustomIcon != NULL) {
            SendMessage(hConsoleWnd, WM_SETICON, ICON_BIG, (LPARAM)hCustomIcon);
            SendMessage(hConsoleWnd, WM_SETICON, ICON_SMALL, (LPARAM)hCustomIcon);
        }

        std::signal(SIGINT, signalHandler);
        std::signal(SIGTERM, signalHandler);

        // Khởi tạo start time
        g_startTime = std::chrono::steady_clock::now();


        // 1. Cấu hình Server
        RemoteServerConfig config = {};
        initRemoteServerConfig(&config);

        config.tcpPort = 2028;
        config.udpPort = 2027;
        config.needPassword = true;
        config.password = "1989";
        config.enableBluetooth = true;
        config.bluetoothUuid = "c05efa2b-5e9f-4a39-9705-72ccf47d2eb9";

        std::cout << "[INIT] Đang khởi tạo các socket và tài nguyên..." << std::endl;

        if (!RemoteServer_Start(&config)) {
            std::cerr << "[ERROR] Khởi chạy Server thất bại!" << std::endl;
            return 1;
        }

        std::cout << "[SUCCESS] Server đang chạy ngầm." << std::endl;
        std::cout << "=========================================" << std::endl;
        std::cout << "  Nhấn Ctrl+C để thoát." << std::endl;
        std::cout << "=========================================" << std::endl;

        // Biến đếm
        const int STATS_INTERVAL = 5;  // Hiển thị stats mỗi 5 giây

        while (g_running) {
            // Đọc log từ server
            const char* logLine = RemoteServer_PopLog();
            if (logLine && strlen(logLine) > 0) {
                std::cout << logLine << std::endl;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
        }

        // Dọn dẹp
        std::cout << "[SHUTDOWN] Đang giải phóng tài nguyên hệ thống..." << std::endl;
        std::cout << "[EXIT] Server đã dừng hẳn. Tạm biệt!" << std::endl;

        return 0;
    };
