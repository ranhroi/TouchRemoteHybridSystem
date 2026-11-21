#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"

#include "safequeue.h"
#include "screencapture.h"
#include "x264encoder.h"
#include "udpscreenstream.h"
#include "audiocapture.h"
#include "mousetracker.h"
#include "sessioncontext.h"

// ===== CẤU TRÚC LƯU THÔNG SỐ CONFIG =====

// Lấy giá trị string từ JSON
std::string getJsonString(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\":\"";
    size_t start = json.find(search);
    if (start == std::string::npos) return "";
    start += search.length();
    size_t end = json.find("\"", start);
    if (end == std::string::npos) return "";
    return json.substr(start, end - start);
}

// Lấy giá trị int từ JSON
int getJsonInt(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\":";
    size_t start = json.find(search);
    if (start == std::string::npos) return 0;
    start += search.length();

    while (start < json.length() && json[start] == ' ') start++;

    size_t end = start;
    while (end < json.length() && (isdigit(json[end]) || json[end] == '-')) end++;

    if (end == start) return 0;
    return std::stoi(json.substr(start, end - start));
}

// Lấy giá trị float từ JSON
float getJsonFloat(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\":";
    size_t start = json.find(search);
    if (start == std::string::npos) return 0.0f;
    start += search.length();

    while (start < json.length() && json[start] == ' ') start++;

    size_t end = start;
    while (end < json.length() && (isdigit(json[end]) || json[end] == '.' || json[end] == '-')) end++;

    if (end == start) return 0.0f;
    return std::stof(json.substr(start, end - start));
}

// Lấy giá trị bool từ JSON
bool getJsonBool(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\":";
    size_t start = json.find(search);
    if (start == std::string::npos) return false;
    start += search.length();

    while (start < json.length() && json[start] == ' ') start++;

    if (json.substr(start, 4) == "true") return true;
    if (json.substr(start, 5) == "false") return false;
    return false;
}

// Lấy object con từ JSON
std::string getJsonObject(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\":{";
    size_t start = json.find(search);
    if (start == std::string::npos) return "";
    start += search.length() - 1;

    int depth = 0;
    size_t pos = start;
    while (pos < json.length()) {
        if (json[pos] == '{') depth++;
        else if (json[pos] == '}') {
            depth--;
            if (depth == 0) break;
        }
        pos++;
    }
    if (depth != 0) return "";
    return json.substr(start, pos - start + 1);
}

// Lấy type từ JSON
std::string getJsonType(const std::string& json) {
    return getJsonString(json, "type");
}

// ===== CẤU TRÚC VIDEO CONFIG MỚI (HỖ TRỢ JSON) =====
struct VideoConfigJSON {
    int width = 1600;
    int height = 900;
    int fps = 30;
    int quality = 65;
    int bitrate = 2500000;
    int udpPort = 5050;
};

// ===== CẤU TRÚC AUDIO CONFIG MỚI (HỖ TRỢ JSON) =====
struct AudioConfigJSON {
    bool enabled = false;
    int sampleRate = 48000;
    int channels = 2;
    int bitDepth = 16;
    std::string codec = "pcm";
    float rmsThreshold = 30.0f;
    bool forcedPlay = false;
    int udpPort = 5051;
};

// ===== PARSE VIDEO CONFIG TỪ JSON =====
VideoConfigJSON parseVideoConfigFromJson(const std::string& json) {
    VideoConfigJSON config;

    std::string videoObj = getJsonObject(json, "video");
    if (!videoObj.empty()) {
        config.width = getJsonInt(videoObj, "width");
        config.height = getJsonInt(videoObj, "height");
        config.fps = getJsonInt(videoObj, "fps");
        config.quality = getJsonInt(videoObj, "quality");
        config.bitrate = getJsonInt(videoObj, "bitrate");
        config.udpPort = getJsonInt(videoObj, "udpPort");
    }

    // Fallback nếu không có video object (parse trực tiếp)
    if (config.width == 0) config.width = getJsonInt(json, "width");
    if (config.height == 0) config.height = getJsonInt(json, "height");
    if (config.fps == 0) config.fps = getJsonInt(json, "fps");
    if (config.quality == 0) config.quality = getJsonInt(json, "quality");
    if (config.bitrate == 0) config.bitrate = getJsonInt(json, "bitrate");
    if (config.udpPort == 0) config.udpPort = 5050;

    return config;
}

// ===== PARSE AUDIO CONFIG TỪ JSON =====
AudioConfigJSON parseAudioConfigFromJson(const std::string& json) {
    AudioConfigJSON config;

    std::string audioObj = getJsonObject(json, "audio");
    if (!audioObj.empty()) {
        config.enabled = getJsonBool(audioObj, "enabled");
        config.sampleRate = getJsonInt(audioObj, "sampleRate");
        config.channels = getJsonInt(audioObj, "channels");
        config.bitDepth = getJsonInt(audioObj, "bitDepth");
        config.codec = getJsonString(audioObj, "codec");
        config.rmsThreshold = getJsonFloat(audioObj, "rmsThreshold");
        config.forcedPlay = getJsonBool(audioObj, "forcedPlay");
        config.udpPort = getJsonInt(audioObj, "udpPort");
    }

    if (config.udpPort == 0) config.udpPort = 5051;
    return config;
}

// ===== CẬP NHẬT VIDEO CONFIG TỪ JSON =====
void updateVideoConfigFromJson(const std::string& json, VideoConfigJSON& videoConfig) {
    VideoConfigJSON newConfig = parseVideoConfigFromJson(json);

    if (newConfig.width > 0) videoConfig.width = newConfig.width;
    if (newConfig.height > 0) videoConfig.height = newConfig.height;
    if (newConfig.fps > 0) videoConfig.fps = static_cast<float>(newConfig.fps);
    if (newConfig.quality > 0) videoConfig.quality = newConfig.quality;
    if (newConfig.bitrate > 0) videoConfig.bitrate = newConfig.bitrate;

    LOG_DEBUG("[VIDEO_CONFIG] Updated from JSON:\n");
    LOG_DEBUG("  - Resolution: %dx%d\n", videoConfig.width, videoConfig.height);
    LOG_DEBUG("  - Quality: %d\n", videoConfig.quality);
    LOG_DEBUG("  - FPS: %.2f\n", videoConfig.fps);
    LOG_DEBUG("  - Bitrate: %d Kbps\n", newConfig.bitrate / 1000);
}

// ===== CẬP NHẬT AUDIO CONFIG TỪ JSON =====
void updateAudioConfigFromJson(const std::string& json, AudioConfigJSON& audioConfig) {
    AudioConfigJSON newConfig = parseAudioConfigFromJson(json);

    audioConfig.enabled = newConfig.enabled;
    audioConfig.sampleRate = newConfig.sampleRate;
    audioConfig.rmsThreshold = newConfig.rmsThreshold;
    audioConfig.forcedPlay = newConfig.forcedPlay;

    LOG_DEBUG("[AUDIO_CONFIG] Updated from JSON:\n");
    LOG_DEBUG("  - Enabled: %s\n", audioConfig.enabled ? "ON" : "OFF");
    LOG_DEBUG("  - Sample Rate: %d Hz\n", audioConfig.sampleRate);
    LOG_DEBUG("  - Channels: %d\n", newConfig.channels);
    LOG_DEBUG("  - Bit Depth: %d\n", newConfig.bitDepth);
    LOG_DEBUG("  - Codec: %s\n", newConfig.codec.c_str());
}

// ===== GỬI DỮ LIỆU QUA TCP =====
bool sendTcpData(SOCKET socket, const std::vector<uint8_t>& data) {
    if (socket == INVALID_SOCKET || data.empty()) return false;

    uint32_t len = htonl(static_cast<uint32_t>(data.size()));
    if (send(socket, (const char*)&len, 4, 0) != 4) return false;

    size_t total = 0;
    while (total < data.size()) {
        int ret = send(socket, (const char*)data.data() + total, (int)(data.size() - total), 0);
        if (ret <= 0) return false;
        total += ret;
    }
    return true;
}

// ==================== MAIN HANDLER ====================

void ProcessCommands::handleScreenStream(const uint8_t* payload, int len, OpCode op, SOCKET socket) {
    if (len <= 0 || socket == INVALID_SOCKET || op != OpCode::SCREENMIRROR) return;

    if (!isSessionAlive(socket)) {
        SenderBinary::send(socket, "error session_not_found\n");
        return;
    }

    if (!isScreenStreamSession(socket)) {
        SenderBinary::send(socket, "error not_screen_stream_session\n");
        return;
    }

    std::shared_ptr<ClientSession> session = findSessionBySocket(socket);
    if (!session) {
        SenderBinary::send(socket, "error session_not_found\n");
        return;
    }

    std::string cmd((const char*)payload, len);
    std::string command = cmd;
    std::transform(command.begin(), command.end(), command.begin(), ::tolower);

    // ===== BIẾN STATIC =====
    static std::atomic<bool> streaming(false);
    static std::mutex streamMutex;
    static std::condition_variable streamCV;

    static VideoConfigJSON videoConfig;
    static AudioConfigJSON audioConfig;

    static std::unique_ptr<ScreenCapturer> screenCap;
    static std::unique_ptr<X264Encoder> encoder;
    static std::unique_ptr<UDPScreenStream> videoUdpSender;
    static std::unique_ptr<UDPScreenStream> audioUdpSender;
    static std::unique_ptr<AudioCapture> audioCapture;

    static SafeQueue<Frame> captureQueue(3);      // Max 3 frames
    static SafeQueue<EncodedFrameData> encodeQueue(5);  // Max 5 encoded frames

    static std::thread captureThread;
    static std::thread encodeThread;
    static std::thread videoThread;

    static std::atomic<int> videoSentCount(0);
    static std::atomic<int> audioSentCount(0);
    static SOCKET currentSocket = INVALID_SOCKET;

    // ===== RESET STATE =====
    auto resetStreamState = [&]() {
        LOG_DEBUG("[MIRROR] Resetting stream state...\n");

        streaming = false;
        videoSentCount = 0;
        audioSentCount = 0;
        currentSocket = INVALID_SOCKET;

        // ✅ CLEAR QUEUES
        captureQueue.clear();
        encodeQueue.clear();
        captureQueue.resetStats();
        encodeQueue.resetStats();
        streamCV.notify_all();

        if (screenCap) screenCap.reset();
        if (encoder) encoder.reset();
        if (videoUdpSender) videoUdpSender.reset();
        if (audioUdpSender) audioUdpSender.reset();
        if (audioCapture) {
            audioCapture->stop();
            audioCapture.reset();
        }

        auto joinThread = [&](std::thread& t, const std::string& name) {
            if (t.joinable()) {
                LOG_DEBUG("[MIRROR] Joining %s thread...\n", name.c_str());

                // ✅ Đợi tối đa 2 giây
                auto startTime = std::chrono::steady_clock::now();
                while (t.joinable()) {
                    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                        std::chrono::steady_clock::now() - startTime).count();
                    if (elapsed > 2000) {
                        LOG_WARN("[MIRROR] %s thread not responding, detaching\n", name.c_str());
                        t.detach();
                        break;
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(10));
                }
                if (t.joinable()) {
                    t.join();
                }
            }
            };

        joinThread(captureThread, "capture");
        joinThread(encodeThread, "encode");
        joinThread(videoThread, "video");

        LOG_DEBUG("[MIRROR] Stream state reset complete.\n");
        };

    // ===== CLEANUP =====
    auto cleanupStream = [&]() {
        LOG_DEBUG("[MIRROR_CLEANUP] ========== CLEANUP START ==========\n");

        streaming = false;
        captureQueue.clear();
        encodeQueue.clear();
        streamCV.notify_all();

        if (captureThread.joinable()) {
            LOG_DEBUG("[MIRROR_CLEANUP] Joining capture thread...\n");
            captureThread.join();
        }
        if (encodeThread.joinable()) {
            LOG_DEBUG("[MIRROR_CLEANUP] Joining encode thread...\n");
            encodeThread.join();
        }
        if (videoThread.joinable()) {
            LOG_DEBUG("[MIRROR_CLEANUP] Joining video thread...\n");
            videoThread.join();
        }

        if (audioCapture) {
            audioCapture->stop();
            audioCapture.reset();
        }

        screenCap.reset();
        encoder.reset();
        videoUdpSender.reset();
        audioUdpSender.reset();

        videoSentCount = 0;
        audioSentCount = 0;
        currentSocket = INVALID_SOCKET;

        LOG_DEBUG("[MIRROR_CLEANUP] ========== CLEANUP DONE ==========\n");
        };

    // ✅ AUTO CLEANUP KHI SESSION CHẾT
    if (currentSocket != INVALID_SOCKET && !isSessionAlive(currentSocket)) {
        LOG_DEBUG("[MIRROR] ========== SESSION DIED, AUTO CLEANUP ==========\n");
        std::lock_guard<std::mutex> lk(streamMutex);
        if (streaming) {
            cleanupStream();
        }
        currentSocket = INVALID_SOCKET;
    }

    // =============================================
    // ===== THÊM XỬ LÝ CONFIG TỪ JSON =====
    // =============================================
    if (command.rfind("config:", 0) == 0) {
        std::string jsonStr = cmd.substr(7); // Bỏ "config:"

        LOG_DEBUG("[CONFIG] Received JSON: %s", jsonStr.c_str());

        try {
            // Lấy type
            std::string type = getJsonType(jsonStr);

            if (type == "stream" || type.empty()) {
                // ===== FULL CONFIG: VIDEO + AUDIO =====
                VideoConfigJSON videoJson = parseVideoConfigFromJson(jsonStr);
                AudioConfigJSON audioJson = parseAudioConfigFromJson(jsonStr);

                // Cập nhật video config
                if (videoJson.width > 0) videoConfig.width = videoJson.width;
                if (videoJson.height > 0) videoConfig.height = videoJson.height;
                if (videoJson.fps > 0) videoConfig.fps = static_cast<float>(videoJson.fps);
                if (videoJson.quality > 0) videoConfig.quality = videoJson.quality;
                if (videoJson.bitrate > 0) videoConfig.bitrate = videoJson.bitrate;

                // Cập nhật audio config
                audioConfig.enabled = audioJson.enabled;
                audioConfig.sampleRate = audioJson.sampleRate;
                audioConfig.rmsThreshold = audioJson.rmsThreshold;
                audioConfig.forcedPlay = audioJson.forcedPlay;

                LOG_DEBUG("[CONFIG] Full config applied:");
                LOG_DEBUG("  - Video: %dx%d@%.2ffps, q=%d, bitrate=%dKbps", videoConfig.width, videoConfig.height, videoConfig.fps, videoConfig.quality, videoJson.bitrate / 1000);
                LOG_DEBUG("  - Audio: %s, %dkHz, %dch, %dbit", audioConfig.enabled ? "ON" : "OFF", audioConfig.sampleRate / 1000, audioJson.channels, audioJson.bitDepth);

                SenderBinary::send(socket, "config_saved\n");
            }
            else if (type == "video") {
                // ===== VIDEO CONFIG =====
                updateVideoConfigFromJson(jsonStr, videoConfig);
                SenderBinary::send(socket, "video_config_saved\n");
            }
            else if (type == "audio") {
                // ===== AUDIO CONFIG =====
                updateAudioConfigFromJson(jsonStr, audioConfig);
                SenderBinary::send(socket, "audio_config_saved\n");
            }
            else {
                std::cerr << "[CONFIG] Unknown type: " << type << std::endl;
                SenderBinary::send(socket, "config_unknown_type\n");
            }
        }
        catch (const std::exception& e) {
            std::cerr << "[CONFIG] Parse error: " << e.what() << std::endl;
            SenderBinary::send(socket, "config_parse_error\n");
        }
        return;
    }


    // ===== START =====
    if (command.rfind("start", 0) == 0) {
        // Lấy video port từ config (hoặc mặc định 5050)
        int videoPort = videoConfig.udpPort > 0 ? videoConfig.udpPort : 5050;
        int audioPort = audioConfig.udpPort > 0 ? audioConfig.udpPort : videoPort + 1;

        if (session->clientAddress.empty()) {
            LOG_DEBUG("[MIRROR] No client IP.");
            return;
        }

        if (!isSessionAlive(socket)) {
            LOG_DEBUG("[MIRROR] Session invalid.");
            return;
        }

        std::lock_guard<std::mutex> lk(streamMutex);

        if (streaming) {
            LOG_DEBUG("[MIRROR] Stream is running, stopping first...");
            cleanupStream();
            std::this_thread::sleep_for(std::chrono::milliseconds(200));

            if (captureThread.joinable() || encodeThread.joinable() || videoThread.joinable()) {
                LOG_DEBUG("[MIRROR] Threads still running, forcing reset...");
                resetStreamState();
            }
        }
        else {
            resetStreamState();
        }

        currentSocket = socket;

        int width = videoConfig.width;
        int height = videoConfig.height;
        float fps = videoConfig.fps;
        int quality = videoConfig.quality;
        int bitrate = videoConfig.bitrate;
        bool isOnAudio = audioConfig.enabled;
        int audioSampleRate = audioConfig.sampleRate;


    LOG_DEBUG("[MIRROR] START with config:");
    LOG_DEBUG("  - Video: %dx%d, %0.2ffps", width, height, fps);
    LOG_DEBUG("  - Quality: %d", quality);
    LOG_DEBUG("  - Bitrate: %d Kbps", bitrate / 1000);
    LOG_DEBUG("  - Audio: %s", isOnAudio ? "ON" : "OFF");
    if (isOnAudio) {
        LOG_DEBUG("    - Sample Rate: %d Hz", audioSampleRate);
    }
    std::cout << "\n";

        streaming = true;
        captureQueue.clear();
        encodeQueue.clear();

        screenCap.reset(new ScreenCapturer());
        if (!screenCap || !screenCap->init(width, height)) {
            LOG_DEBUG("[SCREEN_CAP] ScreenCapturer init failed.");
            streaming = false;
            resetStreamState();
            return;
        }
        LOG_DEBUG("[SCREEN_CAP] OK: %dx%d", screenCap->getWidth(), screenCap->getHeight());

        encoder.reset(new X264Encoder());
        if (!encoder || !encoder->Initialize(width, height, fps, quality, bitrate)) {
            LOG_DEBUG("[ENCODER] Init failed.");
            streaming = false;
            resetStreamState();
            return;
        }
        LOG_DEBUG("[ENCODER] OK");

        videoUdpSender.reset(new UDPScreenStream(session->clientAddress, videoPort));
        if (!videoUdpSender || !videoUdpSender->open()) {
            LOG_DEBUG("[VIDEO] UDPScreenStream failed.");
            streaming = false;
            resetStreamState();
            return;
        }
        LOG_DEBUG("[VIDEO] OK");

        if (audioConfig.enabled) {
            audioUdpSender.reset(new UDPScreenStream(session->clientAddress, audioPort));
            if (!audioUdpSender || !audioUdpSender->open()) {
                LOG_DEBUG("[AUDIO] UDPScreenStream failed.");
                streaming = false;
                resetStreamState();
                return;
            }
            LOG_DEBUG("[AUDIO] OK");
        }


        // ===== THREAD 1: CAPTURE =====
        captureThread = std::thread([&]() {
            LOG_DEBUG("[CAPTURE_THREAD] Started");
            int frameCount = 0;
            int droppedCount = 0;

            while (streaming) {
                try {
                    if (!isSessionAlive(currentSocket)) {
                        LOG_DEBUG("[CAPTURE_THREAD] Session invalid");
                        streaming = false;
                        break;
                    }

                    auto start = std::chrono::high_resolution_clock::now();

                    // Mouse tracker (mỗi 3 frame)
                    static int mouseCounter = 0;
                    if (mouseCounter++ % 3 == 0) {
                        std::vector<uint8_t> mousePacket = MouseTracker::captureStateAndPack();
                        if (!mousePacket.empty()) {
                            sendTcpData(currentSocket, mousePacket);
                        }
                    }

                    // Capture
                    Frame frm;
                    if (screenCap && screenCap->capture(frm)) {
                        if (frm.width > 0 && frm.height > 0 && !frm.data.empty()) {
                            // ✅ push() trả về bool
                            bool success = captureQueue.push(std::move(frm));
                            if (!success) {
                                droppedCount++;
                            }
                            frameCount++;
                        }
                    }

                    // FPS Control
                    auto end = std::chrono::high_resolution_clock::now();
                    std::chrono::duration<double, std::milli> elapsed = end - start;
                    int targetFrameTime = 1000 / fps;
                    if (elapsed.count() < targetFrameTime) {
                        std::this_thread::sleep_for(
                            std::chrono::milliseconds(static_cast<int>(targetFrameTime - elapsed.count()))
                        );
                    }
                }
                catch (...) {
                    LOG_DEBUG("[CAPTURE_THREAD] Exception");
                    streaming = false;
                }
            }
            LOG_DEBUG("[CAPTURE_THREAD] Stopped. Frames: %d, Dropped: %d", frameCount, droppedCount);
            });

        // ===== THREAD 2: ENCODE =====
        encodeThread = std::thread([&]() {
            LOG_DEBUG("[ENCODE_THREAD] Started");
            uint32_t frameId = 0;
            int encodeCount = 0;
            int droppedCount = 0;
            Frame frm;

            while (streaming) {
                if (!isSessionAlive(currentSocket)) {
                    LOG_DEBUG("[ENCODE_THREAD] Session invalid");
                    streaming = false;
                    break;
                }

                if (!captureQueue.pop(frm, streaming)) {
                    std::this_thread::sleep_for(std::chrono::microseconds(100));
                    continue;
                }

                if (encoder) {
                    EncodedFrameData encoded;
                    encoded.timestamp = static_cast<uint32_t>(frm.ptsMs);
                    encoded.frameId = frameId;

                    bool ret = encoder->EncodeFrame(frm.data, [&](const uint8_t* nal, size_t size) {
                        encoded.nals.emplace_back(nal, nal + size);
                        });

                    if (ret && !encoded.nals.empty()) {
                        // ✅ size() có sẵn
                        if (encodeQueue.size() < 10) {
                            encodeQueue.push(std::move(encoded));
                            frameId++;
                            encodeCount++;
                        }
                        else {
                            droppedCount++;
                            if (droppedCount % 50 == 0) {
                                LOG_DEBUG("[ENCODE] Queue full, dropped: %d", droppedCount);
                            }
                        }
                    }
                }
            }
            LOG_DEBUG("[ENCODE_THREAD] Stopped. Encoded: %d, Dropped: %d", encodeCount, droppedCount);
            });
        // ===== THREAD 3: VIDEO UDP =====
        videoThread = std::thread([&]() {
            LOG_DEBUG("[VIDEO_THREAD] Started");
            EncodedFrameData frame;
            int sendCount = 0;
            int errorCount = 0;
            int totalSent = 0;

            while (streaming) {
                if (!isSessionAlive(currentSocket)) {
                    LOG_DEBUG("[VIDEO_THREAD] Session invalid");
                    streaming = false;
                    break;
                }

                if (!encodeQueue.pop(frame, streaming, 100)) {
                    std::this_thread::sleep_for(std::chrono::microseconds(100));
                    continue;
                }

                uint32_t count = static_cast<uint32_t>(frame.nals.size());
                const size_t headerSize = 16;


                for (uint32_t i = 0; i < count && streaming; ++i) {
                    if (!isSessionAlive(currentSocket)) {
                        streaming = false;
                        break;
                    }
                    std::vector<uint8_t> packet(headerSize + frame.nals[i].size());
                    uint32_t* h = reinterpret_cast<uint32_t*>(packet.data());
                    h[0] = frame.frameId;
                    h[1] = i;
                    h[2] = count;
                    h[3] = frame.timestamp;
                    memcpy(packet.data() + headerSize, frame.nals[i].data(), frame.nals[i].size());

                    if (videoUdpSender) {
                        // ✅ sendRawBuffer TRẢ VỀ bool
                        bool sent = videoUdpSender->sendRawBuffer(packet.data(), packet.size(), 0);
                        if (sent) {
                            sendCount++;
                            totalSent++;
                        }
                        else {
                            errorCount++;
                            if (errorCount % 50 == 0) {
                                LOG_DEBUG("[VIDEO] Send errors: %d", errorCount);
                            }
                        }
                    }
                }

                if (sendCount % 100 == 0) {
                    LOG_DEBUG("[VIDEO] Sent: %d, Errors: %d, Queue: %d", totalSent, errorCount, encodeQueue.size());
                }
            }
            LOG_DEBUG("[VIDEO_THREAD] Stopped. Total: %d, Errors: %d", totalSent, errorCount);
            });


        // ===== THREAD 4: AUDIO =====
        if (audioConfig.enabled) {
            audioCapture.reset(new AudioCapture());
            if (!audioCapture || !audioCapture->init(audioSampleRate, 16, 2)) {
                LOG_ERROR("[AUDIO_THREAD 4] Init failed");
                return;
            }

            // ✅ SET SESSION CHECK
            audioCapture->setSessionCheck([&]() -> bool {
                return isSessionAlive(currentSocket);
                });

            audioCapture->start([&](const uint8_t* data, size_t size) {
                if (!streaming) return;
                if (!isSessionAlive(currentSocket)) {
                    streaming = false;
                    return;
                }
                if (audioUdpSender && size > 0) {
                    audioUdpSender->sendRawBuffer(data, size, 0);
                    int c = audioSentCount.fetch_add(1) + 1;
                    if (c % 100 == 0) LOG_DEBUG("[AUDIO] Sent: %d chunks", c);
                }
                });
        }

        LOG_DEBUG("[MIRROR_THREADS] ========== ALL THREADS STARTED ==========");
        return;
    }

    // ===== STOP =====
    else if (command == "stop") {
        LOG_DEBUG("[MIRROR] ========== STOP RECEIVED ==========");
        std::lock_guard<std::mutex> lk(streamMutex);

        if (streaming) {
            cleanupStream();
            LOG_DEBUG("[MIRROR] Stream stopped and cleaned up.");
        }
        else {
            LOG_DEBUG("[MIRROR] No stream running.");
        }
        return;
    }

    LOG_DEBUG("[MIRROR] Unknown command: '%s'", command.c_str());
}