#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"

// ==================== POWER ====================
void ProcessCommands::handlePower(const uint8_t* payload, int len, OpCode op) {
    switch (op) {
    case OpCode::POWEROFF:
        system("shutdown /s /t 0 /f");
        break;
    case OpCode::REBOOT:
        system("shutdown /r /t 0 /f");
        break;
    case OpCode::LOGOFF:
        system("shutdown /l");
        break;
    case OpCode::SLEEP:
        SetSuspendState(FALSE, TRUE, FALSE);
        break;
    case OpCode::BRIGHNESSLOW:
        changeBrightness(-10);
        break;
    case OpCode::BRIGHNESSHIGH:
        changeBrightness(10);
        break;
    default:
        break;
    }
}


// ================= BRIGHTNESS =================
void ProcessCommands::changeBrightness(int delta) {
    static int brightness = 10;

    brightness += delta;
    if (brightness > 100) brightness = 100;
    if (brightness < 0) brightness = 0;

    // Dùng PowerShell với CREATE_NO_WINDOW
    char cmd[512];
    sprintf_s(cmd, "powershell -windowstyle hidden -command \"(Get-WmiObject -Namespace root/wmi -Class WmiMonitorBrightnessMethods).WmiSetBrightness(0,%d)\"", brightness);

    STARTUPINFOA si = { sizeof(si) };
    PROCESS_INFORMATION pi;
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;

    CreateProcessA(NULL, cmd, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
}
