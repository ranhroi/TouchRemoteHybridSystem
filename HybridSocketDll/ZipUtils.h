#pragma once
#include "logmanager.h"

namespace ZipUtils {

    // Helper: Escape single quotes for PowerShell
    inline std::string escapePowerShellPath(const std::string& path) {
        std::string result = path;
        size_t pos = 0;
        while ((pos = result.find("'", pos)) != std::string::npos) {
            result.replace(pos, 1, "''");
            pos += 2;
        }
        return result;
    }

    // Helper: Check if file exists and not empty
    inline bool isFileValid(const std::string& path) {
        WIN32_FILE_ATTRIBUTE_DATA attr;
        if (!GetFileAttributesExA(path.c_str(), GetFileExInfoStandard, &attr)) {
            return false;
        }
        // Check if file size > 0
        ULARGE_INTEGER size;
        size.HighPart = attr.nFileSizeHigh;
        size.LowPart = attr.nFileSizeLow;
        return size.QuadPart > 0;
    }

    // ==================== HÀM NÉN FOLDER THÀNH ZIP ====================
    inline bool compressFolderToZip(const std::string& folderPath,
        const std::string& zipPath,
        SOCKET clientSocket)
    {
        // Kiểm tra folder tồn tại
        DWORD folderAttr = GetFileAttributesA(folderPath.c_str());
        if (folderAttr == INVALID_FILE_ATTRIBUTES || !(folderAttr & FILE_ATTRIBUTE_DIRECTORY)) {
            LOG_ERROR("[ZIP] Folder not found: %s", folderPath.c_str());
            return false;
        }

        // Xóa file zip cũ nếu tồn tại
        if (GetFileAttributesA(zipPath.c_str()) != INVALID_FILE_ATTRIBUTES) {
            DeleteFileA(zipPath.c_str());
        }

        // Tạo thư mục cha cho file zip
        std::string parentDir = zipPath;
        size_t lastSlash = parentDir.find_last_of("\\");
        if (lastSlash != std::string::npos) {
            parentDir = parentDir.substr(0, lastSlash);
            if (!CreateDirectoryA(parentDir.c_str(), NULL)) {
                DWORD err = GetLastError();
                if (err != ERROR_ALREADY_EXISTS) {
                    LOG_ERROR("[ZIP] Cannot create directory: %s", parentDir.c_str());
                    return false;
                }
            }
        }

        // Escape path cho PowerShell
        std::string escapedFolder = escapePowerShellPath(folderPath);
        std::string escapedZip = escapePowerShellPath(zipPath);

        // Tạo lệnh PowerShell để nén // ✅ Giữ nguyên folder gốc trong zip
        std::string cmd = "powershell -NoProfile -Command \"$ProgressPreference = 'SilentlyContinue'; "
            "Compress-Archive -Path '" + escapedFolder + "' -DestinationPath '" + escapedZip +
            "' -CompressionLevel Fastest -Force\"";

        LOG_INFO("[ZIP] Compressing: %s", folderPath.c_str());

        // Khởi tạo STARTUPINFO và PROCESS_INFORMATION
        STARTUPINFOA si = { sizeof(si) };
        si.dwFlags = STARTF_USESHOWWINDOW;
        si.wShowWindow = SW_HIDE;

        PROCESS_INFORMATION pi = { 0 };

        // Tạo bản copy của command để chỉnh sửa (CreateProcessA cần non-const)
        std::vector<char> cmdLine(cmd.begin(), cmd.end());
        cmdLine.push_back('\0');

        // Chạy process
        BOOL success = CreateProcessA(
            NULL,                       // No application name
            cmdLine.data(),             // Command line
            NULL,                       // Process handle not inheritable
            NULL,                       // Thread handle not inheritable
            FALSE,                      // Set handle inheritance to FALSE
            CREATE_NO_WINDOW,           // Creation flags (no console window)
            NULL,                       // Use parent's environment block
            NULL,                       // Use parent's starting directory
            &si,                        // Pointer to STARTUPINFO structure
            &pi                         // Pointer to PROCESS_INFORMATION structure
        );

        if (!success) {
            LOG_ERROR("[ZIP] Failed to start PowerShell process");
            return false;
        }

        // Chờ process kết thúc (timeout 10 phút = 600000ms)
        const int TIMEOUT_MS = 600000;
        const int CHECK_INTERVAL_MS = 100;
        int elapsed = 0;
        DWORD exitCode = STILL_ACTIVE;

        while (elapsed < TIMEOUT_MS) {
            // Kiểm tra socket nếu có
            if (clientSocket && clientSocket != INVALID_SOCKET) {
                fd_set fd;
                FD_ZERO(&fd);
                FD_SET(clientSocket, &fd);
                timeval tv = { 0, 0 };
                // ✅ Sửa: tham số đầu tiên là clientSocket + 1
                if (select(clientSocket + 1, &fd, NULL, NULL, &tv) > 0) {
                    // Có lỗi socket, hủy process
                    TerminateProcess(pi.hProcess, 1);
                    LOG_ERROR("[ZIP] Socket disconnected");
                    CloseHandle(pi.hProcess);
                    CloseHandle(pi.hThread);
                    return false;
                }
            }

            // Kiểm tra process đã kết thúc chưa
            if (WaitForSingleObject(pi.hProcess, CHECK_INTERVAL_MS) == WAIT_OBJECT_0) {
                GetExitCodeProcess(pi.hProcess, &exitCode);
                break;
            }

            elapsed += CHECK_INTERVAL_MS;
        }

        // Nếu timeout, kill process
        if (elapsed >= TIMEOUT_MS) {
            TerminateProcess(pi.hProcess, 1);
            LOG_ERROR("[ZIP] Compression timeout");
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);

            // Xóa file zip lỗi nếu có
            if (GetFileAttributesA(zipPath.c_str()) != INVALID_FILE_ATTRIBUTES) {
                DeleteFileA(zipPath.c_str());
            }
            return false;
        }

        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);

        // Kiểm tra kết quả (exitCode == 0 và file zip hợp lệ, không rỗng)
        bool result = (exitCode == 0) && isFileValid(zipPath);

        if (result) {
            LOG_INFO("[ZIP] Compression completed: %s", zipPath.c_str());
        }
        else {
            LOG_ERROR("[ZIP] Compression failed! Exit code: %d", exitCode);
            if (GetFileAttributesA(zipPath.c_str()) != INVALID_FILE_ATTRIBUTES) {
                DeleteFileA(zipPath.c_str());
            }
        }

        return result;
    }

    // ==================== HÀM GIẢI NÉN ZIP ====================
    inline bool decompressZipToFolder(const std::string& zipPath,
        const std::string& destFolderPath,
        SOCKET clientSocket)
    {
        // Tạo thư mục đích
        if (!CreateDirectoryA(destFolderPath.c_str(), NULL)) {
            DWORD err = GetLastError();
            if (err != ERROR_ALREADY_EXISTS) {
                LOG_ERROR("[UNZIP] Cannot create destination folder: %s", destFolderPath.c_str());
                return false;
            }
        }

        // Kiểm tra file zip tồn tại và hợp lệ
        if (!isFileValid(zipPath)) {
            LOG_ERROR("[UNZIP] Zip file not found or empty: %s", zipPath.c_str());
            return false;
        }

        LOG_INFO("[UNZIP] Extracting: %s -> %s", zipPath.c_str(), destFolderPath.c_str());

        // Escape path
        std::string escapedZip = escapePowerShellPath(zipPath);
        std::string escapedDest = escapePowerShellPath(destFolderPath);

        // Tạo lệnh PowerShell để giải nén
        std::string cmd = "powershell -NoProfile -Command \"Expand-Archive -Path '" + escapedZip +
            "' -DestinationPath '" + escapedDest + "' -Force\"";

        STARTUPINFOA si = { sizeof(si) };
        si.dwFlags = STARTF_USESHOWWINDOW;
        si.wShowWindow = SW_HIDE;

        PROCESS_INFORMATION pi = { 0 };

        std::vector<char> cmdLine(cmd.begin(), cmd.end());
        cmdLine.push_back('\0');

        BOOL success = CreateProcessA(
            NULL,
            cmdLine.data(),
            NULL,
            NULL,
            FALSE,
            CREATE_NO_WINDOW,
            NULL,
            NULL,
            &si,
            &pi
        );

        if (!success) {
            LOG_ERROR("[UNZIP] Failed to start PowerShell process");
            return false;
        }

        const int TIMEOUT_MS = 600000;
        const int CHECK_INTERVAL_MS = 100;
        int elapsed = 0;
        DWORD exitCode = STILL_ACTIVE;

        while (elapsed < TIMEOUT_MS) {
            if (clientSocket && clientSocket != INVALID_SOCKET) {
                fd_set fd;
                FD_ZERO(&fd);
                FD_SET(clientSocket, &fd);
                timeval tv = { 0, 0 };
                if (select(clientSocket + 1, &fd, NULL, NULL, &tv) > 0) {
                    TerminateProcess(pi.hProcess, 1);
                    LOG_ERROR("[UNZIP] Socket disconnected");
                    CloseHandle(pi.hProcess);
                    CloseHandle(pi.hThread);
                    return false;
                }
            }

            if (WaitForSingleObject(pi.hProcess, CHECK_INTERVAL_MS) == WAIT_OBJECT_0) {
                GetExitCodeProcess(pi.hProcess, &exitCode);
                break;
            }

            elapsed += CHECK_INTERVAL_MS;
        }

        if (elapsed >= TIMEOUT_MS) {
            TerminateProcess(pi.hProcess, 1);
            LOG_DEBUG("[UNZIP] Extraction timeout");
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
            return false;
        }

        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);

        bool result = (exitCode == 0);

        if (result) {
            LOG_INFO("[UNZIP] Extraction completed to: %s", destFolderPath.c_str());
        }
        else {
            LOG_ERROR("[UNZIP] Extraction failed! Exit code: %d", exitCode);
        }

        return result;
    }

    // ==================== HÀM GIẢI NÉN BẤT ĐỒNG BỘ ====================
    inline void decompressZipToFolderAsync(const std::string& zipPath,
        const std::string& destFolderPath)
    {
        CreateDirectoryA(destFolderPath.c_str(), NULL);

        std::string escapedZip = escapePowerShellPath(zipPath);
        std::string escapedDest = escapePowerShellPath(destFolderPath);

        std::string cmd = "powershell -NoProfile -Command \"Expand-Archive -Path '" + escapedZip +
            "' -DestinationPath '" + escapedDest + "' -Force\"";

        STARTUPINFOA si = { sizeof(si) };
        si.dwFlags = STARTF_USESHOWWINDOW;
        si.wShowWindow = SW_HIDE;

        PROCESS_INFORMATION pi = { 0 };

        std::vector<char> cmdLine(cmd.begin(), cmd.end());
        cmdLine.push_back('\0');

        CreateProcessA(
            NULL,
            cmdLine.data(),
            NULL,
            NULL,
            FALSE,
            CREATE_NO_WINDOW | DETACHED_PROCESS,
            NULL,
            NULL,
            &si,
            &pi
        );

        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);

        LOG_INFO("[UNZIP] Async extraction started (detached)");
    }

} // namespace ZipUtils