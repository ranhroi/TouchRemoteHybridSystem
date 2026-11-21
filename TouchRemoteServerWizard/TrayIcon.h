// TrayIcon.h
#pragma once
#include <Windows.h>
#include <shellapi.h>

#define WM_TRAYICON (WM_USER + 1)

// ===== TRAY MENU IDs =====
#define TRAY_MENU_STATUS         1
#define TRAY_MENU_CONNECTIONS    2
#define TRAY_MENU_START_SERVER   3
#define TRAY_MENU_STOP_SERVER    4
#define TRAY_MENU_AUTO_START     5
#define TRAY_MENU_AUTO_RUN       6
#define TRAY_MENU_SHOW_WINDOW    7
#define TRAY_MENU_EXIT           8

class TrayIcon
{
public:
    TrayIcon(HWND hWnd, HINSTANCE hInstance);
    ~TrayIcon();

    void Create();
    void Destroy();
    void ShowMessage(const wchar_t* title, const wchar_t* message);

    HMENU GetMenu() const { return m_hMenu; }

    // ===== CẬP NHẬT UI =====
    void UpdateServerStatus(bool running);
    void UpdateConnectionCount(int total, int tcp, int bt);
    void UpdateAutoStartServer(bool enabled);
    void UpdateAutoRunWithWindows(bool enabled);

    // ===== GETTERS =====
    bool IsAutoStartServerChecked() const;
    bool IsAutoRunWithWindowsChecked() const;

private:
    HWND m_hWnd;
    HINSTANCE m_hInstance;
    NOTIFYICONDATAW m_nid;
    HMENU m_hMenu;

    // ===== MENU ITEMS =====
    // Không lưu riêng vì dùng HMENU
    bool m_autoStartChecked;
    bool m_autoRunChecked;
};