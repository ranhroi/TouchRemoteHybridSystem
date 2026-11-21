#pragma once
#include "keymapper.h"

struct ProcessCommands {
public:

    // Static handlers - ZERO INSTANCE OVERHEAD
    static void mouseHandle(const uint8_t* payload, int len, OpCode op);
    static void dragHandle(const uint8_t* payload, int len, OpCode op);

    static void handleKey(const uint8_t* payload, int len, OpCode op);
    static void handleComboKey(const uint8_t* payload, int len);
    static void handleUtf8(const uint8_t* payload, int len, OpCode op);

    static void handleSlide(const uint8_t* payload, int len, OpCode op);
    static void handleZoom(const uint8_t* payload, int len, OpCode op);
    static void handleWindow(const uint8_t* payload, int len, OpCode op);
    static void handleMedia(const uint8_t* payload, int len, OpCode op);
    static void handleWPPT(const uint8_t* payload, int len, OpCode op);
    static void handlePPT(const uint8_t* payload, int len, OpCode op);
    static void handlePlayer(const uint8_t* payload, int len, OpCode op);
    static void handleVolum(const uint8_t* payload, int len, OpCode op);
    static void handleBrowser(const uint8_t* payload, int len, OpCode op);
    static void handlePower(const uint8_t* payload, int len, OpCode op);
    static void handleScreenCapture(const uint8_t* payload, int len, OpCode op);

    // Các hàm có Socket mạng kết nối
    static void handleGetApp(const uint8_t* payload, int len, OpCode op, SOCKET socket);
    static void handleCurrentApp(const uint8_t* payload, int len, OpCode op, SOCKET socket);
    static void handleTaskManager(const uint8_t* payload, int len, OpCode op, SOCKET socket);
    static void handleCommandRun(const uint8_t* payload, int len, OpCode op, SOCKET socket);
    static void handleFileExplorer(const uint8_t* payload, int len, OpCode op, SOCKET socket);
    static void handleScreenStream(const uint8_t* payload, int len, OpCode op, SOCKET socket);


private:


    // ===== MOUSE =====
    static void sendMove(int dx, int dy);
    static void sendClick();
    static void sendButton(char btn, char state);
    static void sendScroll(int delta, bool vertical);
    static void sendDrag(bool start);

    // ===== Slide =====
    static void toggleDesktopSmart();

    // ===== Power =====
    static void shutdown();
    static void restart();
    static void logout();
    static void sleep();
    static void lock();
    static void blank();

    // ===== Brightness =====
    static void changeBrightness(int delta);


    // Hàm điều khiển audio từ bên ngoài
    static void enableScreenAudio(bool enable);
    static bool isScreenAudioEnabled();
    static void setScreenAudioConfig(int sampleRate, int channels);

private:
    static std::atomic<bool> enabled;
    static std::atomic<bool> running;
    static HANDLE processHandle;
};