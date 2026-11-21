// AppSettings.cpp
#include "AppSettings.h"
#include <Windows.h>
#include <shlobj.h>
#include <fstream>
#include <sstream>
#include <string>
#include <cstdlib>
#include <ctime>

#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "advapi32.lib")

// ===== ĐỊNH NGHĨA STATIC MEMBERS =====
int AppSettings::TCPPort = 2028;                    // Default mới
int AppSettings::UDPPort = 2027;                    // Default mới
bool AppSettings::NeedPassword = false;
bool AppSettings::BluetoothEnabled = true;
bool AppSettings::AutoRunWithWindows = false;
bool AppSettings::AutoStartServer = false;
std::string AppSettings::Password = "";
std::string AppSettings::BluetoothUUID = "c05efa2b-5e9f-4a39-9705-72ccf47d2eb8";

// ===== HELPER: LẤY ĐƯỜNG DẪN CONFIG =====
static std::string GetConfigDir()
{
    char path[MAX_PATH] = { 0 };
    if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_LOCAL_APPDATA, NULL, 0, path)))
    {
        std::string dir = std::string(path) + "\\TouchRemoteServer";
        CreateDirectoryA(dir.c_str(), NULL);
        return dir;
    }
    return ".";
}

static std::string GetConfigPath()
{
    return GetConfigDir() + "\\config.dat";
}

// ===== HELPER: PARSE LINE =====
static std::string GetKey(const std::string& line)
{
    size_t pos = line.find('=');
    if (pos == std::string::npos) return line;
    return line.substr(0, pos);
}

static std::string GetValue(const std::string& line)
{
    size_t pos = line.find('=');
    if (pos == std::string::npos) return "";
    return line.substr(pos + 1);
}

// ===== HELPER: MÃ HÓA PASSWORD (ANSI) =====
static std::string EncryptPassword(const std::string& password)
{
    if (password.empty()) return "";

    const char key = 'K';
    std::string encrypted = password;
    for (size_t i = 0; i < encrypted.length(); i++)
    {
        encrypted[i] = encrypted[i] ^ key;
    }

    // Chuyển sang hex
    std::stringstream ss;
    for (size_t i = 0; i < encrypted.length(); i++)
    {
        ss << std::hex << (int)(unsigned char)encrypted[i];
        if (i < encrypted.length() - 1)
            ss << ",";
    }
    return ss.str();
}

static std::string DecryptPassword(const std::string& encrypted)
{
    if (encrypted.empty()) return "";

    std::string decrypted;
    std::stringstream ss(encrypted);
    std::string item;

    while (std::getline(ss, item, ','))
    {
        if (item.empty()) continue;
        try
        {
            int val = std::stoi(item, nullptr, 16);
            decrypted += (char)(val ^ 'K');
        }
        catch (...) {}
    }

    return decrypted;
}

// ===== LOAD SETTINGS =====
void AppSettings::Load()
{
    std::string configPath = GetConfigPath();

    std::ifstream file(configPath.c_str(), std::ios::binary);
    if (!file.is_open())
    {
        // File chưa tồn tại → dùng default
        SetAutoRunRegistry(AutoRunWithWindows);
        return;
    }

    // Đọc toàn bộ file
    std::string content((std::istreambuf_iterator<char>(file)),
        std::istreambuf_iterator<char>());
    file.close();

    // Parse từng dòng
    std::stringstream ss(content);
    std::string line;

    while (std::getline(ss, line))
    {
        // Bỏ \r
        if (!line.empty() && line.back() == '\r')
            line.pop_back();

        if (line.empty()) continue;
        if (line[0] == '#') continue;

        std::string key = GetKey(line);
        std::string value = GetValue(line);

        if (key == "TCPPort")
        {
            try { TCPPort = std::stoi(value); }
            catch (...) { TCPPort = 2028; }
        }
        else if (key == "UDPPort")
        {
            try { UDPPort = std::stoi(value); }
            catch (...) { UDPPort = 2027; }
        }
        else if (key == "NeedPassword")
        {
            NeedPassword = (value == "1" || value == "true");
        }
        else if (key == "BluetoothEnabled")
        {
            BluetoothEnabled = (value == "1" || value == "true");
        }
        else if (key == "AutoRunWithWindows")
        {
            AutoRunWithWindows = (value == "1" || value == "true");
        }
        else if (key == "AutoStartServer")
        {
            AutoStartServer = (value == "1" || value == "true");
        }
        else if (key == "Password")
        {
            Password = DecryptPassword(value);
        }
        else if (key == "BluetoothUUID")
        {
            BluetoothUUID = value;
        }
    }

    SetAutoRunRegistry(AutoRunWithWindows);
}

// ===== SAVE SETTINGS =====
void AppSettings::Save()
{
    std::string configPath = GetConfigPath();

    std::stringstream ss;
    ss << "TCPPort=" << TCPPort << "\n";
    ss << "UDPPort=" << UDPPort << "\n";
    ss << "NeedPassword=" << (NeedPassword ? "1" : "0") << "\n";
    ss << "BluetoothEnabled=" << (BluetoothEnabled ? "1" : "0") << "\n";
    ss << "AutoRunWithWindows=" << (AutoRunWithWindows ? "1" : "0") << "\n";
    ss << "AutoStartServer=" << (AutoStartServer ? "1" : "0") << "\n";
    ss << "Password=" << EncryptPassword(Password) << "\n";
    ss << "BluetoothUUID=" << BluetoothUUID << "\n";

    std::ofstream file(configPath.c_str(), std::ios::binary | std::ios::trunc);
    if (!file.is_open())
    {
        MessageBoxA(NULL, "Cannot save config file!", "Error", MB_OK | MB_ICONERROR);
        return;
    }

    std::string content = ss.str();
    file.write(content.c_str(), content.size());
    file.close();

    SetAutoRunRegistry(AutoRunWithWindows);
}

// ===== CHUYỂN ĐỔI SANG REMOTESERVERCONFIG =====
RemoteServerConfig AppSettings::ToRemoteConfig()
{
    RemoteServerConfig config;

    // ===== THỨ TỰ ĐÚNG THEO STRUCT =====
    config.udpPort = UDPPort;                          // UDP trước
    config.tcpPort = TCPPort;                          // TCP sau
    config.needPassword = NeedPassword;
    config.enableBluetooth = BluetoothEnabled;

    // ===== QUAN TRỌNG: PASSWORD VÀ UUID LÀ CONST CHAR* =====
    // Phải giữ chuỗi sống trong suốt thời gian server chạy
    // → Dùng static buffer hoặc cấp phát động

    static std::string s_password;   // Static để giữ chuỗi sống
    static std::string s_uuid;

    s_password = Password;
    s_uuid = BluetoothUUID;

    config.password = s_password.c_str();
    config.bluetoothUuid = s_uuid.c_str();

    return config;
}

// ===== SET AUTO RUN REGISTRY =====
void AppSettings::SetAutoRunRegistry(bool enable)
{
    HKEY hKey = NULL;
    LONG result = RegOpenKeyExA(
        HKEY_CURRENT_USER,
        "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
        0,
        KEY_SET_VALUE | KEY_QUERY_VALUE,
        &hKey
    );

    if (result != ERROR_SUCCESS)
        return;

    if (enable)
    {
        char exePath[MAX_PATH] = { 0 };
        GetModuleFileNameA(NULL, exePath, MAX_PATH);

        RegSetValueExA(
            hKey,
            "TouchRemoteServer",
            0,
            REG_SZ,
            (const BYTE*)exePath,
            (DWORD)(strlen(exePath) + 1)
        );
    }
    else
    {
        RegDeleteValueA(hKey, "TouchRemoteServer");
    }

    RegCloseKey(hKey);
}

// ===== CHECK AUTO RUN =====
bool AppSettings::IsAutoRunEnabled()
{
    HKEY hKey = NULL;
    LONG result = RegOpenKeyExA(
        HKEY_CURRENT_USER,
        "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
        0,
        KEY_QUERY_VALUE,
        &hKey
    );

    if (result != ERROR_SUCCESS)
        return false;

    char value[MAX_PATH] = { 0 };
    DWORD size = sizeof(value);
    DWORD type = 0;

    result = RegQueryValueExA(
        hKey,
        "TouchRemoteServer",
        NULL,
        &type,
        (LPBYTE)value,
        &size
    );

    RegCloseKey(hKey);
    return (result == ERROR_SUCCESS && type == REG_SZ);
}

// ===== RESET DEFAULT =====
void AppSettings::ResetToDefault()
{
    TCPPort = 2028;
    UDPPort = 2027;
    NeedPassword = false;
    BluetoothEnabled = true;
    AutoRunWithWindows = false;
    AutoStartServer = false;
    Password = "";
    BluetoothUUID = "c05efa2b-5e9f-4a39-9705-72ccf47d2eb8";

    SetAutoRunRegistry(false);
    Save();
}

// ===== GENERATE RANDOM PASSWORD =====
std::string AppSettings::GenerateRandomPassword(int length)
{
    const char chars[] = "0123456789";
    int charCount = (sizeof(chars) / sizeof(char)) - 1;

    std::string result;
    result.reserve(length);

    srand((unsigned int)GetTickCount64());

    for (int i = 0; i < length; i++)
    {
        result += chars[rand() % charCount];
    }

    return result;
}