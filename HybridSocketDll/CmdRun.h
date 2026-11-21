#pragma once

class CmdRun {
public:
    // Chỉ dùng cho lệnh NHANH (dưới 5 giây)
    static std::string runCommandFast(const std::string& command);

    // Chạy lệnh nền, KHÔNG trả kết quả
    static void runCommandBackground(const std::string& command);

    // Kiểm tra lệnh có phải lệnh nhanh không
    static bool isFastCommand(const std::string& command);

    // Lấy gợi ý
    static std::string getCommandSuggestions();
};