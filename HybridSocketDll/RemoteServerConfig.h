#ifndef REMOTE_SERVER_CONFIG_H
#define REMOTE_SERVER_CONFIG_H

#ifdef __cplusplus
extern "C" {
#endif

    // ============================================================================
    //  CẤU HÌNH SERVER
    // ============================================================================

    typedef struct RemoteServerConfig {
        int  udpPort;               // UDP port (default: 2027)
        int  tcpPort;               // TCP port (default: 2028)
        bool needPassword;          // Enable password protection
        bool enableBluetooth;       // Enable Bluetooth server
        const char* password;       // Plain text password
        const char* bluetoothUuid;  // Bluetooth service UUID
    } RemoteServerConfig;

    // ============================================================================
    //  HẰNG SỐ MẶC ĐỊNH
    // ============================================================================

#define DEFAULT_UDP_PORT         2027
#define DEFAULT_TCP_PORT         2028
#define DEFAULT_BLUETOOTH_UUID   "c05efa2b-5e9f-4a39-9705-72ccf47d2eb8"

// ============================================================================
//  HÀM KHỞI TẠO CONFIG
// ============================================================================

    static inline void initRemoteServerConfig(RemoteServerConfig* config) {
        if (!config) return;

        config->udpPort = DEFAULT_UDP_PORT;
        config->tcpPort = DEFAULT_TCP_PORT;
        config->needPassword = false;
        config->enableBluetooth = false;
        config->password = "";
        config->bluetoothUuid = DEFAULT_BLUETOOTH_UUID;
    }

    // ============================================================================
    //  HÀM COPY CONFIG (CHO UI TRUYỀN VÀO)
    // ============================================================================

    static inline void copyRemoteServerConfig(RemoteServerConfig* dest, const RemoteServerConfig* src) {
        if (!dest || !src) return;

        dest->udpPort = src->udpPort;
        dest->tcpPort = src->tcpPort;
        dest->enableBluetooth = src->enableBluetooth;
        dest->bluetoothUuid = src->bluetoothUuid;
        dest->password = src->password;
        dest->needPassword = src->needPassword;
    }

    // ============================================================================
    //  HÀM KIỂM TRA
    // ============================================================================

    static inline bool isValidPort(int port) {
        return (port > 0 && port < 65535);
    }

    static inline bool isValidMaxConnections(int maxConn) {
        return (maxConn > 0 && maxConn <= 100);
    }

    static inline bool isValidUuid(const char* uuid) {
        if (!uuid) return false;

        int len = 0;
        int dashCount = 0;
        int hexCount = 0;

        for (const char* p = uuid; *p; p++) {
            len++;

            if (*p == '-') {
                dashCount++;
            }
            else if ((*p >= '0' && *p <= '9') ||
                (*p >= 'a' && *p <= 'f') ||
                (*p >= 'A' && *p <= 'F')) {
                hexCount++;
            }
            else {
                return false;
            }
        }

        return (len == 36 && dashCount == 4 && hexCount == 32);
    }
#ifdef __cplusplus
}
#endif

#endif // REMOTE_SERVER_CONFIG_H