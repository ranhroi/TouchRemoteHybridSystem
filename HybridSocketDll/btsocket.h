#pragma once
#include "itransporthybrid.h"
#include "networkutils.h"
#include "sessioncontext.h"

class BTSocket : public ITransportHybrid {
public:
    BTSocket(const std::string& uuid);
    ~BTSocket();

    TransportType getTransportType() const override { return TransportType::BLUETOOTH; }

protected:
    void initSocket() override;
    void runAccept(OnAccepted cb) override;
    void cleanup() override;

private:
    void setupSocketForAndroid(SOCKET client);
    bool registerBluetoothService(int channel, GUID myUuid);
    void unregisterBluetoothService(GUID myUuid);
    std::string formatBluetoothAddress(unsigned long long btAddr);

private:
    std::string m_uuidString;
    GUID m_uuidGuid;
    std::mutex m_cleanupMutex;
};