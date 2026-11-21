#include "pch.h"
#include "fileexplorerutils.h"

namespace FileExplorerUtils {

    // ==================== INTERNAL HELPERS ====================

    static std::string WStringToString(const std::wstring& wstr) {
        if (wstr.empty()) return "";
        int size_needed = WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), (int)wstr.length(), NULL, 0, NULL, NULL);
        std::string str(size_needed, 0);
        WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), (int)wstr.length(), &str[0], size_needed, NULL, NULL);
        return str;
    }

    static std::string GetKnownFolderPath(REFKNOWNFOLDERID folderId) {
        PWSTR path = nullptr;
        std::string result;
        if (SUCCEEDED(SHGetKnownFolderPath(folderId, 0, NULL, &path))) {
            result = WStringToString(path);
        }
        return result;
    }

    // ==================== ESCAPE JSON ====================
    std::string EscapeJsonString(const std::string& str) {
        std::string result;
        for (char c : str) {
            switch (c) {
            case '"': result += "\\\""; break;
            case '\\': result += "\\\\"; break;
            case '\b': result += "\\b"; break;
            case '\f': result += "\\f"; break;
            case '\n': result += "\\n"; break;
            case '\r': result += "\\r"; break;
            case '\t': result += "\\t"; break;
            default:
                if (c < 32) {
                    char buf[8];
                    sprintf_s(buf, "\\u%04x", (unsigned char)c);
                    result += buf;
                }
                else {
                    result += c;
                }
                break;
            }
        }
        return result;
    }

    // ==================== PATH UTILITIES ====================

    std::string GetFileName(const std::string& path) {
        size_t lastSlash = path.find_last_of("\\/");
        if (lastSlash != std::string::npos && lastSlash < path.length() - 1) {
            return path.substr(lastSlash + 1);
        }
        return path;
    }

    std::string GetParentDirectory(const std::string& path) {
        size_t lastSlash = path.find_last_of("\\/");
        if (lastSlash != std::string::npos) {
            if (lastSlash == 0) return "";
            return path.substr(0, lastSlash);
        }
        return "";
    }

    std::string GetAbsolutePath(const std::string& path, const std::string& baseDir) {
        if (path.empty()) return baseDir;
        if (path.find(':') != std::string::npos || path.substr(0, 2) == "\\\\") {
            return path;
        }
        std::string result = baseDir;
        if (!result.empty() && result.back() != '\\') result += "\\";
        result += path;
        return NormalizeClientPath(result);
    }

    // ==================== SYSTEM PATHS ====================

    std::string GetDesktopPath() { return GetKnownFolderPath(FOLDERID_Desktop); }
    std::string GetDocumentsPath() { return GetKnownFolderPath(FOLDERID_Documents); }
    std::string GetDownloadsPath() { return GetKnownFolderPath(FOLDERID_Downloads); }
    std::string GetMusicPath() { return GetKnownFolderPath(FOLDERID_Music); }
    std::string GetVideosPath() { return GetKnownFolderPath(FOLDERID_Videos); }
    std::string GetPicturesPath() { return GetKnownFolderPath(FOLDERID_Pictures); }
    std::string GetHomeDirectory() { return GetKnownFolderPath(FOLDERID_Profile); }

    std::string GetTempDirectory() {
        char tempPath[MAX_PATH];
        GetTempPathA(MAX_PATH, tempPath);
        std::string result(tempPath);
        if (!result.empty() && result.back() != '\\') result += "\\";
        return result;
    }

    std::vector<std::pair<std::string, std::string>> GetSystemShortcuts() {
        return {
            {"Home", GetHomeDirectory()},
            {"Desktop", GetDesktopPath()},
            {"Documents", GetDocumentsPath()},
            {"Downloads", GetDownloadsPath()},
            {"Music", GetMusicPath()},
            {"Videos", GetVideosPath()},
            {"Pictures", GetPicturesPath()},
            {"Temp", GetTempDirectory()}
        };
    }

    std::vector<std::string> GetAllRealDrives() {
        std::vector<std::string> drives;
        DWORD drivesMask = GetLogicalDrives();
        for (char drive = 'C'; drive <= 'Z'; drive++) {
            if (drivesMask & (1 << (drive - 'A'))) {
                std::string drivePath = std::string(1, drive) + ":\\";
                ULARGE_INTEGER freeBytes, totalBytes, totalFreeBytes;
                if (GetDiskFreeSpaceExA(drivePath.c_str(), &freeBytes, &totalBytes, &totalFreeBytes)) {
                    if (totalBytes.QuadPart > 0) drives.push_back(drivePath);
                }
            }
        }
        return drives;
    }

    // ==================== BASIC FILE OPERATIONS ====================

    bool FileExists(const std::string& path) {
        if (path.empty()) return false;
        DWORD attrib = GetFileAttributesA(path.c_str());
        return (attrib != INVALID_FILE_ATTRIBUTES);
    }

    bool IsDirectory(const std::string& path) {
        if (path.empty()) return false;
        DWORD attrib = GetFileAttributesA(path.c_str());
        return (attrib != INVALID_FILE_ATTRIBUTES) && (attrib & FILE_ATTRIBUTE_DIRECTORY);
    }

    bool IsFile(const std::string& path) {
        if (path.empty()) return false;
        DWORD attrib = GetFileAttributesA(path.c_str());
        return (attrib != INVALID_FILE_ATTRIBUTES) && !(attrib & FILE_ATTRIBUTE_DIRECTORY);
    }

    uint64_t GetFileSize(const std::string& path) {
        WIN32_FILE_ATTRIBUTE_DATA fileAttr;
        if (GetFileAttributesExA(path.c_str(), GetFileExInfoStandard, &fileAttr)) {
            return ((uint64_t)fileAttr.nFileSizeHigh << 32) + fileAttr.nFileSizeLow;
        }
        return 0;
    }

    // ==================== RECURSIVE FILE OPERATIONS ====================

    bool CreateDirectoryRecursive(const std::string& path) {
        if (path.empty()) return false;
        if (FileExists(path)) return IsDirectory(path);

        std::string currentPath;
        size_t colonPos = path.find(':');
        if (colonPos != std::string::npos && colonPos == 1) {
            currentPath = path.substr(0, 2) + "\\";
        }

        size_t start = (colonPos != std::string::npos) ? colonPos + 2 : 0;
        size_t end = path.find('\\', start);

        while (end != std::string::npos) {
            std::string segment = path.substr(start, end - start);
            if (!segment.empty()) {
                if (!currentPath.empty() && currentPath.back() != '\\') currentPath += "\\";
                currentPath += segment;
                CreateDirectoryA(currentPath.c_str(), NULL);
            }
            start = end + 1;
            end = path.find('\\', start);
        }

        std::string lastSegment = path.substr(start);
        if (!lastSegment.empty()) {
            if (!currentPath.empty() && currentPath.back() != '\\') currentPath += "\\";
            currentPath += lastSegment;
            CreateDirectoryA(currentPath.c_str(), NULL);
        }

        return FileExists(path);
    }

    // ============================================================
    // 🚀 XÓA AN TOÀN — INTERNAL HELPERS
    // ============================================================

    // Prefix \\?\ để hỗ trợ path > 260 ký tự
    static std::string ToExtendedPath(const std::string& path) {
        if (path.size() >= 4 && path[0] == '\\' && path[1] == '\\' &&
            path[2] == '?' && path[3] == '\\') {
            return path;
        }
        if (path.size() >= 2 && path[1] == ':') {
            return "\\\\?\\" + path;
        }
        if (path.size() >= 2 && path[0] == '\\' && path[1] == '\\') {
            return "\\\\?\\UNC\\" + path.substr(2);
        }
        return path;
    }

    // Xóa 1 file với retry (chống lock + read-only)
    static bool InternalDeleteFile(const std::string& path) {
        std::string extPath = ToExtendedPath(path);

        for (int attempt = 0; attempt < 3; ++attempt) {
            DWORD attrib = GetFileAttributesA(extPath.c_str());
            if (attrib != INVALID_FILE_ATTRIBUTES &&
                (attrib & (FILE_ATTRIBUTE_READONLY | FILE_ATTRIBUTE_HIDDEN | FILE_ATTRIBUTE_SYSTEM))) {
                SetFileAttributesA(extPath.c_str(), FILE_ATTRIBUTE_NORMAL);
            }

            if (DeleteFileA(extPath.c_str())) return true;

            DWORD err = GetLastError();
            if (err == ERROR_FILE_NOT_FOUND || err == ERROR_PATH_NOT_FOUND) return true;

            // File đang bị lock → chờ rồi thử lại
            std::this_thread::sleep_for(std::chrono::milliseconds(80 * (attempt + 1)));
        }

        LOG_WARN("[Delete] Failed after retries: %s (error: %lu)", path.c_str(), GetLastError());
        return false;
    }

    // Xóa thư mục rỗng
    static bool InternalRemoveDir(const std::string& path) {
        std::string extPath = ToExtendedPath(path);

        DWORD attrib = GetFileAttributesA(extPath.c_str());
        if (attrib != INVALID_FILE_ATTRIBUTES &&
            (attrib & (FILE_ATTRIBUTE_READONLY | FILE_ATTRIBUTE_HIDDEN | FILE_ATTRIBUTE_SYSTEM))) {
            SetFileAttributesA(extPath.c_str(), FILE_ATTRIBUTE_NORMAL);
        }

        if (RemoveDirectoryA(extPath.c_str())) return true;

        DWORD err = GetLastError();
        if (err == ERROR_FILE_NOT_FOUND || err == ERROR_PATH_NOT_FOUND) return true;
        return false;
    }

    // ============================================================
    // 🚀 REMOVE RECURSIVELY — AN TOÀN TUYỆT ĐỐI
    //    - Không đệ quy (dùng stack) → không tràn stack
    //    - Chống symlink/junction → không xóa nhầm ổ khác
    //    - Retry file bị lock → không treo
    //    - Hỗ trợ path > 260 ký tự
    //    - Chặn xóa root drive và thư mục hệ thống
    // ============================================================
    bool RemoveRecursively(const std::string& inputPath) {
        if (inputPath.empty()) return false;

        // ---- 1. CHUẨN HÓA PATH ----
        std::string path = inputPath;
        std::replace(path.begin(), path.end(), '/', '\\');
        while (path.size() > 3 && path.back() == '\\') path.pop_back();

        // ---- 2. CHẶN XÓA ROOT DRIVE ----
        if (path.size() <= 3 && path.size() >= 2 && path[1] == ':') {
            LOG_ERROR("[DELETE] REFUSED drive root: %s", path.c_str());
            return false;
        }

        // ---- 3. CHẶN XÓA THƯ MỤC HỆ THỐNG ----
        std::string lower = path;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);

        static const char* protectedEnds[] = {
            "\\windows", "\\program files", "\\program files (x86)",
            "\\programdata", "\\users\\all users", "\\users\\default",
            "\\system volume information", "\\$recycle.bin", "\\recovery"
        };
        for (const char* prot : protectedEnds) {
            size_t plen = strlen(prot);
            if (lower.size() >= plen && lower.compare(lower.size() - plen, plen, prot) == 0) {
                LOG_ERROR("[DELETE] REFUSED protected path: %s", path.c_str());
                return false;
            }
        }

        // ---- 4. KIỂM TRA TỒN TẠI ----
        if (!FileExists(path)) return true;

        // ---- 5. NẾU LÀ FILE → XÓA LUÔN ----
        DWORD attrib = GetFileAttributesA(path.c_str());
        if (attrib == INVALID_FILE_ATTRIBUTES) return true;

        if (!(attrib & FILE_ATTRIBUTE_DIRECTORY)) {
            return InternalDeleteFile(path);
        }

        // ---- 6. NẾU LÀ SYMLINK/JUNCTION → CHỈ XÓA LINK ----
        if (attrib & FILE_ATTRIBUTE_REPARSE_POINT) {
            LOG_INFO("[DELETE] Removing reparse point (not following): %s", path.c_str());
            SetFileAttributesA(path.c_str(), FILE_ATTRIBUTE_NORMAL);
            if (RemoveDirectoryA(path.c_str())) return true;
            return InternalDeleteFile(path);
        }

        // ---- 7. DUYỆT CÂY BẰNG STACK ----
        struct Node {
            std::string path;
            bool processed;
        };
        std::vector<Node> stack;
        stack.push_back({ path, false });

        bool allOk = true;
        int totalDeleted = 0;

        while (!stack.empty()) {
            Node& node = stack.back();

            if (node.processed) {
                if (!InternalRemoveDir(node.path)) allOk = false;
                else totalDeleted++;
                stack.pop_back();
                continue;
            }

            node.processed = true;

            std::string searchPath = node.path + "\\*";
            if (node.path.size() >= 2 && node.path[1] == ':') {
                searchPath = "\\\\?\\" + node.path + "\\*";
            }

            WIN32_FIND_DATAA fd;
            HANDLE hFind = FindFirstFileA(searchPath.c_str(), &fd);

            if (hFind == INVALID_HANDLE_VALUE) {
                DWORD err = GetLastError();
                if (err != ERROR_FILE_NOT_FOUND) {
                    LOG_WARN("[DELETE] Cannot list: %s (err: %lu)", node.path.c_str(), err);
                    allOk = false;
                }
                continue;
            }

            std::vector<std::string> subDirs;
            std::vector<std::string> files;

            do {
                std::string name = fd.cFileName;
                if (name == "." || name == "..") continue;

                std::string child = node.path + "\\" + name;

                // Symlink/junction → xóa link, KHÔNG đệ quy
                if (fd.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) {
                    if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                        SetFileAttributesA(child.c_str(), FILE_ATTRIBUTE_NORMAL);
                        if (!RemoveDirectoryA(child.c_str())) allOk = false;
                    }
                    else {
                        if (!InternalDeleteFile(child)) allOk = false;
                    }
                    continue;
                }

                if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                    subDirs.push_back(child);
                }
                else {
                    files.push_back(child);
                }
            } while (FindNextFileA(hFind, &fd));

            FindClose(hFind);

            // Xóa file con
            for (const auto& f : files) {
                if (InternalDeleteFile(f)) totalDeleted++;
                else allOk = false;
            }

            // Đẩy subdir vào stack
            for (auto it = subDirs.rbegin(); it != subDirs.rend(); ++it) {
                stack.push_back({ *it, false });
            }
        }

        LOG_INFO("[DELETE] Deleted %d items from: %s (result: %s)",
            totalDeleted, path.c_str(), allOk ? "OK" : "PARTIAL");
        return allOk;
    }

    unsigned long long GetFileSizeRecursive(const std::string& path) {
        if (!FileExists(path)) return 0;
        if (!IsDirectory(path)) return GetFileSize(path);

        unsigned long long totalSize = 0;
        std::string findPath = path + "\\*";
        WIN32_FIND_DATAA findData;
        HANDLE hFind = FindFirstFileA(findPath.c_str(), &findData);

        if (hFind == INVALID_HANDLE_VALUE) return 0;

        do {
            std::string name = findData.cFileName;
            if (name == "." || name == "..") continue;
            std::string fullPath = path + "\\" + name;
            if (findData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                totalSize += GetFileSizeRecursive(fullPath);
            }
            else {
                totalSize += ((unsigned long long)findData.nFileSizeHigh << 32) + findData.nFileSizeLow;
            }
        } while (FindNextFileA(hFind, &findData));

        FindClose(hFind);
        return totalSize;
    }

    bool CopyDirectoryRecursively(const std::string& srcPath, const std::string& dstPath) {
        if (!IsDirectory(srcPath)) {
            return CopyFileA(srcPath.c_str(), dstPath.c_str(), FALSE) != 0;
        }

        if (!CreateDirectoryRecursive(dstPath)) return false;

        std::string findPath = srcPath + "\\*";
        WIN32_FIND_DATAA findData;
        HANDLE hFind = FindFirstFileA(findPath.c_str(), &findData);

        if (hFind == INVALID_HANDLE_VALUE) return false;

        do {
            std::string name = findData.cFileName;
            if (name == "." || name == "..") continue;

            std::string srcItem = srcPath + "\\" + name;
            std::string dstItem = dstPath + "\\" + name;

            if (findData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                if (!CopyDirectoryRecursively(srcItem, dstItem)) {
                    FindClose(hFind);
                    return false;
                }
            }
            else {
                if (!CopyFileA(srcItem.c_str(), dstItem.c_str(), FALSE)) {
                    FindClose(hFind);
                    return false;
                }
            }
        } while (FindNextFileA(hFind, &findData));

        FindClose(hFind);
        return true;
    }

    // ==================== IFSTREAM SUPPORT ====================

    bool CanReadFile(const std::string& path) {
        if (!FileExists(path)) return false;
        if (IsDirectory(path)) return false;

        std::ifstream test(path, std::ios::binary);
        return test.is_open();
    }

    std::unique_ptr<std::ifstream> OpenFileForReading(const std::string& path) {
        if (!CanReadFile(path)) {
            LOG_DEBUG("[FileExplorerUtils] Cannot open file for reading: %s", path.c_str());
            return nullptr;
        }

        auto fileStream = std::make_unique<std::ifstream>(path, std::ios::binary);
        if (!fileStream->is_open()) {
            LOG_DEBUG("[FileExplorerUtils] Failed to open file: %s", path.c_str());
            return nullptr;
        }

        LOG_DEBUG("[FileExplorerUtils] Opened file for reading: %s", path.c_str());
        return fileStream;
    }

    std::ifstream* OpenFileForReadingRaw(const std::string& path) {
        return OpenFileForReading(path).release();
    }

    std::unique_ptr<std::ifstream> OpenFileForReading(const std::string& path, uint64_t offset) {
        auto fileStream = OpenFileForReading(path);
        if (!fileStream) return nullptr;

        if (offset > 0) {
            fileStream->seekg(offset, std::ios::beg);
            if (fileStream->fail()) {
                LOG_DEBUG("[FileExplorerUtils] Failed to seek to offset %llu in file: %s", static_cast<unsigned long long>(offset), path.c_str());
                return nullptr;
            }
            LOG_DEBUG("[FileExplorerUtils] Opened file with offset %llu: %s", static_cast<unsigned long long>(offset), path.c_str());
        }

        return fileStream;
    }

    std::ifstream* OpenFileForReadingRaw(const std::string& path, uint64_t offset) {
        return OpenFileForReading(path, offset).release();
    }

    bool ReadFileChunk(const std::string& path, uint64_t offset, uint32_t size, std::vector<uint8_t>& buffer) {
        auto fileStream = OpenFileForReading(path, offset);
        if (!fileStream) return false;

        buffer.resize(size);
        fileStream->read(reinterpret_cast<char*>(buffer.data()), size);

        std::streamsize actuallyRead = fileStream->gcount();
        if (actuallyRead <= 0) {
            LOG_DEBUG("[FileExplorerUtils] Failed to read chunk from file: %s", path.c_str());
            return false;
        }

        buffer.resize(static_cast<size_t>(actuallyRead));
        return true;
    }

    FileInfo GetFileInfoDetailed(const std::string& path) {
        FileInfo info;
        info.fullPath = path;
        info.fileName = GetFileName(path);
        info.isDirectory = IsDirectory(path);
        info.fileSize = GetFileSize(path);

        DWORD attrib = GetFileAttributesA(path.c_str());
        if (attrib != INVALID_FILE_ATTRIBUTES) {
            info.isReadOnly = (attrib & FILE_ATTRIBUTE_READONLY) != 0;
            info.isHidden = (attrib & FILE_ATTRIBUTE_HIDDEN) != 0;
        }

        return info;
    }

    // ==================== VALIDATION ====================
    bool IsPathSafe(const std::string& path) {
        if (path.empty()) return false;

        // 1. Chặn path traversal
        if (path.find("..") != std::string::npos) {
            LOG_DEBUG("[FileExplorerUtils] Path traversal detected: %s", path.c_str());
            return false;
        }

        std::string lowerPath = path;
        std::transform(lowerPath.begin(), lowerPath.end(), lowerPath.begin(), ::tolower);

        // 2. Chặn thư mục hệ thống — SO KHỚP CHÍNH XÁC (không substring)
        static const char* forbidden[] = {
            "\\windows\\system32",
            "\\windows\\system",
            "\\windows\\syswow64",
            "\\$recycle.bin",
            "\\system volume information"
        };

        for (const char* f : forbidden) {
            size_t flen = strlen(f);
            // Phải khớp ở CUỐI path hoặc có \ theo sau (không phải substring giữa)
            size_t pos = lowerPath.find(f);
            if (pos != std::string::npos) {
                size_t afterPos = pos + flen;
                if (afterPos == lowerPath.size() || lowerPath[afterPos] == '\\') {
                    LOG_DEBUG("[FileExplorerUtils] Forbidden path: %s", path.c_str());
                    return false;
                }
            }
        }

        return true;
    }

    bool IsFileSizeAllowed(uint64_t fileSize, bool isBluetooth) {
        const uint64_t BLUETOOTH_MAX_SIZE = 200ULL * 1024 * 1024;
        const uint64_t TCP_MAX_SIZE = 10ULL * 1024 * 1024 * 1024;

        uint64_t maxSize = isBluetooth ? BLUETOOTH_MAX_SIZE : TCP_MAX_SIZE;
        return fileSize <= maxSize;
    }

    bool IsPathWithinAllowedDirectory(const std::string& path, const std::string& baseDir) {
        if (baseDir.empty()) return true;

        std::string normalizedPath = NormalizeClientPath(path);
        std::string normalizedBase = NormalizeClientPath(baseDir);

        std::transform(normalizedPath.begin(), normalizedPath.end(), normalizedPath.begin(), ::tolower);
        std::transform(normalizedBase.begin(), normalizedBase.end(), normalizedBase.begin(), ::tolower);

        if (normalizedBase.back() != '\\') normalizedBase += "\\";

        return normalizedPath.find(normalizedBase) == 0;
    }

    // ==================== FORMATTING ====================

    std::string FormatFileSize(uint64_t bytes) {
        const char* units[] = { "B", "KB", "MB", "GB", "TB", "PB" };
        int unitIndex = 0;
        double size = static_cast<double>(bytes);

        while (size >= 1024.0 && unitIndex < 5) {
            size /= 1024.0;
            unitIndex++;
        }

        std::ostringstream oss;
        if (unitIndex == 0) {
            oss << static_cast<uint64_t>(size) << " " << units[unitIndex];
        }
        else {
            oss << std::fixed << std::setprecision(2) << size << " " << units[unitIndex];
        }
        return oss.str();
    }

    std::string GetFileExtension(const std::string& path) {
        size_t dotPos = path.find_last_of('.');
        if (dotPos != std::string::npos && dotPos > path.find_last_of("\\/")) {
            return path.substr(dotPos);
        }
        return "";
    }

    // ==================== NORMALIZE PATH ====================

    std::string NormalizeClientPath(const std::string& path) {
        std::string qdata = path;

        size_t start = qdata.find_first_not_of(" \t\n\r");
        if (start == std::string::npos) return "/";
        qdata = qdata.substr(start);
        size_t end = qdata.find_last_not_of(" \t\n\r");
        if (end != std::string::npos) qdata = qdata.substr(0, end + 1);

        if (qdata.empty() || qdata == "/") return "/";

        std::replace(qdata.begin(), qdata.end(), '/', '\\');

        if (qdata.size() >= 3 && qdata[0] == '\\' && isalpha(qdata[1]) && qdata[2] == ':') {
            qdata = qdata.substr(1);
        }

        if (qdata.length() == 2 && isalpha(qdata[0]) && qdata[1] == ':') {
            qdata += "\\";
        }

        if (qdata == "~") return "~";

        if (qdata.substr(0, 2) == "~\\") {
            std::string remainder = qdata.substr(2);
            size_t slashIndex = remainder.find('\\');

            std::string shortcutName = (slashIndex == std::string::npos) ? remainder : remainder.substr(0, slashIndex);
            std::string subPath = (slashIndex == std::string::npos) ? "" : remainder.substr(slashIndex);

            size_t pipeIndex = shortcutName.find('|');
            std::string pipeData = "";
            if (pipeIndex != std::string::npos) {
                pipeData = shortcutName.substr(pipeIndex);
                shortcutName = shortcutName.substr(0, pipeIndex);
            }

            auto shortcuts = GetSystemShortcuts();
            for (const auto& shortcut : shortcuts) {
                std::string shortcutLower = shortcut.first;
                std::string shortcutNameLower = shortcutName;
                std::transform(shortcutLower.begin(), shortcutLower.end(), shortcutLower.begin(), ::tolower);
                std::transform(shortcutNameLower.begin(), shortcutNameLower.end(), shortcutNameLower.begin(), ::tolower);

                if (shortcutLower == shortcutNameLower) {
                    std::string result = shortcut.second;
                    if (!result.empty() && result.back() != '\\') result += "\\";
                    result += subPath + pipeData;
                    std::replace(result.begin(), result.end(), '/', '\\');
                    return result;
                }
            }
        }

        return qdata;
    }

    // ==================== BUILD FOLDER INFO JSON ====================
    std::string BuildFolderInfoJson(const std::string& folderPath) {
        LOG_DEBUG("[BuildFolderInfoJson] Processing: %s", folderPath.c_str());

        std::string absPath = folderPath;

        size_t pos;
        while ((pos = absPath.find("\\\\")) != std::string::npos) {
            absPath.replace(pos, 2, "\\");
        }

        if (!absPath.empty() && absPath.back() != '\\') {
            absPath += "\\";
        }

        LOG_DEBUG("[BuildFolderInfoJson] Normalized path: %s", absPath.c_str());

        if (!FileExists(absPath)) {
            LOG_DEBUG("[BuildFolderInfoJson] Folder not found: %s", absPath.c_str());
            return "{\"error\":\"folder_not_found\"}";
        }

        if (!IsDirectory(absPath)) {
            LOG_DEBUG("[BuildFolderInfoJson] Not a directory: %s", absPath.c_str());
            return "{\"error\":\"not_a_directory\"}";
        }

        std::string folderName = absPath;
        if (!folderName.empty() && folderName.back() == '\\') {
            folderName.pop_back();
        }
        size_t lastSlash = folderName.find_last_of("\\");
        if (lastSlash != std::string::npos && lastSlash < folderName.length() - 1) {
            folderName = folderName.substr(lastSlash + 1);
        }

        LOG_DEBUG("[BuildFolderInfoJson] Folder name: %s", folderName.c_str());

        std::vector<FileItemInfo> items = GetDirectoryContentsRecursive(absPath, 50000);

        uint64_t totalSize = 0;
        int fileCount = 0;
        int dirCount = 0;

        for (const auto& item : items) {
            if (!item.isDirectory) {
                totalSize += item.size;
                fileCount++;
            }
            else {
                dirCount++;
            }
        }

        LOG_DEBUG("[BuildFolderInfoJson] Found %d folders, %d files, total size: %llu", dirCount, fileCount, static_cast<unsigned long long>(totalSize));

        std::sort(items.begin(), items.end(), [](const FileItemInfo& a, const FileItemInfo& b) {
            if (a.isDirectory != b.isDirectory) return a.isDirectory > b.isDirectory;
            return _stricmp(a.path.c_str(), b.path.c_str()) < 0;
            });

        std::string json = "{";
        json += "\"folder_name\":\"" + EscapeJsonString(folderName) + "\",";
        json += "\"total_files\":" + std::to_string(items.size()) + ",";
        json += "\"total_size\":" + std::to_string(totalSize) + ",";
        json += "\"items\":[";

        for (size_t i = 0; i < items.size(); i++) {
            if (i > 0) json += ",";
            json += "{";
            std::string path = items[i].path;
            if (!path.empty() && path[0] == '/') {
                path = path.substr(1);
            }
            std::replace(path.begin(), path.end(), '/', '\\');
            json += "\"path\":\"" + EscapeJsonString(path) + "\",";
            json += "\"isDir\":" + std::string(items[i].isDirectory ? "true" : "false") + ",";
            json += "\"size\":" + std::to_string(items[i].size);
            json += "}";
        }

        json += "]}";

        LOG_DEBUG("[BuildFolderInfoJson] JSON size: %d bytes", static_cast<int>(json.size()));
        if (json.size() < 1000) {
            LOG_DEBUG("[BuildFolderInfoJson] JSON content: %s", json.c_str());
        }
        else {
            LOG_DEBUG("[BuildFolderInfoJson] JSON preview (first 500 chars): %s", json.substr(0, 500).c_str());
        }

        return json;
    }

    std::vector<FileItemInfo> GetDirectoryContentsRecursive(const std::string& folderPath, int maxItems) {
        std::vector<FileItemInfo> items;

        std::string absPath = folderPath;

        size_t pos;
        while ((pos = absPath.find("\\\\")) != std::string::npos) {
            absPath.replace(pos, 2, "\\");
        }

        if (!absPath.empty() && absPath.back() != '\\') {
            absPath += "\\";
        }

        LOG_DEBUG("[GetDirectoryContentsRecursive] Scanning: %s", absPath.c_str());

        if (!FileExists(absPath) || !IsDirectory(absPath)) {
            LOG_DEBUG("[GetDirectoryContentsRecursive] Invalid directory: %s", absPath.c_str());
            return items;
        }

        int rootLen = (int)absPath.length();
        if (absPath.back() == '\\') rootLen--;

        LOG_DEBUG("[GetDirectoryContentsRecursive] Root length: %d", rootLen);

        std::vector<std::string> stack;
        stack.push_back(absPath);

        int count = 0;
        int folderCount = 0;
        int fileCount = 0;

        auto startTime = std::chrono::steady_clock::now();

        while (!stack.empty() && count < maxItems) {
            std::string currentPath = stack.back();
            stack.pop_back();

            std::string searchPath = currentPath;
            if (!searchPath.empty() && searchPath.back() != '\\') searchPath += "\\";
            searchPath += "*";

            WIN32_FIND_DATAA findData;
            HANDLE hFind = FindFirstFileA(searchPath.c_str(), &findData);

            if (hFind == INVALID_HANDLE_VALUE) continue;

            do {
                std::string name = findData.cFileName;
                if (name == "." || name == "..") continue;

                std::string nameLower = name;
                std::transform(nameLower.begin(), nameLower.end(), nameLower.begin(), ::tolower);
                if (nameLower == "desktop.ini" || nameLower == "thumbs.db") continue;

                std::string fullPath = currentPath + "\\" + name;
                bool isDir = (findData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;

                std::string relativePath;
                if ((int)fullPath.length() > rootLen) {
                    relativePath = fullPath.substr(rootLen);
                    if (!relativePath.empty() && relativePath[0] == '\\') {
                        relativePath = relativePath.substr(1);
                    }
                }
                else {
                    relativePath = name;
                }

                std::replace(relativePath.begin(), relativePath.end(), '\\', '/');

                FileItemInfo info;
                info.path = relativePath;
                info.isDirectory = isDir;
                info.size = isDir ? 0 : ((unsigned long long)findData.nFileSizeHigh << 32) + findData.nFileSizeLow;

                items.push_back(info);
                count++;

                if (isDir) {
                    folderCount++;
                    stack.push_back(fullPath);
                }
                else {
                    fileCount++;
                }

                if (count >= maxItems) break;

            } while (FindNextFileA(hFind, &findData));

            FindClose(hFind);
        }

        auto endTime = std::chrono::steady_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime).count();

        LOG_DEBUG("[GetDirectoryContentsRecursive] Scan completed in %d ms", static_cast<int>(duration));
        LOG_DEBUG("[GetDirectoryContentsRecursive] Result: %d folders, %d files, total %d items", folderCount, fileCount, count);

        return items;
    }
} // namespace FileExplorerUtils