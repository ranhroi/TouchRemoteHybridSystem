#pragma once

class AudioCapture {
public:
    using Callback = std::function<void(const uint8_t* data, size_t size)>;
    using SessionCheck = std::function<bool()>;

    AudioCapture();
    ~AudioCapture();

    bool init(int sampleRate = 48000, int bitsPerSample = 16, int channels = 2);
    void start(Callback cb);
    void stop();
    bool isRunning() const;
    void setSessionCheck(SessionCheck check);
    double getCurrentRms() const { return currentRms_; }

private:
    void captureLoop();
    bool initializeWASAPI();
    void cleanupWASAPI();
    double calculateRMS(const std::vector<uint8_t>& data);
    void processAudioData(BYTE* pData, UINT32 numFramesAvailable, DWORD flags, std::vector<uint8_t>& output);

    // ✅ THÊM MMCSS
    bool setupMMCSS();
    void cleanupMMCSS();

    // ✅ THÊM ZERO-COPY BUFFER
    std::vector<uint8_t> zeroCopyBuffer_;
    size_t zeroCopySize_ = 0;

    // ===== WASAPI =====
    IMMDeviceEnumerator* enumerator_ = nullptr;
    IMMDevice* device_ = nullptr;
    IAudioClient* client_ = nullptr;
    IAudioCaptureClient* capture_ = nullptr;

    // ===== FORMAT =====
    int targetSampleRate_ = 48000;
    int targetBitsPerSample_ = 16;
    int targetChannels_ = 2;
    int bytesPerFrame_ = 4;

    int actualSampleRate_ = 0;
    int actualBitsPerSample_ = 0;
    int actualChannels_ = 0;
    int actualBlockAlign_ = 0;
    bool actualIsFloat_ = false;

    // ===== QUEUE =====
    std::queue<std::vector<uint8_t>> audioQueue_;
    std::mutex queueMutex_;
    static const size_t MAX_QUEUE_SIZE = 30;  // ✅ GIẢM XUỐNG CHO ZERO-COPY
    static const int CHUNK_SIZE = 1400;
    static const int MAX_BUFFER = 65536;

    // ===== THREAD CONTROL =====
    std::atomic<bool> running_{ false };
    std::thread thread_;
    Callback callback_;
    SessionCheck sessionCheck_;

    // ===== MMCSS =====
    HANDLE mmcssHandle_ = nullptr;
    DWORD mmcssTaskIndex_ = 0;
    bool mmcssEnabled_ = false;

    // ===== STATS =====
    std::atomic<double> currentRms_{ 0.0 };
    std::atomic<int> droppedFrames_{ 0 };
    std::atomic<int> totalFrames_{ 0 };
};