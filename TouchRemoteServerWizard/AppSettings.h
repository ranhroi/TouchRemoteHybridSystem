// AppSettings.h
#pragma once
#include <string>
#include "HybridSocket.h"  // Include để dùng RemoteServerConfig

class AppSettings
{
public:
    // ===== SETTINGS =====
    static int TCPPort;              // Default: 2028
    static int UDPPort;              // Default: 2027
    static bool NeedPassword;
    static bool BluetoothEnabled;
    static bool AutoRunWithWindows;
    static bool AutoStartServer;
    static std::string Password;     // ANSI - khớp với DLL
    static std::string BluetoothUUID; // ANSI - khớp với DLL

    // ===== METHODS =====
    static void Load();
    static void Save();
    static void ResetToDefault();

    // ===== CONVERT TO CONFIG =====
    static RemoteServerConfig ToRemoteConfig();

    // ===== AUTO RUN =====
    static void SetAutoRunRegistry(bool enable);
    static bool IsAutoRunEnabled();

    // ===== PASSWORD =====
    static std::string GenerateRandomPassword(int length = 4);
};