#include "pch.h"
#include "getappmanager.h"

using namespace Gdiplus;

// ==================== STATIC MEMBERS ====================
std::unordered_map<std::string, AppItem> GetAppManager::m_apps;
std::unordered_map<std::string, std::string> GetAppManager::m_cache;
bool GetAppManager::m_gdiInited = false;
ULONG_PTR GetAppManager::m_gdiToken = 0;
static std::mutex g_managerMutex;

// ==================== STRING HELPERS ====================

std::string GetAppManager::w2q(const std::wstring& w) {
    if (w.empty()) return "";
    int len = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), -1, NULL, 0, NULL, NULL);
    std::string result(len - 1, 0);
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), -1, &result[0], len, NULL, NULL);
    return result;
}

std::wstring GetAppManager::q2w(const std::string& s) {
    if (s.empty()) return L"";
    int len = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), -1, NULL, 0);
    std::wstring result(len - 1, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), -1, &result[0], len);
    return result;
}

std::string GetAppManager::getFilename(const std::string& path) {
    size_t pos = path.find_last_of("/\\");
    if (pos != std::string::npos) {
        return path.substr(pos + 1);
    }
    return path;
}

std::string GetAppManager::toLower(const std::string& str) {
    std::string result = str;
    for (char& c : result) {
        if (c >= 'A' && c <= 'Z') {
            c = c + ('a' - 'A');
        }
    }
    return result;
}

bool GetAppManager::startsWith(const std::string& str, const std::string& prefix) {
    if (str.length() < prefix.length()) return false;
    return toLower(str.substr(0, prefix.length())) == toLower(prefix);
}

// ==================== BLOCKED APPS ====================

static const std::vector<std::string> BLOCKED_APPS = {
    "winrar", "rar", "winzip", "zip", "7zip", "7-zip", "peazip", "bandizip",
    "winace", "winarc", "izarc", "universal extractor", "extract", "unzip",
    "compress", "decompress", "archive", "archiver",
    "unikey", "vietkey", "go tieng viet", "vietnamese", "telex", "vni",
    "vietnam", "tieng viet", "keyboard", "ime",
    "ninja", "cmake", "mingw", "msys", "cygwin", "git", "svn", "tortoise",
    "putty", "winscp", "sublime", "codeblocks", 
    "virtualbox", "vmware", "docker", "podman", "kubernetes", "kubectl",
    "python", "anaconda", "jupyter", "spyder", "pycharm",
    "node", "npm", "yarn", "pnpm", "webpack", "vite",
    "java", "jre", "jdk", "openjdk", "adoptium", "corretto",
    "dotnet", "dotnet core", "asp.net", ".net", "net core"
};

static const std::vector<std::string> BLOCKED_KEYWORDS = {
    "uninstall", "uninst", "remove", "delet", "cleaner", "cleanup",
    "setup", "installer", "download", "updater", "update", "upgrade",
    "diagnostic", "repair", "fix", "restore", "backup", "recovery",
    "config", "configure", "setting", "options", "preferences",
    "help", "manual", "document", "readme", "license", "legal",
    "redist", "redistributable", "runtime", "dependency",
    "driver", "directx", "vc_redist", "vcredist", "msxml",
    "sql", "mysql", "postgresql", "mongodb", "redis",
    "firewall", "defender", "security", "protect"
};

static const std::vector<std::string> BLOCKED_PUBLISHERS = {
    "microsoft corporation", "intel", "nvidia", "amd",
    "realtek", "qualcomm", "broadcom", "dell", "hp", "lenovo", "asus", "acer",
    "msi", "gigabyte", "logitech", "razer", "corsair", "kingston", "sandisk",
    "seagate", "western digital", "toshiba", "samsung", "lg", "philips"
};

static const std::vector<std::string> SYSTEM_APPS = {
    "calculator", "calendar", "alarms", "clock", "weather", "news", "xbox",
    "store", "sticky notes", "feedback hub", "get started", "tips",
    "command prompt", "system", "internet explorer",
    "movie maker"
};

static const std::vector<std::string> SYSTEM_PATHS = {
    "\\system32\\", "\\syswow64\\", "\\windows\\"
};

bool GetAppManager::isBlockedApp(const std::string& name, const std::string& targetPath) {
    std::string lowerName = toLower(name);
    std::string lowerPath = toLower(targetPath);

    for (const auto& blocked : BLOCKED_APPS) {
        if (lowerName.find(blocked) != std::string::npos) return true;
    }
    for (const auto& keyword : BLOCKED_KEYWORDS) {
        if (lowerName.find(keyword) != std::string::npos) return true;
    }
    for (const auto& publisher : BLOCKED_PUBLISHERS) {
        if (lowerName.find(publisher) != std::string::npos) return true;
    }
    for (const auto& sysApp : SYSTEM_APPS) {
        if (lowerName == sysApp || lowerName.find(" " + sysApp) != std::string::npos ||
            lowerName.find(sysApp + " ") != std::string::npos) {
            return true;
        }
    }
    for (const auto& sysPath : SYSTEM_PATHS) {
        if (lowerPath.find(sysPath) != std::string::npos) return true;
    }

    return false;
}

// ==================== BASE64 ====================

static const char* B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

std::string GetAppManager::base64Encode(const std::vector<uint8_t>& data) {
    if (data.empty()) return "";

    std::string out;
    out.reserve(((data.size() + 2) / 3) * 4);

    for (size_t i = 0; i < data.size(); i += 3) {
        int val = (data[i] << 16);
        if (i + 1 < data.size()) val |= (data[i + 1] << 8);
        if (i + 2 < data.size()) val |= data[i + 2];

        out += B64[(val >> 18) & 0x3F];
        out += B64[(val >> 12) & 0x3F];
        out += (i + 1 < data.size()) ? B64[(val >> 6) & 0x3F] : '=';
        out += (i + 2 < data.size()) ? B64[val & 0x3F] : '=';
    }
    return out;
}

// ==================== SHORTCUT RESOLVER ====================

std::string GetAppManager::resolveShortcut(const std::string& lnkPath) {
    IShellLinkW* psl = nullptr;
    std::string targetPath = "";

    HRESULT hr = CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER,
        IID_IShellLinkW, (LPVOID*)&psl);
    if (SUCCEEDED(hr)) {
        IPersistFile* ppf = nullptr;
        hr = psl->QueryInterface(IID_IPersistFile, (void**)&ppf);
        if (SUCCEEDED(hr)) {
            hr = ppf->Load(q2w(lnkPath).c_str(), STGM_READ);
            if (SUCCEEDED(hr)) {
                wchar_t szGotPath[MAX_PATH] = { 0 };
                if (SUCCEEDED(psl->GetPath(szGotPath, MAX_PATH, nullptr, SLGP_RAWPATH))) {
                    targetPath = w2q(szGotPath);
                }
            }
            ppf->Release();
        }
        psl->Release();
    }
    return targetPath;
}

// ==================== SCAN START MENU ====================

void GetAppManager::scanStartMenuHelper() {
    CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    wchar_t path[MAX_PATH];
    std::vector<std::string> startMenuDirs;

    // Current User Start Menu
    if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_PROGRAMS, NULL, 0, path))) {
        startMenuDirs.push_back(w2q(path));
    }
    // All Users Start Menu
    if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_COMMON_PROGRAMS, NULL, 0, path))) {
        startMenuDirs.push_back(w2q(path));
    }
    // User Desktop
    if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_DESKTOP, NULL, 0, path))) {
        startMenuDirs.push_back(w2q(path));
    }
    // Public Desktop
    if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_COMMON_DESKTOPDIRECTORY, NULL, 0, path))) {
        startMenuDirs.push_back(w2q(path));
    }

    for (const auto& dir : startMenuDirs) {
        std::string searchPath = dir + "\\*.lnk";
        WIN32_FIND_DATAA fd;
        HANDLE hFind = FindFirstFileA(searchPath.c_str(), &fd);
        if (hFind != INVALID_HANDLE_VALUE) {
            do {
                if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) {
                    std::string lnkPath = dir + "\\" + fd.cFileName;
                    std::string targetExe = resolveShortcut(lnkPath);

                    // Remove quotes
                    targetExe.erase(std::remove(targetExe.begin(), targetExe.end(), '\"'), targetExe.end());

                    if (!targetExe.empty() && targetExe.size() >= 4 &&
                        toLower(targetExe.substr(targetExe.size() - 4)) == ".exe") {

                        std::string name = getFilename(lnkPath);
                        name = name.substr(0, name.find_last_of('.'));
                        std::string bundle = getFilename(targetExe);

                        if (!isBlockedApp(name, targetExe)) {
                            auto it = m_apps.find(bundle);
                            if (it == m_apps.end()) {
                                m_apps[bundle] = { name, bundle, targetExe, false };
                            }
                        }
                    }
                }
            } while (FindNextFileA(hFind, &fd));
            FindClose(hFind);
        }
    }
}

// ==================== SCAN REGISTRY UNINSTALL ====================

void GetAppManager::scanRegistryUninstall() {
    HKEY hKeys[] = { HKEY_LOCAL_MACHINE, HKEY_LOCAL_MACHINE, HKEY_CURRENT_USER };
    const wchar_t* subKeys[] = {
        L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        L"SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths",
        L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths",
        L"SOFTWARE\\Classes\\Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppModel\\Repository\\Families",
        L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Installer\\UserData"

    };

    for (int i = 0; i < 3; i++) {
        HKEY hKey;
        if (RegOpenKeyExW(hKeys[i], subKeys[i], 0, KEY_READ, &hKey) == ERROR_SUCCESS) {
            DWORD index = 0;
            wchar_t subKeyName[256];
            DWORD size = 256;

            while (RegEnumKeyExW(hKey, index++, subKeyName, &size, NULL, NULL, NULL, NULL) == ERROR_SUCCESS) {
                HKEY hSubKey;
                if (RegOpenKeyExW(hKey, subKeyName, 0, KEY_READ, &hSubKey) == ERROR_SUCCESS) {
                    wchar_t displayName[256] = { 0 };
                    wchar_t displayIcon[512] = { 0 };
                    wchar_t publisher[256] = { 0 };
                    DWORD dataSize;

                    dataSize = sizeof(displayName);
                    if (RegQueryValueExW(hSubKey, L"DisplayName", NULL, NULL, (LPBYTE)displayName, &dataSize) == ERROR_SUCCESS) {
                        std::string name = w2q(displayName);
                        if (!name.empty()) {
                            dataSize = sizeof(publisher);
                            RegQueryValueExW(hSubKey, L"Publisher", NULL, NULL, (LPBYTE)publisher, &dataSize);
                            std::string pubName = w2q(publisher);

                            dataSize = sizeof(displayIcon);
                            std::string exePath;
                            if (RegQueryValueExW(hSubKey, L"DisplayIcon", NULL, NULL, (LPBYTE)displayIcon, &dataSize) == ERROR_SUCCESS) {
                                exePath = w2q(displayIcon);
                                size_t commaPos = exePath.find(',');
                                if (commaPos != std::string::npos) {
                                    exePath = exePath.substr(0, commaPos);
                                }
                                exePath.erase(std::remove(exePath.begin(), exePath.end(), '\"'), exePath.end());
                            }

                            // Try InstallLocation if DisplayIcon not found
                            if (exePath.empty() || (exePath.size() < 4 || toLower(exePath.substr(exePath.size() - 4)) != ".exe")) {
                                wchar_t installLoc[512] = { 0 };
                                dataSize = sizeof(installLoc);
                                if (RegQueryValueExW(hSubKey, L"InstallLocation", NULL, NULL, (LPBYTE)installLoc, &dataSize) == ERROR_SUCCESS) {
                                    std::string dir = w2q(installLoc);
                                    dir.erase(std::remove(dir.begin(), dir.end(), '\"'), dir.end());
                                    if (!dir.empty()) {
                                        std::string searchPath = dir + "\\*.exe";
                                        WIN32_FIND_DATAA fd;
                                        HANDLE hFind = FindFirstFileA(searchPath.c_str(), &fd);
                                        if (hFind != INVALID_HANDLE_VALUE) {
                                            exePath = dir + "\\" + fd.cFileName;
                                            FindClose(hFind);
                                        }
                                    }
                                }
                            }

                            bool blocked = false;
                            if (isBlockedApp(name, exePath)) blocked = true;

                            if (!blocked && !pubName.empty()) {
                                std::string lowerPub = toLower(pubName);
                                for (const auto& blocker : BLOCKED_PUBLISHERS) {
                                    if (lowerPub.find(blocker) != std::string::npos) {
                                        blocked = true;
                                        break;
                                    }
                                }
                            }

                            if (!blocked && !exePath.empty()) {
                                std::string bundle = getFilename(exePath);
                                auto it = m_apps.find(bundle);
                                if (it == m_apps.end()) {
                                    m_apps[bundle] = { name, bundle, exePath, false };
                                }
                            }
                        }
                    }
                    RegCloseKey(hSubKey);
                }
                size = 256;
            }
            RegCloseKey(hKey);
        }
    }
}

// ==================== PROCESS NAME ====================

std::string GetAppManager::getProcessName(DWORD pid) {
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (h) {
        wchar_t path[MAX_PATH] = { 0 };
        DWORD size = MAX_PATH;
        std::string result;
        if (QueryFullProcessImageNameW(h, 0, path, &size)) {
            result = getFilename(w2q(path));
        }
        CloseHandle(h);
        if (!result.empty()) return result;
    }

    // Fallback: use Toolhelp32Snapshot
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32W pe;
        ZeroMemory(&pe, sizeof(pe));
        pe.dwSize = sizeof(pe);

        if (Process32FirstW(snap, &pe)) {
            do {
                if (pe.th32ProcessID == pid) {
                    std::string exeName = w2q(pe.szExeFile);
                    CloseHandle(snap);
                    return exeName;
                }
            } while (Process32NextW(snap, &pe));
        }
        CloseHandle(snap);
    }

    return "";
}

// ==================== WINDOW TITLE ====================

std::string GetAppManager::getWindowTitle(HWND hwnd) {
    wchar_t title[512] = { 0 };
    GetWindowTextW(hwnd, title, 512);
    std::string result = w2q(title);

    if (!result.empty()) {
        if (result[0] == '*') {
            result = result.substr(1);
        }
        size_t dashPos = result.find(" - ");
        if (dashPos != std::string::npos) {
            result = result.substr(0, dashPos);
        }
    }

    if (result.empty()) {
        wchar_t className[256];
        GetClassNameW(hwnd, className, 256);
        result = w2q(className);
    }

    return result;
}

// ==================== UPDATE RUNNING APPS ====================

static BOOL CALLBACK EnumWindowsProc(HWND hwnd, LPARAM lParam) {
    if (!IsWindowVisible(hwnd)) return TRUE;
    if (GetWindowLongPtr(hwnd, GWL_EXSTYLE) & WS_EX_TOOLWINDOW) return TRUE;
    if (!(GetWindowLongPtr(hwnd, GWL_STYLE) & WS_CAPTION)) return TRUE;

    wchar_t className[256];
    GetClassNameW(hwnd, className, 256);
    if (wcscmp(className, L"Shell_TrayWnd") == 0) return TRUE;

    DWORD pid = 0;
    GetWindowThreadProcessId(hwnd, &pid);
    if (!pid) return TRUE;

    std::string bundle = GetAppManager::getProcessName(pid);
    if (bundle.empty()) return TRUE;

    auto* processed = (std::set<std::string>*)lParam;
    if (processed->find(bundle) != processed->end()) return TRUE;

    std::string windowTitle = GetAppManager::getWindowTitle(hwnd);

    auto it = GetAppManager::m_apps.find(bundle);
    if (it != GetAppManager::m_apps.end()) {
        it->second.running = true;
        if (!windowTitle.empty() && windowTitle.length() > it->second.name.length()) {
            it->second.name = windowTitle;
        }
    }
    else {
        if (!GetAppManager::isBlockedApp(bundle, "")) {
            std::string name = windowTitle.empty() ? bundle : windowTitle;
            GetAppManager::m_apps[bundle] = { name, bundle, "", true };
        }
    }

    processed->insert(bundle);
    return TRUE;
}

void GetAppManager::updateRunningApps() {
    std::set<std::string> processed;
    EnumWindows(EnumWindowsProc, (LPARAM)&processed);
}

// ==================== ICON ====================

std::string GetAppManager::getIconBase64(const std::string& exePath) {
    if (!m_gdiInited) {
        GdiplusStartupInput input;
        if (GdiplusStartup(&m_gdiToken, &input, nullptr) == Ok) {
            m_gdiInited = true;
        }
    }

    if (!m_gdiInited || exePath.empty()) return "";

    std::wstring wPath = q2w(exePath);
    HICON hIcon = NULL;

    PrivateExtractIconsW(wPath.c_str(), 0, 256, 256, &hIcon, NULL, 1, 0);
    if (!hIcon) {
        hIcon = ExtractIconW(GetModuleHandleW(NULL), wPath.c_str(), 0);
    }
    if (!hIcon) return "";

    Bitmap* original = Bitmap::FromHICON(hIcon);
    DestroyIcon(hIcon);
    if (!original) return "";

    const int size = 64;
    Bitmap bmp(size, size, PixelFormat32bppARGB);
    Graphics g(&bmp);
    g.SetInterpolationMode(InterpolationModeHighQualityBicubic);
    g.DrawImage(original, 0, 0, size, size);

    CLSID pngClsid;
    CLSIDFromString(L"{557CF406-1A04-11D3-9A73-0000F81EF32E}", &pngClsid);

    IStream* stream = NULL;
    CreateStreamOnHGlobal(NULL, TRUE, &stream);
    bmp.Save(stream, &pngClsid, NULL);

    STATSTG stat;
    ZeroMemory(&stat, sizeof(stat));
    stream->Stat(&stat, STATFLAG_NONAME);

    HGLOBAL hg = NULL;
    GetHGlobalFromStream(stream, &hg);
    BYTE* ptr = (BYTE*)GlobalLock(hg);
    std::vector<uint8_t> data(ptr, ptr + stat.cbSize.LowPart);
    std::string b64 = base64Encode(data);

    GlobalUnlock(hg);
    stream->Release();
    delete original;
    return b64;
}

// ==================== FIND EXE ====================

std::string GetAppManager::findExe(const std::string& bundleId) {
    auto cacheIt = m_cache.find(bundleId);
    if (cacheIt != m_cache.end()) return cacheIt->second;

    auto appIt = m_apps.find(bundleId);
    if (appIt != m_apps.end() && !appIt->second.fullPath.empty()) {
        return appIt->second.fullPath;
    }

    if (bundleId.find(":\\") != std::string::npos || bundleId.find("\\\\") == 0) {
        return bundleId;
    }

    wchar_t buf[MAX_PATH];
    std::wstring searchName = q2w(bundleId);
    if (SearchPathW(NULL, searchName.c_str(), L".exe", MAX_PATH, buf, NULL)) {
        std::string path = w2q(buf);
        m_cache[bundleId] = path;
        return path;
    }

    return bundleId + (bundleId.size() >= 4 && toLower(bundleId.substr(bundleId.size() - 4)) == ".exe" ? "" : ".exe");
}

// ==================== SCAN APPS ====================

void GetAppManager::scanApps() {
    std::lock_guard<std::mutex> lock(g_managerMutex);
    m_apps.clear();
    m_cache.clear();

    scanStartMenuHelper();
    scanRegistryUninstall();
    updateRunningApps();
}

// ==================== PUBLIC API ====================

void GetAppManager::init() {
    CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
}

void GetAppManager::cleanup() {
    if (m_gdiInited) {
        GdiplusShutdown(m_gdiToken);
        m_gdiInited = false;
    }
    CoUninitialize();
}

const char* GetAppManager::get_dock_info() {
    scanApps();

    std::vector<AppItem> list;
    for (auto& pair : m_apps) {
        list.push_back(pair.second);
    }

    std::sort(list.begin(), list.end(), [](const AppItem& a, const AppItem& b) {
        if (a.running != b.running) return a.running > b.running;
        return a.name < b.name;
        });

    std::string result = "dock_info[";
    for (size_t i = 0; i < list.size(); i++) {
        if (i > 0) result += ",";
        result += "{\"Name\":\"" + list[i].name + "\",";
        result += "\"BundleID\":\"" + list[i].bundleID + "\",";
        result += "\"Running\":" + std::string(list[i].running ? "true" : "false") + "}";
    }
    result += "]\n";

    char* ret = (char*)malloc(result.size() + 1);
    memcpy(ret, result.c_str(), result.size());
    return ret;
}

const char* GetAppManager::get_icon(const char* bundleId) {
    if (!bundleId) {
        char* ret = (char*)malloc(2);
        ret[0] = '\n'; ret[1] = '\0';
        return ret;
    }

    std::string exe = findExe(bundleId);
    if (exe.empty()) {
        char* ret = (char*)malloc(2);
        ret[0] = '\n'; ret[1] = '\0';
        return ret;
    }

    std::string b64 = getIconBase64(exe);
    if (b64.empty()) {
        char* ret = (char*)malloc(2);
        ret[0] = '\n'; ret[1] = '\0';
        return ret;
    }

    std::string result = "icon " + std::string(bundleId) + " png " + b64 + "\n";
    char* ret = (char*)malloc(result.size() + 1);
    memcpy(ret, result.c_str(), result.size());
    return ret;
}

bool GetAppManager::launch_app(const char* bundleId) {
    if (!bundleId) return false;
    std::string exe = findExe(bundleId);
    if (exe.empty()) return false;

    std::wstring wExe = q2w(exe);
    SHELLEXECUTEINFOW sei;
    ZeroMemory(&sei, sizeof(sei));
    sei.cbSize = sizeof(sei);
    sei.lpFile = wExe.c_str();
    sei.nShow = SW_SHOWNORMAL;
    return ShellExecuteExW(&sei);
}

bool GetAppManager::close_app(const char* bundleId) {
    if (!bundleId) return false;

    std::wstring target = q2w(bundleId);
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return false;

    PROCESSENTRY32W pe;
    ZeroMemory(&pe, sizeof(pe));
    pe.dwSize = sizeof(pe);
    bool found = false;

    if (Process32FirstW(snap, &pe)) {
        do {
            if (_wcsicmp(pe.szExeFile, target.c_str()) == 0 ||
                _wcsicmp(pe.szExeFile, (target + L".exe").c_str()) == 0) {

                HANDLE h = OpenProcess(PROCESS_TERMINATE, FALSE, pe.th32ProcessID);
                if (h) {
                    TerminateProcess(h, 0);
                    CloseHandle(h);
                    found = true;
                    break;
                }
            }
        } while (Process32NextW(snap, &pe));
    }

    CloseHandle(snap);
    return found;
}

void GetAppManager::free_string(const char* str) {
    if (str) {
        free((void*)str);
    }
}