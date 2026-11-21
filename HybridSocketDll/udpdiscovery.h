#pragma once
#include "networkutils.h"

class UDPDiscovery {
public:
    UDPDiscovery();
    ~UDPDiscovery();

    // portScan: Cổng Android đang nghe
    void start(int portScan);
    void stop();

private:
    void broadcastThread();

private:
    std::atomic<bool> m_running{ false };
    std::thread m_worker;
    int m_multicastPort = 2027;
    SOCKET m_socket = INVALID_SOCKET;
    const char* MULTICAST_GROUP = "224.3.2.1"; // Khớp với Android
};