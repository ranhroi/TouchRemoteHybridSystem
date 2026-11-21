#include "pch.h"
#include "parsecommand.h"
#include "processcommands.h"
#include "smartsenderbinary.h"
#include "keymapper.h"


/* ================= HÀM CHUẨN ĐỂ SO SÁNH MÃ LỆNH ================= */
static inline OpCode ParseOpcodeFast(const uint8_t* buffer, int start, int cmdLen, int bufSize) {
    uint32_t hash = FastHash::HashBuffer(buffer, start, cmdLen, bufSize);

    switch (hash) {
        // ==================== MOUSE & KEYBOARD ====================
    case HASH_CT("mos"):    return OpCode::MOS;
    case HASH_CT("key"):    return OpCode::KEY;
    case HASH_CT("utf8"):   return OpCode::UTF8;
    case HASH_CT("drag"):   return OpCode::DRAG;
    case HASH_CT("ping"):   return OpCode::PING;
    case HASH_CT("cmdrun"): return OpCode::RUNCMD;

        // ==================== POWERPOINT ====================
    case HASH_CT("ppt"):  return OpCode::PPT;
    case HASH_CT("wppt"): return OpCode::WPPT;

        // ==================== MEDIA CONTROL ====================
    case HASH_CT("media"):   return OpCode::MEDIA;
    case HASH_CT("player"):  return OpCode::PLAYER;
    case HASH_CT("browser"): return OpCode::BROWSER;

        // ==================== WINDOW CONTROL ====================
    case HASH_CT("window"):  return OpCode::WINDOW;
    case HASH_CT("zoomout"): return OpCode::ZOOMOUT;
    case HASH_CT("zoomin"):  return OpCode::ZOOMIN;

        // ==================== VOLUME CONTROL ====================
    case HASH_CT("MUTE"):     return OpCode::MUTE;
    case HASH_CT("VOLUMEUP"): return OpCode::VOLUMEUP;
    case HASH_CT("VOLUMEDN"): return OpCode::VOLUMEDN;

        // ==================== SLIDE GESTURES ====================
    case HASH_CT("slide"):      return OpCode::SLIDE;
    case HASH_CT("slideup"):    return OpCode::SLIDEUP;
    case HASH_CT("slidedown"):  return OpCode::SLIDEDOWN;
    case HASH_CT("slideleft"):  return OpCode::SLIDELEFT;
    case HASH_CT("slideright"): return OpCode::SLIDERIGHT;

        // ==================== APP MANAGEMENT ====================
    case HASH_CT("get_icon"):      return OpCode::GETICONAPP;
    case HASH_CT("launch"):        return OpCode::LAUNCHAPP;
    case HASH_CT("closeapp"):      return OpCode::CLOSEAPP;
    case HASH_CT("launchapp"):     return OpCode::LAUNCHAPP;
    case HASH_CT("get_icon_app"):  return OpCode::GETICONAPP;
    case HASH_CT("get_dock_info"): return OpCode::GETDOCKINFOAPP;

        // ==================== POWER CONTROL ====================
    case HASH_CT("sleep"):          return OpCode::SLEEP;
    case HASH_CT("logoff"):         return OpCode::LOGOFF;
    case HASH_CT("reboot"):         return OpCode::REBOOT;
    case HASH_CT("poweroff"):       return OpCode::POWEROFF;
    case HASH_CT("brightnesslow"):  return OpCode::BRIGHNESSLOW;
    case HASH_CT("brightnesshigh"): return OpCode::BRIGHNESSHIGH;

        // ==================== TASK MANAGEMENT ====================
    case HASH_CT("gettasks"):    return OpCode::GETTASKS;
    case HASH_CT("taskmanager"): return OpCode::GETTASKS;

        // ==================== SCREEN CAPTURE ====================
    case HASH_CT("screenCapture"): return OpCode::SCREENCAPTURE;


        // ==================== STREAMING ====================
    case HASH_CT("mirror"): return OpCode::SCREENMIRROR;

        // ==================== APP REPORTING ====================
    case HASH_CT("reportCurrentApp"):     return OpCode::REPORTCURRENTAPP;
    case HASH_CT("dontreportCurrentApp"): return OpCode::DONTREPORTCURRENTAPP;

    default: return OpCode::UNKNOWN;
    }
}


/* ================= CONSTRUCTOR ================= */
ParseCommand::ParseCommand(SOCKET socket) : m_socket(socket) {
    head = 0;
    tail = 0;
    std::memset(buffer, 0, BUF_SIZE);
}

ParseCommand::~ParseCommand() {}

void ParseCommand::reset() {
    head = 0;
    tail = 0;
    std::memset(buffer, 0, BUF_SIZE);
}

/* ================= PUSH DATA - COPY BLOCK TỐI ƯU ================= */
void ParseCommand::pushData(const uint8_t* data, int len) {
    if (len <= 0 || !data) return;

    // Tính space còn lại trong buffer
    int spaceLeft = BUF_SIZE - ((tail - head + BUF_SIZE) % BUF_SIZE) - 1;
    if (len > spaceLeft) {
        len = spaceLeft;
        if (len <= 0) return;
    }

    // Copy block đầu tiên
    int firstCopy = std::min<int>(len, BUF_SIZE - tail);
    std::memcpy(buffer + tail, data, firstCopy);
    tail = (tail + firstCopy) % BUF_SIZE;

    // Copy block thứ hai (nếu cần)
    if (len > firstCopy) {
        std::memcpy(buffer, data + firstCopy, len - firstCopy);
        tail = len - firstCopy;
    }

    tryParse();
}

/* ================= TRY PARSE - ZERO COPY ================= */
void ParseCommand::tryParse() {
    int maxIterations = 200;
    int iterations = 0;
    int lastHead = -1;

    while (head != tail && iterations < maxIterations) {
        iterations++;

        if (head == lastHead) {
            head = (head + 1) % BUF_SIZE;
            continue;
        }
        lastHead = head;

        // 1. Bỏ qua khoảng trắng đầu (chỉ khoảng trắng, tab, CR, LF)
        while (head != tail && KeyMapper::isWhitespace(buffer[head])) {
            head = (head + 1) % BUF_SIZE;
        }

        if (head == tail) return;

        // 2. ĐỌC OPCODE (chỉ gồm chữ cái, không có khoảng trắng)
        int cmdStart = head;
        int pos = head;
        int cmdLen = 0;

        while (pos != tail && !KeyMapper::isWhitespace(buffer[pos])) {
            pos = (pos + 1) % BUF_SIZE;
            cmdLen++;
            if (cmdLen > 32) { // thiết lập độ dài của mã lệnh nếu mã lệnh dài lên
                head = (head + 1) % BUF_SIZE;
                cmdLen = -1;
                break;
            }
        }

        if (cmdLen <= 0 || pos == tail) return;

        // 3. PARSE OPCODE
        OpCode op = ParseOpcodeFast(buffer, cmdStart, cmdLen, BUF_SIZE);

        // 4. XỬ LÝ KHOẢNG TRẮNG SAU OPCODE
        int scan = pos;

        if (op == OpCode::UTF8) {
            // UTF8: CHỈ BỎ QUA 1 SPACE ĐẦU TIÊN (phân cách opcode và text)
            if (scan != tail && KeyMapper::isWhitespace(buffer[scan])) {
                scan = (scan + 1) % BUF_SIZE;
            }
            // Các khoảng trắng SAU ĐÓ là DỮ LIỆU, KHÔNG BỎ QUA
        }
        else {
            // Các lệnh khác: BỎ QUA TẤT CẢ khoảng trắng
            while (scan != tail && KeyMapper::isWhitespace(buffer[scan])) {
                scan = (scan + 1) % BUF_SIZE;
            }
        }

        // 5. ĐỌC LENGTH (nếu có)
        int dataLen = 0;
        bool hasLen = false;
        int lenPos = scan;

        if (lenPos != tail && buffer[lenPos] >= '0' && buffer[lenPos] <= '9') {
            hasLen = true;
            while (lenPos != tail && buffer[lenPos] >= '0' && buffer[lenPos] <= '9') {
                dataLen = dataLen * 10 + (buffer[lenPos] - '0');
                lenPos = (lenPos + 1) % BUF_SIZE;
            }
            // Bỏ qua khoảng trắng sau length
            while (lenPos != tail && KeyMapper::isWhitespace(buffer[lenPos])) {
                lenPos = (lenPos + 1) % BUF_SIZE;
            }
        }

        int payloadStart;
        int payloadLen;
        int nextHead;

        if (hasLen && (op == OpCode::MOS || op == OpCode::KEY)) {
            // LỆNH CÓ LENGTH PREFIX
            payloadStart = lenPos;

            int available = (tail >= payloadStart) ?
                tail - payloadStart : BUF_SIZE - payloadStart + tail;

            if (available < dataLen) return;

            payloadLen = dataLen;
            nextHead = (payloadStart + dataLen) % BUF_SIZE;

            dispatch(op, payloadStart, payloadLen);
            head = nextHead;
        }
        else {
            // LỆNH KHÔNG CÓ LENGTH PREFIX
            if (op == OpCode::UTF8) {
                // UTF8: PAYLOAD BẮT ĐẦU TỪ SAU KHOẢNG TRẮNG ĐẦU TIÊN
                payloadStart = scan;
                // KHÔNG BỎ QUA BẤT KỲ KHOẢNG TRẮNG NÀO TRONG PAYLOAD
            }
            else {
                payloadStart = scan;
                // Bỏ qua khoảng trắng đầu payload (nếu có)
                if (payloadStart != tail && KeyMapper::isWhitespace(buffer[payloadStart])) {
                    payloadStart = (payloadStart + 1) % BUF_SIZE;
                }
            }

            // Tìm kết thúc payload (gặp \n hoặc \r)
            int endPos = payloadStart;
            int maxSearch = 4096; // Tăng lên cho text dài

            while (endPos != tail && maxSearch > 0) {
                if (buffer[endPos] == '\n' || buffer[endPos] == '\r') {
                    // KHÔNG bỏ qua endPos, dừng ngay trước nó
                    break;
                }
                endPos = (endPos + 1) % BUF_SIZE;
                maxSearch--;
            }

            if (maxSearch <= 0) {
                head = (head + 1) % BUF_SIZE;
                continue;
            }

            // Tính payload length
            if (endPos >= payloadStart) {
                payloadLen = endPos - payloadStart;
            }
            else {
                payloadLen = BUF_SIZE - payloadStart + endPos;
            }

            dispatch(op, payloadStart, payloadLen);
            head = endPos;
        }

        // Bỏ qua CR/LF sau payload
        while (head != tail && (buffer[head] == '\n' || buffer[head] == '\r')) {
            head = (head + 1) % BUF_SIZE;
        }
    }

    if (iterations >= maxIterations) {
        head = tail;
    }
}


/* ================= DISPATCH ================= */
void ParseCommand::dispatch(OpCode op, int start, int len) {
    const uint8_t* payloadPtr = reinterpret_cast<const uint8_t*>(buffer) + start;

    // =========================================================================
    // LUỒNG 1: LUỒNG ĐIỀU KHIỂN (CONTROL STREAM) - Ưu tiên tốc độ, dung lượng siêu nhẹ
    // Chứa Chuột, Bàn phím, Media, Power... (Không bao giờ làm nghẽn mạng)
    // =========================================================================
    switch (op) {
    case OpCode::MOS: ProcessCommands::mouseHandle(payloadPtr, len, op); return;
    case OpCode::KEY: ProcessCommands::handleKey(payloadPtr, len, op); return;
    case OpCode::UTF8: ProcessCommands::handleUtf8(payloadPtr, len, op); return;
    case OpCode::DRAG: ProcessCommands::dragHandle(payloadPtr, len, op); return;

    case OpCode::SLIDE:
    case OpCode::SLIDEUP: 
    case OpCode::SLIDEDOWN:
    case OpCode::SLIDELEFT: 
    case OpCode::SLIDERIGHT: 
        ProcessCommands::handleSlide(payloadPtr, len, op); return;

    case OpCode::ZOOMIN: 
    case OpCode::ZOOMOUT: 
        ProcessCommands::handleZoom(payloadPtr, len, op); return;

    case OpCode::WINDOW: ProcessCommands::handleWindow(payloadPtr, len, op); return;
    case OpCode::MEDIA: ProcessCommands::handleMedia(payloadPtr, len, op); return;
    case OpCode::PPT: ProcessCommands::handlePPT(payloadPtr, len, op); return;
    case OpCode::WPPT: ProcessCommands::handleWPPT(payloadPtr, len, op); return;
    case OpCode::PLAYER: ProcessCommands::handlePlayer(payloadPtr, len, op); return;
    case OpCode::BROWSER: ProcessCommands::handleBrowser(payloadPtr, len, op); return;

    case OpCode::MUTE: 
    case OpCode::VOLUMEUP: 
    case OpCode::VOLUMEDN: 
        ProcessCommands::handleVolum(payloadPtr, len, op); return;

    case OpCode::SLEEP: 
    case OpCode::LOGOFF: 
    case OpCode::REBOOT:
    case OpCode::POWEROFF: 
    case OpCode::BRIGHNESSHIGH: 
    case OpCode::BRIGHNESSLOW: 
        ProcessCommands::handlePower(payloadPtr, len, op); return;

    case OpCode::RUNCMD: 
        ProcessCommands::handleCommandRun(payloadPtr, len, op, m_socket); return;

    case OpCode::GETTASKS: 
        ProcessCommands::handleTaskManager(payloadPtr, len, op, m_socket); return;

    case OpCode::REPORTCURRENTAPP: 
    case OpCode::DONTREPORTCURRENTAPP: 
        ProcessCommands::handleCurrentApp(payloadPtr, len, op, m_socket); return;

    case OpCode::PING: SenderBinary::send(m_socket, "pong\n"); return;

    case OpCode::GETDOCKINFOAPP: 
    case OpCode::GETICONAPP:
    case OpCode::LAUNCHAPP: 
    case OpCode::CLOSEAPP:
        ProcessCommands::handleGetApp(payloadPtr, len, op, m_socket); return;

    case OpCode::SCREENCAPTURE:
        ProcessCommands::handleScreenCapture(payloadPtr, len, op); return;

    case OpCode::SCREENMIRROR:
        ProcessCommands::handleScreenStream(payloadPtr, len, op, m_socket);
        break;

    default: break; // Chuyển xuống kiểm tra luồng File
    }
}
