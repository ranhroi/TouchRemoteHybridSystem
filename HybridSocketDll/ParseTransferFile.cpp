#include "pch.h"
#include "parsetransferFile.h"
#include "processcommands.h"
#include "sessioncontext.h" 

/* ================= HÀM CHUẨN ĐỂ SO SÁNH MÃ LỆNH ================= */
// Chuyển quint8 sang uint8_t
static inline OpCode ParseOpcodeFast(const uint8_t* buffer, int start, int cmdLen, int bufSize) {
    uint32_t hash = FastHash::HashBuffer(buffer, start, cmdLen, bufSize);

    switch (hash) {
    case HASH_CT("ping"):              return OpCode::PING;
        // ==================== FILE EXPLORER ====================
    case HASH_CT("openfile"):           return OpCode::OPENFILE;
    case HASH_CT("copyfile"):           return OpCode::COPYFILE;
    case HASH_CT("cutfile"):            return OpCode::CUTFILE;
    case HASH_CT("uploadfile"):         return OpCode::UPLOADFILE;
    case HASH_CT("pastefile"):          return OpCode::PASTEFILE;
    case HASH_CT("deletefile"):         return OpCode::DELETEFILE;
    case HASH_CT("detailsfile"):        return OpCode::DETAILSFILE;
    case HASH_CT("downloadfile"):       return OpCode::DOWNLOADFILE;
    case HASH_CT("renamefile"):         return OpCode::RENAMEFILE;
    case HASH_CT("createfile"):         return OpCode::CREATEFILE;
    case HASH_CT("createfolder"):       return OpCode::CREATEFOLDER;
    case HASH_CT("fileexplorer"):       return OpCode::FILEEXPLORER;
    case HASH_CT("getfolderinfo"):      return OpCode::GETFOLDERINFO;
    case HASH_CT("uploadfolder_zip"):   return OpCode::UPLOADFOLDERZIP;
    case HASH_CT("downloadfolder_zip"): return OpCode::DOWNLOADFOLDERZIP;

        // ==================== TRANSFER CONTROL ====================
    case HASH_CT("stop_transfer"):       return OpCode::STOPTRANSFER;

    default: return OpCode::UNKNOWN;
    }
}

ParseTransferFile::ParseTransferFile(SOCKET socket) : m_socket(socket) {}

ParseTransferFile::~ParseTransferFile() {
    clear();
}

void ParseTransferFile::clear() {
    m_inputBuffer.clear();
}

// Cổng nhận dữ liệu tập trung từ luồng chính đưa vào
void ParseTransferFile::pushData(const uint8_t* data, int len) {
    if (len <= 0 || !data) return;

    // Đóng gói vào bộ đệm std::string để phân tích chuỗi lệnh text (\n)
    m_inputBuffer.append(reinterpret_cast<const char*>(data), len);
    parseCommands();
}

// Hàm phụ trợ cắt khoảng trắng (chế độ trim của Qt)
std::string ParseTransferFile::trim(const std::string& str) {
    size_t first = str.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return "";
    size_t last = str.find_last_not_of(" \t\r\n");
    return str.substr(first, (last - first + 1));
}

void ParseTransferFile::parseCommands() {
    size_t lineEndIndex;

    // Tìm ký tự xuống dòng \n tương đương m_inputBuffer.indexOf('\n')
    while ((lineEndIndex = m_inputBuffer.find('\n')) != std::string::npos) {
        // Cắt lấy dòng văn bản lệnh và xóa khoảng trắng thừa thừa (Trim)
        std::string line = trim(m_inputBuffer.substr(0, lineEndIndex));

        // Xóa dòng đã xử lý ra khỏi hàng đợi bộ đệm (tương đương .remove(0, len))
        m_inputBuffer.erase(0, lineEndIndex + 1);

        if (line.empty()) continue;

        // ✅ XỬ LÝ PING NGAY, KHÔNG QUA DISPATCH
        if (line == "ping") {
            // Kiểm tra trạng thái socket thô còn kết nối hợp lệ hay không 
            if (m_socket && m_socket != INVALID_SOCKET) {
                SenderBinary::send(m_socket, "pong\n");
            }
            continue;  // Bỏ qua, không đưa vào dispatch
        }

        size_t spaceIndex = line.find(' ');
        std::string opPart = (spaceIndex == std::string::npos) ? line : line.substr(0, spaceIndex);
        std::string payloadPart = (spaceIndex == std::string::npos) ? "" : line.substr(spaceIndex + 1);

        OpCode op = ParseOpcodeFast(
            reinterpret_cast<const uint8_t*>(opPart.data()),
            0,
            static_cast<int>(opPart.size()),
            static_cast<int>(opPart.size())
        );

        if (op != OpCode::UNKNOWN) {
            dispatch(op, payloadPart);
        }
    }
}

void ParseTransferFile::dispatch(OpCode op, const std::string& payload) {
    if (!m_socket || m_socket == INVALID_SOCKET) return;

    switch (op) {
        // Nhóm thao tác nhẹ (Quản lý File)
    case OpCode::FILEEXPLORER:
    case OpCode::GETFOLDERINFO:
    case OpCode::CUTFILE:
    case OpCode::OPENFILE:
    case OpCode::COPYFILE:
    case OpCode::PASTEFILE:
    case OpCode::DELETEFILE:
    case OpCode::RENAMEFILE:
    case OpCode::CREATEFILE:
    case OpCode::CREATEFOLDER:
    case OpCode::DETAILSFILE:

        // Nhóm thao tác Nặng (Truyền tải File Nhị phân)
    case OpCode::UPLOADFILE:
    case OpCode::DOWNLOADFILE:
    case OpCode::UPLOADFOLDERZIP:
    case OpCode::DOWNLOADFOLDERZIP:
    case OpCode::STOPTRANSFER:
        // Gọi hàm xử lý và truyền đối tượng m_socket sang làm đích nhận phản hồi dữ liệu mạng
        ProcessCommands::handleFileExplorer(reinterpret_cast<const uint8_t*>(payload.data()), static_cast<int>(payload.size()), op, m_socket);
        break;
    default:
        break;
    }
}
