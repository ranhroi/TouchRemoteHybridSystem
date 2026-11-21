#include "pch.h"
#include "CmdRun.h"

// Tối ưu hàm toLower bằng hàm chuẩn của C++
static std::string toLower(const std::string& str) {
    std::string result = str;
    std::transform(result.begin(), result.end(), result.begin(), ::tolower);
    return result;
}

// Danh sách lệnh NHANH
static const char* FAST_COMMANDS[] = {
    "ipconfig", "getmac", "hostname", "whoami", "ver",
    "tasklist", "driverquery", "systeminfo", "assoc",
    "ping", "nslookup", "arp", "netstat",
    "echo", "cls", "color", "date", "time",
    "dir", "tree", "type", "find", "fc", "sort",
    "reg", "sc", "taskkill", "pathping",
    "tracert", "gpresult",
    nullptr
};

// Loại bỏ khoảng trắng thừa ở đầu chuỗi để kiểm tra chính xác
static std::string trimLeft(const std::string& str) {
    size_t start = str.find_first_not_of(" \t\r\n");
    return (start == std::string::npos) ? "" : str.substr(start);
}

bool CmdRun::isFastCommand(const std::string& command) {
    std::string cmdClean = toLower(trimLeft(command));
    if (cmdClean.empty()) return true;

    for (int i = 0; FAST_COMMANDS[i]; i++) {
        std::string fastCmd = FAST_COMMANDS[i];
        // Đảm bảo khớp chính xác tên lệnh hoặc lệnh theo sau bởi dấu cách/đối số
        if (cmdClean == fastCmd || cmdClean.rfind(fastCmd + " ", 0) == 0) {
            return true;
        }
    }
    return false;
}

std::string CmdRun::runCommandFast(const std::string& command) {
    if (command.empty()) return "";

    HANDLE hReadPipe, hWritePipe;
    SECURITY_ATTRIBUTES sa = { sizeof(SECURITY_ATTRIBUTES), NULL, TRUE };

    // Tăng kích thước bộ đệm pipe lên 64KB để chứa output lớn tốt hơn
    if (!CreatePipe(&hReadPipe, &hWritePipe, &sa, 65536)) {
        return "[Error] Cannot create pipe";
    }

    // Đảm bảo Handle đọc không bị tiến trình con kế thừa bừa bãi
    SetHandleInformation(hReadPipe, HANDLE_FLAG_INHERIT, 0);

    std::string cmdLineStr = "cmd.exe /c " + command;
    // An toàn bộ nhớ: Windows cần một bộ đệm có thể ghi (writable buffer)
    std::vector<char> cmdLine(cmdLineStr.begin(), cmdLineStr.end());
    cmdLine.push_back('\0');

    STARTUPINFOA si = { sizeof(STARTUPINFOA) };
    PROCESS_INFORMATION pi = { 0 };

    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdOutput = hWritePipe;
    si.hStdError = hWritePipe;
    si.hStdInput = INVALID_HANDLE_VALUE;

    if (!CreateProcessA(NULL, cmdLine.data(), NULL, NULL, TRUE,
        CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        CloseHandle(hReadPipe);
        CloseHandle(hWritePipe);
        return "[Error] Cannot start process";
    }

    // BẮT BUỘC: Đóng handle ghi ở phía cha ngay lập tức để ReadFile biết khi nào tiến trình con dừng ghi
    CloseHandle(hWritePipe);

    std::string output;
    char buffer[4096];
    DWORD bytesRead;
    bool isTimeout = false;

    // GIẢI PHÁP CHỐNG TREO: Đọc dữ liệu song song với việc kiểm tra thời gian (Timeout 5s)
    DWORD startTime = GetTickCount();
    while (true) {
        DWORD availableBytes = 0;
        // Kiểm tra xem có dữ liệu trong pipe không (không block luồng)
        if (PeekNamedPipe(hReadPipe, NULL, 0, NULL, &availableBytes, NULL)) {
            if (availableBytes > 0) {
                if (ReadFile(hReadPipe, buffer, sizeof(buffer) - 1, &bytesRead, NULL) && bytesRead > 0) {
                    buffer[bytesRead] = '\0';
                    output += buffer;
                    continue; // Tiếp tục đọc nếu còn dữ liệu
                }
            }
        }

        // Kiểm tra xem tiến trình con đã thoát chưa
        DWORD exitCode;
        if (GetExitCodeProcess(pi.hProcess, &exitCode) && exitCode != STILL_ACTIVE) {
            // Đọc nốt phần dữ liệu còn sót lại sau khi tiến trình thoát
            while (ReadFile(hReadPipe, buffer, sizeof(buffer) - 1, &bytesRead, NULL) && bytesRead > 0) {
                buffer[bytesRead] = '\0';
                output += buffer;
            }
            break;
        }

        // Kiểm tra timeout 5 giây
        if (GetTickCount() - startTime > 5000) {
            isTimeout = true;
            TerminateProcess(pi.hProcess, 1);
            break;
        }

        Sleep(10); // Tránh quá tải CPU trong vòng lặp vô hạn
    }

    CloseHandle(hReadPipe);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    if (isTimeout) {
        return output + "\n[Timeout] Command took too long (> 5s): " + command;
    }

    if (output.empty()) {
        return "[Done] Command executed successfully (no output)";
    }

    return output;
}

void CmdRun::runCommandBackground(const std::string& command) {
    if (command.empty()) return;

    std::string cmdLineStr = "cmd.exe /c " + command;
    std::vector<char> cmdLine(cmdLineStr.begin(), cmdLineStr.end());
    cmdLine.push_back('\0');

    STARTUPINFOA si = { sizeof(STARTUPINFOA) };
    PROCESS_INFORMATION pi = { 0 };

    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;

    if (CreateProcessA(NULL, cmdLine.data(), NULL, NULL, FALSE,
        CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
    }
}

std::string CmdRun::getCommandSuggestions() {
    return
        "ipconfig\n"
        "ipconfig /all\n"
        "tasklist\n"
        "taskkill /pid <pid>\n"
        "dir\n"
        "ping google.com\n"
        "tracert google.com\n"
        "netstat -an\n"
        "systeminfo\n"
        "driverquery\n"
        "hostname\n"
        "whoami\n"
        "ver\n"
        "cls\n"
        "color\n"
        "reg query HKLM\\SOFTWARE\n"
        "sc query\n"
        "getmac\n"
        "arp -a\n"
        "nslookup google.com\n"
        "pathping google.com\n"
        "gpresult /r\n";
}
