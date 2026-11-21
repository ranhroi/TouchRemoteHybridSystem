#pragma once

struct Frame {
    std::vector<uint8_t> data; // BGRA 4 bytes
    int width = 0;
    int height = 0;
    uint64_t ptsMs = 0;
};

class ScreenCapturer {
public:
    ScreenCapturer();
    ~ScreenCapturer();  // ✅ THÊM DESTRUCTOR

    bool init();
    bool init(int targetWidth, int targetHeight);
    bool capture(Frame& out);
    int getWidth() const { return width_; }
    int getHeight() const { return height_; }

private:
    bool initD3D();
    bool initDuplication();
    void resizeFrame(Frame& out, int targetWidth, int targetHeight);

    // ✅ DÙNG Microsoft::WRL::ComPtr để tự động giải phóng
    Microsoft::WRL::ComPtr<ID3D11Device> device_;
    Microsoft::WRL::ComPtr<ID3D11DeviceContext> context_;
    Microsoft::WRL::ComPtr<IDXGIOutputDuplication> deskDup_;
    Microsoft::WRL::ComPtr<ID3D11Texture2D> stagingTex_;
    int width_ = 0, height_ = 0;
    int targetWidth_ = 0, targetHeight_ = 0;
};