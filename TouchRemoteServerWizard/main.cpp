// main.cpp
#include <Windows.h>
#include <commctrl.h>
#include <mmsystem.h>          // ✅ THÊM: cho timeBeginPeriod
#include "MainWindow.h"
#include "ServerManager.h"
#include "TrayIcon.h"
#include "AppSettings.h"

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "winmm.lib")   // ✅ THÊM: link winmm

// ===== GLOBAL =====
HINSTANCE g_hInstance = NULL;
ServerManager* g_serverManager = NULL;
TrayIcon* g_trayIcon = NULL;
MainWindow* g_mainWindow = NULL;
HANDLE g_hMutex = NULL;

// ===== ENTRY POINT =====
int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
    LPWSTR lpCmdLine, int nCmdShow)
{
    // ===== 1. TĂNG ĐỘ PHÂN GIẢI TIMER LÊN 1MS =====
    // ✅ BẮT BUỘC cho app chuột — giảm sleep/select từ 15.6ms → 1ms
    timeBeginPeriod(1);

    // ===== 2. SINGLE INSTANCE =====
    g_hMutex = CreateMutexW(NULL, TRUE, L"TouchRemoteServer_SingleInstance");
    if (g_hMutex == NULL)
    {
        MessageBoxW(NULL, L"Cannot create mutex!", L"Error", MB_OK | MB_ICONERROR);
        timeEndPeriod(1);
        return 1;
    }

    if (GetLastError() == ERROR_ALREADY_EXISTS)
    {
        // Đã có instance đang chạy
        HWND hWnd = FindWindowW(L"TouchRemoteServerClass", L"Touch Remote Server Control");
        if (hWnd != NULL)
        {
            SetForegroundWindow(hWnd);
            ShowWindow(hWnd, SW_RESTORE);
            FlashWindow(hWnd, TRUE);
        }
        else
        {
            MessageBoxW(NULL,
                L"Application is already running!\n"
                L"Please check the system tray.",
                L"Touch Remote Server",
                MB_OK | MB_ICONINFORMATION);
        }

        CloseHandle(g_hMutex);
        timeEndPeriod(1);
        return 0;
    }

    // ===== 3. INIT COMMON CONTROLS =====
    INITCOMMONCONTROLSEX icc;
    icc.dwSize = sizeof(icc);
    icc.dwICC = ICC_WIN95_CLASSES | ICC_STANDARD_CLASSES;
    InitCommonControlsEx(&icc);

    g_hInstance = hInstance;

    // ===== 4. LOAD SETTINGS =====
    AppSettings::Load();

    // ===== 5. KHỞI TẠO SERVERMANAGER =====
    g_serverManager = new ServerManager();

    // ===== 6. TẠO MAIN WINDOW =====
    g_mainWindow = new MainWindow(g_hInstance);
    if (!g_mainWindow->Create())
    {
        MessageBoxW(NULL, L"Failed to create window!", L"Error", MB_OK | MB_ICONERROR);
        delete g_mainWindow;
        g_mainWindow = NULL;
        timeEndPeriod(1);
        return 1;
    }

    // Refresh UI để hiển thị settings đã load
    g_mainWindow->RefreshAllUI();

    // ===== 7. TẠO TRAY ICON =====
    g_trayIcon = new TrayIcon(g_mainWindow->GetHandle(), g_hInstance);
    g_trayIcon->Create();

    // Đồng bộ trạng thái ban đầu
    g_trayIcon->UpdateAutoStartServer(AppSettings::AutoStartServer);
    g_trayIcon->UpdateAutoRunWithWindows(AppSettings::AutoRunWithWindows);

    // ===== 8. AUTO START SERVER =====
    if (AppSettings::AutoStartServer)
    {
        if (g_serverManager->Start())
        {
            g_trayIcon->UpdateServerStatus(true);
            g_mainWindow->UpdateServerStatus(true);
        }
    }

    // ===== 9. MESSAGE LOOP =====
    MSG msg = { 0 };
    while (GetMessageW(&msg, NULL, 0, 0))
    {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    // ===== 10. CLEANUP =====
    if (g_serverManager != NULL)
    {
        if (g_serverManager->IsRunning())
        {
            g_serverManager->Stop();
        }
        delete g_serverManager;
        g_serverManager = NULL;
    }

    if (g_trayIcon != NULL)
    {
        delete g_trayIcon;
        g_trayIcon = NULL;
    }

    if (g_mainWindow != NULL)
    {
        delete g_mainWindow;
        g_mainWindow = NULL;
    }

    if (g_hMutex != NULL)
    {
        ReleaseMutex(g_hMutex);
        CloseHandle(g_hMutex);
        g_hMutex = NULL;
    }

    // ===== 11. TRẢ LẠI TIMER MẶC ĐỊNH =====
    timeEndPeriod(1);

    return (int)msg.wParam;
}