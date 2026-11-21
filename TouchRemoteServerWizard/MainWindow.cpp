#include "MainWindow.h"
#include "ServerManager.h"
#include "AppSettings.h"
#include "TrayIcon.h"
#include <commctrl.h>
#include <string>
#include <sstream>
#include <cstdio>
#include <thread> 

// ===== EXTERNAL =====
extern ServerManager* g_serverManager;
extern TrayIcon* g_trayIcon;

// ===== CONTROL IDs =====
#define ID_BTN_STARTSTOP     1001
#define ID_BTN_CLEARLOG      1002
#define ID_BTN_PASSWORD      1003
#define ID_BTN_BLUETOOTH     1004
#define ID_BTN_WIFI          1005
#define ID_LBL_IP            1010
#define ID_LBL_IP_VALUE      1011
#define ID_LBL_PORT          1012
#define ID_LBL_PORT_VALUE    1013
#define ID_LBL_PASSWORD      1014
#define ID_LBL_PASS_VALUE    1015
#define ID_LBL_BLUETOOTH     1016
#define ID_LBL_BT_VALUE      1017
#define ID_LBL_STATUS        1018
#define ID_LBL_VERSION       1019
#define ID_EDIT_LOG          1020
#define ID_CHK_AUTORUN       1030
#define ID_CHK_AUTOSTART     1031
#define ID_GRP_STATS         1040
#define ID_LBL_CONN_TOTAL    1041
#define ID_LBL_CONN_TCP      1042
#define ID_LBL_CONN_BT       1043

// ===== TIMER ID =====
#define TIMER_UPDATE_ID      1
#define TIMER_UPDATE_INTERVAL 500

// ===== COLORS =====
#define COLOR_GREEN          RGB(0, 150, 0)
#define COLOR_BLUE           RGB(0, 0, 200)
#define COLOR_ORANGE         RGB(200, 100, 0)
#define COLOR_GRAY           RGB(128, 128, 128)
#define COLOR_RED            RGB(200, 50, 50)

// ===== CONSTRUCTOR =====
MainWindow::MainWindow(HINSTANCE hInstance)
    : m_hInstance(hInstance)
    , m_hWnd(NULL)
    , m_serverRunning(false)
    , m_isExiting(false)
    , m_connectionTotal(0)
    , m_connectionTCP(0)
    , m_connectionBT(0)
{
    // TẠO FONTS
    m_hFont = CreateFontW(-12, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Microsoft Sans Serif");

    m_hFontBold = CreateFontW(-12, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");

    m_hFontConsolas = CreateFontW(-12, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Consolas");
}

MainWindow::~MainWindow()
{
    if (m_hFont) DeleteObject(m_hFont);
    if (m_hFontBold) DeleteObject(m_hFontBold);
    if (m_hFontConsolas) DeleteObject(m_hFontConsolas);
}

// ===== CREATE WINDOW =====
bool MainWindow::Create()
{
    // Đăng ký window class
    WNDCLASSEXW wc = { 0 };
    wc.cbSize = sizeof(WNDCLASSEXW);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = m_hInstance;
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    wc.lpszClassName = L"TouchRemoteServerClass";
    wc.hIcon = LoadIcon(m_hInstance, MAKEINTRESOURCE(101));
    wc.hIconSm = wc.hIcon;

    if (!RegisterClassExW(&wc))
        return false;

    // Tạo window với kích thước giống WinForms cũ
    m_hWnd = CreateWindowExW(
        0, L"TouchRemoteServerClass", L"Touch Remote Server Control",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        CW_USEDEFAULT, CW_USEDEFAULT, 618, 440,
        NULL, NULL, m_hInstance, this
    );

    if (!m_hWnd)
        return false;

    CreateControls();
    RefreshAllUI();

    // Tạo timer 500ms để cập nhật stats
    SetTimer(m_hWnd, TIMER_UPDATE_ID, TIMER_UPDATE_INTERVAL, NULL);

    ShowWindow(m_hWnd, SW_SHOW);
    UpdateWindow(m_hWnd);

    return true;
}

// ===== CREATE CONTROLS =====
// ===== CREATE CONTROLS =====
void MainWindow::CreateControls()
{
    // ===== HÀNG 1: ADDRESS + WiFi Button =====
    m_lblIP = CreateWindowW(L"STATIC", L"ADDRESS:",
        WS_CHILD | WS_VISIBLE | SS_RIGHT | SS_CENTERIMAGE,  // ← SS_RIGHT
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_IP, m_hInstance, NULL);

    m_lblIPValue = CreateWindowExW(WS_EX_STATICEDGE, L"STATIC", L"127.0.0.1",
        WS_CHILD | WS_VISIBLE | SS_CENTER | SS_NOTIFY | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_IP_VALUE, m_hInstance, NULL);

    m_btnWiFi = CreateWindowW(L"BUTTON", L"WiFi ON",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_BTN_WIFI, m_hInstance, NULL);

    // ===== HÀNG 2: TCP - UDP + Start/Stop Button =====
    m_lblPort = CreateWindowW(L"STATIC", L"TCP - UDP:",
        WS_CHILD | WS_VISIBLE | SS_RIGHT | SS_CENTERIMAGE,  // ← SS_RIGHT
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_PORT, m_hInstance, NULL);

    m_lblPortValue = CreateWindowExW(WS_EX_STATICEDGE, L"STATIC", L"",
        WS_CHILD | WS_VISIBLE | SS_CENTER | SS_NOTIFY | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_PORT_VALUE, m_hInstance, NULL);

    m_btnStartStop = CreateWindowW(L"BUTTON", L"Start",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_BTN_STARTSTOP, m_hInstance, NULL);

    // ===== HÀNG 3: PASSWORD + Button =====
    m_lblPassword = CreateWindowW(L"STATIC", L"PASS:",
        WS_CHILD | WS_VISIBLE | SS_RIGHT | SS_CENTERIMAGE,  // ← SS_RIGHT
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_PASSWORD, m_hInstance, NULL);

    m_lblPasswordValue = CreateWindowExW(WS_EX_STATICEDGE, L"STATIC", L"",
        WS_CHILD | WS_VISIBLE | SS_CENTER | SS_NOTIFY | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_PASS_VALUE, m_hInstance, NULL);

    m_btnPassword = CreateWindowW(L"BUTTON", L"PASS OFF",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_BTN_PASSWORD, m_hInstance, NULL);

    // ===== HÀNG 4: BLUETOOTH + Button =====
    m_lblBluetooth = CreateWindowW(L"STATIC", L"BT UUID:",
        WS_CHILD | WS_VISIBLE | SS_RIGHT | SS_CENTERIMAGE,  // ← SS_RIGHT
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_BLUETOOTH, m_hInstance, NULL);

    m_lblBluetoothValue = CreateWindowExW(WS_EX_STATICEDGE, L"STATIC", L"",
        WS_CHILD | WS_VISIBLE | SS_CENTER | SS_NOTIFY | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_BT_VALUE, m_hInstance, NULL);

    m_btnBluetooth = CreateWindowW(L"BUTTON", L"BT OFF",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_BTN_BLUETOOTH, m_hInstance, NULL);

    // ===== HÀNG 5: STATUS + CHECKBOXES + CLEAR LOG =====
    m_lblStatus = CreateWindowW(L"STATIC", L"Status: Idle",
        WS_CHILD | WS_VISIBLE | SS_RIGHT | SS_CENTERIMAGE,  // ← SS_RIGHT
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_STATUS, m_hInstance, NULL);

    m_chkAutoRun = CreateWindowW(L"BUTTON", L"Auto run with Windows",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_CHK_AUTORUN, m_hInstance, NULL);

    m_chkAutoStart = CreateWindowW(L"BUTTON", L"Auto start server",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_CHK_AUTOSTART, m_hInstance, NULL);

    m_btnClearLog = CreateWindowW(L"BUTTON", L"Clear Log",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_BTN_CLEARLOG, m_hInstance, NULL);

    // ===== STATISTICS GROUP =====
    m_grpStatistics = CreateWindowW(L"BUTTON", L"",
        WS_CHILD | WS_VISIBLE | BS_GROUPBOX,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_GRP_STATS, m_hInstance, NULL);

    m_lblConnTotal = CreateWindowW(L"STATIC", L"Total: 0",
        WS_CHILD | WS_VISIBLE | SS_LEFT | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_CONN_TOTAL, m_hInstance, NULL);

    m_lblConnTCP = CreateWindowW(L"STATIC", L"TCP: 0",
        WS_CHILD | WS_VISIBLE | SS_LEFT | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_CONN_TCP, m_hInstance, NULL);

    m_lblConnBT = CreateWindowW(L"STATIC", L"BT: 0",
        WS_CHILD | WS_VISIBLE | SS_LEFT | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_CONN_BT, m_hInstance, NULL);

    // ===== LOG =====
    m_editLog = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
        WS_CHILD | WS_VISIBLE | WS_VSCROLL | WS_HSCROLL |
        ES_MULTILINE | ES_READONLY | ES_AUTOVSCROLL | ES_AUTOHSCROLL,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_EDIT_LOG, m_hInstance, NULL);

    // ===== STATUS BAR =====
    m_lblVersion = CreateWindowW(L"STATIC", L"Version 1.0 - TouchRemoteServer",
        WS_CHILD | WS_VISIBLE | SS_CENTER | SS_CENTERIMAGE,
        0, 0, 0, 0, m_hWnd, (HMENU)ID_LBL_VERSION, m_hInstance, NULL);

    ApplyFonts();
    LayoutControls();
}

// ===== APPLY FONTS =====
void MainWindow::ApplyFonts()
{
    HWND normalControls[] = {
        m_lblIP, m_lblIPValue, m_lblPort, m_lblPortValue,
        m_lblPassword, m_lblPasswordValue, m_lblBluetooth, m_lblBluetoothValue,
        m_btnStartStop, m_btnClearLog, m_btnPassword, m_btnBluetooth, m_btnWiFi,
        m_chkAutoRun, m_chkAutoStart, m_lblStatus
    };
    for (int i = 0; i < sizeof(normalControls) / sizeof(HWND); i++)
    {
        SendMessageW(normalControls[i], WM_SETFONT, (WPARAM)m_hFont, TRUE);
    }

    // Font đậm cho statistics
    SendMessageW(m_grpStatistics, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    SendMessageW(m_lblConnTotal, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    SendMessageW(m_lblConnTCP, WM_SETFONT, (WPARAM)m_hFont, TRUE);
    SendMessageW(m_lblConnBT, WM_SETFONT, (WPARAM)m_hFont, TRUE);

    // Font Consolas cho log
    SendMessageW(m_editLog, WM_SETFONT, (WPARAM)m_hFontConsolas, TRUE);

    // Version font thường
    SendMessageW(m_lblVersion, WM_SETFONT, (WPARAM)m_hFont, TRUE);
}

// ===== LAYOUT CONTROLS =====
// ===== LAYOUT CONTROLS =====
void MainWindow::LayoutControls()
{
    // ===== KÍCH THƯỚC =====
    int labelX = 33;        // ← Đổi từ 73 → 33 (rộng hơn cho "TCP - UDP:")
    int labelW = 110;       // ← Đổi từ 70 → 110 (đủ cho mọi label)
    int valueX = 161;
    int valueW = 258;
    int btnX = 434;
    int btnW = 90;
    int btnH = 25;

    // ===== HÀNG 1: ADDRESS (y = 10) =====
    int row1Y = 10;
    MoveWindow(m_lblIP, labelX, row1Y + 3, labelW, 20, TRUE);
    MoveWindow(m_lblIPValue, valueX, row1Y, valueW, 25, TRUE);
    MoveWindow(m_btnWiFi, btnX, row1Y, btnW, btnH, TRUE);

    // ===== HÀNG 2: TCP - UDP (y = 47) =====
    int row2Y = 47;
    MoveWindow(m_lblPort, labelX, row2Y + 3, labelW, 20, TRUE);
    MoveWindow(m_lblPortValue, valueX, row2Y, valueW, 25, TRUE);
    MoveWindow(m_btnStartStop, btnX, row2Y, btnW, btnH, TRUE);

    // ===== HÀNG 3: PASSWORD (y = 84) =====
    int row3Y = 84;
    MoveWindow(m_lblPassword, labelX, row3Y + 3, labelW, 20, TRUE);
    MoveWindow(m_lblPasswordValue, valueX, row3Y, valueW, 25, TRUE);
    MoveWindow(m_btnPassword, btnX, row3Y, btnW, btnH, TRUE);

    // ===== HÀNG 4: BLUETOOTH (y = 117) =====
    int row4Y = 117;
    MoveWindow(m_lblBluetooth, labelX, row4Y + 3, labelW, 20, TRUE);
    MoveWindow(m_lblBluetoothValue, valueX, row4Y, valueW, 25, TRUE);
    MoveWindow(m_btnBluetooth, btnX, row4Y, btnW, btnH, TRUE);

    // ===== HÀNG 5: STATUS + CHECKBOXES + CLEAR LOG (y = 154) =====
    int row5Y = 154;
    MoveWindow(m_lblStatus, labelX, row5Y + 5, labelW, 20, TRUE);  // ← Cùng labelX, labelW
    MoveWindow(m_chkAutoRun, valueX, row5Y + 5, 140, 20, TRUE);
    MoveWindow(m_chkAutoStart, valueX + 145, row5Y + 5, 110, 20, TRUE);
    MoveWindow(m_btnClearLog, btnX, row5Y + 2, btnW, btnH, TRUE);

    // ===== STATISTICS (y = 177) =====
    int statsY = 177;
    MoveWindow(m_grpStatistics, valueX, statsY, valueW, 35, TRUE);       // ← Rộng = 258
    MoveWindow(m_lblConnTotal, valueX + 10, statsY + 10, 80, 20, TRUE);  // Total: 0
    MoveWindow(m_lblConnTCP, valueX + 95, statsY + 10, 80, 20, TRUE);    // TCP: 0
    MoveWindow(m_lblConnBT, valueX + 180, statsY + 10, 60, 20, TRUE);    // BT: 0

    // ===== LOG (y = 215) =====
    RECT rc;
    GetClientRect(m_hWnd, &rc);
    int logY = 215;
    int logH = rc.bottom - logY - 30;
    if (logH < 80) logH = 80;
    MoveWindow(m_editLog, 12, logY, rc.right - 24, logH, TRUE);

    // ===== VERSION (DƯỚI CÙNG) =====
    int versionY = rc.bottom - 25;
    MoveWindow(m_lblVersion, 12, versionY, rc.right - 24, 20, TRUE);
}

// ===== WINDOW PROC =====
LRESULT CALLBACK MainWindow::WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam)
{
    MainWindow* pThis = NULL;

    if (msg == WM_NCCREATE)
    {
        CREATESTRUCT* pCreate = (CREATESTRUCT*)lParam;
        pThis = (MainWindow*)pCreate->lpCreateParams;
        SetWindowLongPtrW(hWnd, GWLP_USERDATA, (LONG_PTR)pThis);
        pThis->m_hWnd = hWnd;
    }
    else
    {
        pThis = (MainWindow*)GetWindowLongPtrW(hWnd, GWLP_USERDATA);
    }

    if (pThis)
        return pThis->HandleMessage(msg, wParam, lParam);

    return DefWindowProcW(hWnd, msg, wParam, lParam);
}

// ===== HANDLE MESSAGE =====
LRESULT MainWindow::HandleMessage(UINT msg, WPARAM wParam, LPARAM lParam)
{
    switch (msg)
    {
    case WM_COMMAND:
    {
        int id = LOWORD(wParam);
        int code = HIWORD(wParam);

        switch (id)
        {
        case ID_BTN_STARTSTOP:
            if (code == BN_CLICKED) OnStartStopServer();
            break;

        case ID_BTN_CLEARLOG:
            if (code == BN_CLICKED) OnClearLog();
            break;

        case ID_BTN_PASSWORD:
            if (code == BN_CLICKED) OnTogglePassword();
            break;

        case ID_BTN_BLUETOOTH:
            if (code == BN_CLICKED) OnToggleBluetooth();
            break;

        case ID_BTN_WIFI:
            if (code == BN_CLICKED) OnRefreshIP();
            break;

        case ID_LBL_IP_VALUE:
            if (code == STN_CLICKED) OnRefreshIP();
            break;

        case ID_LBL_PORT_VALUE:
            if (code == STN_CLICKED) OnEditPorts();
            break;

        case ID_LBL_PASS_VALUE:
            if (code == STN_CLICKED) OnEditPassword();
            break;

        case ID_LBL_BT_VALUE:
            if (code == STN_CLICKED) OnEditUUID();
            break;

        case ID_CHK_AUTORUN:
            if (code == BN_CLICKED) OnAutoRunChanged();
            break;

        case ID_CHK_AUTOSTART:
            if (code == BN_CLICKED) OnAutoStartChanged();
            break;
        }
    }
    break;

    case WM_TIMER:
        if (wParam == TIMER_UPDATE_ID)
            OnTimerUpdate();
        break;

    case WM_TRAYICON:
    {
        UINT trayMsg = (UINT)lParam;

        if (trayMsg == WM_RBUTTONUP)
        {
            POINT pt;
            GetCursorPos(&pt);
            SetForegroundWindow(m_hWnd);

            if (g_trayIcon != nullptr)
            {
                UINT cmd = TrackPopupMenu(
                    g_trayIcon->GetMenu(),
                    TPM_RETURNCMD | TPM_RIGHTBUTTON,
                    pt.x, pt.y, 0, m_hWnd, NULL);

                switch (cmd)
                {
                case TRAY_MENU_START_SERVER:  OnTrayStartServer(); break;
                case TRAY_MENU_STOP_SERVER:   OnTrayStopServer(); break;
                case TRAY_MENU_AUTO_START:
                    OnTrayAutoStartChanged(!g_trayIcon->IsAutoStartServerChecked());
                    break;
                case TRAY_MENU_AUTO_RUN:
                    OnTrayAutoRunChanged(!g_trayIcon->IsAutoRunWithWindowsChecked());
                    break;
                case TRAY_MENU_SHOW_WINDOW:   OnTrayShowWindow(); break;
                case TRAY_MENU_EXIT:          OnTrayExit(); break;
                }
            }
        }
        else if (trayMsg == WM_LBUTTONDBLCLK)
        {
            OnTrayDoubleClick();
        }
    }
    break;

    case WM_CTLCOLORSTATIC:
    {
        HDC hdc = (HDC)wParam;
        HWND hCtrl = (HWND)lParam;

        if (hCtrl == m_lblConnTotal)
        {
            SetTextColor(hdc, RGB(0, 120, 215));
            SetBkMode(hdc, TRANSPARENT);
            return (LRESULT)GetSysColorBrush(COLOR_BTNFACE);
        }
        else if (hCtrl == m_lblConnTCP)
        {
            SetTextColor(hdc, COLOR_GREEN);
            SetBkMode(hdc, TRANSPARENT);
            return (LRESULT)GetSysColorBrush(COLOR_BTNFACE);
        }
        else if (hCtrl == m_lblConnBT)
        {
            SetTextColor(hdc, COLOR_BLUE);
            SetBkMode(hdc, TRANSPARENT);
            return (LRESULT)GetSysColorBrush(COLOR_BTNFACE);
        }
        else if (hCtrl == m_lblStatus)
        {
            if (m_serverRunning)
                SetTextColor(hdc, COLOR_GREEN);
            else
                SetTextColor(hdc, COLOR_GRAY);
            SetBkMode(hdc, TRANSPARENT);
            return (LRESULT)GetSysColorBrush(COLOR_BTNFACE);
        }
    }
    break;

    case WM_ENDSESSION:
        if (g_serverManager && g_serverManager->IsRunning())
            g_serverManager->Stop();
        AppSettings::Save();
        break;

    case WM_QUERYENDSESSION:
        return TRUE;

    case WM_CLOSE:
        if (m_isExiting)
        {
            // Đang thoát từ OnTrayExit → đóng hẳn
            DestroyWindow(m_hWnd);
        }
        else
        {
            // User nhấn X → minimize to tray
            ShowWindow(m_hWnd, SW_HIDE);
        }
        return 0;

    case WM_DESTROY:
        KillTimer(m_hWnd, TIMER_UPDATE_ID);

        // Cleanup tray icon
        if (g_trayIcon != nullptr)
        {
            g_trayIcon->Destroy();
        }

        PostQuitMessage(0);
        return 0;

    case WM_SIZE:
        LayoutControls();
        break;
    }

    return DefWindowProcW(m_hWnd, msg, wParam, lParam);
}

// ===== TIMER UPDATE =====
void MainWindow::OnTimerUpdate()
{
    if (m_isExiting) return;

    if (g_serverManager == nullptr) return;

    // CẬP NHẬT SERVER STATUS
    bool running = g_serverManager->IsRunning();
    if (running != m_serverRunning)
    {
        UpdateServerStatus(running);
        if (g_trayIcon != nullptr)
            g_trayIcon->UpdateServerStatus(running);
    }

    // CẬP NHẬT CONNECTION STATS
    if (running)
    {
        int tcp = 0, bt = 0;
        g_serverManager->GetConnectionStats(&tcp, &bt);
        int total = tcp + bt;

        if (total != m_connectionTotal)
        {
            UpdateConnectionStats(total, tcp, bt);
            if (g_trayIcon != nullptr)
                g_trayIcon->UpdateConnectionCount(total, tcp, bt);
        }
    }

    // ĐỌC LOG
    std::string log = g_serverManager->PopLog();
    if (!log.empty())
    {
        AppendLog(log);
    }
}

// ===== COMMAND HANDLERS =====
void MainWindow::OnStartStopServer()
{
    if (m_serverRunning)
    {
        // STOP
        if (g_serverManager != nullptr)
            g_serverManager->Stop();

        m_serverRunning = false;
        SetWindowTextW(m_btnStartStop, L"Start");
        SetWindowTextW(m_lblStatus, L"Status: Stopped");
    }
    else
    {
        // START
        if (g_serverManager != nullptr && g_serverManager->Start())
        {
            m_serverRunning = true;
            SetWindowTextW(m_btnStartStop, L"Stop");
            SetWindowTextW(m_lblStatus, L"Status: Running");
        }
        else
        {
            MessageBoxW(m_hWnd,
                L"Failed to start server!\n\n"
                L"Please check:\n"
                L"- Ports are not in use\n"
                L"- Password is valid",
                L"Error",
                MB_OK | MB_ICONERROR);
        }
    }

    // Cập nhật Tray
    if (g_trayIcon != nullptr)
    {
        g_trayIcon->UpdateServerStatus(m_serverRunning);
    }

    InvalidateRect(m_lblStatus, NULL, TRUE);
}

void MainWindow::OnClearLog()
{
    SetWindowTextW(m_editLog, L"");
}

void MainWindow::OnTogglePassword()
{
    AppSettings::NeedPassword = !AppSettings::NeedPassword;

    if (AppSettings::NeedPassword)
    {
        if (AppSettings::Password.empty())
        {
            AppSettings::Password = AppSettings::GenerateRandomPassword(4);

            wchar_t msg[256];
            swprintf_s(msg, L"Default password: %S\nPlease remember!",
                AppSettings::Password.c_str());
            MessageBoxW(m_hWnd, msg, L"Default Password", MB_OK | MB_ICONINFORMATION);
        }
    }

    AppSettings::Save();
    UpdatePasswordDisplay();
}

void MainWindow::OnToggleBluetooth()
{
    AppSettings::BluetoothEnabled = !AppSettings::BluetoothEnabled;
    AppSettings::Save();
    UpdateBluetoothDisplay();
}

void MainWindow::OnRefreshIP()
{
    if (g_serverManager != nullptr)
    {
        std::string ip = g_serverManager->GetLocalIP();
        UpdateIPAddress(ip);
    }
}

void MainWindow::OnEditPorts()
{
    ShowEditPortsDialog();
}

void MainWindow::OnEditPassword()
{
    ShowEditPasswordDialog();
}

void MainWindow::OnEditUUID()
{
    if (!AppSettings::BluetoothEnabled)
    {
        MessageBoxW(m_hWnd,
            L"Please enable Bluetooth first to edit UUID!",
            L"Bluetooth Disabled",
            MB_OK | MB_ICONWARNING);
        return;
    }

    ShowEditUUIDDialog();
}

void MainWindow::OnAutoRunChanged()
{
    BOOL checked = SendMessageW(m_chkAutoRun, BM_GETCHECK, 0, 0);
    AppSettings::AutoRunWithWindows = (checked == BST_CHECKED);
    AppSettings::Save();

    if (g_trayIcon != nullptr)
        g_trayIcon->UpdateAutoRunWithWindows(AppSettings::AutoRunWithWindows);
}

void MainWindow::OnAutoStartChanged()
{
    BOOL checked = SendMessageW(m_chkAutoStart, BM_GETCHECK, 0, 0);
    AppSettings::AutoStartServer = (checked == BST_CHECKED);
    AppSettings::Save();

    if (g_trayIcon != nullptr)
        g_trayIcon->UpdateAutoStartServer(AppSettings::AutoStartServer);

    if (AppSettings::AutoStartServer && !m_serverRunning)
    {
        OnStartStopServer();
    }
}

// ===== UPDATE UI =====
void MainWindow::UpdateServerStatus(bool running)
{
    m_serverRunning = running;

    SetWindowTextW(m_btnStartStop, running ? L"Stop" : L"Start");
    SetWindowTextW(m_lblStatus, running ? L"Status: Running" : L"Status: Stopped");

    InvalidateRect(m_lblStatus, NULL, TRUE);
}

void MainWindow::UpdateConnectionStats(int total, int tcp, int bt)
{
    m_connectionTotal = total;
    m_connectionTCP = tcp;
    m_connectionBT = bt;

    wchar_t buf[64] = { 0 }; 

    swprintf_s(buf, 64, L"Total: %d", total);
    SetWindowTextW(m_lblConnTotal, buf);

    swprintf_s(buf, 64, L"TCP: %d", tcp);
    SetWindowTextW(m_lblConnTCP, buf);

    swprintf_s(buf, 64, L"BT: %d", bt);
    SetWindowTextW(m_lblConnBT, buf);
}

void MainWindow::UpdateIPAddress(const std::string& ip)
{
    wchar_t wip[64] = { 0 };
    MultiByteToWideChar(CP_ACP, 0, ip.c_str(), -1, wip, 64);
    SetWindowTextW(m_lblIPValue, wip);
}

void MainWindow::UpdatePasswordDisplay()
{
    if (AppSettings::NeedPassword)
    {
        SetWindowTextW(m_btnPassword, L"PASS ON");

        wchar_t wpass[64] = { 0 };
        MultiByteToWideChar(CP_ACP, 0, AppSettings::Password.c_str(), -1, wpass, 64);
        SetWindowTextW(m_lblPasswordValue, wpass);
    }
    else
    {
        SetWindowTextW(m_btnPassword, L"PASS OFF");
        SetWindowTextW(m_lblPasswordValue, L"");
    }
}

void MainWindow::UpdateBluetoothDisplay()
{
    if (AppSettings::BluetoothEnabled)
    {
        SetWindowTextW(m_btnBluetooth, L"BT ON");

        wchar_t wuuid[64] = { 0 };
        MultiByteToWideChar(CP_ACP, 0, AppSettings::BluetoothUUID.c_str(), -1, wuuid, 64);
        SetWindowTextW(m_lblBluetoothValue, wuuid);
    }
    else
    {
        SetWindowTextW(m_btnBluetooth, L"BT OFF");
        SetWindowTextW(m_lblBluetoothValue, L"");
    }
}

void MainWindow::UpdatePortsDisplay()
{
    wchar_t buf[64];
    swprintf_s(buf, L"%d - %d", AppSettings::TCPPort, AppSettings::UDPPort);
    SetWindowTextW(m_lblPortValue, buf);
}

// ===== REFRESH ALL UI =====
void MainWindow::RefreshAllUI()
{
    UpdatePortsDisplay();
    UpdatePasswordDisplay();
    UpdateBluetoothDisplay();

    SendMessageW(m_chkAutoRun, BM_SETCHECK,
        AppSettings::AutoRunWithWindows ? BST_CHECKED : BST_UNCHECKED, 0);
    SendMessageW(m_chkAutoStart, BM_SETCHECK,
        AppSettings::AutoStartServer ? BST_CHECKED : BST_UNCHECKED, 0);

    OnRefreshIP();
    UpdateServerStatus(m_serverRunning);
}

// ===== LOG =====
void MainWindow::AppendLog(const std::string& message)
{
    if (message.empty()) return;

    int len = GetWindowTextLengthW(m_editLog);
    SendMessageW(m_editLog, EM_SETSEL, len, len);

    wchar_t wmsg[2048] = { 0 };
    MultiByteToWideChar(CP_ACP, 0, message.c_str(), -1, wmsg, 2048);

    std::wstring text = std::wstring(wmsg) + L"\r\n";
    SendMessageW(m_editLog, EM_REPLACESEL, FALSE, (LPARAM)text.c_str());
    SendMessageW(m_editLog, EM_SCROLLCARET, 0, 0);
}

void MainWindow::ClearLog()
{
    SetWindowTextW(m_editLog, L"");
}

// ===== TRAY EVENT HANDLERS =====
void MainWindow::OnTrayStartServer()
{
    if (!m_serverRunning)
        OnStartStopServer();
}

void MainWindow::OnTrayStopServer()
{
    if (m_serverRunning)
        OnStartStopServer();
}

void MainWindow::OnTrayShowWindow()
{
    ShowWindow(m_hWnd, SW_SHOW);
    ShowWindow(m_hWnd, SW_RESTORE);
    SetForegroundWindow(m_hWnd);
}

// ===== TRAY EVENT HANDLERS =====
void MainWindow::OnTrayExit()
{
    if (MessageBoxW(m_hWnd,
        L"Are you sure you want to exit?\n\nThe server will be stopped automatically.",
        L"Confirm Exit",
        MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2) == IDYES)
    {
        // ===== ĐÁNH DẤU ĐANG THOÁT =====
        m_isExiting = true;

        // ===== HIỂN THỊ TRẠNG THÁI =====
        SetWindowTextW(m_lblStatus, L"Status: Exiting...");
        SetWindowTextW(m_btnStartStop, L"Exiting...");

        // Disable tất cả controls
        EnableWindow(m_btnStartStop, FALSE);
        EnableWindow(m_btnClearLog, FALSE);
        EnableWindow(m_btnPassword, FALSE);
        EnableWindow(m_btnBluetooth, FALSE);
        EnableWindow(m_btnWiFi, FALSE);
        EnableWindow(m_chkAutoRun, FALSE);
        EnableWindow(m_chkAutoStart, FALSE);

        // Force update UI ngay lập tức
        UpdateWindow(m_hWnd);
        RedrawWindow(m_hWnd, NULL, NULL, RDW_UPDATENOW | RDW_ALLCHILDREN);

        // ===== DỪNG TIMER =====
        KillTimer(m_hWnd, TIMER_UPDATE_ID);

        // ===== ẨN TRAY ICON =====
        if (g_trayIcon != nullptr)
        {
            g_trayIcon->Destroy();
        }

        // ===== DÙNG THREAD ĐỂ DỪNG SERVER =====
        HWND hWndCopy = m_hWnd;
        std::thread([hWndCopy]() {
            // 1. Dừng server
            if (g_serverManager != nullptr && g_serverManager->IsRunning())
            {
                g_serverManager->Stop();
            }

            // 2. Lưu settings
            AppSettings::Save();

            // 3. Đóng window trên UI thread
            PostMessageW(hWndCopy, WM_CLOSE, 0, 0);
            }).detach();  // ← Thread tự hủy sau khi xong
    }
}

void MainWindow::OnTrayAutoStartChanged(bool checked)
{
    SendMessageW(m_chkAutoStart, BM_SETCHECK,
        checked ? BST_CHECKED : BST_UNCHECKED, 0);
    AppSettings::AutoStartServer = checked;
    AppSettings::Save();

    if (g_trayIcon != nullptr)
        g_trayIcon->UpdateAutoStartServer(checked);
}

void MainWindow::OnTrayAutoRunChanged(bool checked)
{
    SendMessageW(m_chkAutoRun, BM_SETCHECK,
        checked ? BST_CHECKED : BST_UNCHECKED, 0);
    AppSettings::AutoRunWithWindows = checked;
    AppSettings::Save();

    if (g_trayIcon != nullptr)
        g_trayIcon->UpdateAutoRunWithWindows(checked);
}

void MainWindow::OnTrayDoubleClick()
{
    OnTrayShowWindow();
}

// ===== DIALOGS =====
void MainWindow::ShowEditPortsDialog()
{
    MessageBoxW(m_hWnd, L"Edit Ports dialog - Coming soon!", L"Info", MB_OK);
}

void MainWindow::ShowEditUUIDDialog()
{
    MessageBoxW(m_hWnd, L"Edit UUID dialog - Coming soon!", L"Info", MB_OK);
}

// ===== SHOW EDIT PASSWORD DIALOG =====
void MainWindow::ShowEditPasswordDialog()
{
    if (!AppSettings::NeedPassword)
    {
        MessageBoxW(m_hWnd,
            L"Password is disabled!\n\n"
            L"Please enable Password first.",
            L"Info",
            MB_OK | MB_ICONINFORMATION);
        return;
    }

    // ===== TẠO DIALOG ĐƠN GIẢN =====
    // Tạo dialog window
    HWND hDialog = CreateWindowExW(
        WS_EX_DLGMODALFRAME,
        L"#32770",  // Dialog class
        L"Edit Password",
        WS_POPUP | WS_CAPTION | WS_SYSMENU | DS_MODALFRAME,
        CW_USEDEFAULT, CW_USEDEFAULT, 350, 150,
        m_hWnd, NULL, m_hInstance, NULL
    );

    if (hDialog == NULL) return;

    // Center dialog
    RECT rcParent, rcDialog;
    GetWindowRect(m_hWnd, &rcParent);
    GetWindowRect(hDialog, &rcDialog);
    int x = rcParent.left + (rcParent.right - rcParent.left - (rcDialog.right - rcDialog.left)) / 2;
    int y = rcParent.top + (rcParent.bottom - rcParent.top - (rcDialog.bottom - rcDialog.top)) / 2;
    SetWindowPos(hDialog, NULL, x, y, 0, 0, SWP_NOSIZE | SWP_NOZORDER);

    // Label
    CreateWindowW(L"STATIC", L"Password:",
        WS_CHILD | WS_VISIBLE | SS_RIGHT,
        15, 20, 70, 20, hDialog, NULL, m_hInstance, NULL);

    // Edit box
    HWND hEdit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
        WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL,
        95, 18, 220, 23, hDialog, (HMENU)100, m_hInstance, NULL);

    // Hiển thị password hiện tại
    wchar_t wpass[64] = { 0 };
    MultiByteToWideChar(CP_ACP, 0, AppSettings::Password.c_str(), -1, wpass, 64);
    SetWindowTextW(hEdit, wpass);

    // Set font
    SendMessageW(hEdit, WM_SETFONT, (WPARAM)m_hFont, TRUE);

    // Hint label
    CreateWindowW(L"STATIC", L"4-20 characters",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        95, 45, 220, 15, hDialog, NULL, m_hInstance, NULL);

    // OK button
    CreateWindowW(L"BUTTON", L"OK",
        WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON,
        140, 75, 80, 28, hDialog, (HMENU)IDOK, m_hInstance, NULL);

    // Cancel button
    CreateWindowW(L"BUTTON", L"Cancel",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        235, 75, 80, 28, hDialog, (HMENU)IDCANCEL, m_hInstance, NULL);

    // Show dialog
    ShowWindow(hDialog, SW_SHOW);
    SetFocus(hEdit);

    // ===== MESSAGE LOOP CHO DIALOG =====
    MSG msg;
    bool done = false;
    bool ok = false;

    while (!done && GetMessageW(&msg, NULL, 0, 0))
    {
        if (msg.message == WM_KEYDOWN && msg.wParam == VK_RETURN)
        {
            ok = true;
            done = true;
        }
        else if (msg.message == WM_KEYDOWN && msg.wParam == VK_ESCAPE)
        {
            done = true;
        }
        else if (msg.message == WM_COMMAND)
        {
            if (LOWORD(msg.wParam) == IDOK)
            {
                ok = true;
                done = true;
            }
            else if (LOWORD(msg.wParam) == IDCANCEL)
            {
                done = true;
            }
        }
        else if (msg.message == WM_CLOSE)
        {
            done = true;
        }

        if (IsDialogMessageW(hDialog, &msg))
            continue;

        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    // ===== LƯU PASSWORD NẾU OK =====
    if (ok)
    {
        wchar_t newPassword[64] = { 0 };
        GetWindowTextW(hEdit, newPassword, 64);

        if (wcslen(newPassword) < 4)
        {
            MessageBoxW(m_hWnd,
                L"Password must be at least 4 characters!",
                L"Error",
                MB_OK | MB_ICONERROR);
        }
        else
        {
            // Chuyển Wide → ANSI
            char ansiPassword[64] = { 0 };
            WideCharToMultiByte(CP_ACP, 0, newPassword, -1, ansiPassword, 64, NULL, NULL);

            AppSettings::Password = std::string(ansiPassword);
            AppSettings::Save();

            UpdatePasswordDisplay();

            MessageBoxW(m_hWnd, L"Password updated successfully!", L"Success", MB_OK | MB_ICONINFORMATION);
        }
    }

    DestroyWindow(hDialog);
}
