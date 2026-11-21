#pragma once

struct AppItem {
    std::string name;
    std::string bundleID;
    std::string fullPath;
    bool running = false;
};

class GetAppManager {
public:
    static const char* get_dock_info();
    static const char* get_icon(const char* bundleId);
    static bool launch_app(const char* bundleId);
    static bool close_app(const char* bundleId);
    static void free_string(const char* str);

    static void init();
    static void cleanup();

    static bool isBlockedApp(const std::string& name, const std::string& targetPath);
    static std::string getProcessName(DWORD pid);
    static std::string getWindowTitle(HWND hwnd);

    static std::unordered_map<std::string, AppItem> m_apps;
private:
    static std::unordered_map<std::string, std::string> m_cache;
    static bool m_gdiInited;
    static ULONG_PTR m_gdiToken;

    static std::string findExe(const std::string& bundleId);
    static std::string getIconBase64(const std::string& exePath);
    static void scanApps();
    static void scanRegistryUninstall();
    static void updateRunningApps();
    static std::string resolveShortcut(const std::string& lnkPath);
    static void scanStartMenuHelper();
    static std::string base64Encode(const std::vector<uint8_t>& data);
    static std::string w2q(const std::wstring& w);
    static std::wstring q2w(const std::string& s);
    static std::string getFilename(const std::string& path);
    static std::string toLower(const std::string& str);
    static bool startsWith(const std::string& str, const std::string& prefix);
};

