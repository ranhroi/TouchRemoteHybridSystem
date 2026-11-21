#pragma once

template <typename T>
class SafeQueue {
private:
    std::queue<T> mQueue;
    mutable std::mutex mMutex;              // ✅ MUTEX DÙNG CHO LOCK
    std::condition_variable mCV;
    size_t mMaxSize;
    std::atomic<size_t> mDroppedCount{ 0 };   // ✅ ATOMIC KHÔNG CẦN LOCK
    std::atomic<size_t> mTotalCount{ 0 };     // ✅ ATOMIC KHÔNG CẦN LOCK

public:
    SafeQueue(size_t maxSize = 10) : mMaxSize(maxSize) {}

    // ===== PUSH =====
    bool push(T value) {
        // ✅ ĐÚNG: lock_guard với std::mutex
        std::lock_guard<std::mutex> lock(mMutex);

        mTotalCount++;  // ✅ Atomic, không cần lock

        if (mQueue.size() >= mMaxSize) {
            mQueue.pop();
            mDroppedCount++;  // ✅ Atomic, không cần lock
            mQueue.push(std::move(value));
            mCV.notify_one();
            return false;
        }

        mQueue.push(std::move(value));
        mCV.notify_one();
        return true;
    }

    // ===== POP (BLOCKING) =====
    bool pop(T& value, const std::atomic<bool>& running) {
        // ✅ ĐÚNG: unique_lock với std::mutex
        std::unique_lock<std::mutex> lock(mMutex);
        mCV.wait(lock, [this, &running]() {
            return !mQueue.empty() || !running.load();
            });

        if (!running.load() && mQueue.empty()) {
            return false;
        }

        value = std::move(mQueue.front());
        mQueue.pop();
        return true;
    }

    // ===== POP WITH TIMEOUT =====
    bool pop(T& value, const std::atomic<bool>& running, int timeoutMs) {
        // ✅ ĐÚNG: unique_lock với std::mutex
        std::unique_lock<std::mutex> lock(mMutex);
        mCV.wait_for(lock, std::chrono::milliseconds(timeoutMs), [this, &running]() {
            return !mQueue.empty() || !running.load();
            });

        if (!running.load() && mQueue.empty()) {
            return false;
        }

        if (mQueue.empty()) {
            return false;
        }

        value = std::move(mQueue.front());
        mQueue.pop();
        return true;
    }

    // ===== SIZE =====
    size_t size() const {
        // ✅ ĐÚNG: lock_guard với std::mutex (const method)
        std::lock_guard<std::mutex> lock(mMutex);
        return mQueue.size();
    }

    // ===== EMPTY =====
    bool empty() const {
        // ✅ ĐÚNG: lock_guard với std::mutex (const method)
        std::lock_guard<std::mutex> lock(mMutex);
        return mQueue.empty();
    }

    // ===== DROPPED COUNT =====
    size_t droppedCount() const {
        // ✅ ATOMIC: không cần lock
        return mDroppedCount.load();
    }

    // ===== TOTAL COUNT =====
    size_t totalCount() const {
        // ✅ ATOMIC: không cần lock
        return mTotalCount.load();
    }

    // ===== CLEAR =====
    void clear() {
        // ✅ ĐÚNG: lock_guard với std::mutex
        std::lock_guard<std::mutex> lock(mMutex);
        std::queue<T> empty;
        std::swap(mQueue, empty);
        mDroppedCount = 0;
        mTotalCount = 0;
        mCV.notify_all();
    }

    // ===== RESET STATS =====
    void resetStats() {
        // ✅ ATOMIC: không cần lock
        mDroppedCount = 0;
        mTotalCount = 0;
    }
};

// ===== CẤU TRÚC DỮ LIỆU =====
struct EncodedFrameData {
    uint32_t frameId = 0;
    uint32_t timestamp = 0;
    std::vector<std::vector<uint8_t>> nals;

    void clear() {
        nals.clear();
        frameId = 0;
        timestamp = 0;
    }

    bool empty() const {
        return nals.empty();
    }

    size_t totalSize() const {
        size_t total = 0;
        for (const auto& nal : nals) {
            total += nal.size();
        }
        return total;
    }
};