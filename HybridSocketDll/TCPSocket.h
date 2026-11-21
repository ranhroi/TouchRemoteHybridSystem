#pragma once
#include "itransporthybrid.h"

class TCPSocket : public ITransportHybrid {
public:
    explicit TCPSocket(int port) : port(port) {
        transportName = "TCP-" + std::to_string(port);
    }

    TransportType getTransportType() const override { return TransportType::TCP; }

protected:
    void initSocket() override;
    void runAccept(OnAccepted cb) override;
    void cleanup() override;

private:
    int port;
};