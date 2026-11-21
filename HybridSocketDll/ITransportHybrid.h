#pragma once

class ITransportHybrid {
public:
    using OnAccepted = std::function<void(SOCKET, const std::string&)>;

    ITransportHybrid() : transportName("Unknown") {}
    virtual ~ITransportHybrid() { stop(); }

    void start(OnAccepted cb, const std::string& name = "") {
        if (m_running.exchange(true)) return;
        if (!name.empty()) transportName = name;

        m_onAccepted = cb;
        initSocket(); // Khởi tạo socket lắng nghe
    }

    void stop() {
        if (!m_running.exchange(false)) return;
        cleanup();
        LOG_INFO("[%s] Stopped.", transportName.c_str());
    }

    void pollAccept() {
        if (!m_running || listenSocket == INVALID_SOCKET) return;
        runAccept(m_onAccepted);
    }

    bool isRunning() const { return m_running.load(); }
    virtual TransportType getTransportType() const = 0;
    SOCKET getListenSocket() const { return listenSocket; }

protected:
    virtual void initSocket() = 0;  // Khởi tạo socket
    virtual void runAccept(OnAccepted cb) = 0; // Non-blocking accept
    virtual void cleanup() = 0;

    SOCKET listenSocket = INVALID_SOCKET;
    std::atomic<bool> m_running{ false };
    std::string transportName;
    OnAccepted m_onAccepted;
};