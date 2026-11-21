#pragma once
#include "pch.h"

#ifndef SM_FIXEDSLCNUM_MODE
#define SM_FIXEDSLCNUM_MODE 1
#endif

class X264Encoder {
private:
    ISVCEncoder* pEncoder = nullptr;
    int mWidth = 0;
    int mHeight = 0;
    float mFps = 30.0f;
    int mQuality = 85;
    int mBitrate = 2000000;
    std::vector<uint8_t> mYuvBuffer;

    // ===== HÀM CHUẨN HÓA KÍCH THƯỚC =====
    void normalizeResolution(int& width, int& height) {
        if (width % 2 != 0) width -= 1;
        if (height % 2 != 0) height -= 1;
        if (width < 16) width = 16;
        if (height < 16) height = 16;
        if (width > 4096) width = 4096;
        if (height > 4096) height = 4096;
    }

    // ===== TÍNH SỐ SLICE TỐI ƯU =====
    int getOptimalSliceCount(int width, int height) {
        int pixels = width * height;
        if (pixels <= 640 * 480) return 2;
        else if (pixels <= 1280 * 720) return 3;
        else if (pixels <= 1920 * 1080) return 4;
        else if (pixels <= 2560 * 1440) return 6;
        else if (pixels <= 3840 * 2160) return 8;
        else return 10;
    }

    // ===== CHUẨN HÓA QUALITY (0-100) =====
    struct EncoderPreset {
        int quality;
        int minBitrate;
        int maxBitrate;
        float complexity;
        bool adaptiveQuant;
        bool sceneChange;
        int refFrames;
        int bframes;
        int subpelRefine;
        int meRange;
        bool trellis;
        bool psyRDO;
        float psyTrellis;
        bool lookahead;
        int lookaheadFrames;
        bool openGOP;
        bool cabac;
        int deblockingStrength;
        float qComp;
        float qpStep;
        int rcMode;
    };

    // ===== PRESETS THEO QUALITY =====
    static EncoderPreset getPresetForQuality(int quality) {
        EncoderPreset preset = {};
        preset.quality = quality;
        preset.bframes = 0;
        preset.refFrames = 1;
        preset.subpelRefine = 1;
        preset.meRange = 16;
        preset.trellis = false;
        preset.psyRDO = false;
        preset.psyTrellis = 0.0f;
        preset.lookahead = false;
        preset.lookaheadFrames = 0;
        preset.openGOP = false;
        preset.cabac = false;
        preset.adaptiveQuant = false;
        preset.sceneChange = false;
        preset.complexity = 1.0f;
        preset.deblockingStrength = 0;
        preset.qComp = 0.6f;
        preset.qpStep = 4.0f;
        preset.rcMode = RC_BITRATE_MODE;

        if (quality < 25) {
            preset.minBitrate = 300000;
            preset.maxBitrate = 500000;
            preset.bframes = 0;
            preset.refFrames = 1;
            preset.subpelRefine = 1;
            preset.meRange = 12;
            preset.trellis = false;
            preset.psyRDO = false;
            preset.psyTrellis = 0.0f;
            preset.lookahead = false;
            preset.lookaheadFrames = 0;
            preset.openGOP = false;
            preset.cabac = false;
            preset.adaptiveQuant = false;
            preset.sceneChange = false;
            preset.complexity = 0.6f;
            preset.deblockingStrength = 2;
            preset.qComp = 0.5f;
            preset.qpStep = 6.0f;
            preset.rcMode = RC_BITRATE_MODE;
        }
        else if (quality < 45) {
            preset.minBitrate = 500000;
            preset.maxBitrate = 1000000;
            preset.bframes = 1;
            preset.refFrames = 1;
            preset.subpelRefine = 2;
            preset.meRange = 16;
            preset.trellis = false;
            preset.psyRDO = false;
            preset.psyTrellis = 0.0f;
            preset.lookahead = false;
            preset.lookaheadFrames = 10;
            preset.openGOP = false;
            preset.cabac = true;
            preset.adaptiveQuant = false;
            preset.sceneChange = true;
            preset.complexity = 0.7f;
            preset.deblockingStrength = 1;
            preset.qComp = 0.55f;
            preset.qpStep = 5.0f;
            preset.rcMode = RC_BITRATE_MODE;
        }
        else if (quality < 65) {
            preset.minBitrate = 1000000;
            preset.maxBitrate = 2500000;
            preset.bframes = 2;
            preset.refFrames = 2;
            preset.subpelRefine = 3;
            preset.meRange = 20;
            preset.trellis = true;
            preset.psyRDO = false;
            preset.psyTrellis = 0.1f;
            preset.lookahead = true;
            preset.lookaheadFrames = 20;
            preset.openGOP = true;
            preset.cabac = true;
            preset.adaptiveQuant = true;
            preset.sceneChange = true;
            preset.complexity = 1.0f;
            preset.deblockingStrength = 0;
            preset.qComp = 0.6f;
            preset.qpStep = 4.0f;
            preset.rcMode = RC_BITRATE_MODE;
        }
        else if (quality < 85) {
            preset.minBitrate = 2000000;
            preset.maxBitrate = 5000000;
            preset.bframes = 3;
            preset.refFrames = 3;
            preset.subpelRefine = 5;
            preset.meRange = 24;
            preset.trellis = true;
            preset.psyRDO = true;
            preset.psyTrellis = 0.2f;
            preset.lookahead = true;
            preset.lookaheadFrames = 30;
            preset.openGOP = true;
            preset.cabac = true;
            preset.adaptiveQuant = true;
            preset.sceneChange = true;
            preset.complexity = 1.2f;
            preset.deblockingStrength = -1;
            preset.qComp = 0.65f;
            preset.qpStep = 3.5f;
            preset.rcMode = RC_BITRATE_MODE;
        }
        else {
            preset.minBitrate = 4000000;
            preset.maxBitrate = 10000000;
            preset.bframes = 4;
            preset.refFrames = 4;
            preset.subpelRefine = 7;
            preset.meRange = 32;
            preset.trellis = true;
            preset.psyRDO = true;
            preset.psyTrellis = 0.3f;
            preset.lookahead = true;
            preset.lookaheadFrames = 40;
            preset.openGOP = true;
            preset.cabac = true;
            preset.adaptiveQuant = true;
            preset.sceneChange = true;
            preset.complexity = 1.5f;
            preset.deblockingStrength = -1;
            preset.qComp = 0.7f;
            preset.qpStep = 3.0f;
            preset.rcMode = RC_BITRATE_MODE;
        }

        return preset;
    }

    // ===== HÀM KHỞI TẠO NỘI BỘ =====
    bool initializeEncoder(int width, int height, float fps, int quality, int bitrate) {
        normalizeResolution(width, height);

        mWidth = width;
        mHeight = height;
        mFps = fps;
        mQuality = quality;
        mBitrate = bitrate;

        size_t yuvSize = (size_t)mWidth * mHeight * 3 / 2;
        mYuvBuffer.resize(yuvSize, 0);

        if (WelsCreateSVCEncoder(&pEncoder) != 0) {
            LOG_ERROR("[X264Encoder] WelsCreateSVCEncoder failed");
            return false;
        }

        EncoderPreset preset = getPresetForQuality(quality);

        SEncParamExt param;
        pEncoder->GetDefaultParams(&param);

        param.iUsageType = CAMERA_VIDEO_REAL_TIME;
        param.iPicWidth = mWidth;
        param.iPicHeight = mHeight;
        param.fMaxFrameRate = fps;

        // ✅ DÙNG BITRATE TỪ CLIENT
        param.iTargetBitrate = bitrate;
        param.iMaxBitrate = bitrate * 2;
        param.iRCMode = static_cast<RC_MODES>(preset.rcMode);

        param.bEnableFrameSkip = (fps > 30);
        param.iMultipleThreadIdc = static_cast<int>(preset.complexity * 2);
        if (param.iMultipleThreadIdc < 1) param.iMultipleThreadIdc = 1;
        if (param.iMultipleThreadIdc > 4) param.iMultipleThreadIdc = 4;

        param.bEnableSceneChangeDetect = preset.sceneChange;
        param.uiIntraPeriod = static_cast<int>(fps * 2);
        if (param.uiIntraPeriod < 15) param.uiIntraPeriod = 15;
        if (param.uiIntraPeriod > 300) param.uiIntraPeriod = 300;

        param.sSpatialLayers[0].iVideoWidth = mWidth;
        param.sSpatialLayers[0].iVideoHeight = mHeight;
        param.sSpatialLayers[0].fFrameRate = fps;
        param.sSpatialLayers[0].iSpatialBitrate = bitrate;
        param.sSpatialLayers[0].iMaxSpatialBitrate = bitrate * 2;
        param.sSpatialLayers[0].sSliceArgument.uiSliceMode = static_cast<SliceModeEnum>(SM_FIXEDSLCNUM_MODE);
        param.sSpatialLayers[0].sSliceArgument.uiSliceNum = getOptimalSliceCount(mWidth, mHeight);

        param.bEnableLongTermReference = false;
        param.iLTRRefNum = 0;
        param.eSpsPpsIdStrategy = CONSTANT_ID;
        param.bPrefixNalAddingCtrl = false;
        param.bEnableDenoise = false;
        param.iComplexityMode = LOW_COMPLEXITY;

        int initResult = pEncoder->InitializeExt(&param);
        if (initResult != 0) {
            LOG_ERROR("[X264Encoder] InitializeExt failed: %d", initResult);
            pEncoder->Uninitialize();
            WelsDestroySVCEncoder(pEncoder);
            pEncoder = nullptr;
            return false;
        }

        LOG_DEBUG("\n[X264Encoder] =========================================\n");
        LOG_DEBUG("[X264Encoder]        X264 ENCODER CONFIG               \n");
        LOG_DEBUG("[X264Encoder] =========================================\n");
        LOG_DEBUG("[X264Encoder] Resolution : %dx%d\n", mWidth, mHeight);
        LOG_DEBUG("[X264Encoder] FPS        : %f\n", fps);
        LOG_DEBUG("[X264Encoder] Quality    : %d/100\n", quality);
        LOG_DEBUG("[X264Encoder] Bitrate    : %d.%05d Mbps\n", bitrate / 1000000, (bitrate % 1000000) / 100000);
        LOG_DEBUG("[X264Encoder] MaxBitrate : %d.%05d Mbps\n", (bitrate * 2) / 1000000, ((bitrate * 2) % 1000000) / 100000);
        LOG_DEBUG("[X264Encoder] Threads    : %d\n", param.iMultipleThreadIdc);
        LOG_DEBUG("[X264Encoder] Slices     : %d\n", param.sSpatialLayers[0].sSliceArgument.uiSliceNum);
        LOG_DEBUG("[X264Encoder] =========================================\n\n");

        return true;
    }

public:
    // ===== KHỞI TẠO VỚI ĐÚNG THỨ TỰ: width, height, fps, quality, bitrate =====
    bool Initialize(int width, int height, float fps, int quality, int bitrate) {
        if (pEncoder) {
            pEncoder->Uninitialize();
            WelsDestroySVCEncoder(pEncoder);
            pEncoder = nullptr;
        }
        return initializeEncoder(width, height, fps, quality, bitrate);
    }

    // ===== CẬP NHẬT RESOLUTION =====
    bool reinitialize(int newWidth, int newHeight) {
        return reinitialize(newWidth, newHeight, mFps, mQuality, mBitrate);
    }

    // ===== CẬP NHẬT TOÀN BỘ =====
    bool reinitialize(int newWidth, int newHeight, float fps, int quality, int bitrate) {
        normalizeResolution(newWidth, newHeight);

        if (newWidth == mWidth && newHeight == mHeight &&
            fps == mFps && quality == mQuality && bitrate == mBitrate) {
            return true;
        }

        LOG_DEBUG("[X264Encoder] Reinitializing: "
            "%dx%d@%ffps,q%d -> %dx%d@%ffps,q%d, bitrate=%dKbps",
            mWidth, mHeight, mFps, mQuality,
            newWidth, newHeight, fps, quality, bitrate / 1000);

        if (pEncoder) {
            pEncoder->Uninitialize();
            WelsDestroySVCEncoder(pEncoder);
            pEncoder = nullptr;
        }

        return initializeEncoder(newWidth, newHeight, fps, quality, bitrate);
    }

    // ===== ENCODE FRAME =====
    bool EncodeFrame(const std::vector<uint8_t>& bgraBuffer,
        std::function<void(const uint8_t*, size_t)> callback) {
        size_t expectedSize = (size_t)mWidth * mHeight * 4;
        if (bgraBuffer.size() < expectedSize) {
            std::cerr << "[X264Encoder] Buffer too small: " << bgraBuffer.size()
                << " < " << expectedSize << std::endl;
            return false;
        }

        if (!pEncoder) {
            std::cerr << "[X264Encoder] Encoder not initialized" << std::endl;
            return false;
        }

        size_t yuvSize = (size_t)mWidth * mHeight * 3 / 2;
        if (mYuvBuffer.size() != yuvSize) {
            mYuvBuffer.resize(yuvSize, 0);
        }

        uint8_t* yPlane = mYuvBuffer.data();
        uint8_t* uPlane = yPlane + (mWidth * mHeight);
        uint8_t* vPlane = uPlane + (mWidth * mHeight / 4);

        int srcStride = mWidth * 4;
        int yStride = mWidth;
        int uvStride = mWidth / 2;

        int ret = libyuv::ARGBToI420(
            bgraBuffer.data(), srcStride,
            yPlane, yStride,
            uPlane, uvStride,
            vPlane, uvStride,
            mWidth, mHeight
        );

        if (ret != 0) {
            std::cerr << "[X264Encoder] ARGBToI420 failed: " << ret << std::endl;
            return false;
        }

        SSourcePicture pic = { 0 };
        pic.iPicWidth = mWidth;
        pic.iPicHeight = mHeight;
        pic.iColorFormat = videoFormatI420;
        pic.iStride[0] = yStride;
        pic.iStride[1] = uvStride;
        pic.iStride[2] = uvStride;
        pic.pData[0] = yPlane;
        pic.pData[1] = uPlane;
        pic.pData[2] = vPlane;

        SFrameBSInfo info = { 0 };
        int encodeResult = pEncoder->EncodeFrame(&pic, &info);

        if (encodeResult != cmResultSuccess) {
            return false;
        }

        if (info.eFrameType != videoFrameTypeSkip) {
            for (int i = 0; i < info.iLayerNum; ++i) {
                SLayerBSInfo& layer = info.sLayerInfo[i];
                int pNalOffset = 0;
                for (int l = 0; l < layer.iNalCount; ++l) {
                    int currentNalSize = layer.pNalLengthInByte[l];
                    if (currentNalSize > 0 && callback) {
                        callback(layer.pBsBuf + pNalOffset, currentNalSize);
                    }
                    pNalOffset += currentNalSize;
                }
            }
            return true;
        }

        return false;
    }

    // ===== GETTERS =====
    int getWidth() const { return mWidth; }
    int getHeight() const { return mHeight; }
    float getFps() const { return mFps; }
    int getQuality() const { return mQuality; }
    int getBitrate() const { return mBitrate; }

    // ===== DESTRUCTOR =====
    ~X264Encoder() {
        if (pEncoder) {
            pEncoder->Uninitialize();
            WelsDestroySVCEncoder(pEncoder);
            pEncoder = nullptr;
        }
    }
};