#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"
#include "cmdrun.h"

void ProcessCommands::handleCommandRun(const uint8_t* payload, int len, OpCode op, SOCKET socket) {
    if (len <= 0 || socket == INVALID_SOCKET) return;
    if (op != OpCode::RUNCMD) return;

    std::string cmd((const char*)payload, len);

    // Trim khoảng trắng đầu cuối
    size_t start = cmd.find_first_not_of(" \t\n\r");
    if (start != std::string::npos) cmd = cmd.substr(start);
    size_t end = cmd.find_last_not_of(" \t\n\r");
    if (end != std::string::npos) cmd = cmd.substr(0, end + 1);

    if (cmd.empty()) return;

    // Sử dụng std::format thay vì cộng chuỗi rườm rà
    LOG_DEBUG("[CommandRun] Received: {}", cmd);

    // Lệnh gợi ý
    if (cmd == "suggestions" || cmd == "help") {
        std::string suggestions = CmdRun::getCommandSuggestions();
        size_t pos = 0;
        while ((pos = suggestions.find('\n', pos)) != std::string::npos) {
            suggestions.replace(pos, 1, "\r\n");
            pos += 2;
        }
        SenderBinary::send(socket, std::format("[CMD]suggestions:\r\n{}\r\n", suggestions));
        return;
    }

    // Chuyển sang chuỗi thường dùng std::transform chuẩn hóa
    std::string cmdLower = cmd;
    std::transform(cmdLower.begin(), cmdLower.end(), cmdLower.begin(), ::tolower);

    // Xử lý lệnh start / open (Chạy ứng dụng Windows)
    if (cmdLower.find("start ") == 0 || cmdLower.find("open ") == 0) {
        size_t spacePos = cmd.find(' ');
        std::string app = (spacePos != std::string::npos) ? cmd.substr(spacePos + 1) : "";
        if (!app.empty()) {
            ShellExecuteA(NULL, "open", app.c_str(), NULL, NULL, SW_SHOWNORMAL);
            SenderBinary::send(socket, std::format("[CMD][OK] Started: {}\r\n", app));
        }
        else {
            SenderBinary::send(socket, "[CMD][Error] No application specified\r\n");
        }
        return;
    }

    // ===== PHÂN LOẠI XỬ LÝ LỆNH =====
    if (CmdRun::isFastCommand(cmd)) {
        LOG_DEBUG("[CommandRun] Fast command. Offloading to background thread to prevent socket blocking...");

        // GIẢI PHÁP CỐT LÕI: Tạo một luồng riêng để chạy lệnh nhanh.
        // Luồng mạng chính được giải phóng NGAY LẬP TỨC để tiếp tục nhận lệnh khác.
        std::thread([cmd, socket]() {
            std::string result = CmdRun::runCommandFast(cmd);

            // Thêm prefix [CMD] vào mỗi dòng
            std::string prefixed;
            size_t pos = 0, last = 0;
            while ((pos = result.find('\n', last)) != std::string::npos) {
                std::string line = result.substr(last, pos - last);
                if (!line.empty() && line.find_first_not_of(" \t\r") != std::string::npos) {
                    prefixed += "[CMD]" + line + "\r\n";
                }
                last = pos + 1;
            }
            if (last < result.length()) {
                std::string line = result.substr(last);
                if (!line.empty() && line.find_first_not_of(" \t\r") != std::string::npos) {
                    prefixed += "[CMD]" + line + "\r\n";
                }
            }

            if (prefixed.empty()) {
                prefixed = "[CMD][Done]\r\n";
            }

            // Gửi dữ liệu về từ luồng phụ an toàn
            SenderBinary::send(socket, prefixed);
            }).detach(); // Ngắt kết nối luồng để nó tự giải phóng khi chạy xong
    }
    else {
        // Lệnh CHẬM: chạy nền, không chờ, trả về thông báo ngay lập tức
        LOG_DEBUG("[CommandRun] Slow command, running in background...");
        CmdRun::runCommandBackground(cmd);
        SenderBinary::send(socket, "[CMD][Background] Command is running in background (may take several minutes)\r\n");
    }
}
