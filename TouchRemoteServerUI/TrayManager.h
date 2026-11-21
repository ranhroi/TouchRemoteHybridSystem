// TrayManager.h
#pragma once

#include <Windows.h>
#include "resource.h"

using namespace System;
using namespace System::Windows::Forms;
using namespace System::Drawing;
using namespace System::Runtime::InteropServices;

namespace TouchRemoteServerUI {

    /// <summary>
    /// Quản lý System Tray Icon và Context Menu
    /// </summary>
    public ref class TrayManager
    {
    public:
        TrayManager()
        {
            InitializeTrayIcon();
        }

        ~TrayManager()
        {
            if (trayIcon != nullptr)
            {
                trayIcon->Visible = false;
                delete trayIcon;
                trayIcon = nullptr;
            }

            if (contextMenuStrip != nullptr)
            {
                delete contextMenuStrip;
                contextMenuStrip = nullptr;
            }
        }

        // ===== Phương thức khởi tạo =====
        void InitializeTrayIcon()
        {
            if (trayIcon != nullptr)
            {
                delete trayIcon;
                trayIcon = nullptr;
            }

            trayIcon = gcnew NotifyIcon();
            trayIcon->Text = L"Touch Remote Server";
            trayIcon->Visible = true;

            // ===== CÁCH 1: DÙNG WIN32 RESOURCE =====
            try
            {
                // Lấy instance handle của ứng dụng
                HINSTANCE hInstance = GetModuleHandle(nullptr);

                // Load icon từ resource
                HICON hIcon = LoadIcon(hInstance, MAKEINTRESOURCE(IDI_ICON2));

                if (hIcon != nullptr)
                {
                    // Chuyển HICON sang System::Drawing::Icon
                    Icon^ icon = Icon::FromHandle(IntPtr(hIcon));

                    // Tạo bản sao để quản lý vòng đời
                    trayIcon->Icon = gcnew Icon(icon, 16, 16); // Resize cho tray

                    // Không destroy hIcon vì Icon::FromHandle đã tạo bản sao
                    // và HICON sẽ được quản lý bởi .NET
                }
                else
                {
                    // Fallback nếu không load được
                    trayIcon->Icon = SystemIcons::Application;
                }
            }
            catch (Exception^)
            {
                // Fallback khi có lỗi
                trayIcon->Icon = SystemIcons::Application;
            }

            // Tạo Context Menu
            CreateContextMenu();
        }

        // ===== TẠO CONTEXT MENU =====
        void CreateContextMenu()
        {
            // Tạo Context Menu
            contextMenuStrip = gcnew System::Windows::Forms::ContextMenuStrip();

            // Menu Items
            menuStatus = gcnew ToolStripMenuItem(L"Server: Stopped");
            menuStatus->Enabled = false;
            contextMenuStrip->Items->Add(menuStatus);

            separator1 = gcnew ToolStripSeparator();
            contextMenuStrip->Items->Add(separator1);

            menuConnectionCount = gcnew ToolStripMenuItem(L"Connections: 0");
            menuConnectionCount->Enabled = false;
            contextMenuStrip->Items->Add(menuConnectionCount);

            separator2 = gcnew ToolStripSeparator();
            contextMenuStrip->Items->Add(separator2);

            menuStartServer = gcnew ToolStripMenuItem(L"Start Server");
            contextMenuStrip->Items->Add(menuStartServer);

            menuStopServer = gcnew ToolStripMenuItem(L"Stop Server");
            menuStopServer->Enabled = false;
            contextMenuStrip->Items->Add(menuStopServer);

            separator3 = gcnew ToolStripSeparator();
            contextMenuStrip->Items->Add(separator3);

            menuAutoStartServer = gcnew ToolStripMenuItem(L"Auto Start Server");
            menuAutoStartServer->CheckOnClick = true;
            contextMenuStrip->Items->Add(menuAutoStartServer);

            menuAutoRunWithWindows = gcnew ToolStripMenuItem(L"Auto Run With Windows");
            menuAutoRunWithWindows->CheckOnClick = true;
            contextMenuStrip->Items->Add(menuAutoRunWithWindows);

            separator4 = gcnew ToolStripSeparator();
            contextMenuStrip->Items->Add(separator4);

            menuShowWindow = gcnew ToolStripMenuItem(L"Show Window");
            contextMenuStrip->Items->Add(menuShowWindow);

            menuExit = gcnew ToolStripMenuItem(L"Exit");
            contextMenuStrip->Items->Add(menuExit);

            trayIcon->ContextMenuStrip = contextMenuStrip;
        }

        // ===== Phương thức cập nhật UI =====
        void UpdateServerStatus(bool running)
        {
            if (menuStatus != nullptr)
            {
                menuStatus->Text = running ? L"Server: Running" : L"Server: Stopped";
                menuStatus->ForeColor = running ? Color::Green : Color::Gray;
            }

            if (menuStartServer != nullptr)
            {
                menuStartServer->Enabled = !running;
                menuStartServer->ForeColor = running ? Color::Gray : Color::Black;
            }

            if (menuStopServer != nullptr)
            {
                menuStopServer->Enabled = running;
                menuStopServer->ForeColor = running ? Color::Black : Color::Gray;
            }

            if (menuConnectionCount != nullptr)
            {
                menuConnectionCount->Enabled = running;
                if (!running)
                {
                    menuConnectionCount->Text = L"Connections: 0";
                }
            }

            // Cập nhật tooltip
            if (trayIcon != nullptr)
            {
                trayIcon->Text = running ?
                    L"Touch Remote Server\nStatus: Running" :
                    L"Touch Remote Server\nStatus: Stopped";
            }
        }

        void UpdateConnectionCount(int total, int tcpCount, int btCount)
        {
            if (menuConnectionCount != nullptr)
            {
                menuConnectionCount->Text = String::Format(
                    L"Connections: {0} (TCP:{1} BT:{2})",
                    total, tcpCount, btCount);
            }

            if (trayIcon != nullptr)
            {
                trayIcon->Text = total > 0 ?
                    String::Format(
                        L"Touch Remote Server\nTotal: {0}\nTCP: {1}\nBT: {2}",
                        total, tcpCount, btCount) :
                    L"Touch Remote Server\nNo connections";
            }
        }

        void UpdateAutoStartServer(bool enabled)
        {
            if (menuAutoStartServer != nullptr)
            {
                menuAutoStartServer->Checked = enabled;
            }
        }

        void UpdateAutoRunWithWindows(bool enabled)
        {
            if (menuAutoRunWithWindows != nullptr)
            {
                menuAutoRunWithWindows->Checked = enabled;
            }
        }

        // ===== SHOW MESSAGE =====
        void ShowMessage(String^ title, String^ message)
        {
            if (trayIcon != nullptr && trayIcon->Visible)
            {
                trayIcon->ShowBalloonTip(1000, title, message, ToolTipIcon::Info);
            }
        }

        void ShowMessage(String^ title, String^ message, ToolTipIcon icon)
        {
            if (trayIcon != nullptr && trayIcon->Visible)
            {
                trayIcon->ShowBalloonTip(1000, title, message, icon);
            }
        }

        void ShowMessage(String^ title, String^ message, int timeout)
        {
            if (trayIcon != nullptr && trayIcon->Visible)
            {
                trayIcon->ShowBalloonTip(timeout, title, message, ToolTipIcon::Info);
            }
        }

        void ShowMessage(String^ title, String^ message, ToolTipIcon icon, int timeout)
        {
            if (trayIcon != nullptr && trayIcon->Visible)
            {
                trayIcon->ShowBalloonTip(timeout, title, message, icon);
            }
        }

        // ===== Getter cho các sự kiện =====
        void AddStartServerHandler(EventHandler^ handler)
        {
            if (menuStartServer != nullptr)
            {
                menuStartServer->Click += handler;
            }
        }

        void AddStopServerHandler(EventHandler^ handler)
        {
            if (menuStopServer != nullptr)
            {
                menuStopServer->Click += handler;
            }
        }

        void AddShowWindowHandler(EventHandler^ handler)
        {
            if (menuShowWindow != nullptr)
            {
                menuShowWindow->Click += handler;
            }
        }

        void AddExitHandler(EventHandler^ handler)
        {
            if (menuExit != nullptr)
            {
                menuExit->Click += handler;
            }
        }

        void AddAutoStartServerHandler(EventHandler^ handler)
        {
            if (menuAutoStartServer != nullptr)
            {
                menuAutoStartServer->Click += handler;
            }
        }

        void AddAutoRunWithWindowsHandler(EventHandler^ handler)
        {
            if (menuAutoRunWithWindows != nullptr)
            {
                menuAutoRunWithWindows->Click += handler;
            }
        }

        void AddDoubleClickHandler(EventHandler^ handler)
        {
            if (trayIcon != nullptr)
            {
                trayIcon->DoubleClick += handler;
            }
        }

        // ===== Property =====
        property bool IsVisible
        {
            bool get() { return trayIcon != nullptr && trayIcon->Visible; }
            void set(bool value) { if (trayIcon != nullptr) trayIcon->Visible = value; }
        }

        property bool AutoStartServerChecked
        {
            bool get() { return menuAutoStartServer != nullptr && menuAutoStartServer->Checked; }
        }

        property bool AutoRunWithWindowsChecked
        {
            bool get() { return menuAutoRunWithWindows != nullptr && menuAutoRunWithWindows->Checked; }
        }

    private:
        System::Windows::Forms::NotifyIcon^ trayIcon;
        System::Windows::Forms::ContextMenuStrip^ contextMenuStrip;

        // Menu Items
        ToolStripMenuItem^ menuStatus;
        ToolStripMenuItem^ menuShowWindow;
        ToolStripMenuItem^ menuStartServer;
        ToolStripMenuItem^ menuStopServer;
        ToolStripMenuItem^ menuAutoStartServer;
        ToolStripMenuItem^ menuAutoRunWithWindows;
        ToolStripMenuItem^ menuExit;
        ToolStripMenuItem^ menuConnectionCount;
        ToolStripSeparator^ separator1;
        ToolStripSeparator^ separator2;
        ToolStripSeparator^ separator3;
        ToolStripSeparator^ separator4;
    };
}