#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"
#include "sessioncontext.h"
#include "fileexplorerstate.h"
#include "fileexplorerutils.h"
#include "ziputils.h"

// Global clipboard instance
static auto& g_clipboard = ClipboardState::getInstance();

// ======================= XỬ LÝ CHÍNH =======================
void ProcessCommands::handleFileExplorer(const uint8_t* data, int len, OpCode op, SOCKET clientSocket) {
    if (!clientSocket) return;

    std::string rawPath((const char*)data, len);
    std::string qPath = FileExplorerUtils::NormalizeClientPath(rawPath);

    std::shared_ptr<ClientSession> session = findSessionBySocket(clientSocket);
    if (!session) {
        SenderBinary::send(clientSocket, "error session_not_found\n");
        return;
    }

    switch (op) {
    case OpCode::FILEEXPLORER: {
        // Hiển thị danh sách ổ đĩa
        if (qPath == "/" || qPath == "\\") {
            std::vector<std::string> drives = FileExplorerUtils::GetAllRealDrives();
            std::string result;
            for (const auto& drive : drives) {
                result += "d" + drive + "\r";
            }
            SenderBinary::send(clientSocket, result + "\n");
            break;
        }

        // Hiển thị shortcuts
        if (qPath == "~") {
            auto shortcuts = FileExplorerUtils::GetSystemShortcuts();
            std::string result;
            for (const auto& shortcut : shortcuts) {
                result += "d" + shortcut.first + "\r";
            }
            SenderBinary::send(clientSocket, result + "\n");
            break;
        }

        // ✅ Lưu currentDir vào SESSION (mỗi session riêng)
        session->setCurrentDir(qPath);

        std::string findPath = qPath;
        if (!findPath.empty() && findPath.back() != '\\') findPath += "\\";
        findPath += "*";

        WIN32_FIND_DATAA findData;
        HANDLE hFind = FindFirstFileA(findPath.c_str(), &findData);

        if (hFind == INVALID_HANDLE_VALUE) {
            SenderBinary::send(clientSocket, "path_not_found\n");
            break;
        }

        std::vector<std::pair<std::string, bool>> entries;
        do {
            std::string name = findData.cFileName;
            if (name == "." || name == "..") continue;

            std::string nameLower = name;
            std::transform(nameLower.begin(), nameLower.end(), nameLower.begin(), ::tolower);

            if (nameLower == "$recycle.bin" || nameLower == "system volume information" ||
                nameLower == "desktop.ini" || nameLower == "thumbs.db" || (!name.empty() && name[0] == '.')) {
                continue;
            }

            bool isDir = (findData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
            entries.push_back({ name, isDir });
        } while (FindNextFileA(hFind, &findData));

        FindClose(hFind);

        std::sort(entries.begin(), entries.end(), [](const auto& a, const auto& b) {
            if (a.second != b.second) return a.second > b.second;
            return _stricmp(a.first.c_str(), b.first.c_str()) < 0;
            });

        std::string result;
        for (const auto& entry : entries) {
            result += (entry.second ? "d" : "f") + entry.first + "\r";
        }
        SenderBinary::send(clientSocket, result + "\n");
        break;
    }

    case OpCode::OPENFILE: {
        if (FileExplorerUtils::FileExists(qPath)) {
            ShellExecuteA(NULL, "open", qPath.c_str(), NULL, NULL, SW_SHOWNORMAL);
            SenderBinary::send(clientSocket, "open_success\n");
        }
        else {
            SenderBinary::send(clientSocket, "open_failed\n");
        }
        break;
    }

    case OpCode::COPYFILE: {
        if (FileExplorerUtils::FileExists(qPath)) {
            // ✅ Lưu vào clipboard TOÀN CỤC
            g_clipboard.setClipboard(qPath, false);
            SenderBinary::send(clientSocket, "copy_success\n");
        }
        else {
            SenderBinary::send(clientSocket, "copy_failed\n");
        }
        break;
    }

    case OpCode::CUTFILE: {
        if (FileExplorerUtils::FileExists(qPath)) {
            // ✅ Lưu vào clipboard TOÀN CỤC
            g_clipboard.setClipboard(qPath, true);
            SenderBinary::send(clientSocket, "cut_success\n");
        }
        else {
            SenderBinary::send(clientSocket, "cut_failed\n");
        }
        break;
    }

    case OpCode::PASTEFILE: {
        if (!g_clipboard.hasClipboardContent()) {
            SenderBinary::send(clientSocket, "paste_failed_no_source\n");
            break;
        }

        std::string srcPath = g_clipboard.getClipboardPath();
        bool isCut = g_clipboard.isClipboardCut();

        size_t lastSlash = srcPath.find_last_of("\\");
        std::string fileName = (lastSlash != std::string::npos) ? srcPath.substr(lastSlash + 1) : srcPath;

        std::string dest = qPath;
        if (!dest.empty() && dest.back() != '\\') dest += "\\";
        dest += fileName;

        bool ok = false;
        if (isCut) {
            ok = MoveFileA(srcPath.c_str(), dest.c_str()) != 0;
            if (ok) g_clipboard.clearClipboard();
        }
        else {
            if (FileExplorerUtils::IsDirectory(srcPath)) {
                ok = FileExplorerUtils::CopyDirectoryRecursively(srcPath, dest);
            }
            else {
                ok = CopyFileA(srcPath.c_str(), dest.c_str(), FALSE) != 0;
            }
        }

        SenderBinary::send(clientSocket, ok ? "paste_success\n" : "paste_failed\n");
        break;
    }

    case OpCode::DELETEFILE: {
        std::string targetPath = FileExplorerUtils::GetAbsolutePath(qPath, session->getCurrentDir());

        LOG_INFO("[DELETE] Request: %s", targetPath.c_str());

        if (!FileExplorerUtils::IsPathSafe(targetPath)) {
            LOG_WARN("[DELETE] Path rejected: %s", targetPath.c_str());
            SenderBinary::send(clientSocket, "delete_failed\n");
            break;
        }

        bool ok = false;
        if (!FileExplorerUtils::FileExists(targetPath)) {
            ok = true;
        }
        else if (FileExplorerUtils::IsDirectory(targetPath)) {
            ok = FileExplorerUtils::RemoveRecursively(targetPath);
        }
        else {
            DWORD attrib = GetFileAttributesA(targetPath.c_str());
            if (attrib != INVALID_FILE_ATTRIBUTES &&
                (attrib & (FILE_ATTRIBUTE_READONLY | FILE_ATTRIBUTE_HIDDEN))) {
                SetFileAttributesA(targetPath.c_str(), FILE_ATTRIBUTE_NORMAL);
            }
            ok = (DeleteFileA(targetPath.c_str()) != 0);
        }

        LOG_INFO("[DELETE] Result: %s → %s", targetPath.c_str(), ok ? "OK" : "FAILED");
        SenderBinary::send(clientSocket, ok ? "delete_success\n" : "delete_failed\n");
        break;
    }


    case OpCode::RENAMEFILE: {
        size_t pipeIndex = qPath.find('|');
        if (pipeIndex != std::string::npos) {
            std::string oldPath = qPath.substr(0, pipeIndex);
            std::string newName = qPath.substr(pipeIndex + 1);

            size_t lastSlash = oldPath.find_last_of("\\");
            std::string parentPath = (lastSlash != std::string::npos) ? oldPath.substr(0, lastSlash) : "";
            std::string newPath = parentPath + "\\" + newName;

            bool ok = MoveFileA(oldPath.c_str(), newPath.c_str()) != 0;
            SenderBinary::send(clientSocket, ok ? "rename_success\n" : "rename_failed\n");
        }
        else {
            SenderBinary::send(clientSocket, "rename_failed\n");
        }
        break;
    }

    case OpCode::CREATEFILE: {
        HANDLE hFile = CreateFileA(qPath.c_str(), GENERIC_WRITE, 0, NULL, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
        bool ok = (hFile != INVALID_HANDLE_VALUE);
        if (ok) CloseHandle(hFile);
        SenderBinary::send(clientSocket, ok ? "mkfile_success\n" : "mkfile_failed\n");
        break;
    }

    case OpCode::CREATEFOLDER: {
        bool ok = FileExplorerUtils::CreateDirectoryRecursive(qPath);
        SenderBinary::send(clientSocket, ok ? "mkdir_success\n" : "mkdir_failed\n");
        break;
    }

    case OpCode::DETAILSFILE: {
        WIN32_FILE_ATTRIBUTE_DATA fileAttr;
        if (GetFileAttributesExA(qPath.c_str(), GetFileExInfoStandard, &fileAttr)) {
            size_t lastSlash = qPath.find_last_of("\\");
            std::string fileName = (lastSlash != std::string::npos) ? qPath.substr(lastSlash + 1) : qPath;
            unsigned long long fileSize = ((unsigned long long)fileAttr.nFileSizeHigh << 32) + fileAttr.nFileSizeLow;

            char buffer[512];
            sprintf_s(buffer, "name:%s|size:%llu|date:%lld\n", fileName.c_str(), fileSize, (long long)fileAttr.ftLastWriteTime.dwLowDateTime);
            SenderBinary::send(clientSocket, buffer);
        }
        else {
            SenderBinary::send(clientSocket, "details_failed\n");
        }
        break;
    }

    case OpCode::GETFOLDERINFO: {
        LOG_DEBUG("[GETFOLDERINFO] ========== RECEIVED ==========");
        LOG_DEBUG("[GETFOLDERINFO] Raw path from client: %s", qPath.c_str());

        // Chuyển thành absolute path
        std::string absolutePath = qPath;
        if (absolutePath.find(':') == std::string::npos && absolutePath[0] != '\\' && absolutePath != "~") {
            absolutePath = session->getCurrentDir() + "\\" + qPath;
        }

        LOG_DEBUG("[GETFOLDERINFO] Absolute path: %s", absolutePath.c_str());
        LOG_DEBUG("[GETFOLDERINFO] Session current dir: %s", session->getCurrentDir().c_str());

        if (!FileExplorerUtils::IsDirectory(absolutePath)) {
            LOG_ERROR("[GETFOLDERINFO] Not a directory: %s", absolutePath.c_str());
            SenderBinary::send(clientSocket, "folder_error not_found\n");
            break;
        }

        session->isLongTransfer = true;
        std::string jsonData = FileExplorerUtils::BuildFolderInfoJson(absolutePath);
        std::string response = "folder_info " + jsonData + "\n";

        LOG_DEBUG("[GETFOLDERINFO] Response length: %d bytes", static_cast<int>(response.length()));

        SenderBinary::send(clientSocket, response);
        session->isLongTransfer = false;

        LOG_DEBUG("[GETFOLDERINFO] ========== DONE ==========");
        break;
    }

    case OpCode::DOWNLOADFILE: {
        std::string cmdArgs = qPath;
        std::string cleanPath = cmdArgs;
        long long startOffset = 0;

        size_t pipeIndex = cmdArgs.find_last_of('|');
        if (pipeIndex != std::string::npos) {
            cleanPath = cmdArgs.substr(0, pipeIndex);
            long long parsedOffset = std::stoll(cmdArgs.substr(pipeIndex + 1));
            if (parsedOffset > 0) startOffset = parsedOffset;
        }

        // ✅ Dùng currentDir của session
        std::string absoluteFilePath = FileExplorerUtils::GetAbsolutePath(cleanPath, session->getCurrentDir());

        if (!FileExplorerUtils::CanReadFile(absoluteFilePath)) {
            SenderBinary::send(clientSocket, "download_error permission_denied\n");
            break;
        }

        uint64_t fileSize = FileExplorerUtils::GetFileSize(absoluteFilePath);
        if (fileSize > 10LL * 1024 * 1024 * 1024) { SenderBinary::send(clientSocket, "download_error file_too_large\n"); break; }

        bool isBluetooth = (session->transport == TransportType::BLUETOOTH);

        if (!FileExplorerUtils::IsFileSizeAllowed(fileSize, isBluetooth)) {
            SenderBinary::send(clientSocket, "download_error file_too_large_for_bluetooth\n");
            break;
        }

        // ✅ Mở file và chuyển ownership cho SmartSenderBinary
        std::ifstream* fileStream = FileExplorerUtils::OpenFileForReadingRaw(absoluteFilePath, startOffset);
        if (!fileStream) {
            SenderBinary::send(clientSocket, "download_error cannot_open\n");
            break;
        }

        if (startOffset >= fileSize) startOffset = 0;

        session->isLongTransfer = true;
        SenderBinary::send(clientSocket, "OK\n");

        SenderBinary::setResumeOffset(clientSocket, startOffset);

        // SmartSenderBinary sẽ tự động đóng fileStream khi gửi xong
        SenderBinary::sendFile(clientSocket, fileStream);
        break;
    }

    case OpCode::DOWNLOADFOLDERZIP: {
        g_clipboard.setCancelRequested(false);

        LOG_DEBUG("[ZIP] ========== DOWNLOADFOLDERZIP START ==========");
        LOG_DEBUG("[ZIP] Raw path: %s", qPath.c_str());

        // ==================== 1. LẤY ĐƯỜNG DẪN TUYỆT ĐỐI ====================
        std::string folderPath = qPath;
        if (folderPath.find(':') == std::string::npos && folderPath[0] != '\\' && folderPath != "~") {
            folderPath = session->getCurrentDir() + "\\" + qPath;
        }

        LOG_DEBUG("[ZIP] Absolute folder path: %s", folderPath.c_str());

        // ==================== 2. KIỂM TRA THƯ MỤC ====================
        DWORD folderAttr = GetFileAttributesA(folderPath.c_str());
        if (folderAttr == INVALID_FILE_ATTRIBUTES || !(folderAttr & FILE_ATTRIBUTE_DIRECTORY)) {
            LOG_ERROR("[ZIP] Folder not found: %s", folderPath.c_str());
            SenderBinary::send(clientSocket, "download_error folder_not_found\n");
            break;
        }

        // ==================== 3. NHẬN BIẾT LOẠI KẾT NỐI ====================
        bool isBluetooth = (session->transport == TransportType::BLUETOOTH);

        // ==================== 4. TÍNH KÍCH THƯỚC THƯ MỤC ====================
        unsigned long long folderSize = FileExplorerUtils::GetFileSizeRecursive(folderPath);

        LOG_DEBUG("[ZIP] Folder size: %llu bytes (%.2f MB)", folderSize, folderSize / 1024.0 / 1024.0);

        // ==================== 5. GIỚI HẠN KÍCH THƯỚC ====================
        if (isBluetooth && folderSize > TimeoutConfig::BLUETOOTH_MAX_SIZE) {
            std::string errMsg = "Folder too large for Bluetooth: " +
                std::to_string(folderSize / 1024.0 / 1024.0) + " MB > " +
                std::to_string(TimeoutConfig::BLUETOOTH_MAX_SIZE / 1024.0 / 1024.0) + " MB";
            LOG_DEBUG("[ZIP] %s", errMsg.c_str());
            SenderBinary::send(clientSocket, "download_error folder_too_large_for_bluetooth\n");
            break;
        }

        // ==================== 6. LẤY TÊN THƯ MỤC ====================
        size_t lastSlash = folderPath.find_last_of("\\");
        std::string folderName = (lastSlash != std::string::npos) ? folderPath.substr(lastSlash + 1) : folderPath;

        // ==================== 7. TẠO FILE ZIP TẠM ====================
        char tempPathBuffer[MAX_PATH];
        GetTempPathA(MAX_PATH, tempPathBuffer);
        std::string zipPath = std::string(tempPathBuffer) + folderName + "_" +
            std::to_string(GetTickCount64()) + ".zip";

        session->processingZipPath = zipPath;
        session->isLongTransfer = true;

        LOG_DEBUG("[ZIP] Temp zip path: %s", zipPath.c_str());
        LOG_DEBUG("[ZIP] Starting compression: %s", folderPath.c_str());

        // ==================== 8. NÉN THƯ MỤC (CÓ TIMEOUT) ====================
        auto compressStartTime = std::chrono::steady_clock::now();
        const int COMPRESS_TIMEOUT_SECONDS = TimeoutConfig::COMPRESS_TIMEOUT;

        bool compressSuccess = ZipUtils::compressFolderToZip(folderPath, zipPath, clientSocket);

        auto compressEndTime = std::chrono::steady_clock::now();
        auto compressDuration = std::chrono::duration_cast<std::chrono::seconds>(compressEndTime - compressStartTime).count();

        LOG_DEBUG("[ZIP] Compression completed in %d seconds, result: %s", static_cast<int>(compressDuration), compressSuccess ? "SUCCESS" : "FAILED");

        if (!compressSuccess) {
            SenderBinary::send(clientSocket, "download_error compress_failed\n");
            if (FileExplorerUtils::FileExists(zipPath)) {
                DeleteFileA(zipPath.c_str());
                LOG_DEBUG("[ZIP] Deleted failed zip: %s", zipPath.c_str());
            }
            session->processingZipPath.clear();
            session->isLongTransfer = false;
            break;
        }

        if (compressDuration > COMPRESS_TIMEOUT_SECONDS) {
            LOG_DEBUG("[ZIP] Compression timeout but succeeded, continuing...");
        }

        // ==================== 9. KIỂM TRA FILE ZIP ====================
        if (!FileExplorerUtils::FileExists(zipPath)) {
            LOG_ERROR("[ZIP] Zip file not found after compression: %s", zipPath.c_str());
            SenderBinary::send(clientSocket, "download_error zip_not_found\n");
            session->processingZipPath.clear();
            session->isLongTransfer = false;
            break;
        }

        unsigned long long zipSize = FileExplorerUtils::GetFileSize(zipPath);
        LOG_DEBUG("[ZIP] Zip file created, size: %llu bytes (%.2f MB)", zipSize, zipSize / 1024.0 / 1024.0);

        // ==================== 10. KIỂM TRA KÍCH THƯỚC ZIP ====================
        if (isBluetooth && zipSize > TimeoutConfig::BLUETOOTH_MAX_SIZE) {
            LOG_ERROR("[ZIP] Zip too large for Bluetooth: %llu", zipSize);
            SenderBinary::send(clientSocket, "download_error zip_too_large_for_bluetooth\n");
            DeleteFileA(zipPath.c_str());
            session->processingZipPath.clear();
            session->isLongTransfer = false;
            break;
        }

        // ==================== 11. BÁO OK ====================
        SenderBinary::send(clientSocket, "OK\n");

        // ==================== 12. GỬI FILE ZIP QUA SMARTSENDERBINARY ====================
        std::ifstream* zipStream = FileExplorerUtils::OpenFileForReadingRaw(zipPath, 0);
        if (!zipStream) {
            LOG_ERROR("[ZIP] Cannot open zip file for reading: %s", zipPath.c_str());
            SenderBinary::send(clientSocket, "download_error cannot_open_zip\n");
            DeleteFileA(zipPath.c_str());
            session->processingZipPath.clear();
            session->isLongTransfer = false;
            break;
        }

        LOG_DEBUG("[ZIP] Sending zip file via SmartSenderBinary, size: %llu", zipSize);

        // Thiết lập callback để xóa file zip sau khi gửi xong
        SenderBinary::setFileCallbacks(clientSocket,
            // 1. Progress callback
            [](int64_t sent, int64_t total) {
                int percent = (total > 0) ? static_cast<int>((sent * 100) / total) : 0;
                LOG_DEBUG("[ZIP] Progress: %d%% (%llu / %llu bytes)", percent, sent, total);
            },
            // 2. Finished callback
            [zipPath, session, clientSocket]() {
                LOG_DEBUG("[ZIP] Zip sent successfully, cleaning up: %s", zipPath.c_str());
                if (FileExplorerUtils::FileExists(zipPath)) {
                    DeleteFileA(zipPath.c_str());
                    LOG_DEBUG("[ZIP] Deleted temp zip: %s", zipPath.c_str());
                }
                if (session) {
                    session->processingZipPath.clear();
                    session->isLongTransfer = false;
                }
                // Thông báo cho client biết đã hoàn tất
                SenderBinary::send(clientSocket, "download_complete\n");
            },
            // 3. Error callback
            [zipPath, session](const std::string& error) {
                LOG_ERROR("[ZIP] Send error: %s", error.c_str());
                if (FileExplorerUtils::FileExists(zipPath)) {
                    DeleteFileA(zipPath.c_str());
                    LOG_DEBUG("[ZIP] Deleted temp zip on error: %s", zipPath.c_str());
                }
                if (session) {
                    session->processingZipPath.clear();
                    session->isLongTransfer = false;
                }
            },
            // 4. Started callback (tùy chọn)
            [zipPath](const std::string& fileName, int64_t fileSize) {
                LOG_DEBUG("[ZIP] Started sending zip: %s (%llu bytes)", zipPath.c_str(), fileSize);
            }
        );

        // Gửi file zip
        SenderBinary::sendFile(clientSocket, zipStream);

        LOG_DEBUG("[ZIP] ========== DOWNLOADFOLDERZIP INITIATED ==========");
        break;
    }

    case OpCode::UPLOADFILE: {
        std::string cmdArgs = qPath;
        size_t pipeIndex = cmdArgs.find_last_of('|');
        if (pipeIndex == std::string::npos) {
            SenderBinary::send(clientSocket, "upload_error invalid_format\n");
            break;
        }

        std::string part1 = cmdArgs.substr(0, pipeIndex);
        std::string part2 = cmdArgs.substr(pipeIndex + 1);
        long long totalSize = 0;
        std::string filePath;

        char* endPtr;
        long long size1 = strtoll(part1.c_str(), &endPtr, 10);
        if (*endPtr == '\0') {
            totalSize = size1;
            filePath = part2;
        }
        else {
            totalSize = strtoll(part2.c_str(), &endPtr, 10);
            filePath = part1;
        }

        if (totalSize <= 0) {
            SenderBinary::send(clientSocket, "upload_error invalid_size\n");
            break;
        }

        bool isBluetooth = (session->transport == TransportType::BLUETOOTH);

        if (isBluetooth && (unsigned long long)totalSize > TimeoutConfig::BLUETOOTH_MAX_SIZE) {
            SenderBinary::send(clientSocket, "upload_error file_too_large_for_bluetooth\n");
            break;
        }

        // ✅ Dùng currentDir của session
        std::string savePath = filePath;
        if (savePath.find(':') == std::string::npos && savePath[0] != '\\') {
            savePath = session->getCurrentDir() + "\\" + filePath;
        }

        size_t lastSlashPos = savePath.find_last_of("\\");
        if (lastSlashPos != std::string::npos) {
            std::string parentDir = savePath.substr(0, lastSlashPos);
            FileExplorerUtils::CreateDirectoryRecursive(parentDir);
        }

        session->isLongTransfer = true;
        session->uploadFilePath = savePath;
        session->uploadTotalSize = totalSize;
        session->uploadReceivedSize = 0;

        fopen_s(&session->uploadFile, savePath.c_str(), "wb");

        if (!session->uploadFile) {
            session->isLongTransfer = false;
            SenderBinary::send(clientSocket, "upload_error cannot_create_file\n");
            break;
        }

        SenderBinary::send(clientSocket, "upload_ready\n");
        break;
    }

    case OpCode::UPLOADFOLDERZIP: {
        std::string cmdArgs = qPath;
        size_t pipeIndex = cmdArgs.find_last_of('|');
        if (pipeIndex == std::string::npos) {
            SenderBinary::send(clientSocket, "upload_error invalid_format\n");
            break;
        }

        std::string targetPath = cmdArgs.substr(0, pipeIndex);
        long long totalSize = strtoll(cmdArgs.substr(pipeIndex + 1).c_str(), nullptr, 10);

        if (totalSize <= 0) {
            SenderBinary::send(clientSocket, "upload_error invalid_size\n");
            break;
        }

        bool isBluetooth = (session->transport == TransportType::BLUETOOTH);
        if (isBluetooth && (unsigned long long)totalSize > TimeoutConfig::BLUETOOTH_MAX_SIZE) {
            SenderBinary::send(clientSocket, "upload_error folder_too_large_for_bluetooth\n");
            break;
        }

        // ✅ Dùng currentDir của session
        std::string absFolder = targetPath;
        if (absFolder.find(':') == std::string::npos && absFolder[0] != '\\') {
            absFolder = session->getCurrentDir() + "\\" + targetPath;
        }

        if (!FileExplorerUtils::CreateDirectoryRecursive(absFolder)) {
            SenderBinary::send(clientSocket, "upload_error cannot_create_dir\n");
            break;
        }

        std::string tempZipPath = absFolder + "_upload_temp.zip";
        if (FileExplorerUtils::FileExists(tempZipPath)) remove(tempZipPath.c_str());

        session->isLongTransfer = true;
        session->uploadFilePath = tempZipPath;
        session->uploadTotalSize = totalSize;
        session->uploadReceivedSize = 0;

        fopen_s(&session->uploadFile, tempZipPath.c_str(), "wb");

        if (!session->uploadFile) {
            session->isLongTransfer = false;
            SenderBinary::send(clientSocket, "upload_error cannot_create_temp_zip\n");
            break;
        }

        SenderBinary::send(clientSocket, "upload_ready\n");
        break;
    }

    case OpCode::STOPTRANSFER: {
        LOG_DEBUG("[STOP_TRANSFER] Stopping transfer");
        g_clipboard.setCancelRequested(true);

        SenderBinary::cancelImmediately(clientSocket);

        if (session->uploadFile) {
            fclose(session->uploadFile);
            session->uploadFile = nullptr;
        }

        if (!session->uploadFilePath.empty() && FileExplorerUtils::FileExists(session->uploadFilePath)) {
            DeleteFileA(session->uploadFilePath.c_str());
            LOG_DEBUG("[STOP_TRANSFER] Deleted: %s", session->uploadFilePath.c_str());
        }

        if (!session->processingZipPath.empty() && FileExplorerUtils::FileExists(session->processingZipPath)) {
            DeleteFileA(session->processingZipPath.c_str());
            LOG_DEBUG("[STOP_TRANSFER] Deleted: %s", session->processingZipPath.c_str());
        }

        session->isLongTransfer = false;
        session->uploadFilePath.clear();
        session->uploadTotalSize = 0;
        session->uploadReceivedSize = 0;
        session->processingZipPath.clear();

        SenderBinary::send(clientSocket, "cancelled_ok\n");
        break;
    }

    default:
        break;
    }
}