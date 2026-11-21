#pragma once

#ifdef HYBRIDSOCKET_EXPORTS
#define HYBRIDSOCKET_API __declspec(dllexport)
#else
#define HYBRIDSOCKET_API __declspec(dllimport)
#endif

#include "RemoteServerConfig.h"

#ifdef __cplusplus
extern "C" {
#endif

    HYBRIDSOCKET_API bool RemoteServer_Start(const RemoteServerConfig* config);
    HYBRIDSOCKET_API void RemoteServer_Stop();
    HYBRIDSOCKET_API void RemoteServer_Cleanup();
    HYBRIDSOCKET_API bool RemoteServer_IsRunning();
    HYBRIDSOCKET_API void RemoteServer_Restart();
    HYBRIDSOCKET_API int  RemoteServer_GetConnectionStats(int* tcpCount, int* btCount);
    HYBRIDSOCKET_API const char* RemoteServer_PopLog();
    HYBRIDSOCKET_API const char* RemoteServer_GetLocalIP();
    HYBRIDSOCKET_API const char* RemoteServer_GetMachineName();

#ifdef __cplusplus
}
#endif