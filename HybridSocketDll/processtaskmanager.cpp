#include "pch.h"
#include "processcommands.h"
#include "smartsenderbinary.h"
#include "taskmanager.h"



// ==================== TASK MANAGER ====================
void ProcessCommands::handleTaskManager(const uint8_t* payload, int len, OpCode op, SOCKET socket) {
    if (len <= 0 || socket == INVALID_SOCKET) return;

    const uint8_t* ptr = payload;
    const uint8_t* end = payload + len;

    // Skip whitespace
    while (ptr < end && *ptr <= 32) ptr++;
    if (ptr >= end) {
        LOG_DEBUG("[TASK] Empty payload after trim");
        return;
    }

    // Tìm end of command
    const uint8_t* cmdStart = ptr;
    while (ptr < end && *ptr > 32) ptr++;
    size_t cmdLen = ptr - cmdStart;

    if (cmdLen == 0) {
        LOG_DEBUG("[TASK] No command found");
        return;
    }

    std::string cmdStr((const char*)cmdStart, cmdLen);
    LOG_DEBUG("[TASK] Command: '%s', hash=%u", cmdStr.c_str(), FastHash::HashBytes(cmdStart, cmdLen));

    // Hash command
    uint32_t h = FastHash::HashBytes(cmdStart, cmdLen);

    if (h == HASH_CT("get")) {
        LOG_DEBUG("[TASK] Executing 'get' command - fetching process list");

        std::string json = TaskManager::getOneTimeSnapshot();

        if (!json.empty()) {
            std::string response = "task_manager " + json + "\n";
            LOG_DEBUG("[TASK] Sending response, size=%zu bytes", response.size());
            SenderBinary::send(socket, response);
        }
        else {
            LOG_DEBUG("[TASK] Empty JSON result, sending empty response");
            SenderBinary::send(socket, "task_manager {}\n");
        }
    }
    else if (h == HASH_CT("kill")) {
        LOG_DEBUG("[TASK] Executing 'kill' command");

        // Skip to PID
        while (ptr < end && *ptr <= 32) ptr++;
        if (ptr >= end) {
            LOG_DEBUG("[TASK] No PID provided for kill command");
            SenderBinary::send(socket, "kill_failed no_pid\n");
            return;
        }

        DWORD pid = 0;
        const uint8_t* pidStart = ptr;
        while (ptr < end && *ptr >= '0' && *ptr <= '9') {
            pid = pid * 10 + (*ptr - '0');
            ptr++;
        }

        std::string pidStr((const char*)pidStart, ptr - pidStart);
        LOG_DEBUG("[TASK] Killing PID: %s (%u)", pidStr.c_str(), pid);  

        if (pid > 0) {
            HANDLE hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, pid);
            if (hProcess) {
                if (TerminateProcess(hProcess, 0)) {
                    LOG_DEBUG("[TASK] Successfully killed PID: %u", pid);
                    SenderBinary::send(socket, "kill_ok " + std::to_string(pid) + "\n");
                }
                else {
                    DWORD err = GetLastError();
                    LOG_ERROR("[TASK] Failed to kill PID: %u, error=%u", pid, err);
                    SenderBinary::send(socket, "kill_failed " + std::to_string(pid) + "\n");
                }
                CloseHandle(hProcess);
            }
            else {
                DWORD err = GetLastError();
                LOG_ERROR("[TASK] Cannot open process PID: %u, error=%u", pid, err);
                SenderBinary::send(socket, "kill_failed " + std::to_string(pid) + "\n");
            }
        }
        else {
            LOG_DEBUG("[TASK] Invalid PID value: %s", pidStr.c_str());
            SenderBinary::send(socket, "kill_failed invalid_pid\n");
        }
    }
    else {
        LOG_DEBUG("[TASK] Unknown command: '%s'", cmdStr.c_str());
    }
}
