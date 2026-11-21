#pragma once
#define _CRT_SECURE_NO_WARNINGS
#define WIN32_LEAN_AND_MEAN

// ==================== WINDOWS HEADERS (ĐÚNG THỨ TỰ) ====================
#include <winsock2.h>
#include <ws2tcpip.h>
#include <ws2bth.h>
#include <windows.h>
#include <ws2ipdef.h>

// ==================== WINDOWS EXTENSIONS ====================
#include <windowsx.h>
#include <shlobj.h>
#include <shlwapi.h>
#include <shellapi.h>
#include <objbase.h>
#include <strsafe.h>
#include <VersionHelpers.h>

#include <wrl/client.h>

// ==================== WINDOWS APIs ====================
#include <iphlpapi.h>
#include <pdh.h>
#include <tlhelp32.h>
#include <psapi.h>
#include <powrprof.h>
#include <comdef.h>
#include <Wbemidl.h>
#include <bluetoothapis.h>
#include <gdiplus.h>
#include <wincrypt.h>
#include <wrl.h>
#include <d3d11.h>
#include <dxgi1_2.h>

// ==================== C++ STANDARD HEADERS ====================
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <cstdbool>
#include <cstdint>
#include <climits>
#include <iostream>
#include <iomanip>
#include <sstream>
#include <fstream>
#include <string>
#include <vector>
#include <map>
#include <unordered_map>
#include <set>
#include <queue>
#include <algorithm> 
#include <functional>
#include <memory>
#include <atomic>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <chrono>
#include <locale>
#include <codecvt>
#include <io.h>
#include <utility> 

#include <string_view>
#include <charconv> // Yêu cầu C++17 trở lên để xử lý phân tách siêu nhanh

#include <mmdeviceapi.h>        // IMMDeviceEnumerator, IMMDevice
#include <audioclient.h>        // IAudioClient, IAudioCaptureClient
#include <audiosessiontypes.h>  // AudioSessionTypes
#include <wels/codec_api.h>
#include <wels/codec_app_def.h>
#include <wels/codec_def.h> 
#include <libyuv.h>
#include <avrt.h>

#include "logmanager.h"
#include "commontypes.h"
#include "fasthash.h"

// ==================== THƯ VIỆN LIÊN KẾT ====================
#pragma comment(lib, "iphlpapi.lib")
#pragma comment(lib, "ws2_32.lib")
#pragma comment(lib, "Bthprops.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "powrprof.lib")
#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "wbemuuid.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "Advapi32.lib")
#pragma comment(lib, "pdh.lib")

#pragma comment(lib, "avrt.lib")
#pragma comment(lib, "winmm.lib")
#pragma comment(lib, "ole32.lib")

#pragma execution_character_set("utf-8")
#pragma warning(disable: 4251)

/**
* libyuv.lib (Xử lý chuyển đổi định dạng ảnh/video)
* ws2_32.lib (Kết nối mạng Socket - rất cần cho dự án HybridSocket của bạn)
* d3d11.lib (Đồ họa DirectX 11)
* dxgi.lib (Giao tiếp phần cứng đồ họa DirectX)
* openh264.lib (Bộ mã hóa/giải mã video H264)
* winmm.lib (Hệ thống đa phương tiện Windows)
* ole32.lib (Thành phần COM của Windows)
* mmdevapi.lib (Giao tiếp thiết bị âm thanh)
* d3dcompiler.lib (Biên dịch shader cho DirectX)
* wbemuuid.lib (Quản lý hệ thống WMI Windows)
* advapi32.lib (Bảo mật và Registry của Windows)
* Bthprops.lib (Hỗ trợ kết nối Bluetooth)
* user32.lib (Giao diện người dùng Windows)
* Rpcrt4.lib (Hệ thống gọi hàm từ xa RPC)
* PowrProf.lib (Quản lý năng lượng/Pin máy tính)
* Dxva2.lib (Tăng tốc giải mã video bằng phần cứng)
* Psapi.lib (Giám sát tiến trình và bộ nhớ máy tính)
* gdiplus.lib (Đồ họa 2D GDI+)
*/