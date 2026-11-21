#include "pch.h"
#include "audiocapture.h"


// ===== CONSTRUCTOR / DESTRUCTOR =====

AudioCapture::AudioCapture() {
    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] CoInitializeEx failed: %p", (void*)hr);
    }
}

AudioCapture::~AudioCapture() {
    stop();
    cleanupWASAPI();
    cleanupMMCSS();
    CoUninitialize();
}

// ===== PUBLIC METHODS =====

bool AudioCapture::init(int sampleRate, int bitsPerSample, int channels) {
    targetSampleRate_ = sampleRate;
    targetBitsPerSample_ = bitsPerSample;
    targetChannels_ = channels;
    bytesPerFrame_ = (bitsPerSample / 8) * channels;

    zeroCopySize_ = 4096;
    zeroCopyBuffer_.resize(zeroCopySize_);

    LOG_DEBUG("[AudioCapture] Init: %dHz, %dbit, %dch", sampleRate, bitsPerSample, channels);
    return true;
}

void AudioCapture::setSessionCheck(SessionCheck check) {
    sessionCheck_ = check;
}

void AudioCapture::start(Callback cb) {
    if (running_.load()) {
        LOG_DEBUG("[AudioCapture] Already running");
        return;
    }

    callback_ = cb;
    running_.store(true);
    droppedFrames_ = 0;
    totalFrames_ = 0;

    if (!initializeWASAPI()) {
        LOG_DEBUG("[AudioCapture] WASAPI init failed");
        running_.store(false);
        return;
    }

    HRESULT hr = client_->Start();
    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] Client start failed: %p", (void*)hr);
        running_.store(false);
        cleanupWASAPI();
        return;
    }

    thread_ = std::thread(&AudioCapture::captureLoop, this);
    std::this_thread::sleep_for(std::chrono::milliseconds(50));

    LOG_DEBUG("[AudioCapture] Started successfully");
}

void AudioCapture::stop() {
    if (!running_.load()) return;

    LOG_DEBUG("[AudioCapture] Stopping...");

    // ✅ SỬA: Chỉ set atomic, không lock
    running_.store(false);

    if (thread_.joinable()) {
        auto start = std::chrono::steady_clock::now();
        const auto timeout = std::chrono::milliseconds(2000);

        while (thread_.joinable() &&
            std::chrono::steady_clock::now() - start < timeout) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }

        if (thread_.joinable()) {
            LOG_DEBUG("[AudioCapture] Thread timeout, detaching");
            thread_.detach();
        }
        else {
            LOG_DEBUG("[AudioCapture] Thread joined successfully");
        }
    }

    if (client_) {
        client_->Stop();
    }

    cleanupWASAPI();
    cleanupMMCSS();

    {
        std::lock_guard<std::mutex> lock(queueMutex_);
        while (!audioQueue_.empty()) {
            audioQueue_.pop();
        }
    }

    LOG_DEBUG("[AudioCapture] Stopped. Total: %d, Dropped: %d", totalFrames_.load(), droppedFrames_.load());
}

bool AudioCapture::isRunning() const {
    return running_.load();
}

// ===== MMCSS SUPPORT =====

bool AudioCapture::setupMMCSS() {
#ifdef _WIN32
    // ✅ SỬA: Dùng đúng tên hàm
    mmcssHandle_ = AvSetMmThreadCharacteristicsW(L"Audio", &mmcssTaskIndex_);
    if (mmcssHandle_) {
        mmcssEnabled_ = true;
        LOG_DEBUG("[AudioCapture] MMCSS enabled (Audio)");
        return true;
    }
    else {
        LOG_DEBUG("[AudioCapture] MMCSS failed: %lu", GetLastError());
        return false;
    }
#else
    return false;
#endif
}

void AudioCapture::cleanupMMCSS() {
#ifdef _WIN32
    if (mmcssHandle_) {
        // ✅ SỬA: Dùng đúng tên hàm
        AvRevertMmThreadCharacteristics(mmcssHandle_);
        mmcssHandle_ = nullptr;
        mmcssEnabled_ = false;
        LOG_DEBUG("[AudioCapture] MMCSS disabled");
    }
#endif
}

// ===== CAPTURE LOOP =====

void AudioCapture::captureLoop() {
    LOG_DEBUG("[AudioCapture] Capture loop started");

    setupMMCSS();

#ifdef _WIN32
    SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST);
#endif

    const int OUTPUT_BYTES_PER_FRAME = 4;
    bool isFloat = actualIsFloat_;
    int errorCount = 0;
    const int MAX_ERRORS = 10;
    int silenceCount = 0;

    while (running_.load()) {
        if (sessionCheck_ && !sessionCheck_()) {
            LOG_DEBUG("[AudioCapture] Session dead, stopping");
            running_.store(false);
            break;
        }

        UINT32 packetLength = 0;
        HRESULT hr = capture_->GetNextPacketSize(&packetLength);

        if (FAILED(hr)) {
            errorCount++;
            if (errorCount >= MAX_ERRORS) {
                LOG_DEBUG("[AudioCapture] Too many errors, stopping");
                running_.store(false);
                break;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        errorCount = 0;

        while (packetLength != 0 && running_.load()) {
            BYTE* pData = nullptr;
            UINT32 numFramesAvailable = 0;
            DWORD flags = 0;

            hr = capture_->GetBuffer(&pData, &numFramesAvailable, &flags, nullptr, nullptr);

            if (FAILED(hr)) {
                LOG_DEBUG("[AudioCapture] GetBuffer failed: %p", (void*)hr);
                break;
            }

            if (numFramesAvailable > 0) {
                int outputDataSize = numFramesAvailable * OUTPUT_BYTES_PER_FRAME;

                std::vector<uint8_t> stereoData;
                if (outputDataSize <= (int)zeroCopySize_) {
                    stereoData = std::vector<uint8_t>(
                        zeroCopyBuffer_.data(),
                        zeroCopyBuffer_.data() + outputDataSize
                    );
                }
                else {
                    stereoData.resize(outputDataSize);
                    if (outputDataSize > (int)zeroCopySize_) {
                        zeroCopySize_ = outputDataSize * 2;
                        zeroCopyBuffer_.resize(zeroCopySize_);
                    }
                }

                if (flags & AUDCLNT_BUFFERFLAGS_SILENT) {
                    std::fill(stereoData.begin(), stereoData.end(), 0);
                    silenceCount++;
                }
                else if (pData != nullptr) {
                    silenceCount = 0;
                    processAudioData(pData, numFramesAvailable, flags, stereoData);
                }

                {
                    std::lock_guard<std::mutex> lock(queueMutex_);
                    if (audioQueue_.size() < MAX_QUEUE_SIZE) {
                        audioQueue_.push(std::move(stereoData));
                    }
                    else {
                        if (!audioQueue_.empty()) {
                            audioQueue_.pop();
                            droppedFrames_++;
                        }
                        audioQueue_.push(std::move(stereoData));
                    }
                }

                while (true) {
                    std::vector<uint8_t> chunk;
                    {
                        std::lock_guard<std::mutex> lock(queueMutex_);
                        if (audioQueue_.empty()) break;

                        auto& front = audioQueue_.front();
                        if (front.size() >= CHUNK_SIZE) {
                            chunk.insert(chunk.end(), front.begin(), front.begin() + CHUNK_SIZE);
                            front.erase(front.begin(), front.begin() + CHUNK_SIZE);
                            if (front.empty()) {
                                audioQueue_.pop();
                            }
                        }
                        else {
                            chunk = std::move(front);
                            audioQueue_.pop();
                        }
                    }

                    if (!chunk.empty() && callback_) {
                        callback_(chunk.data(), chunk.size());
                        totalFrames_++;
                    }
                }

                totalFrames_++;
            }

            capture_->ReleaseBuffer(numFramesAvailable);
            hr = capture_->GetNextPacketSize(&packetLength);
        }

        if (packetLength == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }

        if (totalFrames_.load() % 10 == 0) {
            // RMS updated in processAudioData
        }
    }

    cleanupMMCSS();
    LOG_DEBUG("[AudioCapture] Capture loop ended. Total: %d, Dropped: %d", totalFrames_.load(), droppedFrames_.load());
}

// ===== PROCESS AUDIO DATA =====

void AudioCapture::processAudioData(BYTE* pData, UINT32 numFramesAvailable, DWORD flags, std::vector<uint8_t>& output) {
    int totalSamples = numFramesAvailable * actualChannels_;

    if (actualIsFloat_) {
        float* pFloat = reinterpret_cast<float*>(pData);
        int16_t* pInt16 = reinterpret_cast<int16_t*>(output.data());

        for (int i = 0; i < totalSamples; ++i) {
            float sample = pFloat[i];
            if (sample > 1.0f) sample = 1.0f;
            else if (sample < -1.0f) sample = -1.0f;
            pInt16[i] = static_cast<int16_t>(sample * 32767.0f);
        }
    }
    else if (actualBitsPerSample_ == 24) {
        uint8_t* pSrc = pData;
        int16_t* pDst = reinterpret_cast<int16_t*>(output.data());

        for (int i = 0; i < totalSamples; ++i) {
            int sample24 = (pSrc[0]) | (pSrc[1] << 8) | (pSrc[2] << 16);
            if (sample24 & 0x800000) {
                sample24 |= ~0xffffff;
            }
            sample24 >>= 8;
            if (sample24 > 32767) sample24 = 32767;
            if (sample24 < -32768) sample24 = -32768;
            pDst[i] = static_cast<int16_t>(sample24);
            pSrc += 3;
        }
    }
    else if (actualBitsPerSample_ == 16) {
        memcpy(output.data(), pData, output.size());
    }
    else {
        std::fill(output.begin(), output.end(), 0);
        LOG_DEBUG("[AudioCapture] Unknown format: %d bit", actualBitsPerSample_);
    }

    if (totalFrames_.load() % 10 == 0) {
        currentRms_ = calculateRMS(output);
    }
}

// ===== RMS CALCULATION =====

double AudioCapture::calculateRMS(const std::vector<uint8_t>& data) {
    if (data.empty()) return 0.0;

    const int16_t* samples = reinterpret_cast<const int16_t*>(data.data());
    int sampleCount = static_cast<int>(data.size() / sizeof(int16_t));
    if (sampleCount == 0) return 0.0;

    double sum = 0.0;
    for (int i = 0; i < sampleCount; ++i) {
        double sample = samples[i] / 32768.0;
        sum += sample * sample;
    }

    return std::sqrt(sum / sampleCount) * 100.0;
}

// ===== WASAPI INITIALIZATION =====

bool AudioCapture::initializeWASAPI() {
    HRESULT hr;

    hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), NULL, CLSCTX_ALL,
        __uuidof(IMMDeviceEnumerator), (void**)&enumerator_);
    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] CoCreateInstance failed: %p", (void*)hr);
        return false;
    }

    hr = enumerator_->GetDefaultAudioEndpoint(eRender, eConsole, &device_);
    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] GetDefaultAudioEndpoint failed: %p", (void*)hr);
        return false;
    }

    hr = device_->Activate(__uuidof(IAudioClient), CLSCTX_ALL, NULL, (void**)&client_);
    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] Activate failed: %p", (void*)hr);
        return false;
    }

    WAVEFORMATEX* pMixFormat = nullptr;
    hr = client_->GetMixFormat(&pMixFormat);
    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] GetMixFormat failed: %p", (void*)hr);
        return false;
    }

    actualSampleRate_ = pMixFormat->nSamplesPerSec;
    actualBitsPerSample_ = pMixFormat->wBitsPerSample;
    actualChannels_ = pMixFormat->nChannels;
    actualBlockAlign_ = pMixFormat->nBlockAlign;

    actualIsFloat_ = false;
    if (pMixFormat->wFormatTag == WAVE_FORMAT_IEEE_FLOAT) {
        actualIsFloat_ = true;
    }
    else if (pMixFormat->wFormatTag == WAVE_FORMAT_EXTENSIBLE) {
        WAVEFORMATEXTENSIBLE* wf = reinterpret_cast<WAVEFORMATEXTENSIBLE*>(pMixFormat);
        if (IsEqualGUID(wf->SubFormat, KSDATAFORMAT_SUBTYPE_IEEE_FLOAT)) {
            actualIsFloat_ = true;
        }
    }

    LOG_DEBUG("[AudioCapture] Actual format: %dHz, %dbit, %dch, %s", actualSampleRate_, actualBitsPerSample_, actualChannels_, actualIsFloat_ ? "Float" : "PCM");

    WAVEFORMATEX desiredFormat;
    desiredFormat.wFormatTag = WAVE_FORMAT_PCM;
    desiredFormat.nChannels = 2;
    desiredFormat.nSamplesPerSec = 48000;
    desiredFormat.wBitsPerSample = 16;
    desiredFormat.nBlockAlign = (2 * 16) / 8;
    desiredFormat.nAvgBytesPerSec = 48000 * desiredFormat.nBlockAlign;
    desiredFormat.cbSize = 0;

    hr = client_->Initialize(AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_LOOPBACK,
        10000000, 0, &desiredFormat, NULL);

    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] Cannot force 48kHz, using fallback");

        client_->Release();
        client_ = nullptr;

        hr = device_->Activate(__uuidof(IAudioClient), CLSCTX_ALL, NULL, (void**)&client_);
        if (FAILED(hr)) {
            CoTaskMemFree(pMixFormat);
            return false;
        }

        hr = client_->Initialize(AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_LOOPBACK,
            10000000, 0, pMixFormat, NULL);
        if (FAILED(hr)) {
            CoTaskMemFree(pMixFormat);
            return false;
        }

        targetSampleRate_ = actualSampleRate_;
        targetBitsPerSample_ = actualBitsPerSample_;
        targetChannels_ = actualChannels_;
        bytesPerFrame_ = actualBlockAlign_;

        LOG_DEBUG("[AudioCapture] Fallback: %dHz, %dbit", actualSampleRate_, actualBitsPerSample_);
    }
    else {
        LOG_DEBUG("[AudioCapture] Success: 48000Hz, 16-bit, Stereo");

        actualSampleRate_ = 48000;
        actualBitsPerSample_ = 16;
        actualChannels_ = 2;
        actualBlockAlign_ = 4;
        actualIsFloat_ = false;
        bytesPerFrame_ = 4;
    }

    CoTaskMemFree(pMixFormat);

    hr = client_->GetService(__uuidof(IAudioCaptureClient), (void**)&capture_);
    if (FAILED(hr)) {
        LOG_DEBUG("[AudioCapture] GetService failed: %p", (void*)hr);
        return false;
    }

    UINT32 bufferSizeFrames = 0;
    hr = client_->GetBufferSize(&bufferSizeFrames);
    if (SUCCEEDED(hr)) {
        LOG_DEBUG("[AudioCapture] Buffer size: %d frames", bufferSizeFrames);
    }

    return true;
}

// ===== CLEANUP =====

void AudioCapture::cleanupWASAPI() {
    if (capture_) {
        capture_->Release();
        capture_ = nullptr;
    }
    if (client_) {
        client_->Release();
        client_ = nullptr;
    }
    if (device_) {
        device_->Release();
        device_ = nullptr;
    }
    if (enumerator_) {
        enumerator_->Release();
        enumerator_ = nullptr;
    }
}