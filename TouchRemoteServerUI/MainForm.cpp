#include "MainForm.h"
#include <Windows.h>
#include <winuser.h>

using namespace System;
using namespace System::Windows::Forms;
using namespace System::Threading;
using namespace System::Diagnostics;

const UINT WM_ACTIVATE_APP = RegisterWindowMessage(L"TouchRemoteServer_Activate");

[STAThread]
int main(array<String^>^ args)
{
    // ===== SINGLE INSTANCE =====
    bool createdNew = false;
    Mutex^ mutex = gcnew Mutex(true, L"TouchRemoteServer_SingleInstance", createdNew);

    if (!createdNew)
    {
        HWND hWnd = FindWindow(nullptr, L"Touch Remote Server Control");

        if (hWnd != nullptr)
        {
            // Kích hoạt window cũ
            PostMessage(hWnd, WM_ACTIVATE_APP, 0, 0);
            SetForegroundWindow(hWnd);
            ShowWindow(hWnd, SW_RESTORE);

            // Flash window
            FLASHWINFO flashInfo;
            flashInfo.cbSize = sizeof(FLASHWINFO);
            flashInfo.hwnd = hWnd;
            flashInfo.dwFlags = FLASHW_TRAY | FLASHW_TIMERNOFG;
            flashInfo.uCount = 5;
            flashInfo.dwTimeout = 0;
            FlashWindowEx(&flashInfo);
        }

        // ===== DÙNG MESSAGEBOX NHƯNG NGẮN GỌN =====
        MessageBox::Show(
            L"Application is already running!",
            L"Touch Remote Server",
            MessageBoxButtons::OK,
            MessageBoxIcon::Information,
            MessageBoxDefaultButton::Button1,
            MessageBoxOptions::DefaultDesktopOnly
        );

        if (mutex != nullptr)
        {
            try { mutex->ReleaseMutex(); }
            catch (...) {}
            delete mutex;
        }
        return 0;
    }

    try
    {
        TouchRemoteServerUI::MainForm^ form = gcnew TouchRemoteServerUI::MainForm();
        Application::Run(form);
    }
    catch (Exception^ ex)
    {
        MessageBox::Show(ex->ToString(), L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
    }
    finally
    {
        if (mutex != nullptr)
        {
            try { mutex->ReleaseMutex(); }
            catch (...) {}
            delete mutex;
        }
    }

    return 0;
}