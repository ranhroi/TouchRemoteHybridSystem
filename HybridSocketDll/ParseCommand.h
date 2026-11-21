#pragma once

class ParseCommand {
public:
    // Thay QIODevice* bằng con trỏ void* hoặc bỏ đi nếu xử lý nhận mạng ở lớp khác
    ParseCommand(SOCKET socket);
    ~ParseCommand();

    void reset();
    void pushData(const uint8_t* data, int len); 

private:
    void tryParse();
    void dispatch(OpCode op, int start, int len);

private:
    static const int BUF_SIZE = 8192; // Giả định kích thước buffer của bạn
    uint8_t buffer[BUF_SIZE];
    int head;
    int tail;
    SOCKET m_socket; // Lưu trữ nếu cần thiết
};
