#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"
#include "getappmanager.h"

// ==================== APP MANAGEMENT ====================
void ProcessCommands::handleGetApp(const uint8_t* payload, int len, OpCode op, SOCKET socket) {
    if (!socket) return;

    if (op == OpCode::GETDOCKINFOAPP) {
        const char* dockInfo = GetAppManager::get_dock_info();
        if (dockInfo) {
            SenderBinary::send(socket, dockInfo);
            GetAppManager::free_string(dockInfo);
        }
        return;
    }

    if (len <= 0) return;

    // Lấy bundleId từ data
    std::string bundleId((const char*)payload, len);

    // Trim whitespace
    size_t start = bundleId.find_first_not_of(" \t\n\r");
    if (start != std::string::npos) bundleId = bundleId.substr(start);
    size_t end = bundleId.find_last_not_of(" \t\n\r");
    if (end != std::string::npos) bundleId = bundleId.substr(0, end + 1);

    if (bundleId.empty()) return;

    switch (op) {
    case OpCode::GETICONAPP: {
        const char* iconData = GetAppManager::get_icon(bundleId.c_str());
        if (iconData) {
            SenderBinary::send(socket, iconData);
            GetAppManager::free_string(iconData);
            ::Sleep(50);
        }
        break;
    }

    case OpCode::LAUNCHAPP:
        GetAppManager::launch_app(bundleId.c_str());
        break;

    case OpCode::CLOSEAPP:
        GetAppManager::close_app(bundleId.c_str());
        break;

    default:
        break;
    }
}
