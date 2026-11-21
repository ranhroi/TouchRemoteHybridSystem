#pragma once
#include "btsocket.h"
#include "tcpsocket.h"
#include "UDPDiscovery.h"
#include "sessionhandler.h"
#include "remoteserverconfig.h"

class SocketManager {
public:
    SocketManager(const RemoteServerConfig* config);
    ~SocketManager();

    void start();
    void stop();
    void restart();
    bool isRunning() const;

private:
    void onNewConnection(SOCKET socket, TransportType type, const std::string& clientInfo);

private:
    std::shared_ptr<TCPSocket> m_tcpSocket;
    std::shared_ptr<BTSocket> m_btSocket;
    std::unique_ptr<UDPDiscovery> m_udpDiscovery;

    std::shared_ptr<SessionHandler> m_handler;

    std::atomic<bool> m_running{ false };
    std::thread m_acceptThread;              // ✅ Accept thread

    int m_tcpPort;
    int m_udpPort;
    bool m_btEnabled;
    std::string m_BtUuid;
};