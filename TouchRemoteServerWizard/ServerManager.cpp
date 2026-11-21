// ServerManager.cpp
#include "ServerManager.h"
#include "AppSettings.h"
#include "HybridSocket.h"   // Include ở đây thay vì header

#include <string>
#include <sstream>
#include <cstdio>

// ===== CONSTRUCTOR =====
ServerManager::ServerManager()
    : m_running(false)
{
}

// ===== DESTRUCTOR =====
ServerManager::~ServerManager()
{
    Stop();
}

// ===== START SERVER =====
bool ServerManager::Start()
{
    if (m_running)
    {
        LogToDebug("[ServerManager] Server already running");
        return true;
    }

    // ===== CHUYỂN ĐỔI SETTINGS SANG CONFIG =====
    RemoteServerConfig config = AppSettings::ToRemoteConfig();

    // ===== DEBUG LOG =====
    {
        char debug[1024];
        sprintf_s(debug, sizeof(debug),
            "[ServerManager] Starting with config:\n"
            "  UDP Port    : %d\n"
            "  TCP Port    : %d\n"
            "  Need Pass   : %s\n"
            "  Password    : '%s'\n"
            "  Bluetooth   : %s\n"
            "  BT UUID     : '%s'\n",
            config.udpPort,
            config.tcpPort,
            config.needPassword ? "YES" : "NO",
            config.password ? config.password : "(null)",
            config.enableBluetooth ? "YES" : "NO",
            config.bluetoothUuid ? config.bluetoothUuid : "(null)"
        );
        LogToDebug(debug);
    }

    // ===== KHỞI ĐỘNG SERVER =====
    int result = RemoteServer_Start(&config);

    if (result)
    {
        m_running = true;
        LogToDebug("[ServerManager] Server started successfully!");
        return true;
    }
    else
    {
        m_running = false;
        LogToDebug("[ServerManager] FAILED to start server!");
        return false;
    }
}

// ===== STOP SERVER =====
void ServerManager::Stop()
{
    if (!m_running)
    {
        LogToDebug("[ServerManager] Server not running");
        return;
    }

    LogToDebug("[ServerManager] Stopping server...");

    if (RemoteServer_IsRunning())
    {
        RemoteServer_Stop();
    }

    RemoteServer_Cleanup();

    m_running = false;
    LogToDebug("[ServerManager] Server stopped!");
}

// ===== IS RUNNING =====
bool ServerManager::IsRunning() const
{
    return RemoteServer_IsRunning() != 0;
}

// ===== GET LOCAL IP =====
std::string ServerManager::GetLocalIP()
{
    const char* ip = RemoteServer_GetLocalIP();

    if (ip == nullptr || strlen(ip) == 0)
    {
        return "127.0.0.1";
    }

    return std::string(ip);
}

// ===== GET CONNECTION COUNT =====
int ServerManager::GetConnectionCount()
{
    int tcp = 0;
    int bt = 0;

    RemoteServer_GetConnectionStats(&tcp, &bt);

    return tcp + bt;
}

// ===== GET CONNECTION STATS =====
void ServerManager::GetConnectionStats(int* tcp, int* bt)
{
    if (tcp == nullptr || bt == nullptr) return;

    *tcp = 0;
    *bt = 0;

    RemoteServer_GetConnectionStats(tcp, bt);
}

// ===== GET TOTAL CONNECTIONS =====
int ServerManager::GetTotalConnections()
{
    int tcp = 0;
    int bt = 0;

    RemoteServer_GetConnectionStats(&tcp, &bt);

    return tcp + bt;
}

// ===== POP LOG =====
std::string ServerManager::PopLog()
{
    const char* log = RemoteServer_PopLog();

    if (log == nullptr || strlen(log) == 0)
    {
        return std::string();
    }

    return std::string(log);
}

// ===== LOG TO DEBUG =====
void ServerManager::LogToDebug(const std::string& message)
{
#ifdef _DEBUG
    OutputDebugStringA(message.c_str());
    OutputDebugStringA("\n");
#else
    UNREFERENCED_PARAMETER(message);
#endif
}