// MainWindow.h
#pragma once
#include <Windows.h>
#include <string>
#include "TrayIcon.h"

class MainWindow
{
public:
    MainWindow(HINSTANCE hInstance);
    ~MainWindow();

    bool Create();
    HWND GetHandle() const { return m_hWnd; }

    // Cập nhật UI
    void UpdateServerStatus(bool running);
    void UpdateConnectionStats(int total, int tcp, int bt);
    void UpdateIPAddress(const std::string& ip);
    void UpdatePasswordDisplay();
    void UpdateBluetoothDisplay();
    void UpdatePortsDisplay();
    void RefreshAllUI();

    // Log
    void AppendLog(const std::string& message);
    void ClearLog();

    // Getters
    bool IsServerRunning() const { return m_serverRunning; }

    // Tray events
    void OnTrayStartServer();
    void OnTrayStopServer();
    void OnTrayShowWindow();
    void OnTrayExit();
    void OnTrayAutoStartChanged(bool checked);
    void OnTrayAutoRunChanged(bool checked);
    void OnTrayDoubleClick();

private:
    // Window procedure
    static LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam);
    LRESULT HandleMessage(UINT msg, WPARAM wParam, LPARAM lParam);

    // Tạo controls
    void CreateControls();
    void LayoutControls();
    void ApplyFonts();

    // Command handlers
    void OnStartStopServer();
    void OnClearLog();
    void OnTogglePassword();
    void OnToggleBluetooth();
    void OnRefreshIP();
    void OnEditPorts();
    void OnEditUUID();
    void OnEditPassword();
    void OnAutoRunChanged();
    void OnAutoStartChanged();
    void OnTimerUpdate();

    // Dialogs
    void ShowEditPortsDialog();
    void ShowEditUUIDDialog();
    void ShowEditPasswordDialog();

private:
    HINSTANCE m_hInstance;
    HWND m_hWnd;

    // Controls - HÀNG 1
    HWND m_lblIP;
    HWND m_lblIPValue;
    HWND m_btnWiFi;

    // HÀNG 2
    HWND m_lblPort;
    HWND m_lblPortValue;
    HWND m_btnStartStop;

    // HÀNG 3
    HWND m_lblPassword;
    HWND m_lblPasswordValue;
    HWND m_btnPassword;

    // HÀNG 4
    HWND m_lblBluetooth;
    HWND m_lblBluetoothValue;
    HWND m_btnBluetooth;

    // HÀNG 5
    HWND m_chkAutoRun;
    HWND m_chkAutoStart;
    HWND m_btnClearLog;

    // Statistics
    HWND m_grpStatistics;
    HWND m_lblConnTotal;
    HWND m_lblConnTCP;
    HWND m_lblConnBT;

    // Log
    HWND m_editLog;

    // Status
    HWND m_lblStatus;
    HWND m_lblVersion;

    // Fonts
    HFONT m_hFont;
    HFONT m_hFontBold;
    HFONT m_hFontConsolas;

    // State
    bool m_serverRunning;
    bool m_isExiting;
    int m_connectionTotal;
    int m_connectionTCP;
    int m_connectionBT;
};