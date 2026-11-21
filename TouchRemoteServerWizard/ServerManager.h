// ServerManager.h
#pragma once
#include <Windows.h>
#include <string>

// Forward declaration - tránh include HybridSocket.h ở header
// để giảm thời gian build
struct RemoteServerConfig;

class ServerManager
{
public:
    ServerManager();
    ~ServerManager();

    // ===== START / STOP =====
    bool Start();
    void Stop();
    bool IsRunning() const;

    // ===== IP ADDRESS =====
    std::string GetLocalIP();

    // ===== CONNECTION STATS =====
    int GetConnectionCount();
    void GetConnectionStats(int* tcp, int* bt);
    int GetTotalConnections();

    // ===== LOG =====
    std::string PopLog();

    // ===== GETTERS =====
    bool IsServerRunning() const { return m_running; }

private:
    bool m_running;

    // ===== INTERNAL HELPERS =====
    void LogToDebug(const std::string& message);
};