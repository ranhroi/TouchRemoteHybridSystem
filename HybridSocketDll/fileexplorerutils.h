#pragma once

namespace FileExplorerUtils {
    // ==================== PATH UTILITIES ====================
    std::string NormalizeClientPath(const std::string& path);
    std::string BuildFolderInfoJson(const std::string& folderPath);
    std::string EscapeJsonString(const std::string& str);
    std::string GetAbsolutePath(const std::string& path, const std::string& baseDir);
    std::string GetFileName(const std::string& path);
    std::string GetParentDirectory(const std::string& path);

    // ==================== SYSTEM PATHS ====================
    std::string GetDesktopPath();
    std::string GetDocumentsPath();
    std::string GetDownloadsPath();
    std::string GetMusicPath();
    std::string GetVideosPath();
    std::string GetPicturesPath();
    std::string GetHomeDirectory();
    std::string GetTempDirectory();
    std::vector<std::pair<std::string, std::string>> GetSystemShortcuts();
    std::vector<std::string> GetAllRealDrives();

    // ==================== FILE OPERATIONS (Basic) ====================
    bool FileExists(const std::string& path);
    bool IsDirectory(const std::string& path);
    bool IsFile(const std::string& path);
    uint64_t GetFileSize(const std::string& path);

    // ==================== FILE OPERATIONS (Recursive) ====================
    bool CreateDirectoryRecursive(const std::string& path);
    bool RemoveRecursively(const std::string& path);
    unsigned long long GetFileSizeRecursive(const std::string& path);
    bool CopyDirectoryRecursively(const std::string& srcPath, const std::string& dstPath);

    // ==================== IFSTREAM SUPPORT (Đồng bộ với SmartSenderBinary) ====================

    // Mở file và trả về unique_ptr<ifstream> (tự động quản lý bộ nhớ)
    std::unique_ptr<std::ifstream> OpenFileForReading(const std::string& path);

    // Mở file và trả về raw pointer (chuyển ownership cho SmartSenderBinary)
    std::ifstream* OpenFileForReadingRaw(const std::string& path);

    // Mở file với offset (hỗ trợ resume)
    std::unique_ptr<std::ifstream> OpenFileForReading(const std::string& path, uint64_t offset);
    std::ifstream* OpenFileForReadingRaw(const std::string& path, uint64_t offset);

    // Kiểm tra file có thể đọc được không
    bool CanReadFile(const std::string& path);

    // Đọc một phần của file vào buffer
    bool ReadFileChunk(const std::string& path, uint64_t offset, uint32_t size, std::vector<uint8_t>& buffer);

    // Lấy thông tin file chi tiết (cho SmartSenderBinary)
    struct FileInfo {
        std::string fullPath;
        std::string fileName;
        uint64_t fileSize;
        bool isDirectory;
        bool isReadOnly;
        bool isHidden;

        FileInfo() : fileSize(0), isDirectory(false), isReadOnly(false), isHidden(false) {}
    };

    FileInfo GetFileInfoDetailed(const std::string& path);

    struct FileItemInfo {
        std::string path;      // Đường dẫn tương đối
        bool isDirectory;
        uint64_t size;
    };

    std::vector<FileItemInfo> GetDirectoryContentsRecursive(const std::string& folderPath, int maxItems = 50000);

    // ==================== VALIDATION ====================
    bool IsPathSafe(const std::string& path);
    bool IsFileSizeAllowed(uint64_t fileSize, bool isBluetooth);
    bool IsPathWithinAllowedDirectory(const std::string& path, const std::string& baseDir);

    // ==================== FORMATTING ====================
    std::string FormatFileSize(uint64_t bytes);
    std::string GetFileExtension(const std::string& path);
}