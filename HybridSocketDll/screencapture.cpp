#include "pch.h"
#include "screencapture.h"

using namespace Microsoft::WRL;

ScreenCapturer::ScreenCapturer() {}

ScreenCapturer::~ScreenCapturer() {
    // ComPtr tự động giải phóng, nhưng cần ReleaseFrame nếu có
    if (deskDup_) {
        deskDup_->ReleaseFrame();
        deskDup_.Reset();
    }
    stagingTex_.Reset();
    context_.Reset();
    device_.Reset();
}

// ===== GIỮ NGUYÊN init() CŨ =====
bool ScreenCapturer::init() {
    targetWidth_ = 0;
    targetHeight_ = 0;
    return initD3D() && initDuplication();
}

// ===== THÊM init() MỚI NHẬN TARGET SIZE =====
bool ScreenCapturer::init(int targetWidth, int targetHeight) {
    targetWidth_ = targetWidth;
    targetHeight_ = targetHeight;
    
    bool result = initD3D() && initDuplication();
    
    if (result) {
        LOG_DEBUG("[SCREEN_CAP] Real: %dx%d", width_, height_);
        if (targetWidth_ > 0 && targetHeight_ > 0) {
            LOG_DEBUG("[SCREEN_CAP] Target: %dx%d", targetWidth_, targetHeight_);
        }
    }
    
    return result;
}

bool ScreenCapturer::initD3D() {
    UINT flags = D3D11_CREATE_DEVICE_BGRA_SUPPORT;
    D3D_FEATURE_LEVEL levels[] = { D3D_FEATURE_LEVEL_11_0 };
    D3D_FEATURE_LEVEL outLevel;
    HRESULT hr = D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, flags, levels, 1, D3D11_SDK_VERSION, &device_, &outLevel, &context_);
    if (FAILED(hr)) { LOG_ERROR("[SCREEN_CAP] D3D11CreateDevice failed: 0x%08X", hr); return false; }
    return true;
}

bool ScreenCapturer::initDuplication() {
    ComPtr<IDXGIDevice> dxgiDevice;
    if (FAILED(device_.As(&dxgiDevice))) return false;

    ComPtr<IDXGIAdapter> adapter;
    dxgiDevice->GetAdapter(&adapter);

    ComPtr<IDXGIOutput> output;
    if (FAILED(adapter->EnumOutputs(0, &output))) { LOG_ERROR("[SCREEN_CAP] EnumOutputs failed"); return false; }

    DXGI_OUTPUT_DESC desc;
    output->GetDesc(&desc);
    width_ = desc.DesktopCoordinates.right - desc.DesktopCoordinates.left;
    height_ = desc.DesktopCoordinates.bottom - desc.DesktopCoordinates.top;

    ComPtr<IDXGIOutput1> output1;
    if (FAILED(output.As(&output1))) { LOG_ERROR("[SCREEN_CAP] Output.As failed"); return false; }

    HRESULT hr = output1->DuplicateOutput(device_.Get(), &deskDup_);
    if (FAILED(hr)) { LOG_ERROR("[SCREEN_CAP] DuplicateOutput failed: 0x%08X", hr); return false; }

    D3D11_TEXTURE2D_DESC descTex{};
    descTex.Width = width_;
    descTex.Height = height_;
    descTex.MipLevels = 1;
    descTex.ArraySize = 1;
    descTex.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
    descTex.SampleDesc.Count = 1;
    descTex.Usage = D3D11_USAGE_STAGING;
    descTex.CPUAccessFlags = D3D11_CPU_ACCESS_READ;

    hr = device_->CreateTexture2D(&descTex, nullptr, &stagingTex_);
    if (FAILED(hr)) { LOG_ERROR("[SCREEN_CAP] CreateTexture2D failed: 0x%08X", hr); return false; }
    return true;
}

// ===== HÀM RESIZE ẢNH =====
void ScreenCapturer::resizeFrame(Frame& out, int targetWidth, int targetHeight) {
    if (targetWidth <= 0 || targetHeight <= 0) return;
    if (out.width == targetWidth && out.height == targetHeight) return;
    
    // Tạo buffer mới cho ảnh resize
    std::vector<uint8_t> resized(targetWidth * targetHeight * 4);
    
    // Sử dụng libyuv để resize BGRA
    int ret = libyuv::ARGBScale(
        out.data.data(),      // src_argb
        out.width * 4,        // src_stride_argb
        out.width,            // src_width
        out.height,           // src_height
        resized.data(),       // dst_argb
        targetWidth * 4,      // dst_stride_argb
        targetWidth,          // dst_width
        targetHeight,         // dst_height
        libyuv::kFilterBilinear  // Filter
    );
    
    if (ret == 0) {
        out.data = std::move(resized);
        out.width = targetWidth;
        out.height = targetHeight;
    } else {
        LOG_ERROR("[SCREEN_CAP] Resize failed: %d", ret);
    }
}

bool ScreenCapturer::capture(Frame& out) {
    DXGI_OUTDUPL_FRAME_INFO frameInfo{};
    Microsoft::WRL::ComPtr<IDXGIResource> desktopResource;
    HRESULT hr = deskDup_->AcquireNextFrame(50, &frameInfo, &desktopResource);
    if (hr == DXGI_ERROR_WAIT_TIMEOUT) return false;
    if (FAILED(hr)) { LOG_ERROR("[SCREEN_CAP] AcquireNextFrame failed: 0x%08X", hr); return false; }

    Microsoft::WRL::ComPtr<ID3D11Texture2D> acquired;
    desktopResource.As(&acquired);

    context_->CopyResource(stagingTex_.Get(), acquired.Get());

    D3D11_MAPPED_SUBRESOURCE mapped;
    hr = context_->Map(stagingTex_.Get(), 0, D3D11_MAP_READ, 0, &mapped);
    if (FAILED(hr)) {
        deskDup_->ReleaseFrame();
        LOG_ERROR("[SCREEN_CAP] Map failed: 0x%08X", hr);
        return false;
    }

    out.width = width_; 
    out.height = height_;
    size_t rowBytes = (size_t)width_ * 4;
    out.data.resize(rowBytes * height_);

    uint8_t* dst = out.data.data();
    uint8_t* src = reinterpret_cast<uint8_t*>(mapped.pData);
    for (int y = 0; y < height_; ++y) {
        memcpy(dst + (size_t)y * rowBytes, src + (size_t)y * mapped.RowPitch, rowBytes);
    }

    context_->Unmap(stagingTex_.Get(), 0);
    deskDup_->ReleaseFrame();
    out.ptsMs = (uint64_t)std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    
    // ✅ NẾU CÓ TARGET, RESIZE ẢNH
    if (targetWidth_ > 0 && targetHeight_ > 0) {
        resizeFrame(out, targetWidth_, targetHeight_);
    }
    
    return true;
}