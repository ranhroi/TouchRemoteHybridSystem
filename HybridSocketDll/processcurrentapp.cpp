#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"


namespace ToStringUtils {
    // ==================== HÀM TRỢ GIÚP ====================
    static std::string WStringToString(const std::wstring& wstr) {
        if (wstr.empty()) return "";
        int size_needed = WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), (int)wstr.length(), NULL, 0, NULL, NULL);
        std::string str(size_needed, 0);
        WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), (int)wstr.length(), &str[0], size_needed, NULL, NULL);
        return str;
    }

    static std::wstring StringToWString(const std::string& str) {
        if (str.empty()) return L"";
        int size_needed = MultiByteToWideChar(CP_UTF8, 0, str.c_str(), (int)str.length(), NULL, 0);
        std::wstring wstr(size_needed, 0);
        MultiByteToWideChar(CP_UTF8, 0, str.c_str(), (int)str.length(), &wstr[0], size_needed);
        return wstr;
    }
}

// ==================== CURRENT APP ====================
void ProcessCommands::handleCurrentApp(const uint8_t* payload, int len, OpCode op, SOCKET socket) {
    if (op != OpCode::REPORTCURRENTAPP) return;

    std::string exeName;
    HWND foregroundWindow = GetForegroundWindow();

    // ✅ Kiểm tra có cửa sổ nào đang focus không
    if (foregroundWindow == NULL) {
        // Không có cửa sổ nào, có thể là desktop
        SenderBinary::send(socket, "app desktop\n");
        return;
    }

    // ✅ Lấy tên cửa sổ (có thể dùng để log)
    wchar_t windowTitle[256] = { 0 };
    GetWindowTextW(foregroundWindow, windowTitle, 256);

    DWORD processID = 0;
    GetWindowThreadProcessId(foregroundWindow, &processID);

    if (processID == 0) {
        // Không lấy được process ID
        SenderBinary::send(socket, "app unknown\n");
        return;
    }

    // ✅ Mở process với quyền tối thiểu
    HANDLE hProcess = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, processID);
    if (hProcess) {
        wchar_t path[MAX_PATH] = { 0 };
        DWORD size = MAX_PATH;

        // ✅ Lấy đường dẫn đầy đủ
        if (QueryFullProcessImageNameW(hProcess, 0, path, &size)) {
            std::string fullPath = ToStringUtils::WStringToString(path);
            size_t lastSlash = fullPath.find_last_of("\\");
            if (lastSlash != std::string::npos) {
                exeName = fullPath.substr(lastSlash + 1);
            }
            else {
                exeName = fullPath;
            }

            // ✅ Chuyển sang lowercase để so sánh (tuỳ chọn)
            // std::transform(exeName.begin(), exeName.end(), exeName.begin(), ::tolower);
        }
        CloseHandle(hProcess);
    }

    // ✅ Nếu không lấy được tên, thử phương pháp khác
    if (exeName.empty()) {
        // Thử dùng GetWindowModuleFileName (cách khác)
        char moduleName[MAX_PATH] = { 0 };
        GetWindowModuleFileNameA(foregroundWindow, moduleName, MAX_PATH);
        if (strlen(moduleName) > 0) {
            std::string fullPath(moduleName);
            size_t lastSlash = fullPath.find_last_of("\\");
            if (lastSlash != std::string::npos) {
                exeName = fullPath.substr(lastSlash + 1);
            }
            else {
                exeName = fullPath;
            }
        }
    }

    // ✅ Trả về kết quả
    if (!exeName.empty()) {
        std::string response = "app " + exeName + "\n";
        SenderBinary::send(socket, response);
        LOG_DEBUG("[CURRENT APP] %s", exeName.c_str());
    }
    else {
        // ✅ Không lấy được, trả về "unknown" thay vì "unknown.exe"
        SenderBinary::send(socket, "app unknown\n");
        LOG_WARN("[CURRENT APP] Cannot get active app");
    }
}