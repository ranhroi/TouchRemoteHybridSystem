// TrayIcon.cpp
#include "TrayIcon.h"
#include "resource.h"
#include <string>

// ===== CONSTRUCTOR =====
TrayIcon::TrayIcon(HWND hWnd, HINSTANCE hInstance)
    : m_hWnd(hWnd)
    , m_hInstance(hInstance)
    , m_hMenu(NULL)
    , m_autoStartChecked(false)
    , m_autoRunChecked(false)
{
    ZeroMemory(&m_nid, sizeof(m_nid));
}

// ===== DESTRUCTOR =====
TrayIcon::~TrayIcon()
{
    Destroy();
}

// ===== CREATE =====
void TrayIcon::Create()
{
    // ===== LOAD ICON =====
    HICON hIcon = LoadIconW(m_hInstance, MAKEINTRESOURCEW(IDI_ICON2));
    if (hIcon == NULL)
    {
        hIcon = LoadIconW(NULL, IDI_APPLICATION);
    }

    // ===== SETUP NOTIFYICONDATA =====
    m_nid.cbSize = sizeof(NOTIFYICONDATAW);
    m_nid.hWnd = m_hWnd;
    m_nid.uID = 1;
    m_nid.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
    m_nid.uCallbackMessage = WM_TRAYICON;
    m_nid.hIcon = hIcon;
    wcscpy_s(m_nid.szTip, L"Touch Remote Server\nStatus: Stopped");

    // ===== ADD ICON =====
    Shell_NotifyIconW(NIM_ADD, &m_nid);

    // ===== TẠO CONTEXT MENU =====
    m_hMenu = CreatePopupMenu();

    // Status (disabled)
    AppendMenuW(m_hMenu, MF_STRING | MF_DISABLED | MF_GRAYED,
        TRAY_MENU_STATUS, L"Server: Stopped");

    // Separator
    AppendMenuW(m_hMenu, MF_SEPARATOR, 0, NULL);

    // Connections (disabled)
    AppendMenuW(m_hMenu, MF_STRING | MF_DISABLED | MF_GRAYED,
        TRAY_MENU_CONNECTIONS, L"Connections: 0");

    // Separator
    AppendMenuW(m_hMenu, MF_SEPARATOR, 0, NULL);

    // Start/Stop Server
    AppendMenuW(m_hMenu, MF_STRING, TRAY_MENU_START_SERVER, L"Start Server");
    AppendMenuW(m_hMenu, MF_STRING | MF_GRAYED, TRAY_MENU_STOP_SERVER, L"Stop Server");

    // Separator
    AppendMenuW(m_hMenu, MF_SEPARATOR, 0, NULL);

    // Auto Start Server (checkable)
    AppendMenuW(m_hMenu, MF_STRING, TRAY_MENU_AUTO_START, L"Auto Start Server");

    // Auto Run With Windows (checkable)
    AppendMenuW(m_hMenu, MF_STRING, TRAY_MENU_AUTO_RUN, L"Auto Run With Windows");

    // Separator
    AppendMenuW(m_hMenu, MF_SEPARATOR, 0, NULL);

    // Show Window
    AppendMenuW(m_hMenu, MF_STRING, TRAY_MENU_SHOW_WINDOW, L"Show Window");

    // Exit
    AppendMenuW(m_hMenu, MF_STRING, TRAY_MENU_EXIT, L"Exit");
}

// ===== DESTROY =====
void TrayIcon::Destroy()
{
    if (m_nid.hWnd)
    {
        Shell_NotifyIconW(NIM_DELETE, &m_nid);
        m_nid.hWnd = NULL;
    }

    if (m_hMenu)
    {
        DestroyMenu(m_hMenu);
        m_hMenu = NULL;
    }
}

// ===== SHOW MESSAGE =====
void TrayIcon::ShowMessage(const wchar_t* title, const wchar_t* message)
{
    if (m_nid.hWnd == NULL) return;

    m_nid.uFlags = NIF_INFO;
    wcscpy_s(m_nid.szInfoTitle, title);
    wcscpy_s(m_nid.szInfo, message);
    m_nid.dwInfoFlags = NIIF_INFO;
    m_nid.uTimeout = 3000;

    Shell_NotifyIconW(NIM_MODIFY, &m_nid);

    // Reset flags
    m_nid.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
}

// ===== UPDATE SERVER STATUS =====
void TrayIcon::UpdateServerStatus(bool running)
{
    if (m_hMenu == NULL) return;

    // ===== CẬP NHẬT MENU STATUS =====
    ModifyMenuW(m_hMenu, TRAY_MENU_STATUS,
        MF_STRING | MF_BYCOMMAND | MF_DISABLED | MF_GRAYED,
        TRAY_MENU_STATUS,
        running ? L"Server: Running" : L"Server: Stopped");

    // ===== CẬP NHẬT START/STOP =====
    ModifyMenuW(m_hMenu, TRAY_MENU_START_SERVER,
        MF_STRING | MF_BYCOMMAND | (running ? MF_GRAYED : MF_ENABLED),
        TRAY_MENU_START_SERVER, L"Start Server");

    ModifyMenuW(m_hMenu, TRAY_MENU_STOP_SERVER,
        MF_STRING | MF_BYCOMMAND | (running ? MF_ENABLED : MF_GRAYED),
        TRAY_MENU_STOP_SERVER, L"Stop Server");

    // ===== CẬP NHẬT TOOLTIP =====
    if (running)
    {
        wcscpy_s(m_nid.szTip, L"Touch Remote Server\nStatus: Running");
    }
    else
    {
        wcscpy_s(m_nid.szTip, L"Touch Remote Server\nStatus: Stopped");
    }
}

// ===== UPDATE CONNECTION COUNT =====
void TrayIcon::UpdateConnectionCount(int total, int tcp, int bt)
{
    if (m_hMenu == NULL) return;

    wchar_t buf[128];

    // ===== CẬP NHẬT MENU =====
    if (total > 0)
    {
        swprintf_s(buf, L"Connections: %d (TCP:%d BT:%d)", total, tcp, bt);
    }
    else
    {
        wcscpy_s(buf, L"Connections: 0");
    }

    ModifyMenuW(m_hMenu, TRAY_MENU_CONNECTIONS,
        MF_STRING | MF_BYCOMMAND | MF_DISABLED | MF_GRAYED,
        TRAY_MENU_CONNECTIONS, buf);

    // ===== CẬP NHẬT TOOLTIP =====
    if (total > 0)
    {
        swprintf_s(m_nid.szTip,
            L"Touch Remote Server\nTotal: %d\nTCP: %d\nBT: %d",
            total, tcp, bt);
    }
    else
    {
        wcscpy_s(m_nid.szTip, L"Touch Remote Server\nNo connections");
    }

    // Update icon
    Shell_NotifyIconW(NIM_MODIFY, &m_nid);
}

// ===== UPDATE AUTO START =====
void TrayIcon::UpdateAutoStartServer(bool enabled)
{
    if (m_hMenu == NULL) return;

    m_autoStartChecked = enabled;

    CheckMenuItem(m_hMenu, TRAY_MENU_AUTO_START,
        MF_BYCOMMAND | (enabled ? MF_CHECKED : MF_UNCHECKED));
}

// ===== UPDATE AUTO RUN =====
void TrayIcon::UpdateAutoRunWithWindows(bool enabled)
{
    if (m_hMenu == NULL) return;

    m_autoRunChecked = enabled;

    CheckMenuItem(m_hMenu, TRAY_MENU_AUTO_RUN,
        MF_BYCOMMAND | (enabled ? MF_CHECKED : MF_UNCHECKED));
}

// ===== GETTERS =====
bool TrayIcon::IsAutoStartServerChecked() const
{
    return m_autoStartChecked;
}

bool TrayIcon::IsAutoRunWithWindowsChecked() const
{
    return m_autoRunChecked;
}