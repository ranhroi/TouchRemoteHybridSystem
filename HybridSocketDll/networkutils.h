#pragma once
#include "logmanager.h"

namespace NetworkUtils {

    // Khởi tạo tự động Winsock khi gọi hàm đầu tiên (Cơ chế RAII an toàn)
    struct WinsockInit {
        WinsockInit() {
            WSADATA wsaData;
            if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
                LOG_DEBUG("[TCP] WSAStartup failed!");
            }
        }
        ~WinsockInit() {
            WSACleanup();
        }
    };

    inline void ensureWinsock() {
        static WinsockInit init;
    }

    // 1. Lấy tên máy tính Windows
    inline std::string getHostName() {
        ensureWinsock();
        char buffer[256] = { 0 };
        if (gethostname(buffer, sizeof(buffer)) == 0) {
            return std::string(buffer);
        }
        return "Unknown-PC";
    }

    // 2. Lấy tất cả địa chỉ IPv4 đang hoạt động (Bỏ qua card mạng ảo/mạng tắt và loopback)
    inline std::vector<std::string> getAllIPs() {
        ensureWinsock();
        std::vector<std::string> ips;

        ULONG bufLen = sizeof(IP_ADAPTER_ADDRESSES);
        PIP_ADAPTER_ADDRESSES addresses = (IP_ADAPTER_ADDRESSES*)malloc(bufLen);
        if (!addresses) return { "127.0.0.1" };

        // Thử gọi lần một, nếu thiếu bộ nhớ hệ thống sẽ báo để cấp phát lại dung lượng chuẩn
        if (GetAdaptersAddresses(AF_INET, GAA_FLAG_INCLUDE_PREFIX, nullptr, addresses, &bufLen) == ERROR_BUFFER_OVERFLOW) {
            free(addresses);
            addresses = (IP_ADAPTER_ADDRESSES*)malloc(bufLen);
            if (!addresses) return { "127.0.0.1" };
        }

        if (GetAdaptersAddresses(AF_INET, GAA_FLAG_INCLUDE_PREFIX, nullptr, addresses, &bufLen) == NO_ERROR) {
            PIP_ADAPTER_ADDRESSES curr = addresses;
            while (curr) {
                // Chỉ quét các card mạng đang chạy (Up) và KHÔNG PHẢI loopback ảo
                if (curr->OperStatus == IfOperStatusUp && curr->IfType != IF_TYPE_SOFTWARE_LOOPBACK) {
                    PIP_ADAPTER_UNICAST_ADDRESS unicast = curr->FirstUnicastAddress;
                    while (unicast) {
                        if (unicast->Address.lpSockaddr->sa_family == AF_INET) {
                            sockaddr_in* ipv4 = (sockaddr_in*)unicast->Address.lpSockaddr;
                            char ipStr[INET_ADDRSTRLEN] = { 0 };

                            // Dịch cấu trúc nhị phân sang chuỗi văn bản IP phẳng
                            inet_ntop(AF_INET, &(ipv4->sin_addr), ipStr, sizeof(ipStr));

                            if (std::string(ipStr) != "127.0.0.1") {
                                ips.push_back(ipStr);
                            }
                        }
                        unicast = unicast->Next;
                    }
                }
                curr = curr->Next;
            }
        }
        free(addresses);

        if (ips.empty()) ips.push_back("127.0.0.1");
        return ips;
    }

    // 3. Lấy IP chính (IPv4 đầu tiên tìm thấy)
    inline std::string getLocalIP() {
        std::vector<std::string> ips = getAllIPs();
        return ips[0];
    }

    // 4. Lấy tên máy + IP kết hợp (Định dạng: "DESKTOP-ABC (192.168.1.5)")
    inline std::string getHostInfo() {
        std::stringstream ss;
        ss << getHostName() << " (" << getLocalIP() << ")";
        return ss.str();
    }

    // 5. Lấy nhãn phiên bản Windows thô (Thay thế QSysInfo)
    inline std::string getOsInfo() {
        return "windows";
    }

} // namespace NetworkUtils
